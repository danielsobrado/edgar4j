package org.jds.edgar4j.service.provider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.jds.edgar4j.properties.MarketDataProviderProperties;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataService {

    private final List<MarketDataProvider> providers;
    private final MarketDataProviderProperties properties;
    private final MarketDataProviderSettingsResolver settingsResolver;

    @Cacheable(
            value = "stockPrices",
            key = "#symbol + '_' + @marketDataProviderSettingsResolver.resolvePreferredProviderName()",
            condition = "@marketDataProviderProperties.cache.enabled",
            unless = "#result == null")
    public CompletableFuture<MarketDataProvider.StockPrice> getCurrentPrice(String symbol) {
        log.debug("Getting current price for symbol: {}", symbol);

        return tryProvidersSequentially(
                getAvailableProviders(resolveDefaultPreferredProvider()),
                provider -> provider.getCurrentPrice(symbol),
                result -> result != null)
                .thenApply(result -> {
                    if (result == null) {
                        log.warn("Failed to retrieve current price for {} from all providers", symbol);
                    }
                    return result;
                });
    }

    @Cacheable(
            value = "historicalPrices",
            key = "#symbol + '_' + #startDate + '_' + #endDate + '_' + @marketDataProviderSettingsResolver.resolvePreferredProviderName()",
            condition = "@marketDataProviderProperties.cache.enabled")
    public CompletableFuture<List<MarketDataProvider.StockPrice>> getHistoricalPrices(String symbol, LocalDate startDate, LocalDate endDate) {
        return getHistoricalPrices(symbol, startDate, endDate, resolveDefaultPreferredProvider());
    }

    public CompletableFuture<List<MarketDataProvider.StockPrice>> getHistoricalPrices(
            String symbol,
            LocalDate startDate,
            LocalDate endDate,
            String preferredProviderName) {
        log.debug("Getting historical prices for symbol: {} from {} to {}", symbol, startDate, endDate);

        List<MarketDataProvider> availableProviders = getAvailableProviders(preferredProviderName);

        return tryProvidersSequentially(
                availableProviders,
                provider -> provider.getHistoricalPrices(symbol, startDate, endDate),
                result -> result != null && !result.isEmpty())
                .thenApply(result -> {
                    if (result == null || result.isEmpty()) {
                        log.warn("Failed to retrieve historical prices for {} from all providers", symbol);
                        return List.of();
                    }
                    return result;
                });
    }

    @Cacheable(
            value = "companyProfiles",
            key = "#symbol + '_' + @marketDataProviderSettingsResolver.resolvePreferredProviderName()",
            condition = "@marketDataProviderProperties.cache.enabled",
            unless = "#result == null")
    public CompletableFuture<MarketDataProvider.CompanyProfile> getCompanyProfile(String symbol) {
        log.debug("Getting company profile for symbol: {}", symbol);

        return tryProvidersSequentially(
                getAvailableProviders(resolveDefaultPreferredProvider()),
                provider -> provider.getCompanyProfile(symbol),
                result -> result != null)
                .thenApply(result -> {
                    if (result == null) {
                        log.warn("Failed to retrieve company profile for {} from all providers", symbol);
                    }
                    return result;
                });
    }

    @Cacheable(
            value = "financialMetrics",
            key = "#symbol + '_' + @marketDataProviderSettingsResolver.resolvePreferredProviderName()",
            condition = "@marketDataProviderProperties.cache.enabled",
            unless = "#result == null")
    public CompletableFuture<MarketDataProvider.FinancialMetrics> getFinancialMetrics(String symbol) {
        log.debug("Getting financial metrics for symbol: {}", symbol);

        return tryProvidersSequentially(
                getAvailableProviders(resolveDefaultPreferredProvider()),
                provider -> provider.getFinancialMetrics(symbol),
                result -> result != null)
                .thenApply(result -> {
                    if (result == null) {
                        log.warn("Failed to retrieve financial metrics for {} from all providers", symbol);
                    }
                    return result;
                });
    }

    public CompletableFuture<BigDecimal> getPriceForDate(String symbol, LocalDate date) {
        log.debug("Getting price for symbol: {} on date: {}", symbol, date);

        if (date.equals(LocalDate.now())) {
            return getCurrentPrice(symbol)
                    .thenApply(stockPrice -> stockPrice != null ? stockPrice.getPrice() : null);
        }

        return getHistoricalPrices(symbol, date, date)
                .thenCompose(prices -> {
                    BigDecimal exactPrice = extractFirstAvailablePrice(prices);
                    if (exactPrice != null) {
                        return CompletableFuture.completedFuture(exactPrice);
                    }

                    LocalDate startDate = date.minusDays(5);
                    LocalDate endDate = date.plusDays(5);
                    return getHistoricalPrices(symbol, startDate, endDate)
                            .thenApply(rangePrices -> findClosestPrice(rangePrices, date));
                });
    }

    public CompletableFuture<EnhancedMarketData> getEnhancedMarketData(String symbol) {
        log.debug("Getting enhanced market data for symbol: {}", symbol);

        CompletableFuture<MarketDataProvider.StockPrice> priceFuture = getCurrentPrice(symbol);
        CompletableFuture<MarketDataProvider.CompanyProfile> profileFuture = getCompanyProfile(symbol);
        CompletableFuture<MarketDataProvider.FinancialMetrics> metricsFuture = getFinancialMetrics(symbol);

        return CompletableFuture.allOf(priceFuture, profileFuture, metricsFuture)
                .thenApply(v -> new EnhancedMarketData(
                        symbol,
                        priceFuture.join(),
                        profileFuture.join(),
                        metricsFuture.join()));
    }

    public Map<String, ProviderStatus> getProviderStatus() {
        return providers.stream()
                .collect(Collectors.toMap(
                        MarketDataProvider::getProviderName,
                        provider -> new ProviderStatus(
                                provider.getProviderName(),
                                provider.isAvailable(),
                                provider.getPriority())));
    }

    private List<MarketDataProvider> getAvailableProviders(String preferredProviderName) {
        String normalizedPreferredProviderName = normalizeProviderName(preferredProviderName);
        return providers.stream()
                .filter(MarketDataProvider::isAvailable)
                .sorted(Comparator
                        .comparing((MarketDataProvider provider) -> !matchesPreferredProvider(provider, normalizedPreferredProviderName))
                        .thenComparingInt(MarketDataProvider::getPriority))
                .collect(Collectors.toList());
    }

    private boolean matchesPreferredProvider(MarketDataProvider provider, String normalizedPreferredProviderName) {
        return normalizedPreferredProviderName != null
                && normalizedPreferredProviderName.equals(normalizeProviderName(provider.getProviderName()));
    }

    private String normalizeProviderName(String providerName) {
        return MarketDataProviders.normalizeOrNull(providerName);
    }

    private String resolveDefaultPreferredProvider() {
        return settingsResolver.resolvePreferredProviderName();
    }

    private BigDecimal extractFirstAvailablePrice(List<MarketDataProvider.StockPrice> prices) {
        if (prices == null || prices.isEmpty()) {
            return null;
        }
        return prices.get(0).getPrice();
    }

    private BigDecimal findClosestPrice(List<MarketDataProvider.StockPrice> prices, LocalDate targetDate) {
        if (prices == null || prices.isEmpty()) {
            return null;
        }

        return prices.stream()
                .filter(price -> price.getDate() != null)
                .min(Comparator.comparing(price -> Math.abs(price.getDate().toEpochDay() - targetDate.toEpochDay())))
                .map(MarketDataProvider.StockPrice::getPrice)
                .orElse(null);
    }

    private <T> CompletableFuture<T> tryProvidersSequentially(
            List<MarketDataProvider> candidateProviders,
            Function<MarketDataProvider, CompletableFuture<T>> operation,
            Predicate<T> successPredicate) {
        CompletableFuture<T> result = new CompletableFuture<>();
        tryProvidersSequentially(candidateProviders, 0, operation, successPredicate, result);
        return result;
    }

    private <T> void tryProvidersSequentially(
            List<MarketDataProvider> candidateProviders,
            int index,
            Function<MarketDataProvider, CompletableFuture<T>> operation,
            Predicate<T> successPredicate,
            CompletableFuture<T> result) {
        if (index >= candidateProviders.size()) {
            result.complete(null);
            return;
        }

        MarketDataProvider provider = candidateProviders.get(index);
        CompletableFuture<T> providerFuture;
        try {
            providerFuture = operation.apply(provider);
        } catch (Exception e) {
            log.debug("Provider {} failed before request execution", provider.getProviderName(), e);
            tryProvidersSequentially(candidateProviders, index + 1, operation, successPredicate, result);
            return;
        }

        providerFuture.whenComplete((value, throwable) -> {
            if (throwable != null || !successPredicate.test(value)) {
                log.debug("Provider {} failed, trying next provider", provider.getProviderName(), throwable);
                tryProvidersSequentially(candidateProviders, index + 1, operation, successPredicate, result);
                return;
            }

            result.complete(value);
        });
    }

    public static class EnhancedMarketData {
        private final String symbol;
        private final MarketDataProvider.StockPrice stockPrice;
        private final MarketDataProvider.CompanyProfile companyProfile;
        private final MarketDataProvider.FinancialMetrics financialMetrics;

        public EnhancedMarketData(String symbol, MarketDataProvider.StockPrice stockPrice, 
                                 MarketDataProvider.CompanyProfile companyProfile, 
                                 MarketDataProvider.FinancialMetrics financialMetrics) {
            this.symbol = symbol;
            this.stockPrice = stockPrice;
            this.companyProfile = companyProfile;
            this.financialMetrics = financialMetrics;
        }

        public String getSymbol() { return symbol; }
        public MarketDataProvider.StockPrice getStockPrice() { return stockPrice; }
        public MarketDataProvider.CompanyProfile getCompanyProfile() { return companyProfile; }
        public MarketDataProvider.FinancialMetrics getFinancialMetrics() { return financialMetrics; }

        public boolean hasPrice() { return stockPrice != null; }
        public boolean hasProfile() { return companyProfile != null; }
        public boolean hasMetrics() { return financialMetrics != null; }
    }

    public static class ProviderStatus {
        private final String name;
        private final boolean available;
        private final int priority;

        public ProviderStatus(String name, boolean available, int priority) {
            this.name = name;
            this.available = available;
            this.priority = priority;
        }

        public String getName() { return name; }
        public boolean isAvailable() { return available; }
        public int getPriority() { return priority; }
    }
}

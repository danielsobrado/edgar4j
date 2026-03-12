package org.jds.edgar4j.service.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jds.edgar4j.properties.MarketDataProviderProperties;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Multi-provider market data service with failover and caching
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataService {

    private final List<MarketDataProvider> providers;
    private final MarketDataProviderProperties properties;

    /**
     * Get current stock price with provider failover
     */
    @Cacheable(value = "stockPrices", key = "#symbol", condition = "@marketDataProviderProperties.cache.enabled")
    public CompletableFuture<MarketDataProvider.StockPrice> getCurrentPrice(String symbol) {
        log.debug("Getting current price for symbol: {}", symbol);
        
        List<MarketDataProvider> availableProviders = getAvailableProviders();
        
        return tryProvidersSequentially(availableProviders, provider -> provider.getCurrentPrice(symbol))
            .thenApply(result -> {
                if (result != null) {
                    log.debug("Successfully retrieved current price for {} from {}", symbol, getSuccessfulProvider(result));
                } else {
                    log.warn("Failed to retrieve current price for {} from all providers", symbol);
                }
                return result;
            });
    }

    /**
     * Get historical stock prices with provider failover
     */
    @Cacheable(value = "historicalPrices", key = "#symbol + '_' + #startDate + '_' + #endDate", 
               condition = "@marketDataProviderProperties.cache.enabled")
    public CompletableFuture<List<MarketDataProvider.StockPrice>> getHistoricalPrices(String symbol, LocalDate startDate, LocalDate endDate) {
        log.debug("Getting historical prices for symbol: {} from {} to {}", symbol, startDate, endDate);
        
        List<MarketDataProvider> availableProviders = getAvailableProviders();
        
        return tryProvidersSequentially(availableProviders, provider -> provider.getHistoricalPrices(symbol, startDate, endDate))
            .thenApply(result -> {
                if (result != null && !result.isEmpty()) {
                    log.debug("Successfully retrieved {} historical prices for {} from {}", 
                             result.size(), symbol, getSuccessfulProvider(result.get(0)));
                } else {
                    log.warn("Failed to retrieve historical prices for {} from all providers", symbol);
                }
                return result != null ? result : new ArrayList<>();
            });
    }

    /**
     * Get company profile with provider failover
     */
    @Cacheable(value = "companyProfiles", key = "#symbol", condition = "@marketDataProviderProperties.cache.enabled")
    public CompletableFuture<MarketDataProvider.CompanyProfile> getCompanyProfile(String symbol) {
        log.debug("Getting company profile for symbol: {}", symbol);
        
        List<MarketDataProvider> availableProviders = getAvailableProviders();
        
        return tryProvidersSequentially(availableProviders, provider -> provider.getCompanyProfile(symbol))
            .thenApply(result -> {
                if (result != null) {
                    log.debug("Successfully retrieved company profile for {} from {}", symbol, getSuccessfulProvider(result));
                } else {
                    log.warn("Failed to retrieve company profile for {} from all providers", symbol);
                }
                return result;
            });
    }

    /**
     * Get financial metrics with provider failover
     */
    @Cacheable(value = "financialMetrics", key = "#symbol", condition = "@marketDataProviderProperties.cache.enabled")
    public CompletableFuture<MarketDataProvider.FinancialMetrics> getFinancialMetrics(String symbol) {
        log.debug("Getting financial metrics for symbol: {}", symbol);
        
        List<MarketDataProvider> availableProviders = getAvailableProviders();
        
        return tryProvidersSequentially(availableProviders, provider -> provider.getFinancialMetrics(symbol))
            .thenApply(result -> {
                if (result != null) {
                    log.debug("Successfully retrieved financial metrics for {} from {}", symbol, getSuccessfulProvider(result));
                } else {
                    log.warn("Failed to retrieve financial metrics for {} from all providers", symbol);
                }
                return result;
            });
    }

    /**
     * Get the best available price for a transaction on a specific date
     */
    public CompletableFuture<BigDecimal> getPriceForDate(String symbol, LocalDate date) {
        log.debug("Getting price for symbol: {} on date: {}", symbol, date);
        
        // Try current price first if date is today
        if (date.equals(LocalDate.now())) {
            return getCurrentPrice(symbol)
                .thenApply(stockPrice -> stockPrice != null ? stockPrice.getPrice() : null);
        }
        
        // Get historical price for the specific date
        return getHistoricalPrices(symbol, date, date)
            .thenApply(prices -> {
                if (prices != null && !prices.isEmpty()) {
                    return prices.get(0).getPrice();
                }
                
                // Fallback: try a date range around the target date
                LocalDate startDate = date.minusDays(5);
                LocalDate endDate = date.plusDays(5);
                
                return getHistoricalPrices(symbol, startDate, endDate)
                    .thenApply(rangePrices -> {
                        if (rangePrices != null && !rangePrices.isEmpty()) {
                            // Find the closest date
                            return rangePrices.stream()
                                .min(Comparator.comparing(p -> Math.abs(p.getDate().toEpochDay() - date.toEpochDay())))
                                .map(MarketDataProvider.StockPrice::getPrice)
                                .orElse(null);
                        }
                        return null;
                    }).join();
            });
    }

    /**
     * Get enhanced market data combining multiple sources
     */
    public CompletableFuture<EnhancedMarketData> getEnhancedMarketData(String symbol) {
        log.debug("Getting enhanced market data for symbol: {}", symbol);
        
        CompletableFuture<MarketDataProvider.StockPrice> priceFuture = getCurrentPrice(symbol);
        CompletableFuture<MarketDataProvider.CompanyProfile> profileFuture = getCompanyProfile(symbol);
        CompletableFuture<MarketDataProvider.FinancialMetrics> metricsFuture = getFinancialMetrics(symbol);
        
        return CompletableFuture.allOf(priceFuture, profileFuture, metricsFuture)
            .thenApply(v -> {
                MarketDataProvider.StockPrice price = priceFuture.join();
                MarketDataProvider.CompanyProfile profile = profileFuture.join();
                MarketDataProvider.FinancialMetrics metrics = metricsFuture.join();
                
                return new EnhancedMarketData(symbol, price, profile, metrics);
            });
    }

    /**
     * Get provider health status
     */
    public Map<String, ProviderStatus> getProviderStatus() {
        return providers.stream()
            .collect(Collectors.toMap(
                MarketDataProvider::getProviderName,
                provider -> new ProviderStatus(
                    provider.getProviderName(),
                    provider.isAvailable(),
                    provider.getPriority()
                )
            ));
    }

    private List<MarketDataProvider> getAvailableProviders() {
        return providers.stream()
            .filter(MarketDataProvider::isAvailable)
            .sorted(Comparator.comparingInt(MarketDataProvider::getPriority))
            .collect(Collectors.toList());
    }

    private <T> CompletableFuture<T> tryProvidersSequentially(List<MarketDataProvider> providers, 
                                                             java.util.function.Function<MarketDataProvider, CompletableFuture<T>> operation) {
        if (providers.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        MarketDataProvider provider = providers.get(0);
        List<MarketDataProvider> remainingProviders = providers.subList(1, providers.size());
        
        return operation.apply(provider)
            .handle((result, throwable) -> {
                if (throwable != null || result == null) {
                    log.debug("Provider {} failed, trying next provider", provider.getProviderName());
                    return tryProvidersSequentially(remainingProviders, operation).join();
                }
                return result;
            });
    }

    private String getSuccessfulProvider(Object result) {
        // This is a simplified approach - in practice, you might want to track which provider returned the result
        return "MultiProvider";
    }

    /**
     * Enhanced market data combining multiple data sources
     */
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

        // Getters
        public String getSymbol() { return symbol; }
        public MarketDataProvider.StockPrice getStockPrice() { return stockPrice; }
        public MarketDataProvider.CompanyProfile getCompanyProfile() { return companyProfile; }
        public MarketDataProvider.FinancialMetrics getFinancialMetrics() { return financialMetrics; }

        public boolean hasPrice() { return stockPrice != null; }
        public boolean hasProfile() { return companyProfile != null; }
        public boolean hasMetrics() { return financialMetrics != null; }
    }

    /**
     * Provider status information
     */
    public static class ProviderStatus {
        private final String name;
        private final boolean available;
        private final int priority;

        public ProviderStatus(String name, boolean available, int priority) {
            this.name = name;
            this.available = available;
            this.priority = priority;
        }

        // Getters
        public String getName() { return name; }
        public boolean isAvailable() { return available; }
        public int getPriority() { return priority; }
    }
}

package org.jds.edgar4j.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.jds.edgar4j.dto.response.MarketDataResponse;
import org.jds.edgar4j.model.CompanyMarketData;
import org.jds.edgar4j.model.CompanyTicker;
import org.jds.edgar4j.repository.CompanyMarketDataRepository;
import org.jds.edgar4j.repository.CompanyTickerRepository;
import org.jds.edgar4j.service.CompanyMarketDataService;
import org.jds.edgar4j.service.MarketDataService;
import org.jds.edgar4j.service.provider.MarketDataProvider;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyMarketDataServiceImpl implements CompanyMarketDataService {

    private static final String DEFAULT_CURRENCY = "USD";

    private final CompanyMarketDataRepository marketDataRepository;
    private final CompanyTickerRepository companyTickerRepository;
    private final MarketDataService historicalMarketDataService;
    private final org.jds.edgar4j.service.provider.MarketDataService providerMarketDataService;

    @Override
    public CompanyMarketData fetchAndSaveQuote(String ticker) {
        String normalizedTicker = normalizeTicker(ticker);
        if (normalizedTicker == null) {
            return null;
        }

        try {
            CompletableFuture<MarketDataProvider.StockPrice> priceFuture =
                    providerMarketDataService.getCurrentPrice(normalizedTicker);
            CompletableFuture<MarketDataProvider.CompanyProfile> profileFuture =
                    providerMarketDataService.getCompanyProfile(normalizedTicker);

            CompletableFuture.allOf(priceFuture, profileFuture).join();

            MarketDataProvider.StockPrice stockPrice = priceFuture.join();
            MarketDataProvider.CompanyProfile companyProfile = profileFuture.join();
            ResolvedQuoteData resolvedQuoteData = resolveQuoteData(normalizedTicker, stockPrice, companyProfile);

            if (resolvedQuoteData == null || resolvedQuoteData.currentPrice() == null || resolvedQuoteData.currentPrice() <= 0d) {
                log.warn("No valid provider quote snapshot found for ticker {}", normalizedTicker);
                return null;
            }

            Instant now = Instant.now();
            CompanyMarketData marketData = marketDataRepository.findByTickerIgnoreCase(normalizedTicker)
                    .map(existing -> updateMarketData(existing, resolvedQuoteData, now))
                    .orElseGet(() -> CompanyMarketData.builder()
                            .ticker(normalizedTicker)
                            .cik(resolveCik(normalizedTicker))
                            .marketCap(resolvedQuoteData.marketCap())
                            .currentPrice(resolvedQuoteData.currentPrice())
                            .previousClose(resolvedQuoteData.previousClose())
                            .currency(resolveCurrency(resolvedQuoteData.currency()))
                            .lastUpdated(now)
                            .build());

            if (marketData.getCik() == null) {
                marketData.setCik(resolveCik(normalizedTicker));
            }

            return marketDataRepository.save(marketData);
        } catch (Exception e) {
            log.error("Failed to fetch market data for {}", normalizedTicker, e);
            return null;
        }
    }

    @Override
    public List<CompanyMarketData> fetchAndSaveQuotesBatch(List<String> tickers) {
        List<String> normalizedTickers = normalizeBatchTickers(tickers);
        List<CompanyMarketData> results = new ArrayList<>();

        int success = 0;
        int failed = 0;

        for (String ticker : normalizedTickers) {
            CompanyMarketData marketData = fetchAndSaveQuote(ticker);
            if (marketData != null) {
                results.add(marketData);
                success++;
            } else {
                failed++;
            }
        }

        log.info("Company market data batch fetch completed: {}/{} success, {} failed",
                success, normalizedTickers.size(), failed);
        return results;
    }

    @Override
    public Optional<CompanyMarketData> getMarketData(String ticker) {
        String normalizedTicker = normalizeTicker(ticker);
        if (normalizedTicker == null) {
            return Optional.empty();
        }
        return marketDataRepository.findByTickerIgnoreCase(normalizedTicker);
    }

    @Override
    public Double getCurrentPrice(String ticker) {
        return getMarketData(ticker)
                .map(CompanyMarketData::getCurrentPrice)
                .orElse(null);
    }

    @Override
    public Double getHistoricalClosePrice(String ticker, LocalDate date) {
        String normalizedTicker = normalizeTicker(ticker);
        if (normalizedTicker == null || date == null) {
            return null;
        }

        try {
            MarketDataResponse response = historicalMarketDataService.getDailyPrices(
                    normalizedTicker,
                    date.minusDays(7),
                    date);

            if (response == null || response.getPrices() == null) {
                return null;
            }

            return response.getPrices().stream()
                    .filter(priceBar -> priceBar.getDate() != null)
                    .filter(priceBar -> !priceBar.getDate().isAfter(date))
                    .max(java.util.Comparator.comparing(MarketDataResponse.PriceBar::getDate))
                    .map(MarketDataResponse.PriceBar::getClose)
                    .orElse(null);
        } catch (Exception e) {
            log.debug("Could not resolve historical close for {} on {}", normalizedTicker, date, e);
            return null;
        }
    }

    @Override
    public long count() {
        return marketDataRepository.count();
    }

    ResolvedQuoteData resolveQuoteData(
            String ticker,
            MarketDataProvider.StockPrice stockPrice,
            MarketDataProvider.CompanyProfile companyProfile
    ) {
        if (stockPrice == null) {
            return null;
        }

        Double currentPrice = firstNonNull(
                toNullableDouble(stockPrice.getPrice()),
                toNullableDouble(stockPrice.getClose()));
        Double previousClose = firstNonNull(
                toNullableDouble(stockPrice.getPreviousClose()),
                toNullableDouble(stockPrice.getClose()));
        Double marketCap = firstNonNull(
                toNullableDouble(stockPrice.getMarketCap()),
                companyProfile != null ? toNullableDouble(companyProfile.getMarketCapitalization()) : null);
        String currency = firstNonNull(
                blankToNull(stockPrice.getCurrency()),
                companyProfile != null ? blankToNull(companyProfile.getCurrency()) : null,
                DEFAULT_CURRENCY);

        return new ResolvedQuoteData(currentPrice, previousClose, marketCap, currency, normalizeTicker(ticker));
    }

    private CompanyMarketData updateMarketData(CompanyMarketData existing, ResolvedQuoteData quoteData, Instant now) {
        existing.setTicker(quoteData.ticker());
        existing.setCurrentPrice(quoteData.currentPrice());
        existing.setPreviousClose(quoteData.previousClose());
        if (quoteData.marketCap() != null && quoteData.marketCap() > 0d) {
            existing.setMarketCap(quoteData.marketCap());
        }
        if (existing.getCik() == null) {
            existing.setCik(resolveCik(existing.getTicker()));
        }
        existing.setCurrency(resolveCurrency(quoteData.currency()));
        existing.setLastUpdated(now);
        return existing;
    }

    private List<String> normalizeBatchTickers(List<String> tickers) {
        if (tickers == null || tickers.isEmpty()) {
            return List.of();
        }

        return new ArrayList<>(tickers.stream()
                .map(this::normalizeTicker)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
    }

    private String resolveCik(String ticker) {
        return companyTickerRepository.findByTickerIgnoreCase(ticker)
                .map(CompanyTicker::getCikPadded)
                .orElse(null);
    }

    private String normalizeTicker(String ticker) {
        if (ticker == null) {
            return null;
        }

        String normalized = ticker.trim().replace('.', '-').toUpperCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private String resolveCurrency(String currency) {
        String normalizedCurrency = blankToNull(currency);
        return normalizedCurrency != null ? normalizedCurrency : DEFAULT_CURRENCY;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Double toNullableDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : null;
    }

    private Double toNullableDouble(Long value) {
        return value != null ? value.doubleValue() : null;
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }

        for (T value : values) {
            if (value != null) {
                return value;
            }
        }

        return null;
    }

    record ResolvedQuoteData(
            Double currentPrice,
            Double previousClose,
            Double marketCap,
            String currency,
            String ticker) {
    }
}

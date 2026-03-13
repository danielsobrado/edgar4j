package org.jds.edgar4j.service.provider.impl;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import org.jds.edgar4j.service.provider.MarketDataProvider;
import org.jds.edgar4j.service.provider.MarketDataProviderSettingsResolver;
import org.jds.edgar4j.service.provider.MarketDataProviders;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class YahooFinanceProvider implements MarketDataProvider {

    private final ObjectMapper objectMapper;
    private final MarketDataProviderSettingsResolver settingsResolver;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final AtomicLong unavailableUntilEpochMillis = new AtomicLong(0);
    private final AtomicLong lastRequestTime = new AtomicLong(0);

    private static final String USER_AGENT = "Edgar4J/1.0 Yahoo Finance Client";

    @Override
    public String getProviderName() {
        return "YahooFinance";
    }

    @Override
    public int getPriority() {
        return config().priority();
    }

    @Override
    public boolean isAvailable() {
        return config().operational()
                && System.currentTimeMillis() >= unavailableUntilEpochMillis.get();
    }

    @Override
    public CompletableFuture<StockPrice> getCurrentPrice(String symbol) {
        MarketDataProviderSettingsResolver.ResolvedProviderConfig config = config();
        if (!config.operational()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Getting current price for symbol: {} from Yahoo Finance", symbol);

                enforceRateLimit(config);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(buildQuoteUri(config.baseUrl(), symbol))
                        .header("User-Agent", USER_AGENT)
                        .timeout(config.timeout())
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    markAvailable();
                    return parseCurrentPrice(response.body(), symbol);
                }

                log.warn("Yahoo Finance API returned status: {} for symbol: {}", response.statusCode(), symbol);
                markTemporarilyUnavailable(config.retryDelay());
                return null;
            } catch (Exception e) {
                log.error("Error getting current price from Yahoo Finance for symbol: {}", symbol, e);
                markTemporarilyUnavailable(config.retryDelay());
                return null;
            }
        });
    }

    @Override
    public CompletableFuture<List<StockPrice>> getHistoricalPrices(String symbol, LocalDate startDate, LocalDate endDate) {
        MarketDataProviderSettingsResolver.ResolvedProviderConfig config = config();
        if (!config.operational()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Getting historical prices for symbol: {} from {} to {}", symbol, startDate, endDate);

                enforceRateLimit(config);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(buildHistoricalUri(config.baseUrl(), symbol, startDate, endDate))
                        .header("User-Agent", USER_AGENT)
                        .timeout(config.timeout())
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    markAvailable();
                    return parseHistoricalPrices(response.body(), symbol);
                }

                log.warn("Yahoo Finance API returned status: {} for symbol: {}", response.statusCode(), symbol);
                markTemporarilyUnavailable(config.retryDelay());
                return List.of();
            } catch (Exception e) {
                log.error("Error getting historical prices from Yahoo Finance for symbol: {}", symbol, e);
                markTemporarilyUnavailable(config.retryDelay());
                return List.of();
            }
        });
    }

    @Override
    public CompletableFuture<CompanyProfile> getCompanyProfile(String symbol) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<FinancialMetrics> getFinancialMetrics(String symbol) {
        return CompletableFuture.completedFuture(null);
    }

    private MarketDataProviderSettingsResolver.ResolvedProviderConfig config() {
        return settingsResolver.resolve(MarketDataProviders.YAHOO_FINANCE);
    }

    private void markAvailable() {
        unavailableUntilEpochMillis.set(0);
    }

    private void markTemporarilyUnavailable(Duration retryDelay) {
        long cooldownMillis = retryDelay != null ? Math.max(0L, retryDelay.toMillis()) : 0L;
        unavailableUntilEpochMillis.set(System.currentTimeMillis() + cooldownMillis);
    }

    private void enforceRateLimit(MarketDataProviderSettingsResolver.ResolvedProviderConfig config) {
        long now = System.currentTimeMillis();
        long timeSinceLastRequest = now - lastRequestTime.get();
        long requests = config.rateLimit() != null ? Math.max(1, config.rateLimit().getRequests()) : 1;
        long periodMillis = config.rateLimit() != null && config.rateLimit().getPeriod() != null
                ? Math.max(1L, config.rateLimit().getPeriod().toMillis())
                : 1L;
        long minInterval = Math.max(1L, periodMillis / requests);

        if (timeSinceLastRequest < minInterval) {
            try {
                Thread.sleep(minInterval - timeSinceLastRequest);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        lastRequestTime.set(System.currentTimeMillis());
    }

    private URI buildQuoteUri(String baseUrl, String symbol) {
        return UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment(symbol)
                .queryParam("interval", "1d")
                .queryParam("range", "1d")
                .build()
                .encode()
                .toUri();
    }

    private URI buildHistoricalUri(String baseUrl, String symbol, LocalDate startDate, LocalDate endDate) {
        long startTimestamp = startDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        long endTimestamp = endDate.plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC);

        return UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment(symbol)
                .queryParam("period1", startTimestamp)
                .queryParam("period2", endTimestamp)
                .queryParam("interval", "1d")
                .build()
                .encode()
                .toUri();
    }

    private StockPrice parseCurrentPrice(String jsonResponse, String symbol) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            JsonNode chart = rootNode.path("chart").path("result");
            
            if (!chart.isArray() || chart.size() == 0) {
                log.warn("No quote data found in Yahoo Finance response for symbol: {}", symbol);
                return null;
            }

            JsonNode result = chart.get(0);
            JsonNode meta = result.path("meta");
            JsonNode indicators = result.path("indicators").path("quote");
            
            if (!indicators.isArray() || indicators.size() == 0) {
                log.warn("No indicators found in Yahoo Finance response for symbol: {}", symbol);
                return null;
            }

            JsonNode quote = indicators.get(0);
            
            StockPrice stockPrice = new StockPrice();
            stockPrice.setSymbol(symbol);
            stockPrice.setPrice(parseBigDecimal(meta.path("regularMarketPrice").asText()));
            stockPrice.setPreviousClose(firstNonNull(
                    parseBigDecimal(meta.path("previousClose").asText()),
                    parseBigDecimal(meta.path("chartPreviousClose").asText())));
            stockPrice.setMarketCap(parseLong(meta.path("marketCap").asText()));
            
            // Get the latest values from arrays
            JsonNode opens = quote.path("open");
            JsonNode highs = quote.path("high");
            JsonNode lows = quote.path("low");
            JsonNode closes = quote.path("close");
            JsonNode volumes = quote.path("volume");
            
            if (opens.isArray() && opens.size() > 0) {
                int lastIndex = opens.size() - 1;
                stockPrice.setOpen(parseBigDecimal(opens.get(lastIndex).asText()));
                stockPrice.setHigh(parseBigDecimal(highs.get(lastIndex).asText()));
                stockPrice.setLow(parseBigDecimal(lows.get(lastIndex).asText()));
                stockPrice.setClose(parseBigDecimal(closes.get(lastIndex).asText()));
                stockPrice.setVolume(volumes.get(lastIndex).asLong());
            }

            if (stockPrice.getClose() == null) {
                stockPrice.setClose(stockPrice.getPreviousClose());
            }
            
            stockPrice.setDate(LocalDate.now());
            stockPrice.setCurrency(meta.path("currency").asText("USD"));
            stockPrice.setExchange(meta.path("exchangeName").asText("US"));

            return stockPrice;
            
        } catch (Exception e) {
            log.error("Error parsing current price response from Yahoo Finance", e);
            return null;
        }
    }

    private List<StockPrice> parseHistoricalPrices(String jsonResponse, String symbol) {
        List<StockPrice> prices = new ArrayList<>();

        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            JsonNode chart = rootNode.path("chart").path("result");

            if (!chart.isArray() || chart.size() == 0) {
                log.warn("No historical data found in Yahoo Finance response for symbol: {}", symbol);
                return prices;
            }

            JsonNode result = chart.get(0);
            JsonNode meta = result.path("meta");
            JsonNode timestamps = result.path("timestamp");
            JsonNode indicators = result.path("indicators").path("quote");
            
            if (!indicators.isArray() || indicators.size() == 0) {
                log.warn("No indicators found in Yahoo Finance response for symbol: {}", symbol);
                return prices;
            }

            JsonNode quote = indicators.get(0);
            JsonNode opens = quote.path("open");
            JsonNode highs = quote.path("high");
            JsonNode lows = quote.path("low");
            JsonNode closes = quote.path("close");
            JsonNode volumes = quote.path("volume");

            if (timestamps.isArray() && opens.isArray()) {
                for (int i = 0; i < timestamps.size(); i++) {
                    try {
                        long timestamp = timestamps.get(i).asLong();
                        LocalDate date = Instant.ofEpochSecond(timestamp).atOffset(ZoneOffset.UTC).toLocalDate();

                        if (opens.get(i).isNull() || closes.get(i).isNull()) {
                            continue;
                        }

                        StockPrice stockPrice = new StockPrice();
                        stockPrice.setSymbol(symbol);
                        stockPrice.setDate(date);
                        stockPrice.setOpen(parseBigDecimal(opens.get(i).asText()));
                        stockPrice.setHigh(parseBigDecimal(highs.get(i).asText()));
                        stockPrice.setLow(parseBigDecimal(lows.get(i).asText()));
                        stockPrice.setClose(parseBigDecimal(closes.get(i).asText()));
                        stockPrice.setPrice(stockPrice.getClose());
                        
                        if (!volumes.get(i).isNull()) {
                            stockPrice.setVolume(volumes.get(i).asLong());
                        }

                        stockPrice.setCurrency(meta.path("currency").asText("USD"));
                        stockPrice.setExchange(meta.path("exchangeName").asText("US"));

                        prices.add(stockPrice);
                    } catch (Exception e) {
                        log.warn("Error parsing historical price entry for symbol: {}", symbol, e);
                    }
                }
            }

        } catch (Exception e) {
            log.error("Error parsing historical prices response from Yahoo Finance", e);
        }

        return prices.stream()
                .filter(price -> price.getDate() != null)
                .sorted(java.util.Comparator.comparing(StockPrice::getDate))
                .toList();
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.trim().isEmpty() || "null".equals(value)) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            log.debug("Unable to parse BigDecimal: {}", value);
            return null;
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.trim().isEmpty() || "null".equals(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.debug("Unable to parse Long: {}", value);
            return null;
        }
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
}

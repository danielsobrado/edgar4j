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
public class FinnhubProvider implements MarketDataProvider {

    private final ObjectMapper objectMapper;
    private final MarketDataProviderSettingsResolver settingsResolver;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final AtomicLong unavailableUntilEpochMillis = new AtomicLong(0);
    private final AtomicLong lastRequestTime = new AtomicLong(0);

    private static final String USER_AGENT = "Edgar4J/1.0 Finnhub Client";

    @Override
    public String getProviderName() {
        return "Finnhub";
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
                log.debug("Getting current price for symbol: {} from Finnhub", symbol);

                enforceRateLimit(config);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(buildQuoteUri(config.baseUrl(), symbol))
                        .header("User-Agent", USER_AGENT)
                        .header("X-Finnhub-Token", config.apiKey())
                        .timeout(config.timeout())
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    markAvailable();
                    return parseCurrentPrice(response.body(), symbol);
                }

                log.warn("Finnhub API returned status: {} for symbol: {}", response.statusCode(), symbol);
                if (shouldMarkTemporarilyUnavailable(response.statusCode())) {
                    markTemporarilyUnavailable(config.retryDelay());
                }
                return null;
            } catch (Exception e) {
                log.error("Error getting current price from Finnhub for symbol: {}", symbol, e);
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
                        .uri(buildCandlesUri(config.baseUrl(), symbol, startDate, endDate))
                        .header("User-Agent", USER_AGENT)
                        .header("X-Finnhub-Token", config.apiKey())
                        .timeout(config.timeout())
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    markAvailable();
                    return parseHistoricalPrices(response.body(), symbol);
                }

                log.warn("Finnhub API returned status: {} for symbol: {}", response.statusCode(), symbol);
                if (shouldMarkTemporarilyUnavailable(response.statusCode())) {
                    markTemporarilyUnavailable(config.retryDelay());
                }
                return List.of();
            } catch (Exception e) {
                log.error("Error getting historical prices from Finnhub for symbol: {}", symbol, e);
                markTemporarilyUnavailable(config.retryDelay());
                return List.of();
            }
        });
    }

    @Override
    public CompletableFuture<CompanyProfile> getCompanyProfile(String symbol) {
        MarketDataProviderSettingsResolver.ResolvedProviderConfig config = config();
        if (!config.operational()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Getting company profile for symbol: {} from Finnhub", symbol);

                enforceRateLimit(config);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(buildProfileUri(config.baseUrl(), symbol))
                        .header("User-Agent", USER_AGENT)
                        .header("X-Finnhub-Token", config.apiKey())
                        .timeout(config.timeout())
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    markAvailable();
                    return parseCompanyProfile(response.body(), symbol);
                }

                log.warn("Finnhub API returned status: {} for symbol: {}", response.statusCode(), symbol);
                if (shouldMarkTemporarilyUnavailable(response.statusCode())) {
                    markTemporarilyUnavailable(config.retryDelay());
                }
                return null;
            } catch (Exception e) {
                log.error("Error getting company profile from Finnhub for symbol: {}", symbol, e);
                markTemporarilyUnavailable(config.retryDelay());
                return null;
            }
        });
    }

    @Override
    public CompletableFuture<FinancialMetrics> getFinancialMetrics(String symbol) {
        MarketDataProviderSettingsResolver.ResolvedProviderConfig config = config();
        if (!config.operational()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Getting financial metrics for symbol: {} from Finnhub", symbol);

                enforceRateLimit(config);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(buildMetricsUri(config.baseUrl(), symbol))
                        .header("User-Agent", USER_AGENT)
                        .header("X-Finnhub-Token", config.apiKey())
                        .timeout(config.timeout())
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    markAvailable();
                    return parseFinancialMetrics(response.body(), symbol);
                }

                log.warn("Finnhub API returned status: {} for symbol: {}", response.statusCode(), symbol);
                if (shouldMarkTemporarilyUnavailable(response.statusCode())) {
                    markTemporarilyUnavailable(config.retryDelay());
                }
                return null;
            } catch (Exception e) {
                log.error("Error getting financial metrics from Finnhub for symbol: {}", symbol, e);
                markTemporarilyUnavailable(config.retryDelay());
                return null;
            }
        });
    }

    private MarketDataProviderSettingsResolver.ResolvedProviderConfig config() {
        return settingsResolver.resolve(MarketDataProviders.FINNHUB);
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

    private boolean shouldMarkTemporarilyUnavailable(int statusCode) {
        return statusCode == 401 || statusCode == 403 || statusCode == 429 || statusCode >= 500;
    }

    private URI buildQuoteUri(String baseUrl, String symbol) {
        return UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment("quote")
                .queryParam("symbol", symbol)
                .build()
                .encode()
                .toUri();
    }

    private URI buildCandlesUri(String baseUrl, String symbol, LocalDate startDate, LocalDate endDate) {
        long startTimestamp = startDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        long endTimestamp = endDate.plusDays(1).atStartOfDay().minusSeconds(1).toEpochSecond(ZoneOffset.UTC);

        return UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment("stock", "candle")
                .queryParam("symbol", symbol)
                .queryParam("resolution", "D")
                .queryParam("from", startTimestamp)
                .queryParam("to", endTimestamp)
                .build()
                .encode()
                .toUri();
    }

    private URI buildProfileUri(String baseUrl, String symbol) {
        return UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment("stock", "profile2")
                .queryParam("symbol", symbol)
                .build()
                .encode()
                .toUri();
    }

    private URI buildMetricsUri(String baseUrl, String symbol) {
        return UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment("stock", "metric")
                .queryParam("symbol", symbol)
                .queryParam("metric", "all")
                .build()
                .encode()
                .toUri();
    }

    private StockPrice parseCurrentPrice(String jsonResponse, String symbol) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            
            if (rootNode.path("c").isMissingNode()) {
                log.warn("No quote data found in Finnhub response for symbol: {}", symbol);
                return null;
            }

            StockPrice stockPrice = new StockPrice();
            stockPrice.setSymbol(symbol);
            stockPrice.setPrice(parseBigDecimal(rootNode.path("c").asText())); // current price
            stockPrice.setOpen(parseBigDecimal(rootNode.path("o").asText())); // open price
            stockPrice.setHigh(parseBigDecimal(rootNode.path("h").asText())); // high price
            stockPrice.setLow(parseBigDecimal(rootNode.path("l").asText())); // low price
            stockPrice.setClose(parseBigDecimal(rootNode.path("pc").asText())); // previous close
            stockPrice.setPreviousClose(parseBigDecimal(rootNode.path("pc").asText()));
            stockPrice.setDate(LocalDate.now());
            stockPrice.setCurrency("USD");
            stockPrice.setExchange("US");

            return stockPrice;
            
        } catch (Exception e) {
            log.error("Error parsing current price response from Finnhub", e);
            return null;
        }
    }

    private List<StockPrice> parseHistoricalPrices(String jsonResponse, String symbol) {
        List<StockPrice> prices = new ArrayList<>();

        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);

            if (!"ok".equals(rootNode.path("s").asText())) {
                log.warn("No historical data found in Finnhub response for symbol: {}", symbol);
                return prices;
            }

            JsonNode timestamps = rootNode.path("t");
            JsonNode opens = rootNode.path("o");
            JsonNode highs = rootNode.path("h");
            JsonNode lows = rootNode.path("l");
            JsonNode closes = rootNode.path("c");
            JsonNode volumes = rootNode.path("v");

            if (timestamps.isArray() && opens.isArray()) {
                for (int i = 0; i < timestamps.size(); i++) {
                    try {
                        long timestamp = timestamps.get(i).asLong();
                        LocalDate date = Instant.ofEpochSecond(timestamp).atOffset(ZoneOffset.UTC).toLocalDate();

                        StockPrice stockPrice = new StockPrice();
                        stockPrice.setSymbol(symbol);
                        stockPrice.setDate(date);
                        stockPrice.setOpen(parseBigDecimal(opens.get(i).asText()));
                        stockPrice.setHigh(parseBigDecimal(highs.get(i).asText()));
                        stockPrice.setLow(parseBigDecimal(lows.get(i).asText()));
                        stockPrice.setClose(parseBigDecimal(closes.get(i).asText()));
                        stockPrice.setPrice(stockPrice.getClose());
                        stockPrice.setVolume(volumes.get(i).asLong());
                        stockPrice.setCurrency("USD");
                        stockPrice.setExchange("US");

                        prices.add(stockPrice);
                    } catch (Exception e) {
                        log.warn("Error parsing historical price entry for symbol: {}", symbol, e);
                    }
                }
            }

        } catch (Exception e) {
            log.error("Error parsing historical prices response from Finnhub", e);
        }

        return prices.stream()
                .filter(price -> price.getDate() != null)
                .sorted(java.util.Comparator.comparing(StockPrice::getDate))
                .toList();
    }

    private CompanyProfile parseCompanyProfile(String jsonResponse, String symbol) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            
            if (rootNode.path("name").isMissingNode()) {
                log.warn("No company data found in Finnhub response for symbol: {}", symbol);
                return null;
            }

            CompanyProfile profile = new CompanyProfile();
            profile.setSymbol(symbol);
            profile.setName(rootNode.path("name").asText());
            profile.setIndustry(rootNode.path("finnhubIndustry").asText());
            profile.setCountry(rootNode.path("country").asText());
            profile.setCurrency(rootNode.path("currency").asText());
            profile.setExchange(rootNode.path("exchange").asText());
            profile.setMarketCapitalization(rootNode.path("marketCapitalization").asLong() * 1000000L); // Finnhub returns in millions
            profile.setSharesOutstanding(rootNode.path("shareOutstanding").asLong() * 1000000L); // Finnhub returns in millions
            profile.setWebsite(rootNode.path("weburl").asText());
            profile.setLogo(rootNode.path("logo").asText());

            return profile;
            
        } catch (Exception e) {
            log.error("Error parsing company profile response from Finnhub", e);
            return null;
        }
    }

    private FinancialMetrics parseFinancialMetrics(String jsonResponse, String symbol) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            JsonNode metric = rootNode.path("metric");
            
            if (metric.isMissingNode()) {
                log.warn("No financial metrics found in Finnhub response for symbol: {}", symbol);
                return null;
            }

            FinancialMetrics metrics = new FinancialMetrics();
            metrics.setSymbol(symbol);
            metrics.setPeRatio(parseBigDecimal(metric.path("peBasicExclExtraTTM").asText()));
            metrics.setPriceToBook(parseBigDecimal(metric.path("pbAnnual").asText()));
            metrics.setPriceToSales(parseBigDecimal(metric.path("psAnnual").asText()));
            metrics.setProfitMargin(parseBigDecimal(metric.path("netProfitMarginTTM").asText()));
            metrics.setOperatingMargin(parseBigDecimal(metric.path("operatingMarginTTM").asText()));
            metrics.setReturnOnEquity(parseBigDecimal(metric.path("roeTTM").asText()));
            metrics.setReturnOnAssets(parseBigDecimal(metric.path("roaTTM").asText()));
            metrics.setBeta(parseBigDecimal(metric.path("beta").asText()));
            metrics.setFiftyTwoWeekHigh(parseBigDecimal(metric.path("52WeekHigh").asText()));
            metrics.setFiftyTwoWeekLow(parseBigDecimal(metric.path("52WeekLow").asText()));

            return metrics;
            
        } catch (Exception e) {
            log.error("Error parsing financial metrics response from Finnhub", e);
            return null;
        }
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
}

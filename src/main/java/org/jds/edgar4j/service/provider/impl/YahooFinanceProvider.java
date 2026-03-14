package org.jds.edgar4j.service.provider.impl;

import java.math.BigDecimal;
import java.net.CookieManager;
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
import java.util.concurrent.atomic.AtomicReference;

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
    private final CookieManager cookieManager = new CookieManager();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .cookieHandler(cookieManager)
            .build();
    private final AtomicLong unavailableUntilEpochMillis = new AtomicLong(0);
    private final AtomicLong lastRequestTime = new AtomicLong(0);
    private final AtomicReference<YahooSession> yahooSession = new AtomicReference<>();

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36";
    private static final Duration SESSION_TTL = Duration.ofMinutes(15);

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
                if (shouldMarkTemporarilyUnavailable(response.statusCode())) {
                    markTemporarilyUnavailable(config.retryDelay());
                }
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
                if (shouldMarkTemporarilyUnavailable(response.statusCode())) {
                    markTemporarilyUnavailable(config.retryDelay());
                }
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
        MarketDataProviderSettingsResolver.ResolvedProviderConfig config = config();
        if (!config.operational()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Getting company profile for symbol: {} from Yahoo Finance", symbol);

                enforceRateLimit(config);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(buildProfileUri(config.baseUrl(), symbol))
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/json")
                        .timeout(config.timeout())
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    markAvailable();
                    return parseCompanyProfile(response.body(), symbol);
                }

                if (requiresCrumb(response.statusCode())) {
                    CompanyProfile quoteSummaryProfile = fetchQuoteSummaryProfile(config, symbol);
                    if (quoteSummaryProfile != null) {
                        markAvailable();
                        return quoteSummaryProfile;
                    }
                }

                log.warn("Yahoo Finance profile API returned status: {} for symbol: {}", response.statusCode(), symbol);
                if (shouldMarkTemporarilyUnavailable(response.statusCode())) {
                    markTemporarilyUnavailable(config.retryDelay());
                }
                return null;
            } catch (Exception e) {
                log.error("Error getting company profile from Yahoo Finance for symbol: {}", symbol, e);
                markTemporarilyUnavailable(config.retryDelay());
                return null;
            }
        });
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

    private URI buildProfileUri(String baseUrl, String symbol) {
        URI baseUri = URI.create(baseUrl);
        return UriComponentsBuilder.newInstance()
                .scheme(baseUri.getScheme())
                .host(baseUri.getHost())
                .port(baseUri.getPort())
                .path("/v7/finance/quote")
                .queryParam("symbols", symbol)
                .build()
                .encode()
                .toUri();
    }

    private URI buildQuoteSummaryUri(String baseUrl, String symbol, String crumb) {
        URI baseUri = URI.create(baseUrl);
        return UriComponentsBuilder.newInstance()
                .scheme(baseUri.getScheme())
                .host(baseUri.getHost())
                .port(baseUri.getPort())
                .path("/v10/finance/quoteSummary/{symbol}")
                .queryParam("modules", "price,defaultKeyStatistics")
                .queryParam("crumb", crumb)
                .buildAndExpand(symbol)
                .encode()
                .toUri();
    }

    private URI buildCrumbUri(String baseUrl) {
        URI baseUri = URI.create(baseUrl);
        return UriComponentsBuilder.newInstance()
                .scheme(baseUri.getScheme())
                .host(baseUri.getHost())
                .port(baseUri.getPort())
                .path("/v1/test/getcrumb")
                .build()
                .encode()
                .toUri();
    }

    private URI buildSessionBootstrapUri(String baseUrl) {
        URI baseUri = URI.create(baseUrl);
        if (baseUri.getHost() != null && baseUri.getHost().endsWith("finance.yahoo.com")) {
            return URI.create("https://fc.yahoo.com");
        }

        return UriComponentsBuilder.newInstance()
                .scheme(baseUri.getScheme())
                .host(baseUri.getHost())
                .port(baseUri.getPort())
                .path("/")
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

    private CompanyProfile parseCompanyProfile(String jsonResponse, String symbol) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            JsonNode results = rootNode.path("quoteResponse").path("result");
            if (results.isArray() && !results.isEmpty()) {
                return parseQuoteResponseProfile(results.get(0), symbol);
            }

            JsonNode summaryResults = rootNode.path("quoteSummary").path("result");
            if (summaryResults.isArray() && !summaryResults.isEmpty()) {
                return parseQuoteSummaryProfile(summaryResults.get(0), symbol);
            }

            log.warn("No company profile data found in Yahoo Finance response for symbol: {}", symbol);
            return null;
        } catch (Exception e) {
            log.error("Error parsing company profile response from Yahoo Finance", e);
            return null;
        }
    }

    private CompanyProfile parseQuoteResponseProfile(JsonNode result, String symbol) {
        CompanyProfile profile = new CompanyProfile();
        profile.setSymbol(symbol);
        profile.setName(firstNonBlank(
                result.path("longName").asText(null),
                result.path("shortName").asText(null),
                result.path("displayName").asText(null)));
        profile.setExchange(result.path("fullExchangeName").asText(result.path("exchange").asText("US")));
        profile.setCurrency(result.path("currency").asText("USD"));

        Long marketCap = parseLongNode(result.get("marketCap"));
        Long sharesOutstanding = parseLongNode(result.get("sharesOutstanding"));
        if (marketCap == null && sharesOutstanding != null) {
            BigDecimal regularMarketPrice = parseBigDecimalNode(result.get("regularMarketPrice"));
            if (regularMarketPrice != null) {
                marketCap = regularMarketPrice
                        .multiply(BigDecimal.valueOf(sharesOutstanding))
                        .setScale(0, java.math.RoundingMode.HALF_UP)
                        .longValue();
            }
        }

        profile.setMarketCapitalization(marketCap);
        profile.setSharesOutstanding(sharesOutstanding);
        return profile;
    }

    private CompanyProfile parseQuoteSummaryProfile(JsonNode result, String symbol) {
        JsonNode price = result.path("price");
        JsonNode defaultKeyStatistics = result.path("defaultKeyStatistics");

        CompanyProfile profile = new CompanyProfile();
        profile.setSymbol(symbol);
        profile.setName(firstNonBlank(
                extractTextValue(price.get("longName")),
                extractTextValue(price.get("shortName")),
                extractTextValue(price.get("displayName"))));
        profile.setExchange(firstNonBlank(
                extractTextValue(price.get("fullExchangeName")),
                extractTextValue(price.get("exchangeName")),
                extractTextValue(price.get("exchange")),
                "US"));
        profile.setCurrency(firstNonBlank(
                extractTextValue(price.get("currency")),
                "USD"));

        Long marketCap = extractLongValue(price.get("marketCap"));
        Long sharesOutstanding = extractLongValue(defaultKeyStatistics.get("sharesOutstanding"));
        if (marketCap == null && sharesOutstanding != null) {
            BigDecimal regularMarketPrice = extractBigDecimalValue(price.get("regularMarketPrice"));
            if (regularMarketPrice != null) {
                marketCap = regularMarketPrice
                        .multiply(BigDecimal.valueOf(sharesOutstanding))
                        .setScale(0, java.math.RoundingMode.HALF_UP)
                        .longValue();
            }
        }

        profile.setMarketCapitalization(marketCap);
        profile.setSharesOutstanding(sharesOutstanding);
        return profile;
    }

    private CompanyProfile fetchQuoteSummaryProfile(
            MarketDataProviderSettingsResolver.ResolvedProviderConfig config,
            String symbol) {
        try {
            YahooSession session = getOrRefreshYahooSession(config);
            if (session == null || session.crumb() == null || session.crumb().isBlank()) {
                return null;
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(buildQuoteSummaryUri(config.baseUrl(), symbol, session.crumb()))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .timeout(config.timeout())
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return parseCompanyProfile(response.body(), symbol);
            }

            if (requiresCrumb(response.statusCode())) {
                yahooSession.set(null);
            }

            log.warn("Yahoo Finance quoteSummary API returned status: {} for symbol: {}", response.statusCode(), symbol);
            return null;
        } catch (Exception e) {
            log.error("Error fetching company profile from Yahoo Finance quoteSummary for symbol: {}", symbol, e);
            return null;
        }
    }

    private synchronized YahooSession getOrRefreshYahooSession(
            MarketDataProviderSettingsResolver.ResolvedProviderConfig config) throws Exception {
        YahooSession cachedSession = yahooSession.get();
        if (cachedSession != null && cachedSession.expiresAt().isAfter(Instant.now())) {
            return cachedSession;
        }

        HttpRequest bootstrapRequest = HttpRequest.newBuilder()
                .uri(buildSessionBootstrapUri(config.baseUrl()))
                .header("User-Agent", USER_AGENT)
                .timeout(config.timeout())
                .build();
        httpClient.send(bootstrapRequest, HttpResponse.BodyHandlers.discarding());

        HttpRequest crumbRequest = HttpRequest.newBuilder()
                .uri(buildCrumbUri(config.baseUrl()))
                .header("User-Agent", USER_AGENT)
                .timeout(config.timeout())
                .build();
        HttpResponse<String> crumbResponse = httpClient.send(crumbRequest, HttpResponse.BodyHandlers.ofString());
        if (crumbResponse.statusCode() != 200) {
            log.warn("Yahoo Finance crumb endpoint returned status: {}", crumbResponse.statusCode());
            return null;
        }

        String crumb = crumbResponse.body() != null ? crumbResponse.body().trim() : null;
        if (crumb == null || crumb.isBlank()) {
            log.warn("Yahoo Finance crumb endpoint returned an empty crumb");
            return null;
        }

        YahooSession refreshedSession = new YahooSession(crumb, Instant.now().plus(SESSION_TTL));
        yahooSession.set(refreshedSession);
        return refreshedSession;
    }

    private boolean requiresCrumb(int statusCode) {
        return statusCode == 401 || statusCode == 403;
    }

    private boolean shouldMarkTemporarilyUnavailable(int statusCode) {
        return statusCode == 429 || statusCode >= 500;
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

    private BigDecimal parseBigDecimalNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return parseBigDecimal(node.asText());
    }

    private Long parseLongNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return parseLong(node.asText());
    }

    private Long extractLongValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        if (node.isObject()) {
            return extractLongValue(node.get("raw"));
        }

        return parseLongNode(node);
    }

    private BigDecimal extractBigDecimalValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        if (node.isObject()) {
            return extractBigDecimalValue(node.get("raw"));
        }

        return parseBigDecimalNode(node);
    }

    private String extractTextValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        if (node.isObject()) {
            String formatted = extractTextValue(node.get("fmt"));
            if (formatted != null) {
                return formatted;
            }
            return extractTextValue(node.get("longFmt"));
        }

        String value = node.asText(null);
        return value != null && !value.isBlank() ? value : null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
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

    private record YahooSession(String crumb, Instant expiresAt) {
    }
}

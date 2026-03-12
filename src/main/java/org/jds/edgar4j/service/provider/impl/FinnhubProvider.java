package org.jds.edgar4j.service.provider.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jds.edgar4j.properties.MarketDataProviderProperties;
import org.jds.edgar4j.service.provider.MarketDataProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Finnhub market data provider implementation
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinnhubProvider implements MarketDataProvider {

    private final MarketDataProviderProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final AtomicBoolean available = new AtomicBoolean(true);
    private final AtomicLong lastRequestTime = new AtomicLong(0);

    private static final String USER_AGENT = "Edgar4J/1.0 Finnhub Client";

    @Override
    public String getProviderName() {
        return "Finnhub";
    }

    @Override
    public int getPriority() {
        return properties.getFinnhub().getPriority();
    }

    @Override
    public boolean isAvailable() {
        return available.get() && 
               properties.getFinnhub().isEnabled() && 
               properties.getFinnhub().getApiKey() != null;
    }

    @Override
    public CompletableFuture<StockPrice> getCurrentPrice(String symbol) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Getting current price for symbol: {} from Finnhub", symbol);
                
                enforceRateLimit();
                
                String url = buildQuoteUrl(symbol);
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .header("X-Finnhub-Token", properties.getFinnhub().getApiKey())
                    .timeout(properties.getFinnhub().getTimeout())
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    return parseCurrentPrice(response.body(), symbol);
                } else {
                    log.warn("Finnhub API returned status: {} for symbol: {}", response.statusCode(), symbol);
                    available.set(false);
                    return null;
                }
                
            } catch (Exception e) {
                log.error("Error getting current price from Finnhub for symbol: {}", symbol, e);
                available.set(false);
                return null;
            }
        });
    }

    @Override
    public CompletableFuture<List<StockPrice>> getHistoricalPrices(String symbol, LocalDate startDate, LocalDate endDate) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Getting historical prices for symbol: {} from {} to {}", symbol, startDate, endDate);
                
                enforceRateLimit();
                
                String url = buildCandlesUrl(symbol, startDate, endDate);
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .header("X-Finnhub-Token", properties.getFinnhub().getApiKey())
                    .timeout(properties.getFinnhub().getTimeout())
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    return parseHistoricalPrices(response.body(), symbol);
                } else {
                    log.warn("Finnhub API returned status: {} for symbol: {}", response.statusCode(), symbol);
                    available.set(false);
                    return new ArrayList<>();
                }
                
            } catch (Exception e) {
                log.error("Error getting historical prices from Finnhub for symbol: {}", symbol, e);
                available.set(false);
                return new ArrayList<>();
            }
        });
    }

    @Override
    public CompletableFuture<CompanyProfile> getCompanyProfile(String symbol) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Getting company profile for symbol: {} from Finnhub", symbol);
                
                enforceRateLimit();
                
                String url = buildProfileUrl(symbol);
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .header("X-Finnhub-Token", properties.getFinnhub().getApiKey())
                    .timeout(properties.getFinnhub().getTimeout())
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    return parseCompanyProfile(response.body(), symbol);
                } else {
                    log.warn("Finnhub API returned status: {} for symbol: {}", response.statusCode(), symbol);
                    available.set(false);
                    return null;
                }
                
            } catch (Exception e) {
                log.error("Error getting company profile from Finnhub for symbol: {}", symbol, e);
                available.set(false);
                return null;
            }
        });
    }

    @Override
    public CompletableFuture<FinancialMetrics> getFinancialMetrics(String symbol) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Getting financial metrics for symbol: {} from Finnhub", symbol);
                
                enforceRateLimit();
                
                String url = buildMetricsUrl(symbol);
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .header("X-Finnhub-Token", properties.getFinnhub().getApiKey())
                    .timeout(properties.getFinnhub().getTimeout())
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    return parseFinancialMetrics(response.body(), symbol);
                } else {
                    log.warn("Finnhub API returned status: {} for symbol: {}", response.statusCode(), symbol);
                    available.set(false);
                    return null;
                }
                
            } catch (Exception e) {
                log.error("Error getting financial metrics from Finnhub for symbol: {}", symbol, e);
                available.set(false);
                return null;
            }
        });
    }

    private void enforceRateLimit() {
        long now = System.currentTimeMillis();
        long timeSinceLastRequest = now - lastRequestTime.get();
        long minInterval = properties.getFinnhub().getRateLimit().getPeriod().toMillis() / 
                          properties.getFinnhub().getRateLimit().getRequests();
        
        if (timeSinceLastRequest < minInterval) {
            try {
                Thread.sleep(minInterval - timeSinceLastRequest);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        lastRequestTime.set(System.currentTimeMillis());
    }

    private String buildQuoteUrl(String symbol) {
        return String.format("%s/quote?symbol=%s", 
            properties.getFinnhub().getBaseUrl(), symbol);
    }

    private String buildCandlesUrl(String symbol, LocalDate startDate, LocalDate endDate) {
        long startTimestamp = startDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        long endTimestamp = endDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        
        return String.format("%s/stock/candle?symbol=%s&resolution=D&from=%d&to=%d",
            properties.getFinnhub().getBaseUrl(), symbol, startTimestamp, endTimestamp);
    }

    private String buildProfileUrl(String symbol) {
        return String.format("%s/stock/profile2?symbol=%s", 
            properties.getFinnhub().getBaseUrl(), symbol);
    }

    private String buildMetricsUrl(String symbol) {
        return String.format("%s/stock/metric?symbol=%s&metric=all", 
            properties.getFinnhub().getBaseUrl(), symbol);
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
            
            if (!rootNode.path("s").asText().equals("ok")) {
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
                        LocalDate date = LocalDate.ofEpochDay(timestamp / 86400); // Convert seconds to days
                        
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
        
        return prices;
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

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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Yahoo Finance market data provider implementation (free, no API key required)
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YahooFinanceProvider implements MarketDataProvider {

    private final MarketDataProviderProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final AtomicBoolean available = new AtomicBoolean(true);
    private final AtomicLong lastRequestTime = new AtomicLong(0);

    private static final String USER_AGENT = "Edgar4J/1.0 Yahoo Finance Client";

    @Override
    public String getProviderName() {
        return "YahooFinance";
    }

    @Override
    public int getPriority() {
        return properties.getYahooFinance().getPriority();
    }

    @Override
    public boolean isAvailable() {
        return available.get() && properties.getYahooFinance().isEnabled();
    }

    @Override
    public CompletableFuture<StockPrice> getCurrentPrice(String symbol) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Getting current price for symbol: {} from Yahoo Finance", symbol);
                
                enforceRateLimit();
                
                String url = buildQuoteUrl(symbol);
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .timeout(properties.getYahooFinance().getTimeout())
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    return parseCurrentPrice(response.body(), symbol);
                } else {
                    log.warn("Yahoo Finance API returned status: {} for symbol: {}", response.statusCode(), symbol);
                    available.set(false);
                    return null;
                }
                
            } catch (Exception e) {
                log.error("Error getting current price from Yahoo Finance for symbol: {}", symbol, e);
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
                
                String url = buildHistoricalUrl(symbol, startDate, endDate);
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .timeout(properties.getYahooFinance().getTimeout())
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    return parseHistoricalPrices(response.body(), symbol);
                } else {
                    log.warn("Yahoo Finance API returned status: {} for symbol: {}", response.statusCode(), symbol);
                    available.set(false);
                    return new ArrayList<>();
                }
                
            } catch (Exception e) {
                log.error("Error getting historical prices from Yahoo Finance for symbol: {}", symbol, e);
                available.set(false);
                return new ArrayList<>();
            }
        });
    }

    @Override
    public CompletableFuture<CompanyProfile> getCompanyProfile(String symbol) {
        // Yahoo Finance doesn't provide comprehensive company profile via their chart API
        // This would require scraping or using their unofficial API endpoints
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<FinancialMetrics> getFinancialMetrics(String symbol) {
        // Yahoo Finance doesn't provide financial metrics via their chart API
        // This would require scraping or using their unofficial API endpoints
        return CompletableFuture.completedFuture(null);
    }

    private void enforceRateLimit() {
        long now = System.currentTimeMillis();
        long timeSinceLastRequest = now - lastRequestTime.get();
        long minInterval = properties.getYahooFinance().getRateLimit().getPeriod().toMillis() / 
                          properties.getYahooFinance().getRateLimit().getRequests();
        
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
        return String.format("%s/%s?interval=1d&range=1d", 
            properties.getYahooFinance().getBaseUrl(), symbol);
    }

    private String buildHistoricalUrl(String symbol, LocalDate startDate, LocalDate endDate) {
        long startTimestamp = startDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        long endTimestamp = endDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        
        return String.format("%s/%s?period1=%d&period2=%d&interval=1d", 
            properties.getYahooFinance().getBaseUrl(), symbol, startTimestamp, endTimestamp);
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
                        
                        // Skip entries with null values
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
        
        return prices;
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

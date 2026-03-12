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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Alpha Vantage market data provider implementation
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlphaVantageProvider implements MarketDataProvider {

    private final MarketDataProviderProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final AtomicBoolean available = new AtomicBoolean(true);
    private final AtomicLong lastRequestTime = new AtomicLong(0);

    private static final String USER_AGENT = "Edgar4J/1.0 AlphaVantage Client";

    @Override
    public String getProviderName() {
        return "AlphaVantage";
    }

    @Override
    public int getPriority() {
        return properties.getAlphaVantage().getPriority();
    }

    @Override
    public boolean isAvailable() {
        return available.get() && 
               properties.getAlphaVantage().isEnabled() && 
               properties.getAlphaVantage().getApiKey() != null;
    }

    @Override
    public CompletableFuture<StockPrice> getCurrentPrice(String symbol) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Getting current price for symbol: {} from Alpha Vantage", symbol);
                
                enforceRateLimit();
                
                String url = buildQuoteUrl(symbol);
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .timeout(properties.getAlphaVantage().getTimeout())
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    return parseCurrentPrice(response.body(), symbol);
                } else {
                    log.warn("Alpha Vantage API returned status: {} for symbol: {}", response.statusCode(), symbol);
                    available.set(false);
                    return null;
                }
                
            } catch (Exception e) {
                log.error("Error getting current price from Alpha Vantage for symbol: {}", symbol, e);
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
                
                String url = buildDailyUrl(symbol);
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .timeout(properties.getAlphaVantage().getTimeout())
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    return parseHistoricalPrices(response.body(), symbol, startDate, endDate);
                } else {
                    log.warn("Alpha Vantage API returned status: {} for symbol: {}", response.statusCode(), symbol);
                    available.set(false);
                    return new ArrayList<>();
                }
                
            } catch (Exception e) {
                log.error("Error getting historical prices from Alpha Vantage for symbol: {}", symbol, e);
                available.set(false);
                return new ArrayList<>();
            }
        });
    }

    @Override
    public CompletableFuture<CompanyProfile> getCompanyProfile(String symbol) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Getting company profile for symbol: {} from Alpha Vantage", symbol);
                
                enforceRateLimit();
                
                String url = buildOverviewUrl(symbol);
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .timeout(properties.getAlphaVantage().getTimeout())
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    return parseCompanyProfile(response.body(), symbol);
                } else {
                    log.warn("Alpha Vantage API returned status: {} for symbol: {}", response.statusCode(), symbol);
                    available.set(false);
                    return null;
                }
                
            } catch (Exception e) {
                log.error("Error getting company profile from Alpha Vantage for symbol: {}", symbol, e);
                available.set(false);
                return null;
            }
        });
    }

    @Override
    public CompletableFuture<FinancialMetrics> getFinancialMetrics(String symbol) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Getting financial metrics for symbol: {} from Alpha Vantage", symbol);
                
                enforceRateLimit();
                
                String url = buildOverviewUrl(symbol);
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .timeout(properties.getAlphaVantage().getTimeout())
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    return parseFinancialMetrics(response.body(), symbol);
                } else {
                    log.warn("Alpha Vantage API returned status: {} for symbol: {}", response.statusCode(), symbol);
                    available.set(false);
                    return null;
                }
                
            } catch (Exception e) {
                log.error("Error getting financial metrics from Alpha Vantage for symbol: {}", symbol, e);
                available.set(false);
                return null;
            }
        });
    }

    private void enforceRateLimit() {
        long now = System.currentTimeMillis();
        long timeSinceLastRequest = now - lastRequestTime.get();
        long minInterval = properties.getAlphaVantage().getRateLimit().getPeriod().toMillis() / 
                          properties.getAlphaVantage().getRateLimit().getRequests();
        
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
        return String.format("%s?function=GLOBAL_QUOTE&symbol=%s&apikey=%s",
            properties.getAlphaVantage().getBaseUrl(),
            symbol,
            properties.getAlphaVantage().getApiKey());
    }

    private String buildDailyUrl(String symbol) {
        return String.format("%s?function=TIME_SERIES_DAILY&symbol=%s&outputsize=compact&apikey=%s",
            properties.getAlphaVantage().getBaseUrl(),
            symbol,
            properties.getAlphaVantage().getApiKey());
    }

    private String buildOverviewUrl(String symbol) {
        return String.format("%s?function=OVERVIEW&symbol=%s&apikey=%s",
            properties.getAlphaVantage().getBaseUrl(),
            symbol,
            properties.getAlphaVantage().getApiKey());
    }

    private StockPrice parseCurrentPrice(String jsonResponse, String symbol) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            JsonNode quoteNode = rootNode.path("Global Quote");
            
            if (quoteNode.isMissingNode()) {
                log.warn("No quote data found in Alpha Vantage response for symbol: {}", symbol);
                return null;
            }

            StockPrice stockPrice = new StockPrice();
            stockPrice.setSymbol(symbol);
            stockPrice.setPrice(parseBigDecimal(quoteNode.path("05. price").asText()));
            stockPrice.setOpen(parseBigDecimal(quoteNode.path("02. open").asText()));
            stockPrice.setHigh(parseBigDecimal(quoteNode.path("03. high").asText()));
            stockPrice.setLow(parseBigDecimal(quoteNode.path("04. low").asText()));
            stockPrice.setClose(parseBigDecimal(quoteNode.path("08. previous close").asText()));
            stockPrice.setVolume(parseLong(quoteNode.path("06. volume").asText()));
            stockPrice.setDate(LocalDate.parse(quoteNode.path("07. latest trading day").asText()));
            stockPrice.setCurrency("USD");
            stockPrice.setExchange("US");

            return stockPrice;
            
        } catch (Exception e) {
            log.error("Error parsing current price response from Alpha Vantage", e);
            return null;
        }
    }

    private List<StockPrice> parseHistoricalPrices(String jsonResponse, String symbol, LocalDate startDate, LocalDate endDate) {
        List<StockPrice> prices = new ArrayList<>();
        
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            JsonNode timeSeriesNode = rootNode.path("Time Series (Daily)");
            
            if (timeSeriesNode.isMissingNode()) {
                log.warn("No time series data found in Alpha Vantage response for symbol: {}", symbol);
                return prices;
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            
            timeSeriesNode.fields().forEachRemaining(entry -> {
                try {
                    LocalDate date = LocalDate.parse(entry.getKey(), formatter);
                    
                    if (!date.isBefore(startDate) && !date.isAfter(endDate)) {
                        JsonNode dayData = entry.getValue();
                        
                        StockPrice stockPrice = new StockPrice();
                        stockPrice.setSymbol(symbol);
                        stockPrice.setDate(date);
                        stockPrice.setOpen(parseBigDecimal(dayData.path("1. open").asText()));
                        stockPrice.setHigh(parseBigDecimal(dayData.path("2. high").asText()));
                        stockPrice.setLow(parseBigDecimal(dayData.path("3. low").asText()));
                        stockPrice.setClose(parseBigDecimal(dayData.path("4. close").asText()));
                        stockPrice.setPrice(stockPrice.getClose()); // Use close as current price
                        stockPrice.setVolume(parseLong(dayData.path("5. volume").asText()));
                        stockPrice.setCurrency("USD");
                        stockPrice.setExchange("US");
                        
                        prices.add(stockPrice);
                    }
                } catch (Exception e) {
                    log.warn("Error parsing historical price entry for symbol: {}", symbol, e);
                }
            });
            
        } catch (Exception e) {
            log.error("Error parsing historical prices response from Alpha Vantage", e);
        }
        
        return prices;
    }

    private CompanyProfile parseCompanyProfile(String jsonResponse, String symbol) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            
            if (rootNode.path("Symbol").isMissingNode()) {
                log.warn("No company data found in Alpha Vantage response for symbol: {}", symbol);
                return null;
            }

            CompanyProfile profile = new CompanyProfile();
            profile.setSymbol(symbol);
            profile.setName(rootNode.path("Name").asText());
            profile.setDescription(rootNode.path("Description").asText());
            profile.setIndustry(rootNode.path("Industry").asText());
            profile.setSector(rootNode.path("Sector").asText());
            profile.setCountry(rootNode.path("Country").asText());
            profile.setCurrency("USD");
            profile.setExchange(rootNode.path("Exchange").asText());
            profile.setMarketCapitalization(parseLong(rootNode.path("MarketCapitalization").asText()));
            profile.setSharesOutstanding(parseLong(rootNode.path("SharesOutstanding").asText()));

            return profile;
            
        } catch (Exception e) {
            log.error("Error parsing company profile response from Alpha Vantage", e);
            return null;
        }
    }

    private FinancialMetrics parseFinancialMetrics(String jsonResponse, String symbol) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            
            if (rootNode.path("Symbol").isMissingNode()) {
                log.warn("No financial metrics found in Alpha Vantage response for symbol: {}", symbol);
                return null;
            }

            FinancialMetrics metrics = new FinancialMetrics();
            metrics.setSymbol(symbol);
            metrics.setPeRatio(parseBigDecimal(rootNode.path("PERatio").asText()));
            metrics.setPegRatio(parseBigDecimal(rootNode.path("PEGRatio").asText()));
            metrics.setPriceToBook(parseBigDecimal(rootNode.path("PriceToBookRatio").asText()));
            metrics.setPriceToSales(parseBigDecimal(rootNode.path("PriceToSalesRatioTTM").asText()));
            metrics.setProfitMargin(parseBigDecimal(rootNode.path("ProfitMargin").asText()));
            metrics.setOperatingMargin(parseBigDecimal(rootNode.path("OperatingMarginTTM").asText()));
            metrics.setReturnOnEquity(parseBigDecimal(rootNode.path("ReturnOnEquityTTM").asText()));
            metrics.setReturnOnAssets(parseBigDecimal(rootNode.path("ReturnOnAssetsTTM").asText()));
            metrics.setRevenueGrowth(parseBigDecimal(rootNode.path("RevenuePerShareTTM").asText()));
            metrics.setDividendYield(parseBigDecimal(rootNode.path("DividendYield").asText()));
            metrics.setBeta(parseBigDecimal(rootNode.path("Beta").asText()));
            metrics.setFiftyTwoWeekHigh(parseBigDecimal(rootNode.path("52WeekHigh").asText()));
            metrics.setFiftyTwoWeekLow(parseBigDecimal(rootNode.path("52WeekLow").asText()));

            return metrics;
            
        } catch (Exception e) {
            log.error("Error parsing financial metrics response from Alpha Vantage", e);
            return null;
        }
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.trim().isEmpty() || "None".equals(value) || "-".equals(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", ""));
        } catch (NumberFormatException e) {
            log.debug("Unable to parse BigDecimal: {}", value);
            return null;
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.trim().isEmpty() || "None".equals(value) || "-".equals(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.replace(",", ""));
        } catch (NumberFormatException e) {
            log.debug("Unable to parse Long: {}", value);
            return null;
        }
    }
}

package org.jds.edgar4j.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * Configuration properties for market data providers
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@Data
@Component
@ConfigurationProperties(prefix = "edgar4j.providers")
public class MarketDataProviderProperties {

    private AlphaVantageConfig alphaVantage = new AlphaVantageConfig();
    private FinnhubConfig finnhub = new FinnhubConfig();
    private YahooFinanceConfig yahooFinance = new YahooFinanceConfig();
    private CacheConfig cache = new CacheConfig();

    @Data
    public static class AlphaVantageConfig {
        private String apiKey;
        private String baseUrl = "https://www.alphavantage.co/query";
        private boolean enabled = false;
        private int priority = 2;
        private RateLimitConfig rateLimit = new RateLimitConfig(5, Duration.ofMinutes(1));
        private Duration timeout = Duration.ofSeconds(30);
        private int retryAttempts = 3;
        private Duration retryDelay = Duration.ofSeconds(1);
    }

    @Data
    public static class FinnhubConfig {
        private String apiKey;
        private String baseUrl = "https://finnhub.io/api/v1";
        private boolean enabled = false;
        private int priority = 3;
        private RateLimitConfig rateLimit = new RateLimitConfig(60, Duration.ofMinutes(1));
        private Duration timeout = Duration.ofSeconds(30);
        private int retryAttempts = 3;
        private Duration retryDelay = Duration.ofSeconds(1);
    }

    @Data
    public static class YahooFinanceConfig {
        private String baseUrl = "https://query1.finance.yahoo.com/v8/finance/chart";
        private boolean enabled = true;
        private int priority = 1;
        private RateLimitConfig rateLimit = new RateLimitConfig(2000, Duration.ofHours(1));
        private Duration timeout = Duration.ofSeconds(30);
        private int retryAttempts = 3;
        private Duration retryDelay = Duration.ofSeconds(1);
    }

    @Data
    public static class CacheConfig {
        private boolean enabled = true;
        private Duration stockPriceTtl = Duration.ofMinutes(15);
        private Duration companyProfileTtl = Duration.ofHours(24);
        private Duration financialMetricsTtl = Duration.ofHours(6);
        private Duration historicalPricesTtl = Duration.ofHours(1);
        private int maxCacheSize = 10000;
    }

    @Data
    public static class RateLimitConfig {
        private int requests;
        private Duration period;

        public RateLimitConfig() {}

        public RateLimitConfig(int requests, Duration period) {
            this.requests = requests;
            this.period = period;
        }
    }

    @Data
    public static class CircuitBreakerConfig {
        private boolean enabled = true;
        private int failureThreshold = 5;
        private Duration timeout = Duration.ofMinutes(1);
        private int minimumNumberOfCalls = 10;
        private float failureRateThreshold = 50.0f;
    }

    /**
     * Get provider configuration by name
     */
    public ProviderConfig getProviderConfig(String providerName) {
        switch (providerName.toLowerCase()) {
            case "alphavantage":
                return new ProviderConfig(alphaVantage.apiKey, alphaVantage.baseUrl, alphaVantage.enabled, 
                    alphaVantage.priority, alphaVantage.rateLimit, alphaVantage.timeout, 
                    alphaVantage.retryAttempts, alphaVantage.retryDelay);
            case "finnhub":
                return new ProviderConfig(finnhub.apiKey, finnhub.baseUrl, finnhub.enabled, 
                    finnhub.priority, finnhub.rateLimit, finnhub.timeout, 
                    finnhub.retryAttempts, finnhub.retryDelay);
            case "yahoofinance":
                return new ProviderConfig(null, yahooFinance.baseUrl, yahooFinance.enabled, 
                    yahooFinance.priority, yahooFinance.rateLimit, yahooFinance.timeout, 
                    yahooFinance.retryAttempts, yahooFinance.retryDelay);
            default:
                throw new IllegalArgumentException("Unknown provider: " + providerName);
        }
    }

    @Data
    public static class ProviderConfig {
        private String apiKey;
        private String baseUrl;
        private boolean enabled;
        private int priority;
        private RateLimitConfig rateLimit;
        private Duration timeout;
        private int retryAttempts;
        private Duration retryDelay;

        public ProviderConfig(String apiKey, String baseUrl, boolean enabled, int priority,
                            RateLimitConfig rateLimit, Duration timeout, int retryAttempts, Duration retryDelay) {
            this.apiKey = apiKey;
            this.baseUrl = baseUrl;
            this.enabled = enabled;
            this.priority = priority;
            this.rateLimit = rateLimit;
            this.timeout = timeout;
            this.retryAttempts = retryAttempts;
            this.retryDelay = retryDelay;
        }
    }
}

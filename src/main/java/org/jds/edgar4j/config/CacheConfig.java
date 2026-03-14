package org.jds.edgar4j.config;

import java.time.Duration;
import java.util.List;

import org.jds.edgar4j.properties.MarketDataProviderProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

import lombok.RequiredArgsConstructor;

/**
 * Cache configuration for frequently accessed data.
 */
@Configuration
@EnableCaching
@RequiredArgsConstructor
public class CacheConfig {

    private static final Duration DASHBOARD_CACHE_TTL = Duration.ofMinutes(5);
    private static final Duration SETTINGS_CACHE_TTL = Duration.ofMinutes(5);
    private static final Duration INSIDER_PURCHASES_CACHE_TTL = Duration.ofMinutes(5);

    private final MarketDataProviderProperties marketDataProviderProperties;

    public static final String CACHE_COMPANIES = "companies";
    public static final String CACHE_FORM_TYPES = "formTypes";
    public static final String CACHE_DASHBOARD_STATS = "dashboardStats";
    public static final String CACHE_SETTINGS = "settings";
    public static final String CACHE_TICKERS = "tickers";
    public static final String CACHE_STOCK_PRICES = "stockPrices";
    public static final String CACHE_HISTORICAL_PRICES = "historicalPrices";
    public static final String CACHE_COMPANY_PROFILES = "companyProfiles";
    public static final String CACHE_FINANCIAL_METRICS = "financialMetrics";
    public static final String CACHE_SP500 = "sp500Constituents";
    public static final String CACHE_INSIDER_PURCHASES = "insiderPurchases";

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        MarketDataProviderProperties.CacheConfig marketDataCacheConfig = marketDataProviderProperties.getCache();
        int providerCacheSize = marketDataCacheConfig != null && marketDataCacheConfig.getMaxCacheSize() > 0
                ? marketDataCacheConfig.getMaxCacheSize()
                : 10_000;

        cacheManager.setCaches(List.of(
                new ConcurrentMapCache(CACHE_COMPANIES),
                new ConcurrentMapCache(CACHE_FORM_TYPES),
                newCaffeineCache(CACHE_DASHBOARD_STATS, DASHBOARD_CACHE_TTL, 1_000),
                newCaffeineCache(CACHE_SETTINGS, SETTINGS_CACHE_TTL, 100),
                new ConcurrentMapCache(CACHE_TICKERS),
                newAsyncCaffeineCache(
                        CACHE_STOCK_PRICES,
                        marketDataCacheConfig != null ? marketDataCacheConfig.getStockPriceTtl() : Duration.ofMinutes(15),
                        providerCacheSize),
                newAsyncCaffeineCache(
                        CACHE_HISTORICAL_PRICES,
                        marketDataCacheConfig != null ? marketDataCacheConfig.getHistoricalPricesTtl() : Duration.ofHours(1),
                        providerCacheSize),
                newAsyncCaffeineCache(
                        CACHE_COMPANY_PROFILES,
                        marketDataCacheConfig != null ? marketDataCacheConfig.getCompanyProfileTtl() : Duration.ofHours(24),
                        providerCacheSize),
                newAsyncCaffeineCache(
                        CACHE_FINANCIAL_METRICS,
                        marketDataCacheConfig != null ? marketDataCacheConfig.getFinancialMetricsTtl() : Duration.ofHours(6),
                        providerCacheSize),
                new ConcurrentMapCache(CACHE_SP500),
                newCaffeineCache(CACHE_INSIDER_PURCHASES, INSIDER_PURCHASES_CACHE_TTL, 1_000)
        ));
        cacheManager.initializeCaches();
        return cacheManager;
    }

    private Cache newCaffeineCache(String cacheName, Duration ttl, int maxSize) {
        Caffeine<Object, Object> builder = buildCaffeineBuilder(ttl, maxSize);
        return new CaffeineCache(cacheName, builder.build());
    }

    private Cache newAsyncCaffeineCache(String cacheName, Duration ttl, int maxSize) {
        Caffeine<Object, Object> builder = buildCaffeineBuilder(ttl, maxSize);
        return new CaffeineCache(cacheName, builder.buildAsync(), true);
    }

    private Caffeine<Object, Object> buildCaffeineBuilder(Duration ttl, int maxSize) {
        Caffeine<Object, Object> builder = Caffeine.newBuilder()
                .maximumSize(Math.max(1, maxSize));

        if (ttl != null && !ttl.isNegative() && !ttl.isZero()) {
            builder.expireAfterWrite(ttl);
        }

        return builder;
    }
}

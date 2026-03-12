package org.jds.edgar4j.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * Cache configuration for frequently accessed data.
 * Uses in-memory caching with ConcurrentHashMap.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CACHE_COMPANIES = "companies";
    public static final String CACHE_FORM_TYPES = "formTypes";
    public static final String CACHE_DASHBOARD_STATS = "dashboardStats";
    public static final String CACHE_SETTINGS = "settings";
    public static final String CACHE_TICKERS = "tickers";
    public static final String CACHE_STOCK_PRICES = "stockPrices";
    public static final String CACHE_HISTORICAL_PRICES = "historicalPrices";
    public static final String CACHE_COMPANY_PROFILES = "companyProfiles";
    public static final String CACHE_FINANCIAL_METRICS = "financialMetrics";

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(Arrays.asList(
                new ConcurrentMapCache(CACHE_COMPANIES),
                new ConcurrentMapCache(CACHE_FORM_TYPES),
                new ConcurrentMapCache(CACHE_DASHBOARD_STATS),
                new ConcurrentMapCache(CACHE_SETTINGS),
                new ConcurrentMapCache(CACHE_TICKERS),
                new ConcurrentMapCache(CACHE_STOCK_PRICES),
                new ConcurrentMapCache(CACHE_HISTORICAL_PRICES),
                new ConcurrentMapCache(CACHE_COMPANY_PROFILES),
                new ConcurrentMapCache(CACHE_FINANCIAL_METRICS)
        ));
        return cacheManager;
    }
}

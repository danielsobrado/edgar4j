package org.jds.edgar4j.config;

import java.time.Duration;
import java.util.List;

import org.jds.edgar4j.properties.MarketDataProviderProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.github.benmanes.caffeine.cache.Caffeine;

import lombok.RequiredArgsConstructor;

@Configuration
@Profile("resource-low")
@RequiredArgsConstructor
public class LowResourceCacheConfig {

    private static final Duration DASHBOARD_CACHE_TTL = Duration.ofMinutes(5);
    private static final Duration SETTINGS_CACHE_TTL = Duration.ofMinutes(5);
    private static final Duration INSIDER_PURCHASES_CACHE_TTL = Duration.ofMinutes(5);

    private final MarketDataProviderProperties marketDataProviderProperties;

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        MarketDataProviderProperties.CacheConfig marketDataCacheConfig = marketDataProviderProperties.getCache();
        int providerCacheSize = marketDataCacheConfig != null && marketDataCacheConfig.getMaxCacheSize() > 0
                ? marketDataCacheConfig.getMaxCacheSize()
                : 10_000;

        cacheManager.setCaches(List.of(
                new ConcurrentMapCache(CacheConfig.CACHE_COMPANIES),
                new ConcurrentMapCache(CacheConfig.CACHE_FORM_TYPES),
                newCaffeineCache(CacheConfig.CACHE_DASHBOARD_STATS, DASHBOARD_CACHE_TTL, 1_000),
                newCaffeineCache(CacheConfig.CACHE_SETTINGS, SETTINGS_CACHE_TTL, 100),
                new ConcurrentMapCache(CacheConfig.CACHE_TICKERS),
                newAsyncCaffeineCache(
                        CacheConfig.CACHE_STOCK_PRICES,
                        marketDataCacheConfig != null ? marketDataCacheConfig.getStockPriceTtl() : Duration.ofMinutes(15),
                        providerCacheSize),
                newAsyncCaffeineCache(
                        CacheConfig.CACHE_HISTORICAL_PRICES,
                        marketDataCacheConfig != null ? marketDataCacheConfig.getHistoricalPricesTtl() : Duration.ofHours(1),
                        providerCacheSize),
                newAsyncCaffeineCache(
                        CacheConfig.CACHE_COMPANY_PROFILES,
                        marketDataCacheConfig != null ? marketDataCacheConfig.getCompanyProfileTtl() : Duration.ofHours(24),
                        providerCacheSize),
                newAsyncCaffeineCache(
                        CacheConfig.CACHE_FINANCIAL_METRICS,
                        marketDataCacheConfig != null ? marketDataCacheConfig.getFinancialMetricsTtl() : Duration.ofHours(6),
                        providerCacheSize),
                new ConcurrentMapCache(CacheConfig.CACHE_SP500),
                newCaffeineCache(CacheConfig.CACHE_INSIDER_PURCHASES, INSIDER_PURCHASES_CACHE_TTL, 1_000)
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

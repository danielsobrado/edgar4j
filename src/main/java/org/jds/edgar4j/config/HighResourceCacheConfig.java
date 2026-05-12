package org.jds.edgar4j.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jds.edgar4j.properties.MarketDataProviderProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import com.github.benmanes.caffeine.cache.Caffeine;
import tools.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Configuration
@Profile("resource-high")
@ConditionalOnProperty(prefix = "edgar4j", name = "resource-mode", havingValue = "high", matchIfMissing = true)
@RequiredArgsConstructor
public class HighResourceCacheConfig {

    private static final Duration DASHBOARD_CACHE_TTL = Duration.ofMinutes(5);
    private static final Duration SETTINGS_CACHE_TTL = Duration.ofMinutes(5);
    private static final Duration INSIDER_PURCHASES_CACHE_TTL = Duration.ofMinutes(5);
    private static final Duration COMPANIES_CACHE_TTL = Duration.ofHours(1);
    private static final Duration FORM_TYPES_CACHE_TTL = Duration.ofHours(1);
    private static final Duration TICKERS_CACHE_TTL = Duration.ofHours(1);
    private static final Duration SP500_CACHE_TTL = Duration.ofHours(1);

    private final MarketDataProviderProperties marketDataProviderProperties;
    private final ObjectMapper objectMapper;

    @Bean
    @ConditionalOnProperty(prefix = "spring.cache", name = "type", havingValue = "redis", matchIfMissing = true)
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfiguration = baseConfiguration(Duration.ofHours(1));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfiguration)
                .withInitialCacheConfigurations(buildCacheConfigurations())
                .transactionAware()
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    public CacheManager localCacheManager() {
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

    private Map<String, RedisCacheConfiguration> buildCacheConfigurations() {
        MarketDataProviderProperties.CacheConfig marketDataCacheConfig = marketDataProviderProperties.getCache();

        Map<String, RedisCacheConfiguration> cacheConfigurations = new LinkedHashMap<>();
        cacheConfigurations.put(CacheConfig.CACHE_COMPANIES, baseConfiguration(COMPANIES_CACHE_TTL));
        cacheConfigurations.put(CacheConfig.CACHE_FORM_TYPES, baseConfiguration(FORM_TYPES_CACHE_TTL));
        cacheConfigurations.put(CacheConfig.CACHE_DASHBOARD_STATS, baseConfiguration(DASHBOARD_CACHE_TTL));
        cacheConfigurations.put(CacheConfig.CACHE_SETTINGS, baseConfiguration(SETTINGS_CACHE_TTL));
        cacheConfigurations.put(CacheConfig.CACHE_TICKERS, baseConfiguration(TICKERS_CACHE_TTL));
        cacheConfigurations.put(CacheConfig.CACHE_STOCK_PRICES,
                baseConfiguration(marketDataCacheConfig != null ? marketDataCacheConfig.getStockPriceTtl() : Duration.ofMinutes(15)));
        cacheConfigurations.put(CacheConfig.CACHE_HISTORICAL_PRICES,
                baseConfiguration(marketDataCacheConfig != null ? marketDataCacheConfig.getHistoricalPricesTtl() : Duration.ofHours(1)));
        cacheConfigurations.put(CacheConfig.CACHE_COMPANY_PROFILES,
                baseConfiguration(marketDataCacheConfig != null ? marketDataCacheConfig.getCompanyProfileTtl() : Duration.ofHours(24)));
        cacheConfigurations.put(CacheConfig.CACHE_FINANCIAL_METRICS,
                baseConfiguration(marketDataCacheConfig != null ? marketDataCacheConfig.getFinancialMetricsTtl() : Duration.ofHours(6)));
        cacheConfigurations.put(CacheConfig.CACHE_SP500, baseConfiguration(SP500_CACHE_TTL));
        cacheConfigurations.put(CacheConfig.CACHE_INSIDER_PURCHASES, baseConfiguration(INSIDER_PURCHASES_CACHE_TTL));
        return cacheConfigurations;
    }

    private RedisCacheConfiguration baseConfiguration(Duration ttl) {
        GenericJacksonJsonRedisSerializer serializer = new GenericJacksonJsonRedisSerializer(objectMapper.copy());
        RedisSerializationContext.SerializationPair<Object> valueSerializer = RedisSerializationContext.SerializationPair
                .fromSerializer(serializer);

        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(valueSerializer)
                .disableCachingNullValues();

        if (ttl != null && !ttl.isNegative() && !ttl.isZero()) {
            configuration = configuration.entryTtl(ttl);
        }

        return configuration;
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


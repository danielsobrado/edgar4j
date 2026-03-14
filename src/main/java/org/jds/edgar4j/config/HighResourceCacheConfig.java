package org.jds.edgar4j.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jds.edgar4j.properties.MarketDataProviderProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import lombok.RequiredArgsConstructor;

@Configuration
@Profile("resource-high & !resource-low")
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

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfiguration = baseConfiguration(Duration.ofHours(1));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfiguration)
                .withInitialCacheConfigurations(buildCacheConfigurations())
                .transactionAware()
                .build();
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
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer();
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
}

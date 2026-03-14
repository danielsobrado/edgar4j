package org.jds.edgar4j.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.jds.edgar4j.properties.MarketDataProviderProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;

class CacheConfigTest {

    @Test
    @DisplayName("cacheManager should apply configured TTLs to market-data provider caches")
    void cacheManagerShouldApplyConfiguredMarketDataTtls() {
        MarketDataProviderProperties properties = new MarketDataProviderProperties();
        properties.getCache().setStockPriceTtl(Duration.ofMinutes(2));
        properties.getCache().setCompanyProfileTtl(Duration.ofHours(3));
        properties.getCache().setHistoricalPricesTtl(Duration.ofMinutes(30));
        properties.getCache().setFinancialMetricsTtl(Duration.ofHours(8));
        properties.getCache().setMaxCacheSize(42);

        CacheManager cacheManager = new CacheConfig(properties).cacheManager();

        CaffeineCache stockPrices = assertInstanceOf(
                CaffeineCache.class,
                cacheManager.getCache(CacheConfig.CACHE_STOCK_PRICES));
        CaffeineCache companyProfiles = assertInstanceOf(
                CaffeineCache.class,
                cacheManager.getCache(CacheConfig.CACHE_COMPANY_PROFILES));

        assertNotNull(stockPrices.getNativeCache().policy().expireAfterWrite().orElse(null));
        assertNotNull(stockPrices.getAsyncCache());
        assertDoesNotThrow(() -> stockPrices.put("missing", null));
        assertEquals(Duration.ofMinutes(2).toNanos(),
                stockPrices.getNativeCache().policy().expireAfterWrite().orElseThrow()
                        .getExpiresAfter(TimeUnit.NANOSECONDS));
        assertEquals(Duration.ofHours(3).toNanos(),
                companyProfiles.getNativeCache().policy().expireAfterWrite().orElseThrow()
                        .getExpiresAfter(TimeUnit.NANOSECONDS));
        assertEquals(42L, stockPrices.getNativeCache().policy().eviction().orElseThrow().getMaximum());
    }
}

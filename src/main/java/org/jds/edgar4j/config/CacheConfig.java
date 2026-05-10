package org.jds.edgar4j.config;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Configuration(proxyBeanMethods = false)
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
    public static final String CACHE_SP500 = "sp500Constituents";
    public static final String CACHE_INSIDER_PURCHASES = "insiderPurchases";

    @Bean
    @Profile("resource-high")
    @ConditionalOnProperty(prefix = "spring.cache", name = "type", havingValue = "caffeine", matchIfMissing = false)
    @ConditionalOnMissingBean(CacheManager.class)
    public CacheManager fallbackCacheManager() {
        return new ConcurrentMapCacheManager(
                List.of(
                        CACHE_COMPANIES,
                        CACHE_FORM_TYPES,
                        CACHE_DASHBOARD_STATS,
                        CACHE_SETTINGS,
                        CACHE_TICKERS,
                        CACHE_STOCK_PRICES,
                        CACHE_HISTORICAL_PRICES,
                        CACHE_COMPANY_PROFILES,
                        CACHE_FINANCIAL_METRICS,
                        CACHE_SP500,
                        CACHE_INSIDER_PURCHASES)
                        .toArray(String[]::new));
    }

}

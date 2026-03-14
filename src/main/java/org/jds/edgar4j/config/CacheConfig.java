package org.jds.edgar4j.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

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

}

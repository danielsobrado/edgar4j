package org.jds.edgar4j.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingsResponse {

    private String userAgent;
    private boolean autoRefresh;
    private int refreshInterval;
    private boolean darkMode;
    private boolean emailNotifications;
    private String notificationEmailTo;
    private String notificationEmailFrom;
    private String smtpHost;
    private int smtpPort;
    private String smtpUsername;
    @ToString.Exclude
    private String smtpPassword;
    private boolean smtpPasswordConfigured;
    private boolean smtpStartTlsEnabled;
    private String marketDataProvider;
    private String marketDataBaseUrl;
    @ToString.Exclude
    private String marketDataApiKey;
    private boolean marketDataApiKeyConfigured;
    private boolean marketDataConfigured;
    private MarketDataProvidersResponse marketDataProviders;
    // Insider Purchases Dashboard defaults
    private int insiderPurchaseLookbackDays;
    private double insiderPurchaseMinMarketCap;
    private boolean insiderPurchaseSp500Only;
    private double insiderPurchaseMinTransactionValue;

    // Real-time Filing Sync
    private boolean realtimeSyncEnabled;
    private String realtimeSyncForms;
    private int realtimeSyncLookbackHours;
    private int realtimeSyncMaxPages;
    private int realtimeSyncPageSize;

    private ApiEndpointsInfo apiEndpoints;
    private ConnectionStatus mongoDbStatus;
    private ConnectionStatus elasticsearchStatus;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiEndpointsInfo {
        private String baseSecUrl;
        private String submissionsUrl;
        private String edgarArchivesUrl;
        private String companyTickersUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConnectionStatus {
        private boolean connected;
        private String message;
        private long latencyMs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MarketDataProvidersResponse {
        private ProviderResponse tiingo;
        private ProviderResponse yahooFinance;
        private ProviderResponse finnhub;
        private ProviderResponse alphaVantage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProviderResponse {
        private boolean enabled;
        private String baseUrl;
        @ToString.Exclude
        private String apiKey;
        private boolean apiKeyConfigured;
        private boolean configured;
    }
}

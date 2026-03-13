package org.jds.edgar4j.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingsRequest {

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
    private boolean smtpStartTlsEnabled;
    private String marketDataProvider;
    private String marketDataBaseUrl;
    @ToString.Exclude
    private String marketDataApiKey;
    private MarketDataProvidersRequest marketDataProviders;
    @Builder.Default
    private Boolean clearSmtpPassword = Boolean.FALSE;
    @Builder.Default
    private Boolean clearMarketDataApiKey = Boolean.FALSE;
    // Insider Purchases Dashboard defaults
    private int insiderPurchaseLookbackDays;
    private double insiderPurchaseMinMarketCap;
    private boolean insiderPurchaseSp500Only;
    private double insiderPurchaseMinTransactionValue;

    // Real-time Filing Sync
    private Boolean realtimeSyncEnabled;
    private String realtimeSyncForms;
    private Integer realtimeSyncLookbackHours;
    private Integer realtimeSyncMaxPages;
    private Integer realtimeSyncPageSize;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MarketDataProvidersRequest {
        private ProviderRequest tiingo;
        private ProviderRequest yahooFinance;
        private ProviderRequest finnhub;
        private ProviderRequest alphaVantage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProviderRequest {
        private Boolean enabled;
        private String baseUrl;
        @ToString.Exclude
        private String apiKey;
        @Builder.Default
        private Boolean clearApiKey = Boolean.FALSE;
    }
}

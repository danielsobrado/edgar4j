package org.jds.edgar4j.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@EqualsAndHashCode(callSuper = false)
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Document(collection = "app_settings")
public class AppSettings {

    @Id
    private String id;

    @Builder.Default
    private String userAgent = "Edgar4j/1.0";

    @Builder.Default
    private boolean autoRefresh = true;

    @Builder.Default
    private int refreshInterval = 300;

    @Builder.Default
    private boolean darkMode = false;

    @Builder.Default
    private boolean emailNotifications = false;

    private String notificationEmailTo;

    private String notificationEmailFrom;

    private String smtpHost;

    @Builder.Default
    private int smtpPort = 587;

    private String smtpUsername;

    @ToString.Exclude
    private String smtpPassword;

    @Builder.Default
    private boolean smtpStartTlsEnabled = true;

    private String marketDataProvider;

    private String marketDataBaseUrl;

    @ToString.Exclude
    private String marketDataApiKey;

    private MarketDataProvidersSettings marketDataProviders;

    // --- Insider Purchases Dashboard defaults ---

    @Builder.Default
    private int insiderPurchaseLookbackDays = 30;

    @Builder.Default
    private double insiderPurchaseMinMarketCap = 0;

    @Builder.Default
    private boolean insiderPurchaseSp500Only = false;

    @Builder.Default
    private double insiderPurchaseMinTransactionValue = 0;

    // --- Real-time Filing Sync configuration ---

    @Builder.Default
    private Boolean realtimeSyncEnabled = Boolean.TRUE;

    @Builder.Default
    private String realtimeSyncForms = "4";

    @Builder.Default
    private Integer realtimeSyncLookbackHours = 1;

    @Builder.Default
    private Integer realtimeSyncMaxPages = 10;

    @Builder.Default
    private Integer realtimeSyncPageSize = 100;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MarketDataProvidersSettings {

        @Builder.Default
        private ProviderSettings tiingo = ProviderSettings.tiingoDefaults();

        @Builder.Default
        private ProviderSettings yahooFinance = ProviderSettings.yahooFinanceDefaults();

        @Builder.Default
        private ProviderSettings finnhub = ProviderSettings.finnhubDefaults();

        @Builder.Default
        private ProviderSettings alphaVantage = ProviderSettings.alphaVantageDefaults();

        public static MarketDataProvidersSettings defaultSettings() {
            return MarketDataProvidersSettings.builder().build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProviderSettings {

        private Boolean enabled;

        private String baseUrl;

        @ToString.Exclude
        private String apiKey;

        static ProviderSettings tiingoDefaults() {
            return ProviderSettings.builder()
                    .enabled(Boolean.FALSE)
                    .baseUrl("https://api.tiingo.com")
                    .build();
        }

        static ProviderSettings yahooFinanceDefaults() {
            return ProviderSettings.builder()
                    .enabled(Boolean.TRUE)
                    .baseUrl("https://query1.finance.yahoo.com/v8/finance/chart")
                    .build();
        }

        static ProviderSettings finnhubDefaults() {
            return ProviderSettings.builder()
                    .enabled(Boolean.FALSE)
                    .baseUrl("https://finnhub.io/api/v1")
                    .build();
        }

        static ProviderSettings alphaVantageDefaults() {
            return ProviderSettings.builder()
                    .enabled(Boolean.FALSE)
                    .baseUrl("https://www.alphavantage.co/query")
                    .build();
        }
    }
}

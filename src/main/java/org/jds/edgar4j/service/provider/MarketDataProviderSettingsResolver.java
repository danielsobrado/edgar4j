package org.jds.edgar4j.service.provider;

import java.time.Duration;

import org.jds.edgar4j.config.TiingoEnvProperties;
import org.jds.edgar4j.model.AppSettings;
import org.jds.edgar4j.port.AppSettingsDataPort;
import org.jds.edgar4j.properties.MarketDataProviderProperties;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component("marketDataProviderSettingsResolver")
@RequiredArgsConstructor
public class MarketDataProviderSettingsResolver {

    private static final String DEFAULT_SETTINGS_ID = "default";
    private static final String DEFAULT_TIINGO_BASE_URL = "https://api.tiingo.com";

    private final AppSettingsDataPort appSettingsRepository;
    private final MarketDataProviderProperties properties;
    private final TiingoEnvProperties tiingoEnvProperties;

    public String resolvePreferredProviderName() {
        return resolvePreferredProviderName(loadSettings());
    }

    public String resolvePreferredProviderName(AppSettings settings) {
        String selectedProvider = resolveSelectedProvider(settings);
        return MarketDataProviders.isProviderServiceProvider(selectedProvider) ? selectedProvider : null;
    }

    public String resolveSelectedProvider(AppSettings settings) {
        String explicitProvider = MarketDataProviders.normalizeOrNull(
                settings != null ? settings.getMarketDataProvider() : null);
        if (explicitProvider != null) {
            return explicitProvider;
        }

        return tiingoEnvProperties.hasApiToken()
                ? MarketDataProviders.TIINGO
                : MarketDataProviders.NONE;
    }

    public ResolvedProviderConfig resolve(String providerName) {
        return resolve(providerName, loadSettings());
    }

    public ResolvedProviderConfig resolve(String providerName, AppSettings settings) {
        String normalizedProvider = MarketDataProviders.normalize(providerName);
        if (MarketDataProviders.NONE.equals(normalizedProvider)) {
            throw new IllegalArgumentException("Unsupported provider: " + providerName);
        }

        String selectedProvider = resolveSelectedProvider(settings);
        return switch (normalizedProvider) {
            case MarketDataProviders.TIINGO -> resolveTiingoConfig(selectedProvider, settings);
            case MarketDataProviders.YAHOO_FINANCE -> resolveYahooFinanceConfig(selectedProvider, settings);
            case MarketDataProviders.FINNHUB -> resolveFinnhubConfig(selectedProvider, settings);
            case MarketDataProviders.ALPHA_VANTAGE -> resolveAlphaVantageConfig(selectedProvider, settings);
            default -> throw new IllegalArgumentException("Unsupported provider: " + providerName);
        };
    }

    private ResolvedProviderConfig resolveTiingoConfig(String selectedProvider, AppSettings settings) {
        AppSettings.ProviderSettings providerSettings = getStoredProviderSettings(settings, MarketDataProviders.TIINGO);
        boolean selected = MarketDataProviders.TIINGO.equals(selectedProvider);
        boolean enabled = resolveEnabled(providerSettings, selected);
        String baseUrl = firstNonNull(
                trimToNull(providerSettings != null ? providerSettings.getBaseUrl() : null),
                resolveLegacySelectedBaseUrl(MarketDataProviders.TIINGO, settings),
                tiingoEnvProperties.getBaseUrl().orElse(null),
                DEFAULT_TIINGO_BASE_URL);
        String apiKey = firstNonNull(
                trimToNull(providerSettings != null ? providerSettings.getApiKey() : null),
                resolveLegacySelectedApiKey(MarketDataProviders.TIINGO, settings),
                tiingoEnvProperties.getApiToken().orElse(null));
        boolean configured = baseUrl != null && apiKey != null;

        return new ResolvedProviderConfig(
                MarketDataProviders.TIINGO,
                selected,
                enabled,
                configured,
                baseUrl,
                apiKey,
                0,
                null,
                Duration.ofSeconds(30),
                Duration.ofSeconds(1));
    }

    private ResolvedProviderConfig resolveYahooFinanceConfig(String selectedProvider, AppSettings settings) {
        MarketDataProviderProperties.YahooFinanceConfig config = properties.getYahooFinance();
        AppSettings.ProviderSettings providerSettings = getStoredProviderSettings(settings, MarketDataProviders.YAHOO_FINANCE);
        boolean selected = MarketDataProviders.YAHOO_FINANCE.equals(selectedProvider);
        boolean enabled = resolveEnabled(providerSettings, selected || config.isEnabled());
        String baseUrl = resolveBaseUrl(
                providerSettings,
                resolveLegacySelectedBaseUrl(MarketDataProviders.YAHOO_FINANCE, settings),
                config.getBaseUrl());
        boolean configured = enabled && baseUrl != null;

        return new ResolvedProviderConfig(
                MarketDataProviders.YAHOO_FINANCE,
                selected,
                enabled,
                configured,
                baseUrl,
                null,
                config.getPriority(),
                config.getRateLimit(),
                defaultDuration(config.getTimeout(), Duration.ofSeconds(30)),
                defaultDuration(config.getRetryDelay(), Duration.ofSeconds(1)));
    }

    private ResolvedProviderConfig resolveFinnhubConfig(String selectedProvider, AppSettings settings) {
        MarketDataProviderProperties.FinnhubConfig config = properties.getFinnhub();
        AppSettings.ProviderSettings providerSettings = getStoredProviderSettings(settings, MarketDataProviders.FINNHUB);
        boolean selected = MarketDataProviders.FINNHUB.equals(selectedProvider);
        boolean enabled = resolveEnabled(providerSettings, selected || config.isEnabled());
        String baseUrl = resolveBaseUrl(
                providerSettings,
                resolveLegacySelectedBaseUrl(MarketDataProviders.FINNHUB, settings),
                config.getBaseUrl());
        String apiKey = resolveApiKey(
                providerSettings,
                resolveLegacySelectedApiKey(MarketDataProviders.FINNHUB, settings),
                config.getApiKey());
        boolean configured = enabled && baseUrl != null && apiKey != null;

        return new ResolvedProviderConfig(
                MarketDataProviders.FINNHUB,
                selected,
                enabled,
                configured,
                baseUrl,
                apiKey,
                config.getPriority(),
                config.getRateLimit(),
                defaultDuration(config.getTimeout(), Duration.ofSeconds(30)),
                defaultDuration(config.getRetryDelay(), Duration.ofSeconds(1)));
    }

    private ResolvedProviderConfig resolveAlphaVantageConfig(String selectedProvider, AppSettings settings) {
        MarketDataProviderProperties.AlphaVantageConfig config = properties.getAlphaVantage();
        AppSettings.ProviderSettings providerSettings = getStoredProviderSettings(settings, MarketDataProviders.ALPHA_VANTAGE);
        boolean selected = MarketDataProviders.ALPHA_VANTAGE.equals(selectedProvider);
        boolean enabled = resolveEnabled(providerSettings, selected || config.isEnabled());
        String baseUrl = resolveBaseUrl(
                providerSettings,
                resolveLegacySelectedBaseUrl(MarketDataProviders.ALPHA_VANTAGE, settings),
                config.getBaseUrl());
        String apiKey = resolveApiKey(
                providerSettings,
                resolveLegacySelectedApiKey(MarketDataProviders.ALPHA_VANTAGE, settings),
                config.getApiKey());
        boolean configured = enabled && baseUrl != null && apiKey != null;

        return new ResolvedProviderConfig(
                MarketDataProviders.ALPHA_VANTAGE,
                selected,
                enabled,
                configured,
                baseUrl,
                apiKey,
                config.getPriority(),
                config.getRateLimit(),
                defaultDuration(config.getTimeout(), Duration.ofSeconds(30)),
                defaultDuration(config.getRetryDelay(), Duration.ofSeconds(1)));
    }

    private AppSettings loadSettings() {
        return appSettingsRepository.findById(DEFAULT_SETTINGS_ID).orElse(null);
    }

    private boolean resolveEnabled(AppSettings.ProviderSettings providerSettings, boolean fallbackEnabled) {
        return providerSettings != null && providerSettings.getEnabled() != null
                ? providerSettings.getEnabled()
                : fallbackEnabled;
    }

    private String resolveApiKey(AppSettings.ProviderSettings providerSettings, String legacyApiKey, String propertyApiKey) {
        return firstNonNull(
                trimToNull(providerSettings != null ? providerSettings.getApiKey() : null),
                legacyApiKey,
                trimToNull(propertyApiKey));
    }

    private String resolveBaseUrl(AppSettings.ProviderSettings providerSettings, String legacyBaseUrl, String propertyBaseUrl) {
        return firstNonNull(
                trimToNull(providerSettings != null ? providerSettings.getBaseUrl() : null),
                legacyBaseUrl,
                trimToNull(propertyBaseUrl));
    }

    private AppSettings.ProviderSettings getStoredProviderSettings(AppSettings settings, String providerName) {
        if (settings == null || settings.getMarketDataProviders() == null) {
            return null;
        }

        AppSettings.MarketDataProvidersSettings providerSettings = settings.getMarketDataProviders();
        return switch (providerName) {
            case MarketDataProviders.TIINGO -> providerSettings.getTiingo();
            case MarketDataProviders.YAHOO_FINANCE -> providerSettings.getYahooFinance();
            case MarketDataProviders.FINNHUB -> providerSettings.getFinnhub();
            case MarketDataProviders.ALPHA_VANTAGE -> providerSettings.getAlphaVantage();
            default -> null;
        };
    }

    private Duration defaultDuration(Duration value, Duration fallback) {
        return value != null ? value : fallback;
    }

    private String resolveLegacySelectedBaseUrl(String providerName, AppSettings settings) {
        if (!providerName.equals(resolveSelectedProvider(settings))) {
            return null;
        }

        String legacyBaseUrl = trimToNull(settings != null ? settings.getMarketDataBaseUrl() : null);
        if (legacyBaseUrl == null) {
            return null;
        }

        if (!MarketDataProviders.TIINGO.equals(providerName)
                && DEFAULT_TIINGO_BASE_URL.equals(trimTrailingSlashes(legacyBaseUrl))
                && resolveLegacySelectedApiKey(providerName, settings) == null) {
            return null;
        }

        return legacyBaseUrl;
    }

    private String resolveLegacySelectedApiKey(String providerName, AppSettings settings) {
        if (!providerName.equals(resolveSelectedProvider(settings))) {
            return null;
        }
        return trimToNull(settings != null ? settings.getMarketDataApiKey() : null);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimTrailingSlashes(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        return trimmed.replaceAll("/+$", "");
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }

        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    public record ResolvedProviderConfig(
            String providerName,
            boolean selected,
            boolean enabled,
            boolean configured,
            String baseUrl,
            String apiKey,
            int priority,
            MarketDataProviderProperties.RateLimitConfig rateLimit,
            Duration timeout,
            Duration retryDelay) {

        public boolean apiKeyConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }

        public boolean operational() {
            return enabled && configured;
        }
    }
}

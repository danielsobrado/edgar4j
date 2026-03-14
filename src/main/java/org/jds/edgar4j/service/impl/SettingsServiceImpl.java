package org.jds.edgar4j.service.impl;

import java.net.URI;
import java.util.Locale;

import org.jds.edgar4j.config.TiingoEnvProperties;
import org.jds.edgar4j.dto.request.SettingsRequest;
import org.jds.edgar4j.dto.response.SettingsResponse;
import org.jds.edgar4j.integration.SecUserAgentPolicy;
import org.jds.edgar4j.model.AppSettings;
import org.jds.edgar4j.port.AppSettingsDataPort;
import org.jds.edgar4j.properties.Edgar4JProperties;
import org.jds.edgar4j.properties.MarketDataProviderProperties;
import org.jds.edgar4j.service.SettingsService;
import org.jds.edgar4j.service.provider.MarketDataProviderSettingsResolver;
import org.jds.edgar4j.service.provider.MarketDataProviders;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsServiceImpl implements SettingsService {

    private static final String DEFAULT_SETTINGS_ID = "default";
    private static final String DEFAULT_TIINGO_BASE_URL = "https://api.tiingo.com";
    private static final String DEFAULT_REALTIME_SYNC_FORMS = "4";
    private static final int DEFAULT_REALTIME_SYNC_LOOKBACK_HOURS = 1;
    private static final int DEFAULT_REALTIME_SYNC_MAX_PAGES = 10;
    private static final int DEFAULT_REALTIME_SYNC_PAGE_SIZE = 100;
    private static final String MASKED_SECRET_VALUE = "********";

    private final AppSettingsDataPort appSettingsRepository;
    private final ObjectProvider<MongoTemplate> mongoTemplateProvider;
    private final TiingoEnvProperties tiingoEnvProperties;
    private final Edgar4JProperties edgar4JProperties;
    private final MarketDataProviderProperties marketDataProviderProperties;
    private final MarketDataProviderSettingsResolver marketDataProviderSettingsResolver;

    @Value("${edgar4j.sec.user-agent:}")
    private String configuredUserAgent;

    @Override
    public SettingsResponse getSettings() {
        return toSettingsResponse(getOrCreateDefaultSettings());
    }

    @Override
    public SettingsResponse updateSettings(SettingsRequest request) {
        AppSettings settings = getOrCreateDefaultSettings();
        log.info("Updating settings: {}", summarizeRequest(request));

        String selectedMarketDataProvider = request.getMarketDataProvider() != null
                ? normalizeMarketDataProvider(request.getMarketDataProvider())
                : normalizeStoredMarketDataProvider(settings);
        AppSettings.MarketDataProvidersSettings marketDataProviders = hasMarketDataSettingsUpdate(request)
                ? mergeMarketDataProviders(
                        request,
                        settings,
                        selectedMarketDataProvider)
                : settings.getMarketDataProviders();

        validateEmailNotificationSettings(request, settings);
        validateMarketDataSettings(buildMarketDataPreview(settings, selectedMarketDataProvider, marketDataProviders));

        settings.setUserAgent(resolveUserAgent(request.getUserAgent(), settings));
        settings.setAutoRefresh(request.isAutoRefresh());
        settings.setRefreshInterval(request.getRefreshInterval());
        settings.setDarkMode(request.isDarkMode());
        settings.setEmailNotifications(request.isEmailNotifications());
        settings.setNotificationEmailTo(trimToNull(request.getNotificationEmailTo()));
        settings.setNotificationEmailFrom(trimToNull(request.getNotificationEmailFrom()));
        settings.setSmtpHost(trimToNull(request.getSmtpHost()));
        settings.setSmtpPort(request.getSmtpPort() > 0 ? request.getSmtpPort() : 587);
        settings.setSmtpUsername(trimToNull(request.getSmtpUsername()));
        settings.setSmtpPassword(resolveSecretValue(
                request.getSmtpPassword(),
                settings.getSmtpPassword(),
                request.getClearSmtpPassword()));
        settings.setSmtpStartTlsEnabled(request.isSmtpStartTlsEnabled());
        settings.setMarketDataProvider(selectedMarketDataProvider);
        settings.setMarketDataProviders(marketDataProviders);
        syncLegacySelectedMarketDataFields(settings);
        settings.setInsiderPurchaseLookbackDays(resolvePositiveInteger(
                request.getInsiderPurchaseLookbackDays(),
                resolveInsiderPurchaseLookbackDays(settings)));
        settings.setInsiderPurchaseMinMarketCap(resolveNonNegativeDouble(
                request.getInsiderPurchaseMinMarketCap(),
                resolveInsiderPurchaseMinMarketCap(settings)));
        settings.setInsiderPurchaseSp500Only(request.getInsiderPurchaseSp500Only() != null
                ? request.getInsiderPurchaseSp500Only()
                : resolveInsiderPurchaseSp500Only(settings));
        settings.setInsiderPurchaseMinTransactionValue(resolveNonNegativeDouble(
                request.getInsiderPurchaseMinTransactionValue(),
                resolveInsiderPurchaseMinTransactionValue(settings)));
        settings.setRealtimeSyncEnabled(request.getRealtimeSyncEnabled() != null
                ? request.getRealtimeSyncEnabled()
                : resolveRealtimeSyncEnabled(settings));
        settings.setRealtimeSyncForms(request.getRealtimeSyncForms() != null
                ? normalizeRealtimeSyncForms(request.getRealtimeSyncForms())
                : resolveRealtimeSyncForms(settings));
        settings.setRealtimeSyncLookbackHours(resolvePositiveInteger(
                request.getRealtimeSyncLookbackHours(),
                resolveRealtimeSyncLookbackHours(settings)));
        settings.setRealtimeSyncMaxPages(resolvePositiveInteger(
                request.getRealtimeSyncMaxPages(),
                resolveRealtimeSyncMaxPages(settings)));
        settings.setRealtimeSyncPageSize(resolvePositiveInteger(
                request.getRealtimeSyncPageSize(),
                resolveRealtimeSyncPageSize(settings)));

        return toSettingsResponse(appSettingsRepository.save(settings));
    }

    @Override
    public String getUserAgent() {
        AppSettings settings = getOrCreateDefaultSettings();
        String storedUserAgent = SecUserAgentPolicy.normalize(settings.getUserAgent());
        if (SecUserAgentPolicy.isValid(storedUserAgent)) {
            return storedUserAgent;
        }

        String fallbackUserAgent = SecUserAgentPolicy.normalize(configuredUserAgent);
        if (SecUserAgentPolicy.isValid(fallbackUserAgent)) {
            return fallbackUserAgent;
        }

        String effectiveUserAgent = storedUserAgent != null ? storedUserAgent : fallbackUserAgent;
        if (effectiveUserAgent != null) {
            log.warn("SEC user agent is not SEC-compliant. {}", SecUserAgentPolicy.guidance());
        }
        return effectiveUserAgent;
    }

    @Override
    public SettingsResponse.ConnectionStatus checkMongoDbConnection() {
        MongoTemplate mongoTemplate = mongoTemplateProvider.getIfAvailable();
        if (mongoTemplate == null) {
            return SettingsResponse.ConnectionStatus.builder()
                    .connected(false)
                    .message("MongoDB is disabled in low resource mode")
                    .latencyMs(-1)
                    .build();
        }

        try {
            long startTime = System.currentTimeMillis();
            mongoTemplate.getDb().runCommand(new org.bson.Document("ping", 1));
            long latency = System.currentTimeMillis() - startTime;

            return SettingsResponse.ConnectionStatus.builder()
                    .connected(true)
                    .message("Connected to MongoDB")
                    .latencyMs(latency)
                    .build();
        } catch (Exception e) {
            log.error("MongoDB connection check failed", e);
            return SettingsResponse.ConnectionStatus.builder()
                    .connected(false)
                    .message("Failed to connect: " + e.getMessage())
                    .latencyMs(-1)
                    .build();
        }
    }

    @Override
    public SettingsResponse.ConnectionStatus checkElasticsearchConnection() {
        return SettingsResponse.ConnectionStatus.builder()
                .connected(false)
                .message("Elasticsearch not configured")
                .latencyMs(-1)
                .build();
    }

    private AppSettings getOrCreateDefaultSettings() {
        return appSettingsRepository.findById(DEFAULT_SETTINGS_ID)
                .orElseGet(() -> {
                    AppSettings.AppSettingsBuilder builder = AppSettings.builder()
                            .id(DEFAULT_SETTINGS_ID);
                    String normalizedConfiguredUserAgent = SecUserAgentPolicy.normalize(configuredUserAgent);
                    if (normalizedConfiguredUserAgent != null) {
                        if (!SecUserAgentPolicy.isValid(normalizedConfiguredUserAgent)) {
                            log.warn("Configured SEC user agent is not SEC-compliant. {}", SecUserAgentPolicy.guidance());
                        }
                        builder.userAgent(normalizedConfiguredUserAgent);
                    }
                    return appSettingsRepository.save(builder.build());
                });
    }

    private SettingsResponse toSettingsResponse(AppSettings settings) {
        String selectedProvider = resolveSelectedMarketDataProvider(settings);
        MarketDataProviderSettingsResolver.ResolvedProviderConfig selectedConfig = resolveSelectedProviderConfig(settings);
        SettingsResponse.ApiKeySource selectedApiKeySource = resolveApiKeySource(selectedProvider, settings);

        return SettingsResponse.builder()
                .userAgent(SecUserAgentPolicy.normalize(settings.getUserAgent()))
                .autoRefresh(settings.isAutoRefresh())
                .refreshInterval(settings.getRefreshInterval())
                .darkMode(settings.isDarkMode())
                .emailNotifications(settings.isEmailNotifications())
                .notificationEmailTo(settings.getNotificationEmailTo())
                .notificationEmailFrom(settings.getNotificationEmailFrom())
                .smtpHost(settings.getSmtpHost())
                .smtpPort(settings.getSmtpPort())
                .smtpUsername(settings.getSmtpUsername())
                .smtpPassword(null)
                .smtpPasswordConfigured(trimToNull(settings.getSmtpPassword()) != null)
                .smtpStartTlsEnabled(settings.isSmtpStartTlsEnabled())
                .marketDataProvider(selectedProvider)
                .marketDataBaseUrl(selectedConfig != null ? selectedConfig.baseUrl() : null)
                .marketDataApiKey(null)
                .marketDataApiKeyConfigured(selectedConfig != null && selectedConfig.apiKeyConfigured())
                .marketDataApiKeySource(selectedApiKeySource)
                .marketDataConfigured(selectedConfig != null && selectedConfig.operational())
                .marketDataProviders(toMarketDataProvidersResponse(settings))
                .insiderPurchaseLookbackDays(resolveInsiderPurchaseLookbackDays(settings))
                .insiderPurchaseMinMarketCap(resolveInsiderPurchaseMinMarketCap(settings))
                .insiderPurchaseSp500Only(resolveInsiderPurchaseSp500Only(settings))
                .insiderPurchaseMinTransactionValue(resolveInsiderPurchaseMinTransactionValue(settings))
                .realtimeSyncEnabled(resolveRealtimeSyncEnabled(settings))
                .realtimeSyncForms(resolveRealtimeSyncForms(settings))
                .realtimeSyncLookbackHours(resolveRealtimeSyncLookbackHours(settings))
                .realtimeSyncMaxPages(resolveRealtimeSyncMaxPages(settings))
                .realtimeSyncPageSize(resolveRealtimeSyncPageSize(settings))
                .apiEndpoints(SettingsResponse.ApiEndpointsInfo.builder()
                    .baseSecUrl(edgar4JProperties.getUrls().getBaseSecUrl())
                    .submissionsUrl(edgar4JProperties.getUrls().getSubmissionsUrl())
                    .edgarArchivesUrl(edgar4JProperties.getUrls().getEdgarDataArchivesUrl())
                    .companyTickersUrl(edgar4JProperties.getUrls().getCompanyTickersUrl())
                        .build())
                .mongoDbStatus(checkMongoDbConnection())
                .elasticsearchStatus(checkElasticsearchConnection())
                .build();
    }

    private SettingsResponse.MarketDataProvidersResponse toMarketDataProvidersResponse(AppSettings settings) {
        return SettingsResponse.MarketDataProvidersResponse.builder()
                .tiingo(toProviderResponse(MarketDataProviders.TIINGO, settings))
                .yahooFinance(toProviderResponse(MarketDataProviders.YAHOO_FINANCE, settings))
                .finnhub(toProviderResponse(MarketDataProviders.FINNHUB, settings))
                .alphaVantage(toProviderResponse(MarketDataProviders.ALPHA_VANTAGE, settings))
                .build();
    }

    private SettingsResponse.ProviderResponse toProviderResponse(String providerName, AppSettings settings) {
        MarketDataProviderSettingsResolver.ResolvedProviderConfig providerConfig =
                marketDataProviderSettingsResolver.resolve(providerName, settings);
        return SettingsResponse.ProviderResponse.builder()
                .enabled(providerConfig.enabled())
                .baseUrl(providerConfig.baseUrl())
                .apiKey(null)
                .apiKeyConfigured(providerConfig.apiKeyConfigured())
                .apiKeySource(resolveApiKeySource(providerName, settings))
                .configured(providerConfig.operational())
                .build();
    }

    private void validateEmailNotificationSettings(SettingsRequest request, AppSettings existingSettings) {
        if (!request.isEmailNotifications()) {
            return;
        }

        requireNonBlank(request.getNotificationEmailTo(), "Notification recipient email is required when email notifications are enabled");
        requireNonBlank(request.getNotificationEmailFrom(), "Notification sender email is required when email notifications are enabled");
        requireNonBlank(request.getSmtpHost(), "SMTP host is required when email notifications are enabled");
        requireNonBlank(request.getSmtpUsername(), "SMTP username is required when email notifications are enabled");
        if (resolveSecretValue(request.getSmtpPassword(), existingSettings.getSmtpPassword(), request.getClearSmtpPassword()) == null) {
            throw new IllegalArgumentException("SMTP password is required when email notifications are enabled");
        }

        if (request.getSmtpPort() <= 0) {
            throw new IllegalArgumentException("SMTP port must be greater than 0 when email notifications are enabled");
        }
    }

    private void validateMarketDataSettings(AppSettings previewSettings) {
        String selectedProvider = resolveSelectedMarketDataProvider(previewSettings);
        if (!MarketDataProviders.isSupported(selectedProvider)) {
            throw new IllegalArgumentException("Unsupported market data provider: " + selectedProvider);
        }

        for (String providerName : new String[] {
                MarketDataProviders.TIINGO,
                MarketDataProviders.YAHOO_FINANCE,
                MarketDataProviders.FINNHUB,
                MarketDataProviders.ALPHA_VANTAGE }) {
            MarketDataProviderSettingsResolver.ResolvedProviderConfig providerConfig =
                    marketDataProviderSettingsResolver.resolve(providerName, previewSettings);
            if (providerConfig.enabled() && providerConfig.baseUrl() == null) {
                throw new IllegalArgumentException(providerName + " base URL is required when the provider is enabled");
            }
            if (providerConfig.enabled() && !providerConfig.operational()) {
                throw new IllegalArgumentException(providerName + " is enabled but not fully configured");
            }
        }

        if (MarketDataProviders.NONE.equals(selectedProvider)) {
            return;
        }

        MarketDataProviderSettingsResolver.ResolvedProviderConfig selectedConfig =
                marketDataProviderSettingsResolver.resolve(selectedProvider, previewSettings);
        if (!selectedConfig.enabled()) {
            throw new IllegalArgumentException("Selected market data provider is disabled");
        }
        if (!selectedConfig.operational()) {
            throw new IllegalArgumentException("Selected market data provider is not configured");
        }
    }

    private AppSettings.MarketDataProvidersSettings mergeMarketDataProviders(
            SettingsRequest request,
            AppSettings existingSettings,
            String selectedProvider) {
        SettingsRequest.MarketDataProvidersRequest requestedProviders = request.getMarketDataProviders();

        return AppSettings.MarketDataProvidersSettings.builder()
                .tiingo(mergeProviderSettings(
                        MarketDataProviders.TIINGO,
                        existingSettings,
                        requestedProviders != null ? requestedProviders.getTiingo() : null,
                        MarketDataProviders.TIINGO.equals(selectedProvider),
                        request.getMarketDataBaseUrl(),
                        request.getMarketDataApiKey(),
                        request.getClearMarketDataApiKey()))
                .yahooFinance(mergeProviderSettings(
                        MarketDataProviders.YAHOO_FINANCE,
                        existingSettings,
                        requestedProviders != null ? requestedProviders.getYahooFinance() : null,
                        MarketDataProviders.YAHOO_FINANCE.equals(selectedProvider),
                        request.getMarketDataBaseUrl(),
                        request.getMarketDataApiKey(),
                        request.getClearMarketDataApiKey()))
                .finnhub(mergeProviderSettings(
                        MarketDataProviders.FINNHUB,
                        existingSettings,
                        requestedProviders != null ? requestedProviders.getFinnhub() : null,
                        MarketDataProviders.FINNHUB.equals(selectedProvider),
                        request.getMarketDataBaseUrl(),
                        request.getMarketDataApiKey(),
                        request.getClearMarketDataApiKey()))
                .alphaVantage(mergeProviderSettings(
                        MarketDataProviders.ALPHA_VANTAGE,
                        existingSettings,
                        requestedProviders != null ? requestedProviders.getAlphaVantage() : null,
                        MarketDataProviders.ALPHA_VANTAGE.equals(selectedProvider),
                        request.getMarketDataBaseUrl(),
                        request.getMarketDataApiKey(),
                        request.getClearMarketDataApiKey()))
                .build();
    }

    private AppSettings.ProviderSettings mergeProviderSettings(
            String providerName,
            AppSettings existingSettings,
            SettingsRequest.ProviderRequest requestSettings,
            boolean selectedProvider,
            String legacyBaseUrl,
            String legacyApiKey,
            Boolean clearLegacyApiKey) {
        AppSettings.ProviderSettings effectiveExisting = resolvePersistedProviderSettings(providerName, existingSettings);

        Boolean enabled = requestSettings != null && requestSettings.getEnabled() != null
                ? requestSettings.getEnabled()
                : selectedProvider ? Boolean.TRUE : effectiveExisting.getEnabled();

        String requestedBaseUrl = requestSettings != null && requestSettings.getBaseUrl() != null
                ? requestSettings.getBaseUrl()
                : selectedProvider ? legacyBaseUrl : null;
        String baseUrl = requestedBaseUrl != null
                ? normalizeMarketDataBaseUrl(providerName, requestedBaseUrl)
                : normalizeMarketDataBaseUrl(providerName, effectiveExisting.getBaseUrl());

        String requestedApiKey = requestSettings != null && requestSettings.getApiKey() != null
                ? requestSettings.getApiKey()
                : selectedProvider ? legacyApiKey : null;
        Boolean clearApiKey = requestSettings != null && requestSettings.getClearApiKey() != null
                ? requestSettings.getClearApiKey()
                : selectedProvider ? clearLegacyApiKey : Boolean.FALSE;
        String apiKey = resolveSecretValue(requestedApiKey, effectiveExisting.getApiKey(), clearApiKey);

        return AppSettings.ProviderSettings.builder()
                .enabled(enabled)
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();
    }

    private AppSettings.ProviderSettings defaultProviderSettings(String providerName) {
        return switch (providerName) {
            case MarketDataProviders.TIINGO -> AppSettings.ProviderSettings.builder()
                    .enabled(Boolean.FALSE)
                    .baseUrl(DEFAULT_TIINGO_BASE_URL)
                    .build();
            case MarketDataProviders.YAHOO_FINANCE -> AppSettings.ProviderSettings.builder()
                    .enabled(marketDataProviderProperties.getYahooFinance().isEnabled())
                    .baseUrl(trimTrailingSlashes(marketDataProviderProperties.getYahooFinance().getBaseUrl()))
                    .build();
            case MarketDataProviders.FINNHUB -> AppSettings.ProviderSettings.builder()
                    .enabled(marketDataProviderProperties.getFinnhub().isEnabled())
                    .baseUrl(trimTrailingSlashes(marketDataProviderProperties.getFinnhub().getBaseUrl()))
                    .build();
            case MarketDataProviders.ALPHA_VANTAGE -> AppSettings.ProviderSettings.builder()
                    .enabled(marketDataProviderProperties.getAlphaVantage().isEnabled())
                    .baseUrl(trimTrailingSlashes(marketDataProviderProperties.getAlphaVantage().getBaseUrl()))
                    .build();
            default -> AppSettings.ProviderSettings.builder().build();
        };
    }

    private AppSettings.ProviderSettings resolvePersistedProviderSettings(
            String providerName,
            AppSettings settings) {
        AppSettings.ProviderSettings defaults = defaultProviderSettings(providerName);
        AppSettings.ProviderSettings storedProviderSettings = getStoredProviderSettings(settings, providerName);
        boolean providerSelectedInStoredSettings = providerName.equals(normalizeMarketDataProvider(
                settings != null ? settings.getMarketDataProvider() : null));

        Boolean enabled = storedProviderSettings != null && storedProviderSettings.getEnabled() != null
                ? storedProviderSettings.getEnabled()
                : defaults.getEnabled();
        String baseUrl = firstNonBlank(
                trimToNull(storedProviderSettings != null ? storedProviderSettings.getBaseUrl() : null),
                resolveLegacyStoredBaseUrl(providerName, settings, providerSelectedInStoredSettings),
                defaults.getBaseUrl());
        String apiKey = firstNonBlank(
                trimToNull(storedProviderSettings != null ? storedProviderSettings.getApiKey() : null),
                resolveLegacyStoredApiKey(providerName, settings, providerSelectedInStoredSettings));

        return AppSettings.ProviderSettings.builder()
                .enabled(enabled)
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();
    }

    private AppSettings buildMarketDataPreview(
            AppSettings existingSettings,
            String selectedProvider,
            AppSettings.MarketDataProvidersSettings providerSettings) {
        return AppSettings.builder()
                .id(existingSettings.getId())
                .marketDataProvider(selectedProvider)
                .marketDataProviders(providerSettings)
                .build();
    }

    private void syncLegacySelectedMarketDataFields(AppSettings settings) {
        String explicitSelectedProvider = normalizeStoredMarketDataProvider(settings);
        if (explicitSelectedProvider == null || MarketDataProviders.NONE.equals(explicitSelectedProvider)) {
            settings.setMarketDataBaseUrl(null);
            settings.setMarketDataApiKey(null);
            return;
        }

        MarketDataProviderSettingsResolver.ResolvedProviderConfig selectedConfig = resolveSelectedProviderConfig(settings);
        if (selectedConfig == null) {
            settings.setMarketDataBaseUrl(null);
            settings.setMarketDataApiKey(null);
            return;
        }

        settings.setMarketDataBaseUrl(selectedConfig.baseUrl());
        AppSettings.ProviderSettings storedProviderSettings = getStoredProviderSettings(settings, selectedConfig.providerName());
        settings.setMarketDataApiKey(trimToNull(storedProviderSettings != null ? storedProviderSettings.getApiKey() : null));
    }

    private String normalizeMarketDataProvider(String provider) {
        return MarketDataProviders.normalize(provider);
    }

    private String normalizeStoredMarketDataProvider(AppSettings settings) {
        String storedProvider = settings != null ? trimToNull(settings.getMarketDataProvider()) : null;
        return storedProvider != null ? normalizeMarketDataProvider(storedProvider) : null;
    }

    private String normalizeMarketDataBaseUrl(String provider, String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return defaultMarketDataBaseUrl(provider);
        }

        String normalized = trimTrailingSlashes(baseUrl.trim());
        if (MarketDataProviders.TIINGO.equals(provider) && normalized.endsWith("/api/test")) {
            normalized = normalized.substring(0, normalized.length() - "/api/test".length());
        } else if (MarketDataProviders.TIINGO.equals(provider) && normalized.endsWith("/api")) {
            normalized = normalized.substring(0, normalized.length() - "/api".length());
        }

        URI parsedUri = URI.create(normalized);
        String scheme = parsedUri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("Market data base URL must use HTTP or HTTPS");
        }
        if (parsedUri.getHost() == null || parsedUri.getHost().isBlank()) {
            throw new IllegalArgumentException("Market data base URL must include a host");
        }
        if (parsedUri.getRawQuery() != null || parsedUri.getRawFragment() != null) {
            throw new IllegalArgumentException("Market data base URL must not include a query string or fragment");
        }

        return trimTrailingSlashes(parsedUri.toString());
    }

    private String defaultMarketDataBaseUrl(String provider) {
        return switch (provider) {
            case MarketDataProviders.YAHOO_FINANCE -> trimTrailingSlashes(marketDataProviderProperties.getYahooFinance().getBaseUrl());
            case MarketDataProviders.FINNHUB -> trimTrailingSlashes(marketDataProviderProperties.getFinnhub().getBaseUrl());
            case MarketDataProviders.ALPHA_VANTAGE -> trimTrailingSlashes(marketDataProviderProperties.getAlphaVantage().getBaseUrl());
            default -> DEFAULT_TIINGO_BASE_URL;
        };
    }

    private String normalizeRealtimeSyncForms(String forms) {
        if (forms == null || forms.isBlank()) {
            return DEFAULT_REALTIME_SYNC_FORMS;
        }

        String normalized = java.util.Arrays.stream(forms.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.replaceAll("\\s+", " ").toUpperCase(Locale.ROOT))
                .distinct()
                .reduce((left, right) -> left + "," + right)
                .orElse(DEFAULT_REALTIME_SYNC_FORMS);

        return normalized.isBlank() ? DEFAULT_REALTIME_SYNC_FORMS : normalized;
    }

    private String resolveUserAgent(String requestedUserAgent, AppSettings existingSettings) {
        if (requestedUserAgent == null) {
            return SecUserAgentPolicy.normalize(existingSettings.getUserAgent());
        }

        return SecUserAgentPolicy.normalizeAndValidate(requestedUserAgent);
    }

    private String resolveSelectedMarketDataProvider(AppSettings settings) {
        return marketDataProviderSettingsResolver.resolveSelectedProvider(settings);
    }

    private MarketDataProviderSettingsResolver.ResolvedProviderConfig resolveSelectedProviderConfig(AppSettings settings) {
        String selectedProvider = resolveSelectedMarketDataProvider(settings);
        return MarketDataProviders.NONE.equals(selectedProvider)
                ? null
                : marketDataProviderSettingsResolver.resolve(selectedProvider, settings);
    }

    private boolean resolveRealtimeSyncEnabled(AppSettings settings) {
        return settings.getRealtimeSyncEnabled() != null
                ? settings.getRealtimeSyncEnabled()
                : true;
    }

    private String resolveRealtimeSyncForms(AppSettings settings) {
        return normalizeRealtimeSyncForms(settings.getRealtimeSyncForms());
    }

    private int resolveRealtimeSyncLookbackHours(AppSettings settings) {
        Integer value = settings.getRealtimeSyncLookbackHours();
        return value != null && value > 0 ? value : DEFAULT_REALTIME_SYNC_LOOKBACK_HOURS;
    }

    private int resolveRealtimeSyncMaxPages(AppSettings settings) {
        Integer value = settings.getRealtimeSyncMaxPages();
        return value != null && value > 0 ? value : DEFAULT_REALTIME_SYNC_MAX_PAGES;
    }

    private int resolveRealtimeSyncPageSize(AppSettings settings) {
        Integer value = settings.getRealtimeSyncPageSize();
        return value != null && value > 0 ? value : DEFAULT_REALTIME_SYNC_PAGE_SIZE;
    }

    private int resolvePositiveInteger(Integer requestedValue, int fallbackValue) {
        if (requestedValue == null) {
            return fallbackValue;
        }

        return requestedValue > 0 ? requestedValue : fallbackValue;
    }

    private double resolveNonNegativeDouble(Double requestedValue, double fallbackValue) {
        if (requestedValue == null) {
            return fallbackValue;
        }

        return requestedValue >= 0d ? requestedValue : fallbackValue;
    }

    private String summarizeRequest(SettingsRequest request) {
        return String.format(
                "userAgent=%s, emailNotifications=%s, marketDataProvider=%s, realtimeSyncEnabled=%s, realtimeSyncForms=%s",
                trimToNull(request.getUserAgent()),
                request.isEmailNotifications(),
                normalizeMarketDataProvider(request.getMarketDataProvider()),
                request.getRealtimeSyncEnabled(),
                trimToNull(request.getRealtimeSyncForms()));
    }

    private boolean hasMarketDataSettingsUpdate(SettingsRequest request) {
        return request.getMarketDataProvider() != null
                || request.getMarketDataBaseUrl() != null
                || request.getMarketDataApiKey() != null
                || Boolean.TRUE.equals(request.getClearMarketDataApiKey())
                || request.getMarketDataProviders() != null;
    }

    private String resolveSecretValue(String requestedValue, String existingValue, Boolean clearSecret) {
        if (Boolean.TRUE.equals(clearSecret)) {
            return null;
        }

        String normalizedRequestedValue = trimToNull(requestedValue);
        if (MASKED_SECRET_VALUE.equals(normalizedRequestedValue)) {
            return existingValue;
        }
        if (normalizedRequestedValue != null) {
            return normalizedRequestedValue;
        }
        return existingValue;
    }

    private void requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
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

    private int resolveInsiderPurchaseLookbackDays(AppSettings settings) {
        int value = settings.getInsiderPurchaseLookbackDays();
        return value > 0 ? value : 30;
    }

    private double resolveInsiderPurchaseMinMarketCap(AppSettings settings) {
        return Math.max(0d, settings.getInsiderPurchaseMinMarketCap());
    }

    private boolean resolveInsiderPurchaseSp500Only(AppSettings settings) {
        return settings.isInsiderPurchaseSp500Only();
    }

    private double resolveInsiderPurchaseMinTransactionValue(AppSettings settings) {
        return Math.max(0d, settings.getInsiderPurchaseMinTransactionValue());
    }

    private String resolveLegacyStoredBaseUrl(String providerName, AppSettings settings, boolean providerSelectedInStoredSettings) {
        if (!providerSelectedInStoredSettings || settings == null) {
            return null;
        }

        String legacyBaseUrl = trimToNull(settings.getMarketDataBaseUrl());
        if (legacyBaseUrl == null) {
            return null;
        }

        if (!MarketDataProviders.TIINGO.equals(providerName)
                && DEFAULT_TIINGO_BASE_URL.equals(trimTrailingSlashes(legacyBaseUrl))
                && resolveLegacyStoredApiKey(providerName, settings, true) == null) {
            return null;
        }

        return legacyBaseUrl;
    }

    private String resolveLegacyStoredApiKey(String providerName, AppSettings settings, boolean providerSelectedInStoredSettings) {
        if (!providerSelectedInStoredSettings || settings == null) {
            return null;
        }
        return trimToNull(settings.getMarketDataApiKey());
    }

    private AppSettings.ProviderSettings getStoredProviderSettings(AppSettings settings, String providerName) {
        if (settings == null || settings.getMarketDataProviders() == null) {
            return null;
        }

        return switch (providerName) {
            case MarketDataProviders.TIINGO -> settings.getMarketDataProviders().getTiingo();
            case MarketDataProviders.YAHOO_FINANCE -> settings.getMarketDataProviders().getYahooFinance();
            case MarketDataProviders.FINNHUB -> settings.getMarketDataProviders().getFinnhub();
            case MarketDataProviders.ALPHA_VANTAGE -> settings.getMarketDataProviders().getAlphaVantage();
            default -> null;
        };
    }

    private SettingsResponse.ApiKeySource resolveApiKeySource(String providerName, AppSettings settings) {
        if (providerName == null || MarketDataProviders.NONE.equals(providerName)) {
            return SettingsResponse.ApiKeySource.NONE;
        }
        if (hasStoredProviderApiKey(providerName, settings)) {
            return SettingsResponse.ApiKeySource.STORED;
        }

        MarketDataProviderSettingsResolver.ResolvedProviderConfig resolvedConfig =
                marketDataProviderSettingsResolver.resolve(providerName, settings);
        return resolvedConfig.apiKeyConfigured()
                ? SettingsResponse.ApiKeySource.FALLBACK
                : SettingsResponse.ApiKeySource.NONE;
    }

    private boolean hasStoredProviderApiKey(String providerName, AppSettings settings) {
        AppSettings.ProviderSettings storedProviderSettings = getStoredProviderSettings(settings, providerName);
        if (trimToNull(storedProviderSettings != null ? storedProviderSettings.getApiKey() : null) != null) {
            return true;
        }

        boolean providerSelectedInStoredSettings = providerName.equals(normalizeMarketDataProvider(
                settings != null ? settings.getMarketDataProvider() : null));
        return trimToNull(resolveLegacyStoredApiKey(providerName, settings, providerSelectedInStoredSettings)) != null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }
}

package org.jds.edgar4j.service.impl;

import org.jds.edgar4j.config.TiingoEnvProperties;
import org.jds.edgar4j.dto.request.SettingsRequest;
import org.jds.edgar4j.dto.response.SettingsResponse;
import org.jds.edgar4j.model.AppSettings;
import org.jds.edgar4j.repository.AppSettingsRepository;
import org.jds.edgar4j.service.SettingsService;
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
    private static final String DEFAULT_MARKET_DATA_PROVIDER = "NONE";
    private static final String DEFAULT_TIINGO_BASE_URL = "https://api.tiingo.com";
    private static final String DEFAULT_REALTIME_SYNC_FORMS = "4";
    private static final int DEFAULT_REALTIME_SYNC_LOOKBACK_HOURS = 1;
    private static final int DEFAULT_REALTIME_SYNC_MAX_PAGES = 10;
    private static final int DEFAULT_REALTIME_SYNC_PAGE_SIZE = 100;

    private final AppSettingsRepository appSettingsRepository;
    private final MongoTemplate mongoTemplate;
    private final TiingoEnvProperties tiingoEnvProperties;

    @Value("${edgar4j.urls.baseSecUrl}")
    private String baseSecUrl;

    @Value("${edgar4j.urls.submissionsUrl}")
    private String submissionsUrl;

    @Value("${edgar4j.urls.edgarDataArchivesUrl}")
    private String edgarArchivesUrl;

    @Value("${edgar4j.urls.companyTickersUrl}")
    private String companyTickersUrl;

    @Value("${edgar4j.sec.user-agent:}")
    private String configuredUserAgent;

    @Override
    public SettingsResponse getSettings() {
        AppSettings settings = getOrCreateDefaultSettings();
        return toSettingsResponse(settings);
    }

    @Override
    public SettingsResponse updateSettings(SettingsRequest request) {
        log.info("Updating settings: {}", request);

        validateEmailNotificationSettings(request);
        validateMarketDataSettings(request);

        AppSettings settings = getOrCreateDefaultSettings();
        settings.setUserAgent(request.getUserAgent());
        settings.setAutoRefresh(request.isAutoRefresh());
        settings.setRefreshInterval(request.getRefreshInterval());
        settings.setDarkMode(request.isDarkMode());
        settings.setEmailNotifications(request.isEmailNotifications());
        settings.setNotificationEmailTo(trimToNull(request.getNotificationEmailTo()));
        settings.setNotificationEmailFrom(trimToNull(request.getNotificationEmailFrom()));
        settings.setSmtpHost(trimToNull(request.getSmtpHost()));
        settings.setSmtpPort(request.getSmtpPort() > 0 ? request.getSmtpPort() : 587);
        settings.setSmtpUsername(trimToNull(request.getSmtpUsername()));
        settings.setSmtpPassword(trimToNull(request.getSmtpPassword()));
        settings.setSmtpStartTlsEnabled(request.isSmtpStartTlsEnabled());
        settings.setMarketDataProvider(normalizeMarketDataProvider(request.getMarketDataProvider()));
        settings.setMarketDataBaseUrl(normalizeMarketDataBaseUrl(request.getMarketDataBaseUrl()));
        settings.setMarketDataApiKey(trimToNull(request.getMarketDataApiKey()));
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

        settings = appSettingsRepository.save(settings);
        return toSettingsResponse(settings);
    }

    @Override
    public String getUserAgent() {
        return getOrCreateDefaultSettings().getUserAgent();
    }

    @Override
    public SettingsResponse.ConnectionStatus checkMongoDbConnection() {
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
                    if (configuredUserAgent != null && !configuredUserAgent.isBlank()) {
                        builder.userAgent(configuredUserAgent);
                    }
                    AppSettings newSettings = builder.build();
                    return appSettingsRepository.save(newSettings);
                });
    }

    private SettingsResponse toSettingsResponse(AppSettings settings) {
        return SettingsResponse.builder()
                .userAgent(settings.getUserAgent())
                .autoRefresh(settings.isAutoRefresh())
                .refreshInterval(settings.getRefreshInterval())
                .darkMode(settings.isDarkMode())
                .emailNotifications(settings.isEmailNotifications())
                .notificationEmailTo(settings.getNotificationEmailTo())
                .notificationEmailFrom(settings.getNotificationEmailFrom())
                .smtpHost(settings.getSmtpHost())
                .smtpPort(settings.getSmtpPort())
                .smtpUsername(settings.getSmtpUsername())
                .smtpPassword(settings.getSmtpPassword())
                .smtpStartTlsEnabled(settings.isSmtpStartTlsEnabled())
                .marketDataProvider(resolveEffectiveMarketDataProvider(settings))
                .marketDataBaseUrl(resolveEffectiveMarketDataBaseUrl(settings))
                .marketDataApiKey(settings.getMarketDataApiKey())
                .marketDataConfigured(isMarketDataConfigured(settings))
                .realtimeSyncEnabled(resolveRealtimeSyncEnabled(settings))
                .realtimeSyncForms(resolveRealtimeSyncForms(settings))
                .realtimeSyncLookbackHours(resolveRealtimeSyncLookbackHours(settings))
                .realtimeSyncMaxPages(resolveRealtimeSyncMaxPages(settings))
                .realtimeSyncPageSize(resolveRealtimeSyncPageSize(settings))
                .apiEndpoints(SettingsResponse.ApiEndpointsInfo.builder()
                        .baseSecUrl(baseSecUrl)
                        .submissionsUrl(submissionsUrl)
                        .edgarArchivesUrl(edgarArchivesUrl)
                        .companyTickersUrl(companyTickersUrl)
                        .build())
                .mongoDbStatus(checkMongoDbConnection())
                .elasticsearchStatus(checkElasticsearchConnection())
                .build();
    }

    private void validateEmailNotificationSettings(SettingsRequest request) {
        if (!request.isEmailNotifications()) {
            return;
        }

        requireNonBlank(request.getNotificationEmailTo(), "Notification recipient email is required when email notifications are enabled");
        requireNonBlank(request.getNotificationEmailFrom(), "Notification sender email is required when email notifications are enabled");
        requireNonBlank(request.getSmtpHost(), "SMTP host is required when email notifications are enabled");
        requireNonBlank(request.getSmtpUsername(), "SMTP username is required when email notifications are enabled");
        requireNonBlank(request.getSmtpPassword(), "SMTP password is required when email notifications are enabled");

        if (request.getSmtpPort() <= 0) {
            throw new IllegalArgumentException("SMTP port must be greater than 0 when email notifications are enabled");
        }
    }

    private void validateMarketDataSettings(SettingsRequest request) {
        String provider = normalizeMarketDataProvider(request.getMarketDataProvider());

        if (!"NONE".equals(provider) && !"TIINGO".equals(provider)) {
            throw new IllegalArgumentException("Unsupported market data provider: " + provider);
        }

        if ("TIINGO".equals(provider) && !hasEffectiveMarketDataApiKey(request)) {
            requireNonBlank(request.getMarketDataApiKey(),
                    "Tiingo API key is required when Tiingo market data is enabled");
        }
    }

    private String normalizeMarketDataProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return DEFAULT_MARKET_DATA_PROVIDER;
        }

        return provider.trim().toUpperCase();
    }

    private String normalizeMarketDataBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return DEFAULT_TIINGO_BASE_URL;
        }

        String normalized = baseUrl.trim().replaceAll("/+$", "");
        if (normalized.endsWith("/api/test")) {
            normalized = normalized.substring(0, normalized.length() - "/api/test".length());
        } else if (normalized.endsWith("/api")) {
            normalized = normalized.substring(0, normalized.length() - "/api".length());
        }

        return normalized.replaceAll("/+$", "");
    }

    private String normalizeRealtimeSyncForms(String forms) {
        if (forms == null || forms.isBlank()) {
            return DEFAULT_REALTIME_SYNC_FORMS;
        }

        String normalized = java.util.Arrays.stream(forms.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.replaceAll("\\s+", " ").toUpperCase())
                .distinct()
                .reduce((left, right) -> left + "," + right)
                .orElse(DEFAULT_REALTIME_SYNC_FORMS);

        return normalized.isBlank() ? DEFAULT_REALTIME_SYNC_FORMS : normalized;
    }

    private boolean isMarketDataConfigured(AppSettings settings) {
        return "TIINGO".equals(resolveEffectiveMarketDataProvider(settings))
                && resolveEffectiveMarketDataApiKey(settings) != null;
    }

    private String resolveEffectiveMarketDataProvider(AppSettings settings) {
        String provider = normalizeMarketDataProvider(settings.getMarketDataProvider());
        if ("NONE".equals(provider) && tiingoEnvProperties.hasApiToken()) {
            return "TIINGO";
        }
        return provider;
    }

    private boolean hasEffectiveMarketDataApiKey(SettingsRequest request) {
        return trimToNull(request.getMarketDataApiKey()) != null || tiingoEnvProperties.hasApiToken();
    }

    private String resolveEffectiveMarketDataApiKey(AppSettings settings) {
        String explicitKey = trimToNull(settings.getMarketDataApiKey());
        if (explicitKey != null) {
            return explicitKey;
        }
        return tiingoEnvProperties.getApiToken().orElse(null);
    }

    private String resolveEffectiveMarketDataBaseUrl(AppSettings settings) {
        String explicitBaseUrl = trimToNull(settings.getMarketDataBaseUrl());
        if (explicitBaseUrl != null) {
            String normalizedExplicitBaseUrl = normalizeMarketDataBaseUrl(explicitBaseUrl);
            if (!DEFAULT_TIINGO_BASE_URL.equals(normalizedExplicitBaseUrl)) {
                return normalizedExplicitBaseUrl;
            }
        }

        return tiingoEnvProperties.getBaseUrl()
                .map(this::normalizeMarketDataBaseUrl)
                .orElseGet(() -> normalizeMarketDataBaseUrl(explicitBaseUrl));
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
}


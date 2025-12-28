package org.jds.edgar4j.service.impl;

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

    private final AppSettingsRepository appSettingsRepository;
    private final MongoTemplate mongoTemplate;

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

        AppSettings settings = getOrCreateDefaultSettings();
        settings.setUserAgent(request.getUserAgent());
        settings.setAutoRefresh(request.isAutoRefresh());
        settings.setRefreshInterval(request.getRefreshInterval());
        settings.setDarkMode(request.isDarkMode());
        settings.setEmailNotifications(request.isEmailNotifications());

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
}


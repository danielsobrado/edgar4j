package org.jds.edgar4j.service;

import org.jds.edgar4j.dto.request.SettingsRequest;
import org.jds.edgar4j.dto.response.SettingsResponse;

public interface SettingsService {

    SettingsResponse getSettings();

    SettingsResponse updateSettings(SettingsRequest request);

    String getUserAgent();

    SettingsResponse.ConnectionStatus checkMongoDbConnection();

    SettingsResponse.ConnectionStatus checkElasticsearchConnection();
}

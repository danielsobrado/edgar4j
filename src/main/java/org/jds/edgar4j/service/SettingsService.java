package org.jds.edgar4j.service;

import java.util.List;

import org.jds.edgar4j.dto.request.SettingsRequest;
import org.jds.edgar4j.dto.response.SettingsResponse;
import org.jds.edgar4j.dto.response.UsaSpendingColumnPreferencesResponse;

public interface SettingsService {

    SettingsResponse getSettings();

    SettingsResponse updateSettings(SettingsRequest request);

    UsaSpendingColumnPreferencesResponse getUsaSpendingColumnPreferences();

    UsaSpendingColumnPreferencesResponse updateUsaSpendingColumnPreferences(List<String> hiddenColumns);

    String getUserAgent();

    SettingsResponse.ConnectionStatus checkMongoDbConnection();

    SettingsResponse.ConnectionStatus checkElasticsearchConnection();
}

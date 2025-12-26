package org.jds.edgar4j.controller;

import org.jds.edgar4j.dto.request.SettingsRequest;
import org.jds.edgar4j.dto.response.ApiResponse;
import org.jds.edgar4j.dto.response.SettingsResponse;
import org.jds.edgar4j.service.SettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping
    public ResponseEntity<ApiResponse<SettingsResponse>> getSettings() {
        log.info("GET /api/settings");
        SettingsResponse settings = settingsService.getSettings();
        return ResponseEntity.ok(ApiResponse.success(settings));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<SettingsResponse>> updateSettings(@RequestBody SettingsRequest request) {
        log.info("PUT /api/settings: {}", request);
        SettingsResponse settings = settingsService.updateSettings(request);
        return ResponseEntity.ok(ApiResponse.success(settings, "Settings updated successfully"));
    }

    @GetMapping("/health/mongodb")
    public ResponseEntity<ApiResponse<SettingsResponse.ConnectionStatus>> checkMongoDb() {
        log.info("GET /api/settings/health/mongodb");
        SettingsResponse.ConnectionStatus status = settingsService.checkMongoDbConnection();
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    @GetMapping("/health/elasticsearch")
    public ResponseEntity<ApiResponse<SettingsResponse.ConnectionStatus>> checkElasticsearch() {
        log.info("GET /api/settings/health/elasticsearch");
        SettingsResponse.ConnectionStatus status = settingsService.checkElasticsearchConnection();
        return ResponseEntity.ok(ApiResponse.success(status));
    }
}

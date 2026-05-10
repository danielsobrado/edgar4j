package org.jds.edgar4j.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.Executor;

import org.jds.edgar4j.dto.response.ApiResponse;
import org.jds.edgar4j.dto.response.SettingsResponse;
import org.jds.edgar4j.job.TickerSyncJob;
import org.jds.edgar4j.service.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class SettingsControllerTest {

    @Mock
    private SettingsService settingsService;

    @Mock
    private TickerSyncJob tickerSyncJob;

    private SettingsController settingsController;

    private static final Executor INLINE_EXECUTOR = Runnable::run;

    @BeforeEach
    void setUp() {
        settingsController = new SettingsController(settingsService, tickerSyncJob, INLINE_EXECUTOR);
    }

    @Test
    @DisplayName("getSettings should return settings payload")
    void getSettingsShouldReturnPayload() {
        SettingsResponse expected = SettingsResponse.builder()
                .userAgent("agent")
                .autoRefresh(true)
                .refreshInterval(60)
                .darkMode(true)
                .build();
        when(settingsService.getSettings()).thenReturn(expected);

        ResponseEntity<ApiResponse<SettingsResponse>> response = settingsController.getSettings();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals(expected, response.getBody().getData());
    }

    @Test
    @DisplayName("triggerTickerSync should start async job when not running")
    void triggerTickerSyncShouldStartAsyncJob() {
        when(tickerSyncJob.isRunning()).thenReturn(false);

        ResponseEntity<ApiResponse<String>> response = settingsController.triggerTickerSync();

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        verify(tickerSyncJob).triggerSync();
    }

    @Test
    @DisplayName("triggerTickerSync should reject while another sync is running")
    void triggerTickerSyncShouldRejectWhenRunning() {
        when(tickerSyncJob.isRunning()).thenReturn(true);

        ResponseEntity<ApiResponse<String>> response = settingsController.triggerTickerSync();

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Ticker sync is already running", response.getBody().getMessage());
    }
}

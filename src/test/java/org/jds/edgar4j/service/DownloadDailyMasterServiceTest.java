package org.jds.edgar4j.service;

import org.jds.edgar4j.integration.SecRateLimiter;
import org.jds.edgar4j.properties.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author J. Daniel Sobrado
 * @version 1.1
 * @since 2022-09-18
 */
@ExtendWith(MockitoExtension.class)
class DownloadDailyMasterServiceTest {

    @Mock
    private StorageProperties storageProperties;

    @Mock
    private SettingsService settingsService;

    @Mock
    private SecRateLimiter secRateLimiter;

    @Mock
    private WebClient.Builder webClientBuilder;

    private DownloadDailyMasterService downloadDailyMasterService;

    private static final String TEST_DATE = "02/14/2019";
    private static final String TEST_USER_AGENT = "Test User Agent";

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        downloadDailyMasterService = spy(new DownloadDailyMasterService(
                storageProperties,
                settingsService,
                secRateLimiter,
                webClientBuilder));
    }

    @DisplayName("Should download daily master for valid date")
    @Test
    void testDownloadDailyMaster_ValidDate() throws Exception {
        // Given
        String validDate = TEST_DATE;
        Path outputDirectory = tempDir.resolve("daily-indexes");

        when(storageProperties.getDailyIndexesPath()).thenReturn(outputDirectory.toString());
        when(settingsService.getUserAgent()).thenReturn(TEST_USER_AGENT);
        doReturn(Optional.of("daily-index-content"))
                .when(downloadDailyMasterService)
                .fetchDailyMasterIndex(anyString(), eq(TEST_USER_AGENT));
        
        // When
        downloadDailyMasterService.downloadDailyMaster(validDate);
        
        // Then
        Path outputFile = outputDirectory.resolve("daily_idx_20190214.idx");
        assertTrue(Files.exists(outputFile));
        assertEquals("daily-index-content", Files.readString(outputFile));
        verify(settingsService).getUserAgent();
        verify(storageProperties).getDailyIndexesPath();
    }

    @DisplayName("Should reject future dates")
    @Test
    void testDownloadDailyMaster_FutureDate() {
        // Given
        LocalDate futureDate = LocalDate.now().plusDays(1);
        String futureDateString = futureDate.format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));
        
        // When
        downloadDailyMasterService.downloadDailyMaster(futureDateString);
        
        // Then
        verifyNoInteractions(storageProperties, settingsService, secRateLimiter, webClientBuilder);
    }

    @DisplayName("Should handle invalid date format")
    @Test
    void testDownloadDailyMaster_InvalidDateFormat() {
        // Given
        String invalidDate = "invalid-date";
        
        // When & Then
        assertThrows(DateTimeParseException.class, () -> downloadDailyMasterService.downloadDailyMaster(invalidDate));
    }

    @DisplayName("Should handle null date input")
    @Test
    void testDownloadDailyMaster_NullDate() {
        // Given
        String nullDate = null;

        // When & Then
        assertThrows(NullPointerException.class, () -> downloadDailyMasterService.downloadDailyMaster(nullDate));
    }

    @DisplayName("Should handle empty date input")
    @Test
    void testDownloadDailyMaster_EmptyDate() {
        // Given
        String emptyDate = "";
        
        // When & Then
        assertThrows(DateTimeParseException.class, () -> downloadDailyMasterService.downloadDailyMaster(emptyDate));
    }
}

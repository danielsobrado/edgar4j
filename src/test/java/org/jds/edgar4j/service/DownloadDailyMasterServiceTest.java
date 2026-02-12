package org.jds.edgar4j.service;

import org.jds.edgar4j.properties.Edgar4JProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @author J. Daniel Sobrado
 * @version 1.1
 * @since 2022-09-18
 */
@ExtendWith(MockitoExtension.class)
class DownloadDailyMasterServiceTest {

    @Mock
    private Edgar4JProperties edgar4jProperties;

    @InjectMocks
    private DownloadDailyMasterService downloadDailyMasterService;

    private static final String TEST_DATE = "02/14/2019";
    private static final String TEST_USER_AGENT = "Test User Agent";
    private static final String TEST_PATH = "/tmp/test";

    @BeforeEach
    void setUp() {
        when(edgar4jProperties.getUserAgent()).thenReturn(TEST_USER_AGENT);
        when(edgar4jProperties.getDailyIndexesPath()).thenReturn(TEST_PATH);
    }

    @DisplayName("Should download daily master for valid date")
    @Test
    void testDownloadDailyMaster_ValidDate() {
        // Given
        String validDate = TEST_DATE;
        
        // When
        downloadDailyMasterService.downloadDailyMaster(validDate);
        
        // Then
        verify(edgar4jProperties, atLeastOnce()).getUserAgent();
        verify(edgar4jProperties, atLeastOnce()).getDailyIndexesPath();
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
        // Service should log error and return early for future dates
        // Properties should not be called if date validation fails
        verify(edgar4jProperties, never()).getUserAgent();
        verify(edgar4jProperties, never()).getDailyIndexesPath();
    }

    @DisplayName("Should handle invalid date format")
    @Test
    void testDownloadDailyMaster_InvalidDateFormat() {
        // Given
        String invalidDate = "invalid-date";
        
        // When & Then
        assertThrows(Exception.class, () -> {
            downloadDailyMasterService.downloadDailyMaster(invalidDate);
        });
    }

    @DisplayName("Should create directories before download")
    @Test
    void testDownloadDailyMaster_CreatesDirectories() {
        // Given
        String validDate = TEST_DATE;
        
        // When
        downloadDailyMasterService.downloadDailyMaster(validDate);
        
        // Then
        verify(edgar4jProperties, atLeastOnce()).getDailyIndexesPath();
    }

    @DisplayName("Should handle past date successfully")
    @Test
    void testDownloadDailyMaster_PastDate() {
        // Given
        String pastDate = "01/01/2020";
        
        // When
        downloadDailyMasterService.downloadDailyMaster(pastDate);
        
        // Then
        verify(edgar4jProperties, atLeastOnce()).getUserAgent();
        verify(edgar4jProperties, atLeastOnce()).getDailyIndexesPath();
    }

    @DisplayName("Should handle null date input")
    @Test
    void testDownloadDailyMaster_NullDate() {
        // Given
        String nullDate = null;
        
        // When & Then
        assertThrows(Exception.class, () -> {
            downloadDailyMasterService.downloadDailyMaster(nullDate);
        });
    }

    @DisplayName("Should handle empty date input")
    @Test
    void testDownloadDailyMaster_EmptyDate() {
        // Given
        String emptyDate = "";
        
        // When & Then
        assertThrows(Exception.class, () -> {
            downloadDailyMasterService.downloadDailyMaster(emptyDate);
        });
    }
}

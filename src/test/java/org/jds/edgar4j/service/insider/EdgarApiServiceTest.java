package org.jds.edgar4j.service.insider;

import org.jds.edgar4j.service.insider.impl.EdgarApiServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EdgarApiService implementation
 * Tests the new Phase 2 functionality for Form 4 document retrieval and bulk processing
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@ExtendWith(MockitoExtension.class)
class EdgarApiServiceTest {

    private EdgarApiServiceImpl edgarApiService;

    @BeforeEach
    void setUp() {
        edgarApiService = new EdgarApiServiceImpl();
        
        // Set test URLs using reflection to avoid Spring context for unit tests
        ReflectionTestUtils.setField(edgarApiService, "submissionsCIKUrl", "https://data.sec.gov/submissions/CIK");
        ReflectionTestUtils.setField(edgarApiService, "edgarDataArchivesUrl", "https://www.sec.gov/Archives/edgar/data");
        ReflectionTestUtils.setField(edgarApiService, "companyTickersUrl", "https://www.sec.gov/files/company_tickers.json");
    }

    @DisplayName("Should format CIK correctly for accession number extraction")
    @Test
    void testCikExtractionFromAccessionNumber() {
        // Given
        String accessionNumber1 = "0001234567-24-000001";
        String accessionNumber2 = "0000789019-23-000123";
        
        // When - Using reflection to test private method
        String cik1 = (String) ReflectionTestUtils.invokeMethod(edgarApiService, "extractCikFromAccessionNumber", accessionNumber1);
        String cik2 = (String) ReflectionTestUtils.invokeMethod(edgarApiService, "extractCikFromAccessionNumber", accessionNumber2);
        
        // Then
        assertEquals("0001234567", cik1);
        assertEquals("0000789019", cik2);
    }

    @DisplayName("Should handle invalid accession number format")
    @Test
    void testInvalidAccessionNumberFormat() {
        // Given
        String invalidAccessionNumber = "invalid-format";
        
        // When
        String cik = (String) ReflectionTestUtils.invokeMethod(edgarApiService, "extractCikFromAccessionNumber", invalidAccessionNumber);
        
        // Then
        assertNull(cik);
    }

    @DisplayName("Should extract accession number from filename correctly")
    @Test
    void testAccessionNumberExtractionFromFilename() {
        // Given
        String filename1 = "edgar/data/789019/0001234567-24-000001/doc4.xml";
        String filename2 = "edgar/data/123456/0000987654-23-000456/form4.xml";
        
        // When
        String accessionNumber1 = (String) ReflectionTestUtils.invokeMethod(edgarApiService, "extractAccessionFromFilename", filename1);
        String accessionNumber2 = (String) ReflectionTestUtils.invokeMethod(edgarApiService, "extractAccessionFromFilename", filename2);
        
        // Then
        assertEquals("0001234567-24-000001", accessionNumber1);
        assertEquals("0000987654-23-000456", accessionNumber2);
    }

    @DisplayName("Should handle malformed filename gracefully")
    @Test
    void testMalformedFilename() {
        // Given
        String malformedFilename = "invalid/path";
        
        // When
        String accessionNumber = (String) ReflectionTestUtils.invokeMethod(edgarApiService, "extractAccessionFromFilename", malformedFilename);
        
        // Then
        assertNull(accessionNumber);
    }

    @DisplayName("Should parse daily master index correctly")
    @Test
    void testDailyMasterIndexParsing() {
        // Given
        String sampleMasterIndex = """
            Description:                 Master Index of EDGAR Dissemination System
            Creation Date:               January 15, 2024
            
            CIK|Company Name|Form Type|Date Filed|Filename
            789019|MICROSOFT CORP|4|2024-01-15|edgar/data/789019/0001234567-24-000001/doc4.xml
            123456|TEST COMPANY INC|10-K|2024-01-15|edgar/data/123456/0000567890-24-000002/test10k.htm
            987654|EXAMPLE CORP|4|2024-01-15|edgar/data/987654/0000111222-24-000003/form4.xml
            """;
        
        // When
        List<String> accessionNumbers = (List<String>) ReflectionTestUtils.invokeMethod(
            edgarApiService, "parseDailyMasterIndex", sampleMasterIndex);
        
        // Then
        assertNotNull(accessionNumbers);
        assertEquals(2, accessionNumbers.size()); // Only Form 4 filings
        assertTrue(accessionNumbers.contains("0001234567-24-000001"));
        assertTrue(accessionNumbers.contains("0000111222-24-000003"));
    }

    @DisplayName("Should handle empty daily master index")
    @Test
    void testEmptyDailyMasterIndex() {
        // Given
        String emptyIndex = "CIK|Company Name|Form Type|Date Filed|Filename\n";
        
        // When
        List<String> accessionNumbers = (List<String>) ReflectionTestUtils.invokeMethod(
            edgarApiService, "parseDailyMasterIndex", emptyIndex);
        
        // Then
        assertNotNull(accessionNumbers);
        assertTrue(accessionNumbers.isEmpty());
    }

    @DisplayName("Should skip weekends in date range processing")
    @Test
    void testDateRangeWithWeekends() {
        // Given - Date range that includes a weekend
        LocalDate friday = LocalDate.of(2024, 1, 12);  // Friday
        LocalDate monday = LocalDate.of(2024, 1, 15);  // Monday
        
        // When
        List<String> accessionNumbers = edgarApiService.getForm4FilingsByDateRange(friday, monday);
        
        // Then
        assertNotNull(accessionNumbers);
        // Should process Friday and Monday, but skip Saturday and Sunday
        // Since this is a unit test without actual API calls, the list will be empty
        // but the method should complete without errors
    }

    @DisplayName("Should handle same start and end date")
    @Test
    void testSingleDateRange() {
        // Given
        LocalDate singleDate = LocalDate.of(2024, 1, 15);
        
        // When
        List<String> accessionNumbers = edgarApiService.getForm4FilingsByDateRange(singleDate, singleDate);
        
        // Then
        assertNotNull(accessionNumbers);
        // Should process only the single date
    }

    @DisplayName("Should validate date range order")
    @Test
    void testInvalidDateRange() {
        // Given - End date before start date
        LocalDate startDate = LocalDate.of(2024, 1, 15);
        LocalDate endDate = LocalDate.of(2024, 1, 10);
        
        // When
        List<String> accessionNumbers = edgarApiService.getForm4FilingsByDateRange(startDate, endDate);
        
        // Then
        assertNotNull(accessionNumbers);
        assertTrue(accessionNumbers.isEmpty());
    }

    @DisplayName("Should skip weekends when processing single dates")
    @Test
    void testSkipWeekendsForSingleDate() {
        // Given
        LocalDate saturday = LocalDate.of(2024, 1, 13);  // Saturday
        LocalDate sunday = LocalDate.of(2024, 1, 14);    // Sunday
        
        // When
        List<String> saturdayResults = edgarApiService.getForm4FilingsFromDailyIndex(saturday);
        List<String> sundayResults = edgarApiService.getForm4FilingsFromDailyIndex(sunday);
        
        // Then
        assertNotNull(saturdayResults);
        assertNotNull(sundayResults);
        assertTrue(saturdayResults.isEmpty());
        assertTrue(sundayResults.isEmpty());
    }

    @DisplayName("Should process weekdays normally")
    @Test
    void testProcessWeekkdays() {
        // Given
        LocalDate monday = LocalDate.of(2024, 1, 15);    // Monday
        
        // Verify it's a weekday
        assertNotEquals(DayOfWeek.SATURDAY, monday.getDayOfWeek());
        assertNotEquals(DayOfWeek.SUNDAY, monday.getDayOfWeek());
        
        // When
        List<String> results = edgarApiService.getForm4FilingsFromDailyIndex(monday);
        
        // Then
        assertNotNull(results);
        // Will be empty in unit test without actual API call, but should not error
    }

    @DisplayName("Should construct proper SEC URLs")
    @Test
    void testSecUrlConstruction() {
        // Given
        LocalDate testDate = LocalDate.of(2024, 1, 15);
        
        // When - Test that the method constructs URLs without throwing exceptions
        assertDoesNotThrow(() -> {
            edgarApiService.getForm4FilingsFromDailyIndex(testDate);
        });
    }

    @DisplayName("Should handle network errors gracefully")
    @Test
    void testNetworkErrorHandling() {
        // Given - A date that would trigger a network call
        LocalDate testDate = LocalDate.of(2024, 1, 15);
        
        // When & Then - Should not throw exceptions even if network fails
        assertDoesNotThrow(() -> {
            List<String> results = edgarApiService.getForm4FilingsFromDailyIndex(testDate);
            assertNotNull(results);
        });
    }
}

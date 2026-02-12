package org.jds.edgar4j.integration;

import org.jds.edgar4j.service.insider.EdgarApiService;
import org.jds.edgar4j.service.insider.Form4ParserService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests with real SEC EDGAR API
 * These tests are disabled by default to avoid hitting SEC API during regular testing
 * Enable for manual testing and validation
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@ExtendWith(SpringJUnitExtension.class)
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:integrationtest",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "edgar4j.urls.submissionsCIKUrl=https://data.sec.gov/submissions/CIK",
    "edgar4j.urls.edgarDataArchivesUrl=https://www.sec.gov/Archives/edgar/data",
    "edgar4j.urls.companyTickersUrl=https://www.sec.gov/files/company_tickers.json"
})
class SecEdgarEndToEndIntegrationTest {

    @Autowired
    private EdgarApiService edgarApiService;

    @Autowired
    private Form4ParserService form4ParserService;

    @Test
    @Disabled("Enable manually for real SEC API testing - requires internet connection")
    @DisplayName("Should download company tickers from SEC")
    void testDownloadCompanyTickers() throws Exception {
        // When
        CompletableFuture<List<EdgarApiService.CompanyTicker>> future = edgarApiService.getCompanyTickers();
        List<EdgarApiService.CompanyTicker> tickers = future.get();

        // Then
        assertNotNull(tickers);
        assertFalse(tickers.isEmpty());
        
        // Verify some expected companies exist
        boolean foundMicrosoft = tickers.stream()
            .anyMatch(t -> "MSFT".equals(t.getTicker()));
        boolean foundApple = tickers.stream()
            .anyMatch(t -> "AAPL".equals(t.getTicker()));
        
        assertTrue(foundMicrosoft || foundApple, "Should find at least one major company");
        
        // Verify ticker structure
        EdgarApiService.CompanyTicker sampleTicker = tickers.get(0);
        assertNotNull(sampleTicker.getCik());
        assertNotNull(sampleTicker.getTicker());
        assertNotNull(sampleTicker.getTitle());
    }

    @Test
    @Disabled("Enable manually for real SEC API testing - requires internet connection")
    @DisplayName("Should download Microsoft submissions")
    void testDownloadMicrosoftSubmissions() throws Exception {
        // Given - Microsoft's CIK
        String microsoftCik = "789019";

        // When
        CompletableFuture<List<EdgarApiService.FilingInfo>> future = edgarApiService.getRecentForm4Filings(microsoftCik);
        List<EdgarApiService.FilingInfo> filings = future.get();

        // Then
        assertNotNull(filings);
        // Microsoft should have some Form 4 filings
        
        if (!filings.isEmpty()) {
            EdgarApiService.FilingInfo filing = filings.get(0);
            assertNotNull(filing.getAccessionNumber());
            assertNotNull(filing.getFilingDate());
            assertNotNull(filing.getPrimaryDocument());
            assertNotNull(filing.getDocumentUrl());
            assertEquals("4", filing.getFormType());
        }
    }

    @Test
    @Disabled("Enable manually for real SEC API testing - requires internet connection")
    @DisplayName("Should download and parse Form 4 document")
    void testDownloadAndParseForm4Document() throws Exception {
        // Given - Try to get recent Form 4 filings first
        String testCik = "789019"; // Microsoft
        
        CompletableFuture<List<EdgarApiService.FilingInfo>> future = edgarApiService.getRecentForm4Filings(testCik);
        List<EdgarApiService.FilingInfo> filings = future.get();
        
        // Skip test if no recent filings
        if (filings.isEmpty()) {
            System.out.println("No recent Form 4 filings found for test CIK: " + testCik);
            return;
        }

        EdgarApiService.FilingInfo filing = filings.get(0);
        
        // When - Download the Form 4 document
        CompletableFuture<String> docFuture = edgarApiService.downloadForm4Document(
            testCik, filing.getAccessionNumber(), filing.getPrimaryDocument());
        String xmlContent = docFuture.get();

        // Then
        assertNotNull(xmlContent);
        assertFalse(xmlContent.trim().isEmpty());
        assertTrue(xmlContent.contains("ownershipDocument") || xmlContent.contains("form4"));

        // Test parsing the downloaded XML
        boolean isValid = form4ParserService.validateForm4Xml(xmlContent);
        assertTrue(isValid, "Downloaded Form 4 XML should be valid");

        // Test issuer extraction
        Form4ParserService.IssuerInfo issuerInfo = form4ParserService.extractIssuerInfo(xmlContent);
        assertNotNull(issuerInfo);
        assertNotNull(issuerInfo.getCik());
        assertNotNull(issuerInfo.getName());
    }

    @Test
    @Disabled("Enable manually for real SEC API testing - requires internet connection")
    @DisplayName("Should process daily master index")
    void testDailyMasterIndexProcessing() {
        // Given - Recent business day
        LocalDate recentBusinessDay = getRecentBusinessDay();

        // When
        List<String> accessionNumbers = edgarApiService.getForm4FilingsFromDailyIndex(recentBusinessDay);

        // Then
        assertNotNull(accessionNumbers);
        // Note: May be empty if no Form 4 filings on that specific day
        
        // Verify accession number format if any found
        for (String accessionNumber : accessionNumbers) {
            assertTrue(accessionNumber.matches("\\d{10}-\\d{2}-\\d{6}"), 
                "Accession number should match SEC format: " + accessionNumber);
        }
    }

    @Test
    @Disabled("Enable manually for real SEC API testing - requires internet connection")
    @DisplayName("Should process date range for Form 4 filings")
    void testDateRangeProcessing() {
        // Given - Small date range (last week)
        LocalDate endDate = getRecentBusinessDay();
        LocalDate startDate = endDate.minusDays(7);

        // When
        List<String> accessionNumbers = edgarApiService.getForm4FilingsByDateRange(startDate, endDate);

        // Then
        assertNotNull(accessionNumbers);
        // Should find some Form 4 filings in a week period (may be 0 for quiet periods)
        
        // Verify accession number format if any found
        for (String accessionNumber : accessionNumbers) {
            assertTrue(accessionNumber.matches("\\d{10}-\\d{2}-\\d{6}"), 
                "Accession number should match SEC format: " + accessionNumber);
        }
    }

    @Test
    @Disabled("Enable manually for real SEC API testing - requires internet connection")
    @DisplayName("Should test complete end-to-end pipeline")
    void testCompleteEndToEndPipeline() throws Exception {
        // Given - Small date range
        LocalDate endDate = getRecentBusinessDay();
        LocalDate startDate = endDate.minusDays(3);

        // When - Get Form 4 filings from date range
        List<String> accessionNumbers = edgarApiService.getForm4FilingsByDateRange(startDate, endDate);
        
        if (accessionNumbers.isEmpty()) {
            System.out.println("No Form 4 filings found in date range for end-to-end test");
            return;
        }

        // Take first accession number and download document
        String firstAccessionNumber = accessionNumbers.get(0);
        String xmlContent = edgarApiService.getForm4Document(firstAccessionNumber);

        // Then - Validate and parse the document
        assertNotNull(xmlContent, "Should successfully download Form 4 document");
        assertFalse(xmlContent.trim().isEmpty(), "Downloaded content should not be empty");

        boolean isValid = form4ParserService.validateForm4Xml(xmlContent);
        assertTrue(isValid, "Downloaded Form 4 should be valid XML");

        // Test complete parsing
        try {
            var transactions = form4ParserService.parseForm4Xml(xmlContent, firstAccessionNumber);
            assertNotNull(transactions, "Should successfully parse Form 4 XML");
            
            System.out.println("Successfully processed Form 4: " + firstAccessionNumber + 
                             " with " + transactions.size() + " transactions");
            
        } catch (Exception e) {
            fail("Form 4 parsing should not throw exception: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Should handle network timeouts gracefully")
    void testNetworkTimeoutHandling() {
        // Given - Future date that won't have data
        LocalDate futureDate = LocalDate.now().plusDays(30);

        // When & Then - Should not hang or throw unhandled exceptions
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(10), () -> {
            List<String> results = edgarApiService.getForm4FilingsFromDailyIndex(futureDate);
            assertNotNull(results);
        });
    }

    @Test
    @DisplayName("Should handle rate limiting correctly")
    void testRateLimitingCompliance() {
        // Given - Multiple rapid requests
        LocalDate testDate = getRecentBusinessDay();

        // When - Make multiple requests
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < 3; i++) {
            edgarApiService.getForm4FilingsFromDailyIndex(testDate.minusDays(i));
        }
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        // Then - Should respect rate limits (SEC allows 10 requests/second)
        assertTrue(totalTime >= 200, "Should implement proper rate limiting between requests");
    }

    /**
     * Get a recent business day for testing
     */
    private LocalDate getRecentBusinessDay() {
        LocalDate date = LocalDate.now().minusDays(1);
        
        // Go back to most recent weekday
        while (date.getDayOfWeek().getValue() > 5) { // 6=Saturday, 7=Sunday
            date = date.minusDays(1);
        }
        
        return date;
    }
}

package org.jds.edgar4j.integration;

import org.jds.edgar4j.service.insider.Form4ParserService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@Disabled("Enable manually for real SEC API testing - requires internet connection")
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:integrationtest",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class SecEdgarEndToEndIntegrationTest {

    @Autowired
    private SecApiClient secApiClient;

    @Autowired
    private Form4ParserService form4ParserService;

    @Test
    @Disabled("Enable manually for real SEC API testing - requires internet connection")
    @DisplayName("Should download company tickers from SEC")
    void testDownloadCompanyTickers() {
        String tickersJson = secApiClient.fetchCompanyTickers();

        assertNotNull(tickersJson);
        assertFalse(tickersJson.isBlank());
        assertTrue(tickersJson.contains("MSFT") || tickersJson.contains("AAPL"));
    }

    @Test
    @Disabled("Enable manually for real SEC API testing - requires internet connection")
    @DisplayName("Should download Microsoft submissions")
    void testDownloadMicrosoftSubmissions() {
        String submissionsJson = secApiClient.fetchSubmissions("789019");

        assertNotNull(submissionsJson);
        assertFalse(submissionsJson.isBlank());
        assertTrue(submissionsJson.contains("\"cik\""));
    }

    @Test
    @Disabled("Enable manually for real SEC API testing - requires internet connection")
    @DisplayName("Should process daily master index")
    void testDailyMasterIndexProcessing() {
        LocalDate recentBusinessDay = getRecentBusinessDay();

        List<String> accessionNumbers = secApiClient.fetchDailyMasterIndex(recentBusinessDay)
                .map(SecForm4DocumentSupport::parseDailyMasterIndex)
                .orElseGet(List::of);

        assertNotNull(accessionNumbers);
        for (String accessionNumber : accessionNumbers) {
            assertTrue(accessionNumber.matches("\\d{10}-\\d{2}-\\d{6}"),
                "Accession number should match SEC format: " + accessionNumber);
        }
    }

    @Test
    @Disabled("Enable manually for real SEC API testing - requires internet connection")
    @DisplayName("Should download and parse a Form 4 document")
    void testDownloadAndParseForm4Document() {
        String cik = "789019";
        String submissionsJson = secApiClient.fetchSubmissions(cik);
        assertNotNull(submissionsJson);
        assertFalse(submissionsJson.isBlank());

        String accessionNumber = SecForm4DocumentSupport.parseDailyMasterIndex(
                secApiClient.fetchDailyMasterIndex(getRecentBusinessDay()).orElse(""))
                .stream()
                .findFirst()
                .orElse(null);
        if (accessionNumber == null) {
            return;
        }

        String filingIndexJson = secApiClient.fetchFiling(cik, accessionNumber, "index.json");
        String primaryDocument = SecForm4DocumentSupport.selectPrimaryXmlDocument(filingIndexJson);
        if (primaryDocument == null) {
            return;
        }

        String xmlContent = secApiClient.fetchForm4(cik, accessionNumber, primaryDocument);

        assertNotNull(xmlContent);
        assertFalse(xmlContent.trim().isEmpty());
        assertTrue(xmlContent.contains("ownershipDocument") || xmlContent.contains("form4"));
        assertTrue(form4ParserService.validateForm4Xml(xmlContent));
    }

    @Test
    @DisplayName("Should handle network timeouts gracefully")
    void testNetworkTimeoutHandling() {
        LocalDate futureDate = LocalDate.now().plusDays(30);

        assertTimeoutPreemptively(java.time.Duration.ofSeconds(10), () -> {
            List<String> results = secApiClient.fetchDailyMasterIndex(futureDate)
                    .map(SecForm4DocumentSupport::parseDailyMasterIndex)
                    .orElseGet(List::of);
            assertNotNull(results);
        });
    }

    private LocalDate getRecentBusinessDay() {
        LocalDate date = LocalDate.now().minusDays(1);
        while (date.getDayOfWeek().getValue() > 5) {
            date = date.minusDays(1);
        }
        return date;
    }
}

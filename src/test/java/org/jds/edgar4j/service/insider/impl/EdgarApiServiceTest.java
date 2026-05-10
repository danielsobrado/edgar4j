package org.jds.edgar4j.service.insider.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

import java.time.LocalDate;
import java.util.List;

import org.jds.edgar4j.properties.Edgar4JProperties;
import org.jds.edgar4j.service.SettingsService;
import org.jds.edgar4j.service.insider.InsiderTransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EdgarApiServiceTest {

    @Mock
    private SettingsService settingsService;

    @Mock
    private InsiderTransactionService insiderTransactionService;

    private EdgarApiServiceImpl edgarApiService;

    @BeforeEach
    void setUp() {
        lenient().when(settingsService.getUserAgent()).thenReturn("My Company sec-ops@mycompany.com");

        Edgar4JProperties properties = new Edgar4JProperties();
        properties.getUrls().setSubmissionsCIKUrl("https://data.sec.gov/submissions/CIK");
        properties.getUrls().setEdgarDataArchivesUrl("https://www.sec.gov/Archives/edgar/data");
        properties.getUrls().setCompanyTickersUrl("https://www.sec.gov/files/company_tickers.json");
        edgarApiService = new EdgarApiServiceImpl(settingsService, properties, insiderTransactionService);
    }

    @Test
    @DisplayName("extractCikFromAccessionNumber should return the leading 10 digits")
    void extractCikFromAccessionNumberShouldReturnLeadingDigits() {
        assertEquals("0001234567", EdgarForm4ParsingUtils.extractCikFromAccessionNumber("0001234567-24-000001"));
        assertEquals("0000789019", EdgarForm4ParsingUtils.extractCikFromAccessionNumber("0000789019-23-000123"));
        assertNull(EdgarForm4ParsingUtils.extractCikFromAccessionNumber("invalid-format"));
    }

    @Test
    @DisplayName("extractAccessionFromFilename should return the accession segment")
    void extractAccessionFromFilenameShouldReturnAccessionSegment() {
        assertEquals(
                "0001234567-24-000001",
                EdgarForm4ParsingUtils.extractAccessionFromFilename("edgar/data/789019/0001234567-24-000001/doc4.xml"));
        assertEquals(
                "0000987654-23-000456",
                EdgarForm4ParsingUtils.extractAccessionFromFilename("edgar/data/123456/0000987654-23-000456/form4.xml"));
        assertNull(EdgarForm4ParsingUtils.extractAccessionFromFilename("invalid/path"));
    }

    @Test
    @DisplayName("parseDailyMasterIndex should keep only Form 4 accessions")
    void parseDailyMasterIndexShouldKeepOnlyForm4Accessions() {
        String sampleMasterIndex = """
            Description:                 Master Index of EDGAR Dissemination System
            Creation Date:               January 15, 2024

            CIK|Company Name|Form Type|Date Filed|Filename
            789019|MICROSOFT CORP|4|2024-01-15|edgar/data/789019/0001234567-24-000001/doc4.xml
            123456|TEST COMPANY INC|10-K|2024-01-15|edgar/data/123456/0000567890-24-000002/test10k.htm
            987654|EXAMPLE CORP|4|2024-01-15|edgar/data/987654/0000111222-24-000003/form4.xml
            """;

        List<String> accessionNumbers = EdgarForm4ParsingUtils.parseDailyMasterIndex(sampleMasterIndex);

        assertEquals(List.of("0001234567-24-000001", "0000111222-24-000003"), accessionNumbers);
    }

    @Test
    @DisplayName("selectPrimaryXmlDocument should prefer Form 4 ownership XML")
    void selectPrimaryXmlDocumentShouldPreferForm4OwnershipXml() {
        String filingIndexJson = """
            {
              "directory": {
                "item": [
                  { "name": "primary_doc.html" },
                  { "name": "FilingSummary.xml" },
                  { "name": "doc4.xml" },
                  { "name": "schema.xsd" }
                ]
              }
            }
            """;

        assertEquals("doc4.xml", EdgarApiServiceImpl.selectPrimaryXmlDocument(filingIndexJson));
    }

    @Test
    @DisplayName("getForm4FilingsByDateRange should return empty when end date precedes start date")
    void getForm4FilingsByDateRangeShouldReturnEmptyForInvalidRange() {
        List<String> accessionNumbers = edgarApiService.getForm4FilingsByDateRange(
                LocalDate.of(2025, 1, 15),
                LocalDate.of(2025, 1, 10));

        assertTrue(accessionNumbers.isEmpty());
    }

    @Test
    @DisplayName("getForm4FilingsFromDailyIndex should skip weekends without making remote assertions")
    void getForm4FilingsFromDailyIndexShouldSkipWeekends() {
        assertTrue(edgarApiService.getForm4FilingsFromDailyIndex(LocalDate.of(2025, 1, 11)).isEmpty());
        assertTrue(edgarApiService.getForm4FilingsFromDailyIndex(LocalDate.of(2025, 1, 12)).isEmpty());
    }
}

package org.jds.edgar4j.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SecForm4DocumentSupportTest {

    @Test
    @DisplayName("extractCikFromAccessionNumber should return the leading 10 digits")
    void extractCikFromAccessionNumberShouldReturnLeadingDigits() {
        assertEquals("0001234567", SecForm4DocumentSupport.extractCikFromAccessionNumber("0001234567-24-000001"));
        assertEquals("0000789019", SecForm4DocumentSupport.extractCikFromAccessionNumber("0000789019-23-000123"));
        assertNull(SecForm4DocumentSupport.extractCikFromAccessionNumber("invalid-format"));
    }

    @Test
    @DisplayName("extractAccessionFromFilename should return the accession segment")
    void extractAccessionFromFilenameShouldReturnAccessionSegment() {
        assertEquals(
                "0001234567-24-000001",
                SecForm4DocumentSupport.extractAccessionFromFilename("edgar/data/789019/0001234567-24-000001/doc4.xml"));
        assertEquals(
                "0000987654-23-000456",
                SecForm4DocumentSupport.extractAccessionFromFilename("edgar/data/123456/0000987654-23-000456/form4.xml"));
        assertNull(SecForm4DocumentSupport.extractAccessionFromFilename("invalid/path"));
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

        List<String> accessionNumbers = SecForm4DocumentSupport.parseDailyMasterIndex(sampleMasterIndex);

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

        assertEquals("doc4.xml", SecForm4DocumentSupport.selectPrimaryXmlDocument(filingIndexJson));
    }
}

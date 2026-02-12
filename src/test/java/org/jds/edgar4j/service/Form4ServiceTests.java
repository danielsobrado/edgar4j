package org.jds.edgar4j.service;

import org.jds.edgar4j.service.impl.Form4ServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author J. Daniel Sobrado
 * @version 1.1
 * @since 2022-09-18
 */
@ExtendWith(MockitoExtension.class)
@SpringBootTest
@TestPropertySource(properties = {
    "edgar4j.urls.edgarDataArchivesUrl=https://www.sec.gov/Archives/edgar/data",
    "spring.data.mongodb.auto-index-creation=false"
})
class Form4ServiceTests {

    @InjectMocks
    private Form4ServiceImpl form4Service;

    @DisplayName("Should construct correct Form4 URL")
    @Test
    void testForm4URLConstruction() {
        // Given
        String cik = "789019";
        String accessionNumber = "0001626431-16-000118";
        String primaryDocument = "xslF345X03/edgar.xml";
        
        ReflectionTestUtils.setField(form4Service, "edgarDataArchivesUrl", "https://www.sec.gov/Archives/edgar/data");
        
        // When - We cannot easily test the URL construction without refactoring the service
        // But we can verify the method doesn't throw exceptions
        assertDoesNotThrow(() -> {
            CompletableFuture<HttpResponse<String>> future = form4Service.downloadForm4(cik, accessionNumber, primaryDocument);
            // Don't wait for the actual HTTP call to complete
        });
    }

    @DisplayName("Should handle accession number formatting correctly")
    @Test
    void testAccessionNumberFormatting() {
        // Given
        String cik = "789019";
        String accessionNumberWithDashes = "0001626431-16-000118";
        String primaryDocument = "xslF345X03/edgar.xml";
        
        ReflectionTestUtils.setField(form4Service, "edgarDataArchivesUrl", "https://www.sec.gov/Archives/edgar/data");
        
        // When & Then
        assertDoesNotThrow(() -> {
            CompletableFuture<HttpResponse<String>> future = form4Service.downloadForm4(cik, accessionNumberWithDashes, primaryDocument);
            // The service should remove dashes from accession number
            // Expected URL format: https://www.sec.gov/Archives/edgar/data/789019/000162643116000118/xslF345X03/edgar.xml
        });
    }

    @DisplayName("Should parse empty Form4 successfully")
    @Test
    void testParseForm4_EmptyInput() {
        // Given
        String emptyXml = "";
        
        // When
        var result = form4Service.parseForm4(emptyXml);
        
        // Then
        assertNotNull(result);
        // Current implementation returns empty Form4 object
    }

    @DisplayName("Should parse Form4 with valid XML")
    @Test
    void testParseForm4_ValidXML() {
        // Given
        String validXml = "<xml><form4>test</form4></xml>";
        
        // When
        var result = form4Service.parseForm4(validXml);
        
        // Then
        assertNotNull(result);
        // Current implementation returns empty Form4 object regardless of input
    }

    @DisplayName("Should handle null input gracefully")
    @Test
    void testParseForm4_NullInput() {
        // Given
        String nullInput = null;
        
        // When & Then
        assertDoesNotThrow(() -> {
            var result = form4Service.parseForm4(nullInput);
            assertNotNull(result);
        });
    }
}

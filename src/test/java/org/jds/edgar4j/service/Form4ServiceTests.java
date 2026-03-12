package org.jds.edgar4j.service;

import org.jds.edgar4j.integration.Form4Parser;
import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.model.Form4;
import org.jds.edgar4j.repository.Form4Repository;
import org.jds.edgar4j.repository.TickerRepository;
import org.jds.edgar4j.service.impl.Form4ServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * @author J. Daniel Sobrado
 * @version 1.1
 * @since 2022-09-18
 */
@ExtendWith(MockitoExtension.class)
class Form4ServiceTests {

    @Mock
    private Form4Repository form4Repository;

    @Mock
    private Form4Parser form4Parser;

    @Mock
    private SettingsService settingsService;

    @Mock
    private SecApiClient secApiClient;

    @Mock
    private CompanyService companyService;

    @Mock
    private TickerRepository tickerRepository;

    @InjectMocks
    private Form4ServiceImpl form4Service;

    @DisplayName("Should return null for empty Form4 payloads")
    @Test
    void testParseForm4_EmptyInput() {
        // Given
        String emptyXml = "";
        
        // When
        Form4 result = form4Service.parseForm4(emptyXml, "test-accession");

        // Then
        assertNull(result);
        verifyNoInteractions(form4Parser);
    }

    @DisplayName("Should parse valid Form4 XML and stamp timestamps")
    @Test
    void testParseForm4_ValidXml() {
        // Given
        String validXml = "<xml><form4>test</form4></xml>";
        Form4 parsedForm4 = new Form4();

        when(form4Parser.parse(validXml, "test-accession")).thenReturn(parsedForm4);
        
        // When
        Form4 result = form4Service.parseForm4(validXml, "test-accession");

        // Then
        assertSame(parsedForm4, result);
        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getUpdatedAt());
    }

    @DisplayName("Should return null when parser throws")
    @Test
    void testParseForm4_ParserFailure() {
        // Given
        String validXml = "<xml><form4>test</form4></xml>";
        when(form4Parser.parse(validXml, "test-accession")).thenThrow(new RuntimeException("parse failure"));

        // When
        Form4 result = form4Service.parseForm4(validXml, "test-accession");

        // Then
        assertNull(result);
    }

    @DisplayName("Should delegate parsing after a successful download")
    @Test
    void testDownloadAndParseForm4_SuccessfulResponse() throws Exception {
        // Given
        HttpResponse<String> response = mock(HttpResponse.class);
        Form4 parsedForm4 = new Form4();
        Form4ServiceImpl spyService = spy(form4Service);

        doReturn(CompletableFuture.completedFuture(response))
                .when(spyService)
                .downloadForm4("789019", "0001626431-16-000118", "edgar.xml");
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("<xml><form4>test</form4></xml>");
        doReturn(parsedForm4).when(spyService).parseForm4("<xml><form4>test</form4></xml>", "0001626431-16-000118");

        // When
        Form4 result = spyService.downloadAndParseForm4("789019", "0001626431-16-000118", "edgar.xml").get();

        // Then
        assertSame(parsedForm4, result);
        verify(spyService).parseForm4("<xml><form4>test</form4></xml>", "0001626431-16-000118");
    }

    @DisplayName("Should return null when download returns a non-200 status")
    @Test
    void testDownloadAndParseForm4_Non200Response() throws Exception {
        // Given
        HttpResponse<String> response = mock(HttpResponse.class);
        Form4ServiceImpl spyService = spy(form4Service);

        doReturn(CompletableFuture.completedFuture(response))
                .when(spyService)
                .downloadForm4(anyString(), anyString(), anyString());
        when(response.statusCode()).thenReturn(404);

        // When
        Form4 result = spyService.downloadAndParseForm4("789019", "0001626431-16-000118", "edgar.xml").get();

        // Then
        assertNull(result);
        verify(spyService, never()).parseForm4(anyString(), anyString());
    }
}

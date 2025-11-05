package org.jds.edgar4j.service;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;

import org.jds.edgar4j.model.Company;
import org.jds.edgar4j.service.impl.IndustryLookupServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Test cases for IndustryLookupService
 * Tests company data fetching and caching using mocked HTTP responses
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-05
 */
@ExtendWith(MockitoExtension.class)
public class IndustryLookupServiceTests {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private IndustryLookupService industryLookupService;

    private String appleJson;
    private String microsoftJson;

    @BeforeEach
    public void setUp() throws IOException {
        // Load test data files
        appleJson = loadTestResource("src/test/java/resources/data/CIK0000320193.json");
        microsoftJson = loadTestResource("src/test/java/resources/data/CIK0000789019.json");

        // Create service with mocked HTTP client
        industryLookupService = new IndustryLookupServiceImpl(httpClient);
    }

    private String loadTestResource(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)));
    }

    @DisplayName("Test get company by CIK - Apple Inc")
    @Test
    public void testGetCompanyByCik_Apple() throws Exception {
        // Mock HTTP response
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
            .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(appleJson);

        // Execute
        Optional<Company> company = industryLookupService.getCompanyByCik("0000320193");

        // Verify
        assertTrue(company.isPresent());
        assertEquals("320193", company.get().getCik());
        assertEquals("Apple Inc.", company.get().getName());
        assertEquals("3571", company.get().getSic());
        assertEquals("Electronic Computers", company.get().getIndustry());
        assertEquals("AAPL", company.get().getTicker());
        assertEquals("CA", company.get().getStateOfIncorporation());
    }

    @DisplayName("Test get company by CIK - Microsoft Corp")
    @Test
    public void testGetCompanyByCik_Microsoft() throws Exception {
        // Mock HTTP response
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
            .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(microsoftJson);

        // Execute
        Optional<Company> company = industryLookupService.getCompanyByCik("0000789019");

        // Verify
        assertTrue(company.isPresent());
        assertEquals("789019", company.get().getCik());
        assertTrue(company.get().getName().contains("MICROSOFT"));
        assertEquals("7372", company.get().getSic());
        assertEquals("Services-Prepackaged Software", company.get().getIndustry());
        assertEquals("MSFT", company.get().getTicker());
    }

    @DisplayName("Test get company by CIK - with leading zeros stripped")
    @Test
    public void testGetCompanyByCik_LeadingZeros() throws Exception {
        // Mock HTTP response
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
            .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(appleJson);

        // Execute (without leading zeros)
        Optional<Company> company = industryLookupService.getCompanyByCik("320193");

        // Verify
        assertTrue(company.isPresent());
        assertEquals("320193", company.get().getCik());
    }

    @DisplayName("Test get company by CIK - invalid CIK (404)")
    @Test
    public void testGetCompanyByCik_InvalidCik() throws Exception {
        // Mock HTTP 404 response
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
            .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(404);

        // Execute
        Optional<Company> company = industryLookupService.getCompanyByCik("9999999999");

        // Verify
        assertFalse(company.isPresent());
    }

    @DisplayName("Test get company by CIK - null CIK")
    @Test
    public void testGetCompanyByCik_NullCik() {
        Optional<Company> company = industryLookupService.getCompanyByCik(null);

        assertFalse(company.isPresent());
    }

    @DisplayName("Test get company by CIK - empty CIK")
    @Test
    public void testGetCompanyByCik_EmptyCik() {
        Optional<Company> company = industryLookupService.getCompanyByCik("");

        assertFalse(company.isPresent());
    }

    @DisplayName("Test get company by CIK - network error")
    @Test
    public void testGetCompanyByCik_NetworkError() throws Exception {
        // Mock HTTP client to throw IOException
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
            .thenThrow(new IOException("Network error"));

        // Execute
        Optional<Company> company = industryLookupService.getCompanyByCik("0000320193");

        // Verify - should return empty on error
        assertFalse(company.isPresent());
    }

    @DisplayName("Test get industry by CIK")
    @Test
    public void testGetIndustryByCik() throws Exception {
        // Mock HTTP response
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
            .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(appleJson);

        // Execute
        String industry = industryLookupService.getIndustryByCik("0000320193");

        // Verify
        assertNotNull(industry);
        assertEquals("Electronic Computers", industry);
    }

    @DisplayName("Test get industry by CIK - invalid")
    @Test
    public void testGetIndustryByCik_Invalid() throws Exception {
        // Mock HTTP 404 response
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
            .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(404);

        // Execute
        String industry = industryLookupService.getIndustryByCik("9999999999");

        // Verify
        assertNull(industry);
    }

    @DisplayName("Test get SIC code by CIK")
    @Test
    public void testGetSicCodeByCik() throws Exception {
        // Mock HTTP response
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
            .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(appleJson);

        // Execute
        String sic = industryLookupService.getSicCodeByCik("0000320193");

        // Verify
        assertNotNull(sic);
        assertEquals("3571", sic);
    }

    @DisplayName("Test get SIC code by CIK - invalid")
    @Test
    public void testGetSicCodeByCik_Invalid() throws Exception {
        // Mock HTTP 404 response
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
            .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(404);

        // Execute
        String sic = industryLookupService.getSicCodeByCik("9999999999");

        // Verify
        assertNull(sic);
    }

    @DisplayName("Test caching - same CIK requested twice")
    @Test
    public void testCaching() throws Exception {
        // Mock HTTP response for first call only
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
            .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(appleJson);

        // First call - should fetch from "API"
        Optional<Company> company1 = industryLookupService.getCompanyByCik("0000320193");
        assertTrue(company1.isPresent());

        // Second call - should use cache (HTTP client should not be called again)
        Optional<Company> company2 = industryLookupService.getCompanyByCik("0000320193");
        assertTrue(company2.isPresent());

        // Verify both return same data
        assertEquals(company1.get().getCik(), company2.get().getCik());
        assertEquals(company1.get().getName(), company2.get().getName());
        assertEquals(company1.get().getIndustry(), company2.get().getIndustry());
    }

    @DisplayName("Test clear cache")
    @Test
    public void testClearCache() throws Exception {
        // Mock HTTP response
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
            .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(appleJson);

        // Fetch company (will be cached)
        industryLookupService.getCompanyByCik("0000320193");

        // Verify cache has data
        if (industryLookupService instanceof IndustryLookupServiceImpl) {
            IndustryLookupServiceImpl impl = (IndustryLookupServiceImpl) industryLookupService;
            assertTrue(impl.getCacheSize() > 0);

            // Clear cache
            industryLookupService.clearCache();

            // Verify cache is empty
            assertEquals(0, impl.getCacheSize());
        }
    }

    @DisplayName("Test get cache size")
    @Test
    public void testGetCacheSize() throws Exception {
        if (industryLookupService instanceof IndustryLookupServiceImpl) {
            IndustryLookupServiceImpl impl = (IndustryLookupServiceImpl) industryLookupService;

            // Initially empty
            assertEquals(0, impl.getCacheSize());

            // Mock HTTP response for Apple
            when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(appleJson);

            // Fetch one company
            industryLookupService.getCompanyByCik("0000320193");
            assertEquals(1, impl.getCacheSize());

            // Mock HTTP response for Microsoft
            when(httpResponse.body()).thenReturn(microsoftJson);

            // Fetch another company
            industryLookupService.getCompanyByCik("0000789019");
            assertEquals(2, impl.getCacheSize());

            // Fetch same company again (should not increase cache size)
            industryLookupService.getCompanyByCik("0000320193");
            assertEquals(2, impl.getCacheSize());
        }
    }

    @DisplayName("Test CIK format handling - with spaces")
    @Test
    public void testCikFormatHandling_WithSpaces() throws Exception {
        // Mock HTTP response
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
            .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(appleJson);

        // Execute with spaces
        Optional<Company> company = industryLookupService.getCompanyByCik("  320193  ");

        // Verify
        assertTrue(company.isPresent());
        assertEquals("320193", company.get().getCik());
    }

    @DisplayName("Test entity type populated")
    @Test
    public void testEntityTypePopulated() throws Exception {
        // Mock HTTP response
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
            .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(appleJson);

        // Execute
        Optional<Company> company = industryLookupService.getCompanyByCik("0000320193");

        // Verify
        assertTrue(company.isPresent());
        assertNotNull(company.get().getEntityType());
        assertEquals("operating", company.get().getEntityType());
    }

    @DisplayName("Test state of incorporation populated")
    @Test
    public void testStateOfIncorporationPopulated() throws Exception {
        // Mock HTTP response
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
            .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(appleJson);

        // Execute
        Optional<Company> company = industryLookupService.getCompanyByCik("0000320193");

        // Verify
        assertTrue(company.isPresent());
        assertNotNull(company.get().getStateOfIncorporation());
        assertEquals("CA", company.get().getStateOfIncorporation());
    }

    @DisplayName("Test get company with all fields")
    @Test
    public void testGetCompanyWithAllFields() throws Exception {
        // Mock HTTP response
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
            .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(appleJson);

        // Execute
        Optional<Company> company = industryLookupService.getCompanyByCik("0000320193");

        // Verify
        assertTrue(company.isPresent());

        Company c = company.get();
        assertNotNull(c.getCik());
        assertNotNull(c.getName());
        assertNotNull(c.getSic());
        assertNotNull(c.getIndustry());
        assertNotNull(c.getTicker());
        assertNotNull(c.getEntityType());
        assertNotNull(c.getStateOfIncorporation());

        // Verify values
        assertEquals("320193", c.getCik());
        assertEquals("Apple Inc.", c.getName());
        assertEquals("AAPL", c.getTicker());
        assertEquals("Electronic Computers", c.getIndustry());
    }

    @DisplayName("Test malformed JSON response")
    @Test
    public void testMalformedJsonResponse() throws Exception {
        // Mock HTTP response with malformed JSON
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
            .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{invalid json}");

        // Execute
        Optional<Company> company = industryLookupService.getCompanyByCik("0000320193");

        // Verify - should return empty on parse error
        assertFalse(company.isPresent());
    }

    @DisplayName("Test HTTP 500 error")
    @Test
    public void testHttp500Error() throws Exception {
        // Mock HTTP 500 response
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
            .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(500);

        // Execute
        Optional<Company> company = industryLookupService.getCompanyByCik("0000320193");

        // Verify
        assertFalse(company.isPresent());
    }
}

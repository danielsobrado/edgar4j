package org.jds.edgar4j.service;

import java.util.Optional;

import org.jds.edgar4j.model.Company;
import org.jds.edgar4j.service.impl.IndustryLookupServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for IndustryLookupService
 * Tests company data fetching and caching
 *
 * NOTE: These are integration tests that make real API calls to SEC EDGAR
 * They may be slow and require internet connectivity
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-05
 */
@SpringBootTest
@TestPropertySource(properties = {
    "edgar4j.urls.submissionsCIKUrl=https://data.sec.gov/submissions/CIK"
})
public class IndustryLookupServiceTests {

    @Autowired
    private IndustryLookupService industryLookupService;

    @BeforeEach
    public void setUp() {
        // Clear cache before each test
        industryLookupService.clearCache();
    }

    @DisplayName("Test get company by CIK - Apple Inc")
    @Test
    public void testGetCompanyByCik_Apple() {
        // Apple Inc. CIK: 0000320193
        Optional<Company> company = industryLookupService.getCompanyByCik("0000320193");

        assertTrue(company.isPresent());
        assertEquals("320193", company.get().getCik());
        assertEquals("Apple Inc.", company.get().getName());
        assertNotNull(company.get().getSic());
        assertNotNull(company.get().getIndustry());
    }

    @DisplayName("Test get company by CIK - Microsoft Corp")
    @Test
    public void testGetCompanyByCik_Microsoft() {
        // Microsoft Corp CIK: 0000789019
        Optional<Company> company = industryLookupService.getCompanyByCik("0000789019");

        assertTrue(company.isPresent());
        assertEquals("789019", company.get().getCik());
        assertTrue(company.get().getName().contains("MICROSOFT"));
        assertNotNull(company.get().getSic());
        assertNotNull(company.get().getIndustry());
    }

    @DisplayName("Test get company by CIK - with leading zeros stripped")
    @Test
    public void testGetCompanyByCik_LeadingZeros() {
        // Test that leading zeros are properly handled
        Optional<Company> company = industryLookupService.getCompanyByCik("320193");

        assertTrue(company.isPresent());
        assertEquals("320193", company.get().getCik());
    }

    @DisplayName("Test get company by CIK - invalid CIK")
    @Test
    public void testGetCompanyByCik_InvalidCik() {
        Optional<Company> company = industryLookupService.getCompanyByCik("9999999999");

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

    @DisplayName("Test get industry by CIK")
    @Test
    public void testGetIndustryByCik() {
        String industry = industryLookupService.getIndustryByCik("0000320193");

        assertNotNull(industry);
        assertFalse(industry.isEmpty());
    }

    @DisplayName("Test get industry by CIK - invalid")
    @Test
    public void testGetIndustryByCik_Invalid() {
        String industry = industryLookupService.getIndustryByCik("9999999999");

        assertNull(industry);
    }

    @DisplayName("Test get SIC code by CIK")
    @Test
    public void testGetSicCodeByCik() {
        String sic = industryLookupService.getSicCodeByCik("0000320193");

        assertNotNull(sic);
        assertFalse(sic.isEmpty());
        assertEquals("3571", sic);  // Apple's SIC code
    }

    @DisplayName("Test get SIC code by CIK - invalid")
    @Test
    public void testGetSicCodeByCik_Invalid() {
        String sic = industryLookupService.getSicCodeByCik("9999999999");

        assertNull(sic);
    }

    @DisplayName("Test caching - same CIK requested twice")
    @Test
    public void testCaching() {
        // First call - should fetch from API
        long startTime = System.currentTimeMillis();
        Optional<Company> company1 = industryLookupService.getCompanyByCik("0000320193");
        long firstCallDuration = System.currentTimeMillis() - startTime;

        assertTrue(company1.isPresent());

        // Second call - should use cache (much faster)
        startTime = System.currentTimeMillis();
        Optional<Company> company2 = industryLookupService.getCompanyByCik("0000320193");
        long secondCallDuration = System.currentTimeMillis() - startTime;

        assertTrue(company2.isPresent());
        assertEquals(company1.get().getCik(), company2.get().getCik());
        assertEquals(company1.get().getName(), company2.get().getName());

        // Second call should be significantly faster (cached)
        assertTrue(secondCallDuration < firstCallDuration / 2,
            String.format("Second call (%dms) should be faster than first call (%dms)",
                secondCallDuration, firstCallDuration));
    }

    @DisplayName("Test clear cache")
    @Test
    public void testClearCache() {
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
    public void testGetCacheSize() {
        if (industryLookupService instanceof IndustryLookupServiceImpl) {
            IndustryLookupServiceImpl impl = (IndustryLookupServiceImpl) industryLookupService;

            // Initially empty
            assertEquals(0, impl.getCacheSize());

            // Fetch one company
            industryLookupService.getCompanyByCik("0000320193");
            assertEquals(1, impl.getCacheSize());

            // Fetch another company
            industryLookupService.getCompanyByCik("0000789019");
            assertEquals(2, impl.getCacheSize());

            // Fetch same company again (should not increase cache size)
            industryLookupService.getCompanyByCik("0000320193");
            assertEquals(2, impl.getCacheSize());
        }
    }

    @DisplayName("Test company with ticker information")
    @Test
    public void testCompanyWithTicker() {
        Optional<Company> company = industryLookupService.getCompanyByCik("0000320193");

        assertTrue(company.isPresent());
        assertNotNull(company.get().getTicker());
        assertEquals("AAPL", company.get().getTicker());
    }

    @DisplayName("Test CIK format handling - all zeros")
    @Test
    public void testCikFormatHandling_AllZeros() {
        Optional<Company> company = industryLookupService.getCompanyByCik("0000000000");

        // Should handle edge case gracefully (may or may not find company "0")
        assertNotNull(company);
    }

    @DisplayName("Test CIK format handling - with spaces")
    @Test
    public void testCikFormatHandling_WithSpaces() {
        Optional<Company> company = industryLookupService.getCompanyByCik("  320193  ");

        assertTrue(company.isPresent());
        assertEquals("320193", company.get().getCik());
    }

    @DisplayName("Test entity type populated")
    @Test
    public void testEntityTypePopulated() {
        Optional<Company> company = industryLookupService.getCompanyByCik("0000320193");

        assertTrue(company.isPresent());
        assertNotNull(company.get().getEntityType());
    }

    @DisplayName("Test state of incorporation populated")
    @Test
    public void testStateOfIncorporationPopulated() {
        Optional<Company> company = industryLookupService.getCompanyByCik("0000320193");

        assertTrue(company.isPresent());
        assertNotNull(company.get().getStateOfIncorporation());
    }

    @DisplayName("Test get company with all fields")
    @Test
    public void testGetCompanyWithAllFields() {
        Optional<Company> company = industryLookupService.getCompanyByCik("0000320193");

        assertTrue(company.isPresent());

        Company c = company.get();
        assertNotNull(c.getCik());
        assertNotNull(c.getName());
        assertNotNull(c.getSic());
        assertNotNull(c.getIndustry());
        assertNotNull(c.getTicker());
        assertNotNull(c.getEntityType());
        assertNotNull(c.getStateOfIncorporation());

        // Verify reasonable values
        assertEquals("320193", c.getCik());
        assertEquals("Apple Inc.", c.getName());
        assertEquals("AAPL", c.getTicker());
    }
}

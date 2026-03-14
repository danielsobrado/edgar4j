package org.jds.edgar4j.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SecApiConfigTest {

    @Test
    @DisplayName("archive filing URLs should use the SEC archive CIK path without leading zeros")
    void archiveFilingUrlsShouldUseUnpaddedCikPath() {
        SecApiConfig config = new SecApiConfig();
        ReflectionTestUtils.setField(config, "edgarDataArchivesUrl", "https://www.sec.gov/Archives/edgar/data");

        assertEquals(
                "https://www.sec.gov/Archives/edgar/data/320193/000032019324000001/doc.xml",
                config.getFilingUrl("0000320193", "0000320193-24-000001", "doc.xml"));
        assertEquals(
                "https://www.sec.gov/Archives/edgar/data/320193/000032019324000001/doc4.xml",
                config.getForm4Url("0000320193", "0000320193-24-000001", "doc4.xml"));
    }

    @Test
    @DisplayName("EFTS URLs should include the realtime search filters without raw spaces")
    void eftsUrlsShouldIncludeRealtimeSearchFilters() {
        SecApiConfig config = new SecApiConfig();
        ReflectionTestUtils.setField(config, "eftsSearchUrl", "https://efts.sec.gov/LATEST/search-index");

        String url = config.getEftsSearchUrl("4,SC 13D", "2026-03-11", "2026-03-12", 100, 50);
        URI uri = URI.create(url);

        assertTrue(url.startsWith("https://efts.sec.gov/LATEST/search-index?"));
        assertFalse(url.contains(" "));
        assertTrue(uri.getRawQuery().contains("forms=4,SC%2013D")
                || uri.getRawQuery().contains("forms=4,SC+13D"));
        assertTrue(uri.getRawQuery().contains("startdt=2026-03-11"));
        assertTrue(uri.getRawQuery().contains("enddt=2026-03-12"));
        assertTrue(uri.getRawQuery().contains("from=100"));
        assertTrue(uri.getRawQuery().contains("size=50"));
    }

    @Test
    @DisplayName("companyfacts URLs should use the zero-padded CIK on data.sec.gov")
    void companyFactsUrlShouldUsePaddedCik() {
        SecApiConfig config = new SecApiConfig();
        ReflectionTestUtils.setField(config, "baseDataSecUrl", "https://data.sec.gov");

        assertEquals(
                "https://data.sec.gov/api/xbrl/companyfacts/CIK0000320193.json",
                config.getCompanyFactsUrl("320193"));
    }
}

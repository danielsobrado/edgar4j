package org.jds.edgar4j.service;

import org.jds.edgar4j.service.impl.DownloadTickersServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author J. Daniel Sobrado
 * @version 1.1
 * @since 2022-09-18
 */
@ExtendWith(MockitoExtension.class)
@SpringBootTest
@TestPropertySource(properties = {
    "edgar4j.urls.companyTickersUrl=https://www.sec.gov/files/company_tickers.json",
    "edgar4j.urls.companyTickersExchangesUrl=https://www.sec.gov/files/company_tickers_exchange.json",
    "edgar4j.urls.companyTickersMFsUrl=https://www.sec.gov/files/company_tickers_mf.json",
    "spring.data.mongodb.auto-index-creation=false"
})
class DownloadTickersServiceTests {

    @InjectMocks
    private DownloadTickersServiceImpl downloadTickersService;

    @DisplayName("Should execute downloadTickers without throwing exceptions")
    @Test
    void testDownloadTickers_ExecutesSuccessfully() {
        // Given
        ReflectionTestUtils.setField(downloadTickersService, "companyTickersUrl", "https://www.sec.gov/files/company_tickers.json");
        
        // When & Then
        assertDoesNotThrow(() -> {
            downloadTickersService.downloadTickers();
        });
    }

    @DisplayName("Should execute downloadTickersExchanges without throwing exceptions")
    @Test
    void testDownloadTickersExchanges_ExecutesSuccessfully() {
        // Given
        ReflectionTestUtils.setField(downloadTickersService, "companyTickersExchangesUrl", "https://www.sec.gov/files/company_tickers_exchange.json");
        
        // When & Then
        assertDoesNotThrow(() -> {
            downloadTickersService.downloadTickersExchanges();
        });
    }

    @DisplayName("Should execute downloadTickersMFs without throwing exceptions")
    @Test
    void testDownloadTickersMFs_ExecutesSuccessfully() {
        // Given
        ReflectionTestUtils.setField(downloadTickersService, "companyTickersMFsUrl", "https://www.sec.gov/files/company_tickers_mf.json");
        
        // When & Then
        assertDoesNotThrow(() -> {
            downloadTickersService.downloadTickersMFs();
        });
    }

    @DisplayName("Should handle null URL configuration gracefully")
    @Test
    void testDownloadTickers_NullURL_HandlesGracefully() {
        // Given
        ReflectionTestUtils.setField(downloadTickersService, "companyTickersUrl", null);
        
        // When & Then
        assertThrows(Exception.class, () -> {
            downloadTickersService.downloadTickers();
        });
    }

    @DisplayName("Should handle empty URL configuration gracefully")
    @Test
    void testDownloadTickersExchanges_EmptyURL_HandlesGracefully() {
        // Given
        ReflectionTestUtils.setField(downloadTickersService, "companyTickersExchangesUrl", "");
        
        // When & Then
        assertThrows(Exception.class, () -> {
            downloadTickersService.downloadTickersExchanges();
        });
    }

    @DisplayName("Should handle malformed URL configuration gracefully")
    @Test
    void testDownloadTickersMFs_MalformedURL_HandlesGracefully() {
        // Given
        ReflectionTestUtils.setField(downloadTickersService, "companyTickersMFsUrl", "not-a-valid-url");
        
        // When & Then
        assertThrows(Exception.class, () -> {
            downloadTickersService.downloadTickersMFs();
        });
    }
}

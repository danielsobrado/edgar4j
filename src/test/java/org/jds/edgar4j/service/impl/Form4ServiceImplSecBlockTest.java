package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jds.edgar4j.integration.Form4Parser;
import org.jds.edgar4j.integration.SecAccessDiagnostics;
import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.repository.FillingRepository;
import org.jds.edgar4j.repository.Form4Repository;
import org.jds.edgar4j.repository.TickerRepository;
import org.jds.edgar4j.service.CompanyService;
import org.jds.edgar4j.service.SettingsService;
import org.jds.edgar4j.storage.DownloadedResourceStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Form4ServiceImplSecBlockTest {

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

    @Mock
    private FillingRepository fillingRepository;

    @Mock
    private DownloadedResourceStore downloadedResourceStore;

    @InjectMocks
    private Form4ServiceImpl form4Service;

    @Test
    @DisplayName("fetchRecentFromSecApi should stop after the SEC automation block instead of continuing silently")
    void fetchRecentFromSecApiShouldStopAfterSecAutomationBlock() {
        when(secApiClient.fetchCompanyTickers()).thenReturn("""
                {
                  "0": {"ticker":"AAPL","cik":"320193"},
                  "1": {"ticker":"MSFT","cik":"789019"}
                }
                """);
        when(secApiClient.fetchSubmissions("0000320193"))
                .thenThrow(new RuntimeException(SecAccessDiagnostics.buildUndeclaredAutomationBlockMessage(
                        "https://www.sec.gov/submissions/CIK0000320193.json",
                        "ref-123")));

        assertTrue(form4Service.fetchRecentFromSecApi(5).isEmpty());

        verify(secApiClient).fetchSubmissions("0000320193");
        verify(secApiClient, never()).fetchSubmissions("0000789019");
    }
}

package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.dto.response.CompanyResponse;
import org.jds.edgar4j.dto.response.DividendOverviewResponse;
import org.jds.edgar4j.dto.response.DividendSyncStatusResponse;
import org.jds.edgar4j.model.DividendSyncState;
import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.model.FormType;
import org.jds.edgar4j.port.DividendSyncStateDataPort;
import org.jds.edgar4j.port.FillingDataPort;
import org.jds.edgar4j.service.CompanyMarketDataService;
import org.jds.edgar4j.service.CompanyService;
import org.jds.edgar4j.service.DividendAnalysisService;
import org.jds.edgar4j.service.DownloadSubmissionsService;
import org.jds.edgar4j.service.Sp500Service;
import org.jds.edgar4j.service.xbrl.CompanyFactsIngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class DividendSyncServiceImplTest {

    @Mock
    private CompanyService companyService;

    @Mock
    private DownloadSubmissionsService downloadSubmissionsService;

    @Mock
    private FillingDataPort fillingRepository;

    @Mock
    private DividendSyncStateDataPort dividendSyncStateRepository;

    @Mock
    private CompanyMarketDataService companyMarketDataService;

    @Mock
    private DividendAnalysisService dividendAnalysisService;

    @Mock
    private Sp500Service sp500Service;

    @Mock
    private CompanyFactsIngestionService companyFactsIngestionService;

    private DividendSyncServiceImpl dividendSyncService;

    @BeforeEach
    void setUp() {
        dividendSyncService = new DividendSyncServiceImpl(
                companyService,
                downloadSubmissionsService,
                fillingRepository,
                dividendSyncStateRepository,
                companyMarketDataService,
                dividendAnalysisService,
                sp500Service,
                companyFactsIngestionService);
    }

    @Test
    @DisplayName("syncCompany should refresh submissions, market data, and sync state")
    void syncCompanyShouldRefreshSubmissionsMarketDataAndState() {
        CompanyResponse company = CompanyResponse.builder()
                .cik("0000320193")
                .ticker("AAPL")
                .name("Apple Inc.")
                .fiscalYearEnd(930L)
                .build();
        DividendSyncState existingState = DividendSyncState.builder()
                .id("0000320193")
                .cik("0000320193")
                .ticker("AAPL")
                .companyName("Apple Inc.")
                .lastAccession("0000320193-24-000090")
                .factsVersion(2)
                .createdAt(Instant.parse("2026-03-01T00:00:00Z"))
                .build();

        Filling latestFiling = Filling.builder()
                .accessionNumber("0000320193-25-000106")
                .fillingDate(Date.from(Instant.parse("2025-11-01T00:00:00Z")))
                .formType(FormType.builder().number("10-K").build())
                .build();

        when(companyService.getCompanyByTicker("AAPL")).thenReturn(Optional.of(company));
        when(dividendSyncStateRepository.findByCik("0000320193")).thenReturn(Optional.of(existingState));
        when(fillingRepository.findByCik(eq("0000320193"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(latestFiling)));
        when(dividendSyncStateRepository.save(any(DividendSyncState.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(companyFactsIngestionService.ingest("0000320193"))
                .thenReturn(new CompanyFactsIngestionService.IngestionResult("0000320193", 4, 0, 0));
        when(dividendAnalysisService.getOverview("0000320193")).thenReturn(
                DividendOverviewResponse.builder()
                        .company(DividendOverviewResponse.CompanySummary.builder()
                                .cik("0000320193")
                                .ticker("AAPL")
                                .name("Apple Inc.")
                                .build())
                        .warnings(List.of())
                        .build());

        DividendSyncStatusResponse response = dividendSyncService.syncCompany("AAPL", true);

        assertEquals(DividendSyncState.SyncStatus.IDLE, response.getStatus());
        assertEquals(1, response.getNewFilingsDetected());
        assertEquals(3, response.getFactsVersion());
        assertTrue(response.isRefreshedSubmissions());
        assertTrue(response.isRefreshedMarketData());
        assertTrue(response.isAnalysisWarmupSucceeded());
        assertEquals("0000320193-25-000106", response.getLastAccession());
        verify(downloadSubmissionsService).downloadSubmissions("0000320193");
        verify(companyFactsIngestionService).ingest("0000320193");
        verify(companyMarketDataService).fetchAndSaveQuote("AAPL");
        verify(dividendAnalysisService).getOverview("0000320193");
        verify(dividendSyncStateRepository, atLeast(2)).save(any(DividendSyncState.class));
    }

    @Test
    @DisplayName("getSyncStatus should return an idle placeholder when no sync state exists yet")
    void getSyncStatusShouldReturnIdlePlaceholderWhenMissing() {
        CompanyResponse company = CompanyResponse.builder()
                .cik("0000789019")
                .ticker("MSFT")
                .name("Microsoft Corp.")
                .fiscalYearEnd(630L)
                .build();

        when(companyService.getCompanyByTicker("MSFT")).thenReturn(Optional.of(company));
        when(dividendSyncStateRepository.findByCik("0000789019")).thenReturn(Optional.empty());
        when(fillingRepository.findByCik(eq("0000789019"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        DividendSyncStatusResponse response = dividendSyncService.getSyncStatus("MSFT");

        assertEquals(DividendSyncState.SyncStatus.IDLE, response.getStatus());
        assertEquals(0, response.getFactsVersion());
        assertTrue(response.getWarnings().stream().anyMatch(warning -> warning.contains("No dividend sync")));
    }

    @Test
    @DisplayName("syncCompany should record failure state and exponential backoff")
    void syncCompanyShouldRecordFailureStateAndBackoff() {
        CompanyResponse company = CompanyResponse.builder()
                .cik("0000320193")
                .ticker("AAPL")
                .name("Apple Inc.")
                .fiscalYearEnd(930L)
                .build();
        DividendSyncState existingState = DividendSyncState.builder()
                .id("0000320193")
                .cik("0000320193")
                .ticker("AAPL")
                .companyName("Apple Inc.")
                .retryCount(1)
                .createdAt(Instant.parse("2026-03-01T00:00:00Z"))
                .build();

        when(companyService.getCompanyByTicker("AAPL")).thenReturn(Optional.of(company));
        when(dividendSyncStateRepository.findByCik("0000320193")).thenReturn(Optional.of(existingState));
        when(dividendSyncStateRepository.save(any(DividendSyncState.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(downloadSubmissionsService.downloadSubmissions("0000320193"))
                .thenThrow(new RuntimeException("SEC unavailable"));

        DividendSyncStatusResponse response = dividendSyncService.syncCompany("AAPL", false);

        assertEquals(DividendSyncState.SyncStatus.ERROR, response.getStatus());
        assertEquals(2, response.getRetryCount());
        assertEquals("SEC unavailable", response.getErrorMessage());
        assertTrue(response.getNextRetryAt() != null);
        assertTrue(response.getWarnings().stream().anyMatch(warning -> warning.contains("Dividend sync failed")));
        verify(companyFactsIngestionService, never()).ingest("0000320193");
    }

    @Test
    @DisplayName("syncCompany should trigger event warmup when new current report is detected")
    void syncCompanyShouldTriggerEventWarmupWhenNewCurrentReportDetected() {
        CompanyResponse company = CompanyResponse.builder()
                .cik("0000320193")
                .ticker("AAPL")
                .name("Apple Inc.")
                .fiscalYearEnd(930L)
                .build();
        DividendSyncState existingState = DividendSyncState.builder()
                .id("0000320193")
                .cik("0000320193")
                .ticker("AAPL")
                .companyName("Apple Inc.")
                .lastAccession("0000320193-25-000001")
                .createdAt(Instant.parse("2026-03-01T00:00:00Z"))
                .build();
        Filling currentReport = Filling.builder()
                .accessionNumber("0000320193-26-000010")
                .fillingDate(Date.from(Instant.parse("2026-02-20T00:00:00Z")))
                .formType(FormType.builder().number("8-K").build())
                .build();

        when(companyService.getCompanyByTicker("AAPL")).thenReturn(Optional.of(company));
        when(dividendSyncStateRepository.findByCik("0000320193")).thenReturn(Optional.of(existingState));
        when(fillingRepository.findByCik(eq("0000320193"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(currentReport)));
        when(dividendSyncStateRepository.save(any(DividendSyncState.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(companyFactsIngestionService.ingest("0000320193"))
                .thenReturn(new CompanyFactsIngestionService.IngestionResult("0000320193", 0, 0, 0));
        when(dividendAnalysisService.getOverview("0000320193")).thenReturn(
                DividendOverviewResponse.builder()
                        .company(DividendOverviewResponse.CompanySummary.builder()
                                .cik("0000320193")
                                .ticker("AAPL")
                                .name("Apple Inc.")
                                .build())
                        .warnings(List.of())
                        .build());

        DividendSyncStatusResponse response = dividendSyncService.syncCompany("AAPL", false);

        assertEquals(DividendSyncState.SyncStatus.IDLE, response.getStatus());
        assertEquals(1, response.getNewFilingsDetected());
        assertTrue(response.getLastEventsSync() != null);
        verify(dividendAnalysisService).getEvents(eq("0000320193"), any());
    }

    @Test
    @DisplayName("trackCompany should create idle sync state without running sync when syncNow is false")
    void trackCompanyShouldCreateIdleStateWithoutImmediateSync() {
        CompanyResponse company = CompanyResponse.builder()
                .cik("0000320193")
                .ticker("AAPL")
                .name("Apple Inc.")
                .fiscalYearEnd(930L)
                .build();

        when(companyService.getCompanyByTicker("AAPL")).thenReturn(Optional.of(company));
        when(dividendSyncStateRepository.findByCik("0000320193")).thenReturn(Optional.empty());
        when(dividendSyncStateRepository.save(any(DividendSyncState.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DividendSyncStatusResponse response = dividendSyncService.trackCompany("AAPL", false, true);

        assertEquals(DividendSyncState.SyncStatus.IDLE, response.getStatus());
        assertEquals("AAPL", response.getCompany().getTicker());
        assertEquals("0000320193", response.getCompany().getCik());
        verify(dividendSyncStateRepository).save(any(DividendSyncState.class));
        verify(downloadSubmissionsService, never()).downloadSubmissions("0000320193");
        verify(companyFactsIngestionService, never()).ingest("0000320193");
    }

    @Test
    @DisplayName("untrackCompany should delete existing sync state without deleting historical data")
    void untrackCompanyShouldDeleteExistingSyncState() {
        CompanyResponse company = CompanyResponse.builder()
                .cik("0000320193")
                .ticker("AAPL")
                .name("Apple Inc.")
                .fiscalYearEnd(930L)
                .build();
        DividendSyncState existingState = DividendSyncState.builder()
                .id("0000320193")
                .cik("0000320193")
                .ticker("AAPL")
                .companyName("Apple Inc.")
                .factsVersion(3)
                .build();

        when(companyService.getCompanyByTicker("AAPL")).thenReturn(Optional.of(company));
        when(dividendSyncStateRepository.findByCik("0000320193")).thenReturn(Optional.of(existingState));

        DividendSyncStatusResponse response = dividendSyncService.untrackCompany("AAPL");

        assertEquals(DividendSyncState.SyncStatus.IDLE, response.getStatus());
        assertEquals(3, response.getFactsVersion());
        assertTrue(response.getWarnings().isEmpty());
        verify(dividendSyncStateRepository).deleteById("0000320193");
        verify(fillingRepository, never()).deleteById(any());
        verify(companyFactsIngestionService, never()).ingest("0000320193");
    }

    @Test
    @DisplayName("syncTrackedCompanies should use stored tracked universe and continue after one company fails")
    void syncTrackedCompaniesShouldUseTrackedUniverseAndContinueAfterFailure() {
        CompanyResponse company = CompanyResponse.builder()
                .cik("0000320193")
                .ticker("AAPL")
                .name("Apple Inc.")
                .fiscalYearEnd(930L)
                .build();
        DividendSyncState appleState = DividendSyncState.builder()
                .id("0000320193")
                .cik("0000320193")
                .ticker("AAPL")
                .companyName("Apple Inc.")
                .createdAt(Instant.parse("2026-03-01T00:00:00Z"))
                .build();
        DividendSyncState badState = DividendSyncState.builder()
                .id("bad")
                .cik("0000000000")
                .ticker("BAD")
                .companyName("Bad Co")
                .build();
        Filling latestFiling = Filling.builder()
                .accessionNumber("0000320193-25-000106")
                .fillingDate(Date.from(Instant.parse("2025-11-01T00:00:00Z")))
                .formType(FormType.builder().number("10-K").build())
                .build();

        when(dividendSyncStateRepository.findAll()).thenReturn(List.of(appleState, badState));
        when(companyService.getCompanyByTicker("AAPL")).thenReturn(Optional.of(company));
        when(companyService.getCompanyByTicker("BAD")).thenReturn(Optional.empty());
        when(dividendSyncStateRepository.findByCik("0000320193")).thenReturn(Optional.of(appleState));
        when(fillingRepository.findByCik(eq("0000320193"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(latestFiling)));
        when(dividendSyncStateRepository.save(any(DividendSyncState.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(companyFactsIngestionService.ingest("0000320193"))
                .thenReturn(new CompanyFactsIngestionService.IngestionResult("0000320193", 0, 0, 0));
        when(dividendAnalysisService.getOverview("0000320193")).thenReturn(
                DividendOverviewResponse.builder()
                        .company(DividendOverviewResponse.CompanySummary.builder()
                                .cik("0000320193")
                                .ticker("AAPL")
                                .name("Apple Inc.")
                                .build())
                        .warnings(List.of())
                        .build());

        List<DividendSyncStatusResponse> responses = dividendSyncService.syncTrackedCompanies(10, false);

        assertEquals(1, responses.size());
        assertEquals("AAPL", responses.get(0).getCompany().getTicker());
        verify(sp500Service, never()).getAllTickers();
        verify(companyService).getCompanyByTicker("BAD");
    }
}

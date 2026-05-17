package org.jds.edgar4j.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.jds.edgar4j.dto.response.DividendAlertsResponse;
import org.jds.edgar4j.dto.response.DividendEventsResponse;
import org.jds.edgar4j.dto.response.DividendHistoryResponse;
import org.jds.edgar4j.dto.response.DividendOverviewResponse;
import org.jds.edgar4j.dto.response.DividendReconciliationResponse;
import org.jds.edgar4j.dto.response.DividendSyncStatusResponse;
import org.jds.edgar4j.model.DividendSyncState;
import org.jds.edgar4j.service.DividendAnalysisService;
import org.jds.edgar4j.service.DividendSyncService;
import org.jds.edgar4j.service.dividend.DividendAnalysisSnapshotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DividendReconciliationServiceImplTest {

    @Mock
    private DividendSyncService dividendSyncService;

    @Mock
    private DividendAnalysisService dividendAnalysisService;

    @Mock
    private DividendAnalysisSnapshotService dividendAnalysisSnapshotService;

    private DividendReconciliationServiceImpl reconciliationService;

    @BeforeEach
    void setUp() {
        reconciliationService = new DividendReconciliationServiceImpl(
                dividendSyncService,
                dividendAnalysisService,
                dividendAnalysisSnapshotService);
    }

    @Test
    @DisplayName("reconcile should sync live inputs, rebuild dashboard surfaces, and mark the snapshot reconciled")
    void reconcileShouldSyncRebuildAndMarkSnapshot() {
        DividendOverviewResponse.CompanySummary company = DividendOverviewResponse.CompanySummary.builder()
                .cik("0000320193")
                .ticker("AAPL")
                .name("Apple Inc.")
                .build();
        DividendSyncStatusResponse syncStatus = DividendSyncStatusResponse.builder()
                .company(company)
                .status(DividendSyncState.SyncStatus.IDLE)
                .factsVersion(12)
                .newFilingsDetected(1)
                .warnings(List.of("sync warning"))
                .build();
        DividendOverviewResponse overview = DividendOverviewResponse.builder()
                .company(company)
                .warnings(List.of("overview warning"))
                .build();
        DividendHistoryResponse history = DividendHistoryResponse.builder()
                .company(company)
                .warnings(List.of())
                .build();
        DividendAlertsResponse alerts = DividendAlertsResponse.builder()
                .company(company)
                .warnings(List.of())
                .build();
        DividendEventsResponse events = DividendEventsResponse.builder()
                .company(company)
                .warnings(List.of("events warning"))
                .build();

        when(dividendSyncService.syncCompany("AAPL", true)).thenReturn(syncStatus);
        when(dividendAnalysisService.getOverview("AAPL")).thenReturn(overview);
        when(dividendAnalysisService.getHistory(eq("AAPL"), any(), eq("FY"), eq(15))).thenReturn(history);
        when(dividendAnalysisService.getAlerts("AAPL", false)).thenReturn(alerts);
        when(dividendAnalysisService.getEvents("AAPL", null)).thenReturn(events);

        DividendReconciliationResponse response = reconciliationService.reconcile("AAPL", true);

        assertThat(response.getCompany()).isSameAs(company);
        assertThat(response.getSyncStatus()).isSameAs(syncStatus);
        assertThat(response.getOverview()).isSameAs(overview);
        assertThat(response.getHistory()).isSameAs(history);
        assertThat(response.getAlerts()).isSameAs(alerts);
        assertThat(response.getEvents()).isSameAs(events);
        assertThat(response.getWarnings()).containsExactly("sync warning", "overview warning", "events warning");
        assertThat(response.getReconciledAt()).isNotNull();
        verify(dividendAnalysisSnapshotService).markLiveReconciled(company, 12, response.getReconciledAt());
    }
}

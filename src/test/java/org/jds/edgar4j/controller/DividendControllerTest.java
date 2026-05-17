package org.jds.edgar4j.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

import org.jds.edgar4j.dto.response.ApiResponse;
import org.jds.edgar4j.dto.response.DividendAlertsResponse;
import org.jds.edgar4j.dto.response.DividendComparisonResponse;
import org.jds.edgar4j.dto.response.DividendEvidenceResponse;
import org.jds.edgar4j.dto.response.DividendEventsResponse;
import org.jds.edgar4j.dto.response.DividendHistoryResponse;
import org.jds.edgar4j.dto.response.DividendMetricDefinitionResponse;
import org.jds.edgar4j.dto.response.DividendOverviewResponse;
import org.jds.edgar4j.dto.response.DividendQualityResponse;
import org.jds.edgar4j.dto.request.DividendScreenRequest;
import org.jds.edgar4j.dto.response.DividendScreenResponse;
import org.jds.edgar4j.dto.response.DividendSyncStatusResponse;
import org.jds.edgar4j.service.DividendAnalysisService;
import org.jds.edgar4j.service.DividendQualityService;
import org.jds.edgar4j.service.DividendSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class DividendControllerTest {

    @Mock
    private DividendAnalysisService dividendAnalysisService;

    @Mock
    private DividendSyncService dividendSyncService;

    @Mock
    private DividendQualityService dividendQualityService;

    private DividendController dividendController;

    @BeforeEach
    void setUp() {
        dividendController = new DividendController(
                dividendAnalysisService,
                dividendSyncService,
                dividendQualityService);
    }

    @Test
    @DisplayName("getOverview should wrap the service response in ApiResponse.success")
    void getOverviewShouldReturnApiResponse() {
        DividendOverviewResponse overview = DividendOverviewResponse.builder()
                .company(DividendOverviewResponse.CompanySummary.builder()
                        .ticker("AAPL")
                        .cik("0000320193")
                        .build())
                .viability(DividendOverviewResponse.ViabilitySummary.builder()
                        .rating(DividendOverviewResponse.DividendRating.SAFE)
                        .score(90)
                        .activeAlerts(0)
                        .build())
                .build();

        when(dividendAnalysisService.getOverview("AAPL")).thenReturn(overview);

        ResponseEntity<ApiResponse<DividendOverviewResponse>> response = dividendController.getOverview("AAPL");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().isSuccess());
        assertSame(overview, response.getBody().getData());
        assertEquals("Success", response.getBody().getMessage());
    }

    @Test
    @DisplayName("compare should wrap the service response in ApiResponse.success")
    void compareShouldReturnApiResponse() {
        DividendComparisonResponse comparison = DividendComparisonResponse.builder()
                .companies(java.util.List.of())
                .metrics(java.util.List.of())
                .build();

        when(dividendAnalysisService.compare(
                java.util.List.of("AAPL", "MSFT"),
                java.util.List.of("fcf_payout", "dps_cagr_5y")))
                .thenReturn(comparison);

        ResponseEntity<ApiResponse<DividendComparisonResponse>> response = dividendController.compare(
                java.util.List.of("AAPL", "MSFT"),
                java.util.List.of("fcf_payout", "dps_cagr_5y"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().isSuccess());
        assertSame(comparison, response.getBody().getData());
    }

    @Test
    @DisplayName("getMetricDefinitions should wrap the service response in ApiResponse.success")
    void getMetricDefinitionsShouldReturnApiResponse() {
        java.util.List<DividendMetricDefinitionResponse> definitions = java.util.List.of(
                DividendMetricDefinitionResponse.builder()
                        .id("fcf_payout")
                        .label("Free Cash Flow Payout Ratio")
                        .build());

        when(dividendAnalysisService.getMetricDefinitions()).thenReturn(definitions);

        ResponseEntity<ApiResponse<java.util.List<DividendMetricDefinitionResponse>>> response =
                dividendController.getMetricDefinitions();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().isSuccess());
        assertSame(definitions, response.getBody().getData());
    }

    @Test
    @DisplayName("screen should wrap the service response in ApiResponse.success")
    void screenShouldReturnApiResponse() {
        DividendScreenRequest request = DividendScreenRequest.builder()
                .tickersOrCiks(java.util.List.of("AAPL", "MSFT"))
                .build();
        DividendScreenResponse screen = DividendScreenResponse.builder()
                .candidatesEvaluated(2)
                .warnings(java.util.List.of())
                .build();

        when(dividendAnalysisService.screen(request)).thenReturn(screen);

        ResponseEntity<ApiResponse<DividendScreenResponse>> response = dividendController.screen(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().isSuccess());
        assertSame(screen, response.getBody().getData());
    }

    @Test
    @DisplayName("syncCompany should wrap the service response in ApiResponse.success")
    void syncCompanyShouldReturnApiResponse() {
        DividendSyncStatusResponse syncStatus = DividendSyncStatusResponse.builder()
                .status(org.jds.edgar4j.model.DividendSyncState.SyncStatus.IDLE)
                .newFilingsDetected(2)
                .refreshedSubmissions(true)
                .build();

        when(dividendSyncService.syncCompany("AAPL", true)).thenReturn(syncStatus);

        ResponseEntity<ApiResponse<DividendSyncStatusResponse>> response = dividendController.syncCompany("AAPL", true);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().isSuccess());
        assertSame(syncStatus, response.getBody().getData());
    }

    @Test
    @DisplayName("getSyncStatus should wrap the service response in ApiResponse.success")
    void getSyncStatusShouldReturnApiResponse() {
        DividendSyncStatusResponse syncStatus = DividendSyncStatusResponse.builder()
                .status(org.jds.edgar4j.model.DividendSyncState.SyncStatus.IDLE)
                .factsVersion(3)
                .build();

        when(dividendSyncService.getSyncStatus("AAPL")).thenReturn(syncStatus);

        ResponseEntity<ApiResponse<DividendSyncStatusResponse>> response = dividendController.getSyncStatus("AAPL");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().isSuccess());
        assertSame(syncStatus, response.getBody().getData());
    }

    @Test
    @DisplayName("trackCompany should wrap the service response in ApiResponse.success")
    void trackCompanyShouldReturnApiResponse() {
        DividendSyncStatusResponse syncStatus = DividendSyncStatusResponse.builder()
                .status(org.jds.edgar4j.model.DividendSyncState.SyncStatus.IDLE)
                .build();

        when(dividendSyncService.trackCompany("AAPL", false, true)).thenReturn(syncStatus);

        ResponseEntity<ApiResponse<DividendSyncStatusResponse>> response =
                dividendController.trackCompany("AAPL", false, true);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().isSuccess());
        assertSame(syncStatus, response.getBody().getData());
    }

    @Test
    @DisplayName("untrackCompany should wrap the service response in ApiResponse.success")
    void untrackCompanyShouldReturnApiResponse() {
        DividendSyncStatusResponse syncStatus = DividendSyncStatusResponse.builder()
                .status(org.jds.edgar4j.model.DividendSyncState.SyncStatus.IDLE)
                .build();

        when(dividendSyncService.untrackCompany("AAPL")).thenReturn(syncStatus);

        ResponseEntity<ApiResponse<DividendSyncStatusResponse>> response =
                dividendController.untrackCompany("AAPL");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().isSuccess());
        assertSame(syncStatus, response.getBody().getData());
    }

    @Test
    @DisplayName("getQuality should wrap the service response in ApiResponse.success")
    void getQualityShouldReturnApiResponse() {
        DividendQualityResponse quality = DividendQualityResponse.builder()
                .overallStatus(DividendQualityResponse.QualityStatus.PASS)
                .benchmarks(java.util.List.of())
                .issues(java.util.List.of())
                .warnings(java.util.List.of())
                .build();

        when(dividendQualityService.assess("AAPL")).thenReturn(quality);

        ResponseEntity<ApiResponse<DividendQualityResponse>> response = dividendController.getQuality("AAPL");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().isSuccess());
        assertSame(quality, response.getBody().getData());
    }

    @Test
    @DisplayName("getHistory should wrap the service response in ApiResponse.success")
    void getHistoryShouldReturnApiResponse() {
        DividendHistoryResponse history = DividendHistoryResponse.builder()
                .period("FY")
                .yearsRequested(10)
                .build();

        when(dividendAnalysisService.getHistory("AAPL",
                java.util.List.of("dps_declared", "fcf_payout"),
                "FY",
                10)).thenReturn(history);

        ResponseEntity<ApiResponse<DividendHistoryResponse>> response = dividendController.getHistory(
                "AAPL",
                java.util.List.of("dps_declared", "fcf_payout"),
                "FY",
                10);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().isSuccess());
        assertSame(history, response.getBody().getData());
    }

    @Test
    @DisplayName("getAlerts should wrap the service response in ApiResponse.success")
    void getAlertsShouldReturnApiResponse() {
        DividendAlertsResponse alerts = DividendAlertsResponse.builder()
                .activeOnly(true)
                .build();

        when(dividendAnalysisService.getAlerts("AAPL", true)).thenReturn(alerts);

        ResponseEntity<ApiResponse<DividendAlertsResponse>> response = dividendController.getAlerts("AAPL", true);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().isSuccess());
        assertSame(alerts, response.getBody().getData());
    }

    @Test
    @DisplayName("getEvents should wrap the service response in ApiResponse.success")
    void getEventsShouldReturnApiResponse() {
        DividendEventsResponse events = DividendEventsResponse.builder()
                .events(java.util.List.of())
                .build();

        when(dividendAnalysisService.getEvents("AAPL", java.time.LocalDate.of(2025, 1, 1))).thenReturn(events);

        ResponseEntity<ApiResponse<DividendEventsResponse>> response = dividendController.getEvents(
                "AAPL",
                java.time.LocalDate.of(2025, 1, 1));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().isSuccess());
        assertSame(events, response.getBody().getData());
    }

    @Test
    @DisplayName("getEvidence should wrap the service response in ApiResponse.success")
    void getEvidenceShouldReturnApiResponse() {
        DividendEvidenceResponse evidence = DividendEvidenceResponse.builder()
                .cleanedText("Board of Directors declared a cash dividend.")
                .truncated(false)
                .build();

        when(dividendAnalysisService.getEvidence("AAPL", "0000320193-26-000010")).thenReturn(evidence);

        ResponseEntity<ApiResponse<DividendEvidenceResponse>> response = dividendController.getEvidence(
                "AAPL",
                "0000320193-26-000010");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().isSuccess());
        assertSame(evidence, response.getBody().getData());
    }
}

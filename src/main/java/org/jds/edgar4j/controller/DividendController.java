package org.jds.edgar4j.controller;

import java.util.List;
import java.time.LocalDate;

import org.jds.edgar4j.dto.request.DividendScreenRequest;
import org.jds.edgar4j.dto.response.ApiResponse;
import org.jds.edgar4j.dto.response.DividendAlertsResponse;
import org.jds.edgar4j.dto.response.DividendComparisonResponse;
import org.jds.edgar4j.dto.response.DividendEvidenceResponse;
import org.jds.edgar4j.dto.response.DividendEventsResponse;
import org.jds.edgar4j.dto.response.DividendHistoryResponse;
import org.jds.edgar4j.dto.response.DividendMetricDefinitionResponse;
import org.jds.edgar4j.dto.response.DividendOverviewResponse;
import org.jds.edgar4j.dto.response.DividendQualityResponse;
import org.jds.edgar4j.dto.response.DividendScreenResponse;
import org.jds.edgar4j.dto.response.DividendSyncStatusResponse;
import org.jds.edgar4j.service.DividendAnalysisService;
import org.jds.edgar4j.service.DividendQualityService;
import org.jds.edgar4j.service.DividendSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/dividend")
@RequiredArgsConstructor
@Tag(name = "Dividend Viability", description = "Long-term dividend safety analysis endpoints")
public class DividendController {

    private final DividendAnalysisService dividendAnalysisService;
    private final DividendSyncService dividendSyncService;
    private final DividendQualityService dividendQualityService;

    @Operation(summary = "Compare dividend viability across companies",
               description = "Returns a compact peer-comparison view for multiple tickers or SEC CIKs.")
    @GetMapping("/compare")
    public ResponseEntity<ApiResponse<DividendComparisonResponse>> compare(
            @Parameter(description = "Ticker symbols or SEC CIKs", example = "AAPL,MSFT,JNJ")
            @RequestParam List<String> tickers,
            @Parameter(description = "Metric ids to compare", example = "fcf_payout,dps_cagr_5y,net_debt_to_ebitda")
            @RequestParam(defaultValue = "fcf_payout,dps_cagr_5y,net_debt_to_ebitda") List<String> metrics) {
        log.info("GET /api/dividend/compare tickers={} metrics={}", tickers, metrics);
        return ResponseEntity.ok(ApiResponse.success(dividendAnalysisService.compare(tickers, metrics)));
    }

    @Operation(summary = "Get dividend metric definitions",
               description = "Returns the metric catalog used by the dividend overview, history, and comparison APIs.")
    @GetMapping("/metrics")
    public ResponseEntity<ApiResponse<List<DividendMetricDefinitionResponse>>> getMetricDefinitions() {
        log.info("GET /api/dividend/metrics");
        return ResponseEntity.ok(ApiResponse.success(dividendAnalysisService.getMetricDefinitions()));
    }

    @Operation(summary = "Screen companies by dividend criteria",
               description = "Runs a bounded dividend screener over the provided identifiers or the local company universe.")
    @PostMapping("/screen")
    public ResponseEntity<ApiResponse<DividendScreenResponse>> screen(
            @RequestBody @Valid DividendScreenRequest request) {
        log.info("POST /api/dividend/screen request={}", request);
        return ResponseEntity.ok(ApiResponse.success(dividendAnalysisService.screen(request)));
    }

    @Operation(summary = "Run a dividend sync for one company",
               description = "Refreshes submissions, optionally refreshes market data, and records sync status for a ticker or SEC CIK.")
    @PostMapping("/{tickerOrCik}/sync")
    public ResponseEntity<ApiResponse<DividendSyncStatusResponse>> syncCompany(
            @Parameter(description = "Ticker symbol or SEC CIK", example = "AAPL")
            @PathVariable String tickerOrCik,
            @Parameter(description = "Refresh market data during the sync run.", example = "true")
            @RequestParam(defaultValue = "true") boolean refreshMarketData) {
        log.info("POST /api/dividend/{}/sync refreshMarketData={}", tickerOrCik, refreshMarketData);
        return ResponseEntity.ok(ApiResponse.success(
                dividendSyncService.syncCompany(tickerOrCik, refreshMarketData)));
    }

    @Operation(summary = "Get stored dividend sync status",
               description = "Returns the last known dividend sync state for a ticker or SEC CIK.")
    @GetMapping("/{tickerOrCik}/sync")
    public ResponseEntity<ApiResponse<DividendSyncStatusResponse>> getSyncStatus(
            @Parameter(description = "Ticker symbol or SEC CIK", example = "AAPL")
            @PathVariable String tickerOrCik) {
        log.info("GET /api/dividend/{}/sync", tickerOrCik);
        return ResponseEntity.ok(ApiResponse.success(dividendSyncService.getSyncStatus(tickerOrCik)));
    }

    @Operation(summary = "Get dividend data quality and benchmark status",
               description = "Returns consistency checks and benchmark comparisons for the current dividend analysis.")
    @GetMapping("/{tickerOrCik}/quality")
    public ResponseEntity<ApiResponse<DividendQualityResponse>> getQuality(
            @Parameter(description = "Ticker symbol or SEC CIK", example = "AAPL")
            @PathVariable String tickerOrCik) {
        log.info("GET /api/dividend/{}/quality", tickerOrCik);
        return ResponseEntity.ok(ApiResponse.success(dividendQualityService.assess(tickerOrCik)));
    }

    @Operation(summary = "Get dividend viability overview",
               description = "Returns the dividend viability overview for a ticker or SEC CIK.")
    @GetMapping("/{tickerOrCik}")
    public ResponseEntity<ApiResponse<DividendOverviewResponse>> getOverview(
            @Parameter(description = "Ticker symbol or SEC CIK", example = "AAPL")
            @PathVariable String tickerOrCik) {
        log.info("GET /api/dividend/{}", tickerOrCik);
        return ResponseEntity.ok(ApiResponse.success(dividendAnalysisService.getOverview(tickerOrCik)));
    }

    @Operation(summary = "Get dividend metric history",
               description = "Returns annual historical dividend and coverage metrics for a ticker or SEC CIK.")
    @GetMapping("/{tickerOrCik}/history")
    public ResponseEntity<ApiResponse<DividendHistoryResponse>> getHistory(
            @Parameter(description = "Ticker symbol or SEC CIK", example = "AAPL")
            @PathVariable String tickerOrCik,
            @Parameter(description = "Metric ids to return", example = "dps_declared,fcf_payout,earnings_payout")
            @RequestParam(defaultValue = "dps_declared,fcf_payout,earnings_payout") List<String> metrics,
            @Parameter(description = "Period granularity. Only FY is currently supported.", example = "FY")
            @RequestParam(defaultValue = "FY") String periods,
            @Parameter(description = "Number of annual periods to return", example = "15")
            @RequestParam(defaultValue = "15") int years) {
        log.info("GET /api/dividend/{}/history metrics={} periods={} years={}",
                tickerOrCik, metrics, periods, years);
        return ResponseEntity.ok(ApiResponse.success(
                dividendAnalysisService.getHistory(tickerOrCik, metrics, periods, years)));
    }

    @Operation(summary = "Get dividend alerts",
               description = "Returns the current active alerts and the historical alert timeline for a ticker or SEC CIK.")
    @GetMapping("/{tickerOrCik}/alerts")
    public ResponseEntity<ApiResponse<DividendAlertsResponse>> getAlerts(
            @Parameter(description = "Ticker symbol or SEC CIK", example = "AAPL")
            @PathVariable String tickerOrCik,
            @Parameter(description = "When true, only currently active alert events are returned in the history list.",
                       example = "true")
            @RequestParam(defaultValue = "true") boolean active) {
        log.info("GET /api/dividend/{}/alerts active={}", tickerOrCik, active);
        return ResponseEntity.ok(ApiResponse.success(dividendAnalysisService.getAlerts(tickerOrCik, active)));
    }

    @Operation(summary = "Get dividend event timeline",
               description = "Returns extracted dividend declaration and policy events from filing text for a ticker or SEC CIK.")
    @GetMapping("/{tickerOrCik}/events")
    public ResponseEntity<ApiResponse<DividendEventsResponse>> getEvents(
            @Parameter(description = "Ticker symbol or SEC CIK", example = "AAPL")
            @PathVariable String tickerOrCik,
            @Parameter(description = "Only return events on or after this date.", example = "2025-01-01")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate since) {
        log.info("GET /api/dividend/{}/events since={}", tickerOrCik, since);
        return ResponseEntity.ok(ApiResponse.success(dividendAnalysisService.getEvents(tickerOrCik, since)));
    }

    @Operation(summary = "Get filing evidence view",
               description = "Returns extracted highlights and a cleaned text preview for a specific filing accession.")
    @GetMapping("/{tickerOrCik}/evidence/{accession}")
    public ResponseEntity<ApiResponse<DividendEvidenceResponse>> getEvidence(
            @Parameter(description = "Ticker symbol or SEC CIK", example = "AAPL")
            @PathVariable String tickerOrCik,
            @Parameter(description = "SEC accession number", example = "0000320193-26-000010")
            @PathVariable String accession) {
        log.info("GET /api/dividend/{}/evidence/{}", tickerOrCik, accession);
        return ResponseEntity.ok(ApiResponse.success(dividendAnalysisService.getEvidence(tickerOrCik, accession)));
    }
}

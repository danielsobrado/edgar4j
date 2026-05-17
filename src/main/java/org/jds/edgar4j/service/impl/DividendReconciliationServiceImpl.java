package org.jds.edgar4j.service.impl;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;

import org.jds.edgar4j.dto.response.DividendAlertsResponse;
import org.jds.edgar4j.dto.response.DividendEventsResponse;
import org.jds.edgar4j.dto.response.DividendHistoryResponse;
import org.jds.edgar4j.dto.response.DividendOverviewResponse;
import org.jds.edgar4j.dto.response.DividendReconciliationResponse;
import org.jds.edgar4j.dto.response.DividendSyncStatusResponse;
import org.jds.edgar4j.service.DividendAnalysisService;
import org.jds.edgar4j.service.DividendReconciliationService;
import org.jds.edgar4j.service.DividendSyncService;
import org.jds.edgar4j.service.dividend.DividendAnalysisSnapshotService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DividendReconciliationServiceImpl implements DividendReconciliationService {

    private static final List<String> DEFAULT_HISTORY_METRICS = List.of(
            "dps_declared",
            "eps_diluted",
            "earnings_payout",
            "revenue",
            "free_cash_flow",
            "dividends_paid",
            "fcf_payout",
            "cash_coverage",
            "retained_cash",
            "gross_debt",
            "net_debt_to_ebitda",
            "current_ratio",
            "interest_coverage",
            "fcf_margin");

    private final DividendSyncService dividendSyncService;
    private final DividendAnalysisService dividendAnalysisService;
    private final DividendAnalysisSnapshotService dividendAnalysisSnapshotService;

    @Override
    public DividendReconciliationResponse reconcile(String tickerOrCik, boolean refreshMarketData) {
        Instant reconciledAt = Instant.now();
        DividendSyncStatusResponse syncStatus = dividendSyncService.syncCompany(tickerOrCik, refreshMarketData);
        String identifier = resolveIdentifier(tickerOrCik, syncStatus);

        DividendOverviewResponse overview = dividendAnalysisService.getOverview(identifier);
        DividendHistoryResponse history = dividendAnalysisService.getHistory(identifier, DEFAULT_HISTORY_METRICS, "FY", 15);
        DividendAlertsResponse alerts = dividendAnalysisService.getAlerts(identifier, false);
        DividendEventsResponse events = dividendAnalysisService.getEvents(identifier, null);

        dividendAnalysisSnapshotService.markLiveReconciled(
                overview.getCompany(),
                syncStatus.getFactsVersion(),
                reconciledAt);

        return DividendReconciliationResponse.builder()
                .company(overview.getCompany())
                .syncStatus(syncStatus)
                .overview(overview)
                .history(history)
                .alerts(alerts)
                .events(events)
                .reconciledAt(reconciledAt)
                .warnings(mergeWarnings(syncStatus, overview, history, alerts, events))
                .build();
    }

    private String resolveIdentifier(String requestedIdentifier, DividendSyncStatusResponse syncStatus) {
        if (syncStatus.getCompany() != null) {
            String ticker = syncStatus.getCompany().getTicker();
            if (ticker != null && !ticker.isBlank()) {
                return ticker;
            }
            String cik = syncStatus.getCompany().getCik();
            if (cik != null && !cik.isBlank()) {
                return cik;
            }
        }
        return requestedIdentifier;
    }

    private List<String> mergeWarnings(
            DividendSyncStatusResponse syncStatus,
            DividendOverviewResponse overview,
            DividendHistoryResponse history,
            DividendAlertsResponse alerts,
            DividendEventsResponse events) {
        LinkedHashSet<String> warnings = new LinkedHashSet<>();
        addWarnings(warnings, syncStatus != null ? syncStatus.getWarnings() : null);
        addWarnings(warnings, overview != null ? overview.getWarnings() : null);
        addWarnings(warnings, history != null ? history.getWarnings() : null);
        addWarnings(warnings, alerts != null ? alerts.getWarnings() : null);
        addWarnings(warnings, events != null ? events.getWarnings() : null);
        return List.copyOf(warnings);
    }

    private void addWarnings(LinkedHashSet<String> warnings, List<String> values) {
        if (values != null) {
            values.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .forEach(warnings::add);
        }
    }
}

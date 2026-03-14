package org.jds.edgar4j.service;

import java.util.List;
import java.time.LocalDate;

import org.jds.edgar4j.dto.response.DividendAlertsResponse;
import org.jds.edgar4j.dto.response.DividendComparisonResponse;
import org.jds.edgar4j.dto.response.DividendEvidenceResponse;
import org.jds.edgar4j.dto.response.DividendEventsResponse;
import org.jds.edgar4j.dto.response.DividendHistoryResponse;
import org.jds.edgar4j.dto.response.DividendMetricDefinitionResponse;
import org.jds.edgar4j.dto.response.DividendOverviewResponse;
import org.jds.edgar4j.dto.request.DividendScreenRequest;
import org.jds.edgar4j.dto.response.DividendScreenResponse;

public interface DividendAnalysisService {

    DividendOverviewResponse getOverview(String tickerOrCik);

    DividendHistoryResponse getHistory(String tickerOrCik, List<String> metrics, String period, int years);

    DividendAlertsResponse getAlerts(String tickerOrCik, boolean activeOnly);

    DividendEventsResponse getEvents(String tickerOrCik, LocalDate since);

    DividendEvidenceResponse getEvidence(String tickerOrCik, String accessionNumber);

    DividendComparisonResponse compare(List<String> tickersOrCiks, List<String> metrics);

    List<DividendMetricDefinitionResponse> getMetricDefinitions();

    DividendScreenResponse screen(DividendScreenRequest request);
}

package org.jds.edgar4j.service.impl;

import java.util.List;
import java.util.Map;

import org.jds.edgar4j.dto.response.DividendOverviewResponse;
import org.jds.edgar4j.service.impl.DividendFilingAnalysisService.AnalyzedFilingData;
import org.jds.edgar4j.service.impl.DividendHistoryAnalysisService.HistoryRowData;

record DividendAnalysisContext(
        DividendOverviewResponse.CompanySummary companySummary,
        DividendOverviewResponse.Snapshot snapshot,
        Map<String, DividendOverviewResponse.MetricConfidence> confidence,
        List<DividendOverviewResponse.Alert> alerts,
        DividendOverviewResponse.Coverage coverage,
        DividendOverviewResponse.Balance balance,
        List<DividendOverviewResponse.TrendPoint> trend,
        DividendOverviewResponse.Evidence evidence,
        Double referencePrice,
        List<String> warnings,
        AnalyzedFilingData latestAnnual,
        AnalyzedFilingData latestBalance,
        List<HistoryRowData> historyRows,
        int score,
        DividendOverviewResponse.DividendRating rating) {
}

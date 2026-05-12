package org.jds.edgar4j.service.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jds.edgar4j.dto.response.DividendComparisonResponse;
import org.jds.edgar4j.dto.response.DividendOverviewResponse;
import org.jds.edgar4j.dto.response.DividendScreenResponse;
import org.jds.edgar4j.service.dividend.DividendScreeningService;
import org.jds.edgar4j.service.impl.DividendHistoryAnalysisService.HistoryRowData;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class DividendPeerAnalysisService {

    private final DividendHistoryAnalysisService dividendHistoryAnalysisService;
    private final DividendMetricCatalogService dividendMetricCatalogService;

    DividendComparisonResponse.ComparisonRow buildComparisonRow(
            DividendAnalysisContext context,
            List<String> requestedMetrics) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (String metric : requestedMetrics) {
            values.put(metric, getComparisonMetricValue(context, metric));
        }

        return DividendComparisonResponse.ComparisonRow.builder()
                .company(context.companySummary())
                .viability(DividendOverviewResponse.ViabilitySummary.builder()
                        .rating(context.rating())
                        .activeAlerts(context.alerts().size())
                        .score(context.score())
                        .build())
                .values(values)
                .warnings(context.warnings())
                .build();
    }

    DividendScreenResponse.ScreenResult buildScreenResult(
            DividendAnalysisContext context,
            List<String> requestedMetrics) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (String metric : requestedMetrics) {
            values.put(metric, getComparisonMetricValue(context, metric));
        }

        return DividendScreenResponse.ScreenResult.builder()
                .company(context.companySummary())
                .viability(DividendOverviewResponse.ViabilitySummary.builder()
                        .rating(context.rating())
                        .activeAlerts(context.alerts().size())
                        .score(context.score())
                        .build())
                .values(values)
                .warnings(context.warnings())
                .build();
    }

    DividendScreeningService.ScreeningCandidate toScreeningCandidate(DividendAnalysisContext context) {
        return new DividendScreeningService.ScreeningCandidate(
                context.companySummary(),
                context.alerts(),
                context.score(),
                context.rating(),
                context.warnings(),
                buildComparisonMetricValues(context));
    }

    private Map<String, Double> buildComparisonMetricValues(DividendAnalysisContext context) {
        return dividendMetricCatalogService.metricIds().stream()
                .collect(
                        LinkedHashMap::new,
                        (map, metric) -> map.put(metric, getComparisonMetricValue(context, metric)),
                        Map::putAll);
    }

    private Double getComparisonMetricValue(DividendAnalysisContext context, String metric) {
        if (context == null || metric == null) {
            return null;
        }

        return switch (metric) {
            case "dps_latest" -> context.snapshot().getDpsLatest();
            case "dps_cagr_5y" -> context.snapshot().getDpsCagr5y();
            case "fcf_payout" -> context.snapshot().getFcfPayoutRatio();
            case "uninterrupted_years" -> context.snapshot().getUninterruptedYears() != null
                    ? context.snapshot().getUninterruptedYears().doubleValue()
                    : null;
            case "consecutive_raises" -> context.snapshot().getConsecutiveRaises() != null
                    ? context.snapshot().getConsecutiveRaises().doubleValue()
                    : null;
            case "net_debt_to_ebitda" -> context.snapshot().getNetDebtToEbitda();
            case "interest_coverage" -> context.snapshot().getInterestCoverage();
            case "current_ratio" -> context.snapshot().getCurrentRatio();
            case "fcf_margin" -> context.snapshot().getFcfMargin();
            case "dividend_yield" -> context.snapshot().getDividendYield();
            case "score" -> Double.valueOf(context.score());
            case "active_alerts" -> Double.valueOf(context.alerts().size());
            default -> {
                HistoryRowData latestHistoryRow = dividendHistoryAnalysisService.latestHistoryRow(context.historyRows());
                yield latestHistoryRow != null ? dividendHistoryAnalysisService.getHistoryMetricValue(latestHistoryRow, metric) : null;
            }
        };
    }
}

package org.jds.edgar4j.service.impl;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jds.edgar4j.dto.request.DividendScreenRequest;
import org.jds.edgar4j.dto.response.DividendMetricDefinitionResponse;
import org.jds.edgar4j.service.impl.DividendHistoryAnalysisService.HistoryMetricDefinition;
import org.springframework.stereotype.Service;

@Service
public class DividendMetricCatalogService {

    private static final String HISTORY_PERIOD_FY = "FY";
    private static final List<String> DEFAULT_HISTORY_METRICS = List.of(
            "dps_declared",
            "fcf_payout",
            "earnings_payout");
    private static final List<String> DEFAULT_COMPARISON_METRICS = List.of(
            "fcf_payout",
            "dps_cagr_5y",
            "net_debt_to_ebitda");

    private static final Map<String, HistoryMetricDefinition> HISTORY_METRIC_DEFINITIONS = createHistoryMetricDefinitions();
    private static final Map<String, MetricDefinitionData> METRIC_DEFINITIONS = createMetricDefinitions();

    public String normalizeHistoryPeriod(String period) {
        String normalized = blankToNull(period);
        if (normalized == null) {
            return HISTORY_PERIOD_FY;
        }

        String upper = normalized.toUpperCase(Locale.ROOT);
        if (HISTORY_PERIOD_FY.equals(upper) || "ANNUAL".equals(upper) || "YEARLY".equals(upper)) {
            return HISTORY_PERIOD_FY;
        }

        throw new IllegalArgumentException("Unsupported history period: " + period + ". Only FY is currently supported.");
    }

    public List<String> normalizeHistoryMetrics(List<String> metrics) {
        List<String> requested = metrics == null || metrics.isEmpty() ? DEFAULT_HISTORY_METRICS : metrics;
        LinkedHashSet<String> normalized = new LinkedHashSet<>();

        for (String rawMetric : requested) {
            if (rawMetric == null) {
                continue;
            }

            for (String token : rawMetric.split(",")) {
                String metric = blankToNull(token);
                if (metric == null) {
                    continue;
                }

                String normalizedMetric = metric.toLowerCase(Locale.ROOT);
                if (!HISTORY_METRIC_DEFINITIONS.containsKey(normalizedMetric)) {
                    throw new IllegalArgumentException(
                            "Unsupported dividend history metric: " + metric
                                    + ". Supported metrics: " + String.join(", ", HISTORY_METRIC_DEFINITIONS.keySet()));
                }
                normalized.add(normalizedMetric);
            }
        }

        return normalized.isEmpty() ? DEFAULT_HISTORY_METRICS : List.copyOf(normalized);
    }

    public List<String> normalizeComparisonMetrics(List<String> metrics) {
        List<String> requested = metrics == null || metrics.isEmpty() ? DEFAULT_COMPARISON_METRICS : metrics;
        LinkedHashSet<String> normalized = new LinkedHashSet<>();

        for (String rawMetric : requested) {
            if (rawMetric == null) {
                continue;
            }

            for (String token : rawMetric.split(",")) {
                String metric = blankToNull(token);
                if (metric == null) {
                    continue;
                }

                String normalizedMetric = metric.toLowerCase(Locale.ROOT);
                if (!METRIC_DEFINITIONS.containsKey(normalizedMetric)) {
                    throw new IllegalArgumentException(
                            "Unsupported dividend comparison metric: " + metric
                                    + ". Supported metrics: " + String.join(", ", METRIC_DEFINITIONS.keySet()));
                }
                normalized.add(normalizedMetric);
            }
        }

        return normalized.isEmpty() ? DEFAULT_COMPARISON_METRICS : List.copyOf(normalized);
    }

    public List<String> resolveScreenMetrics(DividendScreenRequest request) {
        LinkedHashSet<String> metrics = new LinkedHashSet<>(normalizeComparisonMetrics(request.getMetrics()));
        if (request.getFilters() != null && request.getFilters().getMetrics() != null) {
            request.getFilters().getMetrics().keySet().stream()
                    .map(this::blankToNull)
                    .filter(Objects::nonNull)
                    .map(metric -> metric.toLowerCase(Locale.ROOT))
                    .forEach(metrics::add);
        }

        String sortMetric = blankToNull(request.getSort());
        if (sortMetric != null && METRIC_DEFINITIONS.containsKey(sortMetric.toLowerCase(Locale.ROOT))) {
            metrics.add(sortMetric.toLowerCase(Locale.ROOT));
        }

        return List.copyOf(metrics);
    }

    public Map<String, String> metricFormatHints() {
        return METRIC_DEFINITIONS.entrySet().stream()
                .collect(
                        LinkedHashMap::new,
                        (map, entry) -> map.put(entry.getKey(), entry.getValue().formatHint()),
                        Map::putAll);
    }

    public Set<String> metricIds() {
        return METRIC_DEFINITIONS.keySet();
    }

    public Map<String, HistoryMetricDefinition> historyMetricDefinitions() {
        return HISTORY_METRIC_DEFINITIONS;
    }

    public List<DividendMetricDefinitionResponse> metricDefinitions(List<String> metricIds) {
        return metricIds.stream()
                .map(METRIC_DEFINITIONS::get)
                .filter(Objects::nonNull)
                .map(this::toMetricDefinitionResponse)
                .toList();
    }

    public List<DividendMetricDefinitionResponse> allMetricDefinitions() {
        return METRIC_DEFINITIONS.values().stream()
                .map(this::toMetricDefinitionResponse)
                .toList();
    }

    private static Map<String, HistoryMetricDefinition> createHistoryMetricDefinitions() {
        Map<String, HistoryMetricDefinition> definitions = new LinkedHashMap<>();
        definitions.put("dps_declared", new HistoryMetricDefinition("dps_declared", "Dividend Per Share", "USD/share"));
        definitions.put("eps_diluted", new HistoryMetricDefinition("eps_diluted", "Diluted EPS", "USD/share"));
        definitions.put("earnings_payout", new HistoryMetricDefinition("earnings_payout", "Earnings Payout Ratio", "ratio"));
        definitions.put("revenue", new HistoryMetricDefinition("revenue", "Revenue", "USD"));
        definitions.put("operating_cash_flow", new HistoryMetricDefinition("operating_cash_flow", "Operating Cash Flow", "USD"));
        definitions.put("capital_expenditures", new HistoryMetricDefinition("capital_expenditures", "Capital Expenditures", "USD"));
        definitions.put("free_cash_flow", new HistoryMetricDefinition("free_cash_flow", "Free Cash Flow", "USD"));
        definitions.put("dividends_paid", new HistoryMetricDefinition("dividends_paid", "Dividends Paid", "USD"));
        definitions.put("fcf_payout", new HistoryMetricDefinition("fcf_payout", "Free Cash Flow Payout Ratio", "ratio"));
        definitions.put("cash_coverage", new HistoryMetricDefinition("cash_coverage", "Cash Coverage", "ratio"));
        definitions.put("retained_cash", new HistoryMetricDefinition("retained_cash", "Retained Cash After Dividends", "USD"));
        definitions.put("cash", new HistoryMetricDefinition("cash", "Cash", "USD"));
        definitions.put("gross_debt", new HistoryMetricDefinition("gross_debt", "Gross Debt", "USD"));
        definitions.put("net_debt", new HistoryMetricDefinition("net_debt", "Net Debt", "USD"));
        definitions.put("net_debt_to_ebitda", new HistoryMetricDefinition("net_debt_to_ebitda", "Net Debt To EBITDA", "ratio"));
        definitions.put("current_ratio", new HistoryMetricDefinition("current_ratio", "Current Ratio", "ratio"));
        definitions.put("interest_coverage", new HistoryMetricDefinition("interest_coverage", "Interest Coverage", "ratio"));
        definitions.put("fcf_margin", new HistoryMetricDefinition("fcf_margin", "Free Cash Flow Margin", "ratio"));
        return Map.copyOf(definitions);
    }

    private static Map<String, MetricDefinitionData> createMetricDefinitions() {
        Map<String, MetricDefinitionData> definitions = new LinkedHashMap<>();
        definitions.put("dps_latest", metric("dps_latest", "Latest Dividend / Share", "USD/share", "currency",
                "overview", "Most recent annual dividend per share used by the overview snapshot."));
        definitions.put("dps_cagr_5y", metric("dps_cagr_5y", "Dividend CAGR (5Y)", "percent", "percent",
                "overview", "Five-year compound annual growth rate for annual dividend per share."));
        definitions.put("fcf_payout", metric("fcf_payout", "Free Cash Flow Payout Ratio", "percent", "percent",
                "overview", "Dividends paid divided by free cash flow."));
        definitions.put("uninterrupted_years", metric("uninterrupted_years", "Uninterrupted Years", "years", "count",
                "overview", "Consecutive annual periods with a non-zero dividend."));
        definitions.put("consecutive_raises", metric("consecutive_raises", "Consecutive Raises", "years", "count",
                "overview", "Consecutive annual periods in which dividend per share increased."));
        definitions.put("net_debt_to_ebitda", metric("net_debt_to_ebitda", "Net Debt To EBITDA", "x", "multiple",
                "overview", "Net debt divided by EBITDA proxy."));
        definitions.put("interest_coverage", metric("interest_coverage", "Interest Coverage", "x", "multiple",
                "overview", "Operating income divided by interest expense."));
        definitions.put("current_ratio", metric("current_ratio", "Current Ratio", "x", "multiple",
                "overview", "Current assets divided by current liabilities."));
        definitions.put("fcf_margin", metric("fcf_margin", "Free Cash Flow Margin", "percent", "percent",
                "overview", "Free cash flow divided by revenue."));
        definitions.put("dividend_yield", metric("dividend_yield", "Dividend Yield", "percent", "percent",
                "overview", "Estimated dividend yield using stored market price."));
        definitions.put("score", metric("score", "Viability Score", "score", "score",
                "overview", "Composite dividend viability score on a 0-100 scale."));
        definitions.put("active_alerts", metric("active_alerts", "Active Alerts", "count", "count",
                "overview", "Number of currently active dividend pressure alerts."));

        HISTORY_METRIC_DEFINITIONS.values().forEach(definition -> definitions.put(
                definition.id(),
                metric(
                        definition.id(),
                        definition.label(),
                        switch (definition.id()) {
                            case "dps_declared", "eps_diluted" -> "USD/share";
                            case "revenue", "operating_cash_flow", "capital_expenditures", "free_cash_flow",
                                    "dividends_paid", "retained_cash", "cash", "gross_debt", "net_debt" -> "USD";
                            case "net_debt_to_ebitda", "interest_coverage", "current_ratio", "cash_coverage" -> "x";
                            default -> "percent";
                        },
                        switch (definition.id()) {
                            case "dps_declared", "eps_diluted" -> "currency";
                            case "revenue", "operating_cash_flow", "capital_expenditures", "free_cash_flow",
                                    "dividends_paid", "retained_cash", "cash", "gross_debt", "net_debt" -> "compact_currency";
                            case "net_debt_to_ebitda", "interest_coverage", "current_ratio", "cash_coverage" -> "multiple";
                            default -> "percent";
                        },
                        "history",
                        "Annual history metric returned by the dividend history endpoint.")));

        return Map.copyOf(definitions);
    }

    private static MetricDefinitionData metric(
            String id,
            String label,
            String unit,
            String formatHint,
            String group,
            String description) {
        return new MetricDefinitionData(id, label, unit, formatHint, group, description);
    }

    private DividendMetricDefinitionResponse toMetricDefinitionResponse(MetricDefinitionData definition) {
        return DividendMetricDefinitionResponse.builder()
                .id(definition.id())
                .label(definition.label())
                .unit(definition.unit())
                .formatHint(definition.formatHint())
                .group(definition.group())
                .description(definition.description())
                .build();
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record MetricDefinitionData(
            String id,
            String label,
            String unit,
            String formatHint,
            String group,
            String description) {
    }
}

package org.jds.edgar4j.service.dividend;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jds.edgar4j.dto.request.DividendScreenRequest;
import org.jds.edgar4j.dto.response.DividendOverviewResponse;
import org.jds.edgar4j.dto.response.DividendScreenResponse;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DividendScreeningService {

    public static final int DEFAULT_SCREEN_CANDIDATES = 100;

    public record ScreeningCandidate(
            DividendOverviewResponse.CompanySummary companySummary,
            List<DividendOverviewResponse.Alert> alerts,
            int score,
            DividendOverviewResponse.DividendRating rating,
            List<String> warnings,
            Map<String, Double> comparisonMetrics) {
    }

    public DividendScreenResponse.ScreenResult buildScreenResult(
            ScreeningCandidate candidate,
            List<String> requestedMetrics) {
        Map<String, Double> values = new LinkedHashMap<>();
        Map<String, Double> candidateValues = candidate == null || candidate.comparisonMetrics() == null
                ? Map.of()
                : candidate.comparisonMetrics();
        if (requestedMetrics != null) {
            for (String metric : requestedMetrics) {
                values.put(metric, candidateValues.get(metric));
            }
        }

        return DividendScreenResponse.ScreenResult.builder()
                .company(candidate != null ? candidate.companySummary() : null)
                .viability(DividendOverviewResponse.ViabilitySummary.builder()
                        .rating(candidate != null ? candidate.rating() : null)
                        .activeAlerts(candidate != null ? candidate.alerts().size() : 0)
                        .score(candidate != null ? candidate.score() : 0)
                        .build())
                .values(values)
                .warnings(candidate != null ? candidate.warnings() : List.of())
                .build();
    }

    public Comparator<DividendScreenResponse.ScreenResult> buildScreenComparator(
            String sort,
            String direction,
            Set<String> supportedMetrics) {
        String normalizedSort = sort != null ? sort.toLowerCase(Locale.ROOT) : "score";
        boolean descending = direction == null || !"asc".equalsIgnoreCase(direction);

        if ("name".equals(normalizedSort)) {
            return (left, right) -> compareNullableStrings(
                    firstNonBlank(left.getCompany().getName(), left.getCompany().getTicker(), left.getCompany().getCik()),
                    firstNonBlank(right.getCompany().getName(), right.getCompany().getTicker(), right.getCompany().getCik()),
                    descending);
        }

        if ("ticker".equals(normalizedSort)) {
            return (left, right) -> compareNullableStrings(
                    left.getCompany().getTicker(),
                    right.getCompany().getTicker(),
                    descending);
        }

        if (!supportedMetrics.contains(normalizedSort)) {
            String supported = String.join(", ", supportedMetrics);
            throw new IllegalArgumentException(
                    "Unsupported dividend screen sort field: " + sort
                            + ". Supported fields: " + supported + ", name, ticker");
        }

        return (left, right) -> compareNullableDoubles(
                left.getValues().get(normalizedSort),
                right.getValues().get(normalizedSort),
                descending);
    }

    public boolean matchesScreenFilters(
            ScreeningCandidate candidate,
            DividendScreenRequest.DividendScreenFilters filters,
            Set<String> supportedMetrics,
            Map<String, String> metricFormatHints) {
        if (candidate == null || filters == null) {
            return true;
        }

        if (filters.getViabilityRatings() != null
                && !filters.getViabilityRatings().isEmpty()
                && !filters.getViabilityRatings().contains(candidate.rating())) {
            return false;
        }

        if (filters.getSectors() != null && !filters.getSectors().isEmpty()) {
            String sector = blankToNull(candidate.companySummary() != null
                    ? candidate.companySummary().getSector()
                    : null);
            boolean sectorMatch = filters.getSectors().stream()
                    .map(this::blankToNull)
                    .filter(Objects::nonNull)
                    .anyMatch(candidateSector -> sector != null && sector.equalsIgnoreCase(candidateSector));
            if (!sectorMatch) {
                return false;
            }
        }

        if (filters.getMetrics() == null || filters.getMetrics().isEmpty()) {
            return true;
        }

        for (Map.Entry<String, DividendScreenRequest.MetricRange> entry : filters.getMetrics().entrySet()) {
            String metric = blankToNull(entry.getKey());
            if (metric == null) {
                continue;
            }

            String normalizedMetric = metric.toLowerCase(Locale.ROOT);
            if (!supportedMetrics.contains(normalizedMetric)) {
                throw new IllegalArgumentException(
                        "Unsupported dividend screen metric: " + metric
                                + ". Supported metrics: " + String.join(", ", supportedMetrics));
            }

            Double actualValue = candidate.comparisonMetrics() != null
                    ? candidate.comparisonMetrics().get(normalizedMetric)
                    : null;
            Double minValue = normalizeScreenBound(
                    normalizedMetric,
                    entry.getValue() != null ? entry.getValue().getMin() : null,
                    metricFormatHints);
            Double maxValue = normalizeScreenBound(
                    normalizedMetric,
                    entry.getValue() != null ? entry.getValue().getMax() : null,
                    metricFormatHints);

            if (minValue != null && (actualValue == null || actualValue < minValue)) {
                return false;
            }
            if (maxValue != null && (actualValue == null || actualValue > maxValue)) {
                return false;
            }
        }

        return true;
    }

    private Double normalizeScreenBound(String metric, Double rawValue, Map<String, String> metricFormatHints) {
        if (rawValue == null) {
            return null;
        }

        String formatHint = metricFormatHints != null ? metricFormatHints.get(metric) : null;
        if (formatHint != null
                && "percent".equalsIgnoreCase(formatHint)
                && Math.abs(rawValue) > 1d) {
            return rawValue / 100d;
        }
        return rawValue;
    }

    private int compareNullableStrings(String left, String right, boolean descending) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        int comparison = String.CASE_INSENSITIVE_ORDER.compare(left, right);
        return descending ? -comparison : comparison;
    }

    private int compareNullableDoubles(Double left, Double right, boolean descending) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        int comparison = Double.compare(left, right);
        return descending ? -comparison : comparison;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return null;
    }
}

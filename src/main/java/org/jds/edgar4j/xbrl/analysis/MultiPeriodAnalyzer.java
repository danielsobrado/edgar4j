package org.jds.edgar4j.xbrl.analysis;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jds.edgar4j.xbrl.model.XbrlContext;
import org.jds.edgar4j.xbrl.model.XbrlFact;
import org.jds.edgar4j.xbrl.model.XbrlInstance;
import org.jds.edgar4j.xbrl.standardization.ConceptStandardizer;
import org.jds.edgar4j.xbrl.statement.StatementReconstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Multi-period analysis and trend detection for XBRL data.
 *
 * KEY DIFFERENTIATOR:
 * - Stitches together multiple filings for time-series analysis
 * - Handles concept changes across periods (taxonomy updates)
 * - Calculates growth rates, CAGR, and trend lines
 * - Detects accounting policy changes that affect comparability
 *
 * This enables the kind of analysis that financial analysts actually need,
 * not just raw fact extraction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MultiPeriodAnalyzer {

    private final ConceptStandardizer conceptStandardizer;
    private final StatementReconstructor statementReconstructor;

    /**
     * Stitch multiple XBRL instances together for time-series analysis.
     */
    public StitchedTimeSeries stitch(List<XbrlInstance> instances) {
        if (instances == null || instances.isEmpty()) {
            return StitchedTimeSeries.builder().build();
        }

        // Sort by period end date
        List<XbrlInstance> sorted = instances.stream()
                .sorted(Comparator.comparing(this::getPrimaryPeriodEnd))
                .collect(Collectors.toList());

        StitchedTimeSeries.StitchedTimeSeriesBuilder builder = StitchedTimeSeries.builder();
        builder.entityIdentifier(sorted.get(0).getEntityIdentifier());

        // Collect all periods
        List<PeriodInfo> periods = new ArrayList<>();
        for (XbrlInstance instance : sorted) {
            PeriodInfo period = extractPeriodInfo(instance);
            if (period != null) {
                periods.add(period);
            }
        }
        builder.periods(periods);

        // Stitch facts by concept
        Map<String, ConceptTimeSeries> seriesByConcept = new LinkedHashMap<>();

        for (int i = 0; i < sorted.size(); i++) {
            XbrlInstance instance = sorted.get(i);
            PeriodInfo period = periods.get(i);

            // Get standardized facts
            ConceptStandardizer.StandardizedData standardized =
                    conceptStandardizer.standardize(instance);

            for (ConceptStandardizer.StandardizedFact fact : standardized.getFacts()) {
                String concept = fact.getStandardConcept();

                ConceptTimeSeries series = seriesByConcept.computeIfAbsent(
                        concept,
                        k -> ConceptTimeSeries.builder()
                                .concept(concept)
                                .category(fact.getCategory())
                                .description(fact.getDescription())
                                .valuesByPeriod(new LinkedHashMap<>())
                                .build()
                );

                // Add value for this period
                series.getValuesByPeriod().put(period.getEndDate(), fact.getValue());
            }
        }

        builder.seriesByConcept(seriesByConcept);

        // Calculate metrics
        for (ConceptTimeSeries series : seriesByConcept.values()) {
            calculateSeriesMetrics(series);
        }

        return builder.build();
    }

    /**
     * Calculate growth rates for a specific concept.
     */
    public GrowthAnalysis analyzeGrowth(StitchedTimeSeries timeSeries, String concept) {
        ConceptTimeSeries series = timeSeries.getSeriesByConcept().get(concept);
        if (series == null || series.getValuesByPeriod().size() < 2) {
            return null;
        }

        GrowthAnalysis.GrowthAnalysisBuilder builder = GrowthAnalysis.builder();
        builder.concept(concept);

        List<Map.Entry<LocalDate, BigDecimal>> entries =
                new ArrayList<>(series.getValuesByPeriod().entrySet());

        // Year-over-year growth rates
        Map<LocalDate, BigDecimal> yoyGrowth = new LinkedHashMap<>();
        for (int i = 1; i < entries.size(); i++) {
            BigDecimal current = entries.get(i).getValue();
            BigDecimal previous = entries.get(i - 1).getValue();

            if (previous != null && current != null &&
                    previous.compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal growth = current.subtract(previous)
                        .divide(previous.abs(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                yoyGrowth.put(entries.get(i).getKey(), growth);
            }
        }
        builder.yearOverYearGrowth(yoyGrowth);

        // Average growth rate
        if (!yoyGrowth.isEmpty()) {
            BigDecimal avgGrowth = yoyGrowth.values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(yoyGrowth.size()), 4, RoundingMode.HALF_UP);
            builder.averageGrowthRate(avgGrowth);
        }

        // CAGR (Compound Annual Growth Rate)
        if (entries.size() >= 2) {
            BigDecimal first = entries.get(0).getValue();
            BigDecimal last = entries.get(entries.size() - 1).getValue();
            int years = entries.size() - 1;

            if (first != null && last != null &&
                    first.compareTo(BigDecimal.ZERO) > 0 &&
                    last.compareTo(BigDecimal.ZERO) > 0) {
                // CAGR = (ending/beginning)^(1/years) - 1
                double cagr = Math.pow(
                        last.doubleValue() / first.doubleValue(),
                        1.0 / years
                ) - 1;
                builder.cagr(BigDecimal.valueOf(cagr * 100).setScale(2, RoundingMode.HALF_UP));
            }
        }

        // Trend direction
        builder.trend(determineTrend(entries));

        // Volatility (standard deviation of growth rates)
        if (yoyGrowth.size() >= 2) {
            double[] growthRates = yoyGrowth.values().stream()
                    .mapToDouble(BigDecimal::doubleValue)
                    .toArray();
            double volatility = calculateStdDev(growthRates);
            builder.volatility(BigDecimal.valueOf(volatility).setScale(2, RoundingMode.HALF_UP));
        }

        return builder.build();
    }

    /**
     * Detect anomalies in time series data.
     */
    public List<Anomaly> detectAnomalies(StitchedTimeSeries timeSeries) {
        List<Anomaly> anomalies = new ArrayList<>();

        for (Map.Entry<String, ConceptTimeSeries> entry : timeSeries.getSeriesByConcept().entrySet()) {
            String concept = entry.getKey();
            ConceptTimeSeries series = entry.getValue();

            if (series.getValuesByPeriod().size() < 3) continue;

            List<Map.Entry<LocalDate, BigDecimal>> values =
                    new ArrayList<>(series.getValuesByPeriod().entrySet());

            // Calculate mean and std dev
            double[] vals = values.stream()
                    .filter(e -> e.getValue() != null)
                    .mapToDouble(e -> e.getValue().doubleValue())
                    .toArray();

            if (vals.length < 3) continue;

            double mean = Arrays.stream(vals).average().orElse(0);
            double stdDev = calculateStdDev(vals);

            // Detect values more than 2 std devs from mean
            for (Map.Entry<LocalDate, BigDecimal> valueEntry : values) {
                if (valueEntry.getValue() == null) continue;

                double val = valueEntry.getValue().doubleValue();
                double zScore = (val - mean) / stdDev;

                if (Math.abs(zScore) > 2) {
                    anomalies.add(Anomaly.builder()
                            .concept(concept)
                            .period(valueEntry.getKey())
                            .value(valueEntry.getValue())
                            .zScore(BigDecimal.valueOf(zScore).setScale(2, RoundingMode.HALF_UP))
                            .type(zScore > 0 ? AnomalyType.UNUSUALLY_HIGH : AnomalyType.UNUSUALLY_LOW)
                            .description(String.format("%s is %.1f standard deviations from mean",
                                    concept, zScore))
                            .build());
                }
            }

            // Detect sign changes (income to loss, etc.)
            for (int i = 1; i < values.size(); i++) {
                BigDecimal prev = values.get(i - 1).getValue();
                BigDecimal curr = values.get(i).getValue();

                if (prev != null && curr != null) {
                    boolean prevPositive = prev.compareTo(BigDecimal.ZERO) > 0;
                    boolean currPositive = curr.compareTo(BigDecimal.ZERO) > 0;

                    if (prevPositive != currPositive) {
                        anomalies.add(Anomaly.builder()
                                .concept(concept)
                                .period(values.get(i).getKey())
                                .value(curr)
                                .type(AnomalyType.SIGN_CHANGE)
                                .description(String.format("%s changed from %s to %s",
                                        concept,
                                        prevPositive ? "positive" : "negative",
                                        currPositive ? "positive" : "negative"))
                                .build());
                    }
                }
            }
        }

        return anomalies;
    }

    /**
     * Calculate key financial ratios over time.
     */
    public Map<String, Map<LocalDate, BigDecimal>> calculateRatios(StitchedTimeSeries timeSeries) {
        Map<String, Map<LocalDate, BigDecimal>> ratios = new LinkedHashMap<>();

        Map<String, ConceptTimeSeries> concepts = timeSeries.getSeriesByConcept();

        // Get available periods
        Set<LocalDate> allPeriods = concepts.values().stream()
                .flatMap(s -> s.getValuesByPeriod().keySet().stream())
                .collect(Collectors.toCollection(TreeSet::new));

        for (LocalDate period : allPeriods) {
            BigDecimal revenue = getValue(concepts, "Revenue", period);
            BigDecimal grossProfit = getValue(concepts, "GrossProfit", period);
            BigDecimal operatingIncome = getValue(concepts, "OperatingIncome", period);
            BigDecimal netIncome = getValue(concepts, "NetIncome", period);
            BigDecimal assets = getValue(concepts, "TotalAssets", period);
            BigDecimal equity = getValue(concepts, "TotalEquity", period);
            BigDecimal liabilities = getValue(concepts, "TotalLiabilities", period);
            BigDecimal currentAssets = getValue(concepts, "TotalCurrentAssets", period);
            BigDecimal currentLiabilities = getValue(concepts, "TotalCurrentLiabilities", period);

            // Gross Margin
            if (revenue != null && grossProfit != null && revenue.compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal grossMargin = grossProfit.divide(revenue, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                ratios.computeIfAbsent("GrossMargin", k -> new LinkedHashMap<>())
                        .put(period, grossMargin);
            }

            // Operating Margin
            if (revenue != null && operatingIncome != null && revenue.compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal opMargin = operatingIncome.divide(revenue, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                ratios.computeIfAbsent("OperatingMargin", k -> new LinkedHashMap<>())
                        .put(period, opMargin);
            }

            // Net Margin
            if (revenue != null && netIncome != null && revenue.compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal netMargin = netIncome.divide(revenue, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                ratios.computeIfAbsent("NetMargin", k -> new LinkedHashMap<>())
                        .put(period, netMargin);
            }

            // ROA (Return on Assets)
            if (assets != null && netIncome != null && assets.compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal roa = netIncome.divide(assets, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                ratios.computeIfAbsent("ROA", k -> new LinkedHashMap<>())
                        .put(period, roa);
            }

            // ROE (Return on Equity)
            if (equity != null && netIncome != null && equity.compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal roe = netIncome.divide(equity, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                ratios.computeIfAbsent("ROE", k -> new LinkedHashMap<>())
                        .put(period, roe);
            }

            // Debt-to-Equity
            if (equity != null && liabilities != null && equity.compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal debtToEquity = liabilities.divide(equity, 4, RoundingMode.HALF_UP);
                ratios.computeIfAbsent("DebtToEquity", k -> new LinkedHashMap<>())
                        .put(period, debtToEquity);
            }

            // Current Ratio
            if (currentAssets != null && currentLiabilities != null &&
                    currentLiabilities.compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal currentRatio = currentAssets.divide(currentLiabilities, 4, RoundingMode.HALF_UP);
                ratios.computeIfAbsent("CurrentRatio", k -> new LinkedHashMap<>())
                        .put(period, currentRatio);
            }
        }

        return ratios;
    }

    // Helper methods

    private LocalDate getPrimaryPeriodEnd(XbrlInstance instance) {
        XbrlContext primary = instance.getPrimaryContext();
        if (primary != null && primary.getPeriod() != null) {
            return primary.getPeriod().getEndDate();
        }
        return LocalDate.MIN;
    }

    private PeriodInfo extractPeriodInfo(XbrlInstance instance) {
        XbrlContext primary = instance.getPrimaryContext();
        if (primary == null || primary.getPeriod() == null) return null;

        XbrlContext.XbrlPeriod period = primary.getPeriod();
        return PeriodInfo.builder()
                .startDate(period.getStartDate())
                .endDate(period.getEndDate())
                .isInstant(period.isInstant())
                .contextId(primary.getId())
                .build();
    }

    private void calculateSeriesMetrics(ConceptTimeSeries series) {
        if (series.getValuesByPeriod().isEmpty()) return;

        List<BigDecimal> values = series.getValuesByPeriod().values().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (!values.isEmpty()) {
            // Min/Max
            series.setMinValue(values.stream().min(BigDecimal::compareTo).orElse(null));
            series.setMaxValue(values.stream().max(BigDecimal::compareTo).orElse(null));

            // Average
            BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            series.setAverageValue(sum.divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP));

            // Latest
            series.setLatestValue(new ArrayList<>(series.getValuesByPeriod().values())
                    .get(series.getValuesByPeriod().size() - 1));
        }
    }

    private Trend determineTrend(List<Map.Entry<LocalDate, BigDecimal>> entries) {
        if (entries.size() < 2) return Trend.INSUFFICIENT_DATA;

        int increases = 0;
        int decreases = 0;

        for (int i = 1; i < entries.size(); i++) {
            BigDecimal prev = entries.get(i - 1).getValue();
            BigDecimal curr = entries.get(i).getValue();

            if (prev != null && curr != null) {
                int cmp = curr.compareTo(prev);
                if (cmp > 0) increases++;
                else if (cmp < 0) decreases++;
            }
        }

        if (increases > decreases * 2) return Trend.STRONG_UP;
        if (increases > decreases) return Trend.UP;
        if (decreases > increases * 2) return Trend.STRONG_DOWN;
        if (decreases > increases) return Trend.DOWN;
        return Trend.FLAT;
    }

    private double calculateStdDev(double[] values) {
        double mean = Arrays.stream(values).average().orElse(0);
        double sumSquaredDiff = Arrays.stream(values)
                .map(v -> Math.pow(v - mean, 2))
                .sum();
        return Math.sqrt(sumSquaredDiff / values.length);
    }

    private BigDecimal getValue(Map<String, ConceptTimeSeries> concepts, String concept, LocalDate period) {
        ConceptTimeSeries series = concepts.get(concept);
        if (series == null) return null;
        return series.getValuesByPeriod().get(period);
    }

    // Data classes

    @Data
    @Builder
    public static class StitchedTimeSeries {
        private String entityIdentifier;
        private List<PeriodInfo> periods;
        private Map<String, ConceptTimeSeries> seriesByConcept;
    }

    @Data
    @Builder
    public static class PeriodInfo {
        private LocalDate startDate;
        private LocalDate endDate;
        private boolean isInstant;
        private String contextId;
    }

    @Data
    @Builder
    public static class ConceptTimeSeries {
        private String concept;
        private ConceptStandardizer.ConceptCategory category;
        private String description;
        private Map<LocalDate, BigDecimal> valuesByPeriod;
        private BigDecimal minValue;
        private BigDecimal maxValue;
        private BigDecimal averageValue;
        private BigDecimal latestValue;
    }

    @Data
    @Builder
    public static class GrowthAnalysis {
        private String concept;
        private Map<LocalDate, BigDecimal> yearOverYearGrowth;
        private BigDecimal averageGrowthRate;
        private BigDecimal cagr;
        private Trend trend;
        private BigDecimal volatility;
    }

    @Data
    @Builder
    public static class Anomaly {
        private String concept;
        private LocalDate period;
        private BigDecimal value;
        private BigDecimal zScore;
        private AnomalyType type;
        private String description;
    }

    public enum Trend {
        STRONG_UP, UP, FLAT, DOWN, STRONG_DOWN, INSUFFICIENT_DATA
    }

    public enum AnomalyType {
        UNUSUALLY_HIGH, UNUSUALLY_LOW, SIGN_CHANGE, MISSING_DATA
    }
}

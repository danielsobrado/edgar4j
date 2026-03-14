package org.jds.edgar4j.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DividendHistoryResponse {

    private DividendOverviewResponse.CompanySummary company;
    private String period;
    private int yearsRequested;
    private List<String> metrics;
    private List<MetricSeries> series;
    private List<HistoryRow> rows;
    private List<String> warnings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricSeries {
        private String metric;
        private String label;
        private String unit;
        private Double latestValue;
        private Double cagr;
        private Double volatility;
        private TrendDirection trend;
        private List<MetricPoint> points;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricPoint {
        private LocalDate periodEnd;
        private LocalDate filingDate;
        private String accessionNumber;
        private Double value;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoryRow {
        private LocalDate periodEnd;
        private LocalDate filingDate;
        private String accessionNumber;
        private Map<String, Double> metrics;
    }

    public enum TrendDirection {
        UP,
        FLAT,
        DOWN,
        VOLATILE,
        INSUFFICIENT_DATA
    }
}

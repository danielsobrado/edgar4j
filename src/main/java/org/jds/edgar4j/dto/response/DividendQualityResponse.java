package org.jds.edgar4j.dto.response;

import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DividendQualityResponse {

    private DividendOverviewResponse.CompanySummary company;
    private QualityStatus overallStatus;
    private boolean benchmarkAvailable;
    private Integer benchmarkFiscalYear;
    private List<QualityIssue> issues;
    private List<BenchmarkCheck> benchmarks;
    private List<String> warnings;

    public enum QualityStatus {
        PASS,
        WARN,
        FAIL
    }

    public enum IssueSeverity {
        LOW,
        MEDIUM,
        HIGH
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QualityIssue {
        private IssueSeverity severity;
        private String code;
        private String message;
        private String metricId;
        private LocalDate periodEnd;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BenchmarkCheck {
        private String metricId;
        private String label;
        private Double expectedValue;
        private Double actualValue;
        private Double tolerance;
        private boolean passed;
    }
}

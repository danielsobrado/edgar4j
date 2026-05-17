package org.jds.edgar4j.dto.response;

import java.time.Instant;
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
public class DividendOverviewResponse {

    private CompanySummary company;
    private ViabilitySummary viability;
    private Snapshot snapshot;
    private Map<String, MetricConfidence> confidence;
    private List<Alert> alerts;
    private Coverage coverage;
    private Balance balance;
    private List<TrendPoint> trend;
    private Evidence evidence;
    private Double referencePrice;
    private List<String> warnings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompanySummary {
        private String cik;
        private String ticker;
        private String name;
        private String sector;
        private String fiscalYearEnd;
        private LocalDate lastFilingDate;
        private Instant dataFreshness;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ViabilitySummary {
        private DividendRating rating;
        private int activeAlerts;
        private int score;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Snapshot {
        private Double dpsLatest;
        private Double dpsCagr5y;
        private Double fcfPayoutRatio;
        private Integer uninterruptedYears;
        private Integer consecutiveRaises;
        private Double netDebtToEbitda;
        private Double interestCoverage;
        private Double currentRatio;
        private Double fcfMargin;
        private Double dividendYield;
        private Double shareholderYield;
        private Double buybackYield;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Alert {
        private String id;
        private AlertSeverity severity;
        private String title;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Coverage {
        private Double revenue;
        private Double operatingCashFlow;
        private Double capitalExpenditures;
        private Double freeCashFlow;
        private Double dividendsPaid;
        private Double cashCoverage;
        private Double retainedCash;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Balance {
        private Double cash;
        private Double grossDebt;
        private Double netDebt;
        private Double ebitdaProxy;
        private Double netDebtToEbitda;
        private Double currentRatio;
        private Double interestCoverage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendPoint {
        private LocalDate periodEnd;
        private LocalDate filingDate;
        private String accessionNumber;
        private Double dividendsPerShare;
        private Double earningsPerShare;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Evidence {
        private SourceFiling latestAnnualReport;
        private SourceFiling latestCurrentReport;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceFiling {
        private String formType;
        private String accessionNumber;
        private LocalDate filingDate;
        private String url;
    }

    public enum DividendRating {
        SAFE,
        STABLE,
        WATCH,
        AT_RISK
    }

    public enum AlertSeverity {
        LOW,
        MEDIUM,
        HIGH
    }

    public enum MetricConfidence {
        HIGH,
        MEDIUM,
        LOW_MEDIUM,
        LOW
    }
}

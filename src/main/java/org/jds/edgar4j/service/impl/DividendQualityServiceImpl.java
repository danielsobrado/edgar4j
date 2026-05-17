package org.jds.edgar4j.service.impl;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.jds.edgar4j.dto.response.DividendHistoryResponse;
import org.jds.edgar4j.dto.response.DividendOverviewResponse;
import org.jds.edgar4j.dto.response.DividendQualityResponse;
import org.jds.edgar4j.service.DividendAnalysisService;
import org.jds.edgar4j.service.DividendQualityService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DividendQualityServiceImpl implements DividendQualityService {

    private static final List<String> QUALITY_HISTORY_METRICS = List.of(
            "dps_declared",
            "eps_diluted",
            "earnings_payout",
            "free_cash_flow",
            "dividends_paid",
            "fcf_payout");
    private static final Map<String, BenchmarkProfile> BENCHMARKS = createBenchmarks();

    private final DividendAnalysisService dividendAnalysisService;

    @Override
    public DividendQualityResponse assess(String tickerOrCik) {
        DividendOverviewResponse overview = dividendAnalysisService.getOverview(tickerOrCik);
        DividendHistoryResponse history = dividendAnalysisService.getHistory(
                tickerOrCik,
                QUALITY_HISTORY_METRICS,
                "FY",
                15);

        List<DividendQualityResponse.QualityIssue> issues = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (overview.getWarnings() != null) {
            warnings.addAll(overview.getWarnings());
        }
        if (history.getWarnings() != null) {
            warnings.addAll(history.getWarnings());
        }

        List<DividendHistoryResponse.HistoryRow> rows = history.getRows() != null ? history.getRows() : List.of();
        DividendHistoryResponse.HistoryRow latestRow = rows.isEmpty()
                ? null
                : rows.get(rows.size() - 1);

        checkAnnualHistoryDepth(history, issues);
        checkAnnualHistoryGaps(rows, issues);
        checkImpossibleMetricValues(rows, issues);
        checkSnapshotDpsConsistency(overview, latestRow, issues);
        checkFcfPayoutConsistency(latestRow, issues);
        checkEarningsPayoutConsistency(latestRow, issues);
        checkStaleness(overview, issues);

        List<DividendQualityResponse.BenchmarkCheck> benchmarks = buildBenchmarkChecks(
                overview.getCompany(),
                history,
                warnings);
        DividendQualityResponse.QualityStatus overallStatus = determineOverallStatus(issues, benchmarks);
        BenchmarkProfile benchmarkProfile = lookupBenchmark(overview.getCompany());

        return DividendQualityResponse.builder()
                .company(overview.getCompany())
                .overallStatus(overallStatus)
                .benchmarkAvailable(benchmarkProfile != null)
                .benchmarkFiscalYear(benchmarkProfile != null ? benchmarkProfile.fiscalYear() : null)
                .issues(issues)
                .benchmarks(benchmarks)
                .warnings(warnings.stream().distinct().toList())
                .build();
    }

    private void checkAnnualHistoryDepth(
            DividendHistoryResponse history,
            List<DividendQualityResponse.QualityIssue> issues) {
        List<DividendHistoryResponse.HistoryRow> rows = history.getRows() != null ? history.getRows() : List.of();
        if (rows.size() < 3) {
            issues.add(issue(
                    DividendQualityResponse.IssueSeverity.MEDIUM,
                    "history_depth",
                    "Less than three annual history rows are available for validation.",
                    null,
                    null));
        }
    }

    private void checkAnnualHistoryGaps(
            List<DividendHistoryResponse.HistoryRow> rows,
            List<DividendQualityResponse.QualityIssue> issues) {
        List<LocalDate> periodEnds = rows.stream()
                .filter(Objects::nonNull)
                .map(DividendHistoryResponse.HistoryRow::getPeriodEnd)
                .filter(Objects::nonNull)
                .sorted()
                .toList();

        for (int index = 1; index < periodEnds.size(); index++) {
            LocalDate previous = periodEnds.get(index - 1);
            LocalDate current = periodEnds.get(index);
            if (current.getYear() - previous.getYear() > 1) {
                issues.add(issue(
                        DividendQualityResponse.IssueSeverity.MEDIUM,
                        "annual_history_gap",
                        String.format(Locale.ROOT,
                                "Annual history has a gap between fiscal years %d and %d.",
                                previous.getYear(),
                                current.getYear()),
                        null,
                        current));
            }
        }
    }

    private void checkImpossibleMetricValues(
            List<DividendHistoryResponse.HistoryRow> rows,
            List<DividendQualityResponse.QualityIssue> issues) {
        for (DividendHistoryResponse.HistoryRow row : rows) {
            if (row == null || row.getMetrics() == null) {
                continue;
            }

            Map<String, Double> metrics = row.getMetrics();
            LocalDate periodEnd = row.getPeriodEnd();
            Double dps = metrics.get("dps_declared");
            Double eps = metrics.get("eps_diluted");
            Double earningsPayout = metrics.get("earnings_payout");
            Double freeCashFlow = metrics.get("free_cash_flow");
            Double dividendsPaid = metrics.get("dividends_paid");
            Double fcfPayout = metrics.get("fcf_payout");

            if (dps != null && dps < 0d) {
                issues.add(issue(
                        DividendQualityResponse.IssueSeverity.HIGH,
                        "negative_dps",
                        "Dividend per share is negative, which is not a valid cash dividend history value.",
                        "dps_declared",
                        periodEnd));
            }
            if (dividendsPaid != null && dividendsPaid < 0d) {
                issues.add(issue(
                        DividendQualityResponse.IssueSeverity.MEDIUM,
                        "negative_dividends_paid",
                        "Dividends paid is negative after normalization.",
                        "dividends_paid",
                        periodEnd));
            }
            if (freeCashFlow != null && freeCashFlow <= 0d && dividendsPaid != null && dividendsPaid > 0d) {
                issues.add(issue(
                        DividendQualityResponse.IssueSeverity.HIGH,
                        "dividends_without_positive_fcf",
                        "Dividends were paid while free cash flow was zero or negative.",
                        "free_cash_flow",
                        periodEnd));
            }
            if (freeCashFlow != null && freeCashFlow <= 0d && fcfPayout != null) {
                issues.add(issue(
                        DividendQualityResponse.IssueSeverity.MEDIUM,
                        "fcf_payout_with_nonpositive_fcf",
                        "FCF payout ratio is defined even though free cash flow is zero or negative.",
                        "fcf_payout",
                        periodEnd));
            }
            if (eps != null && eps <= 0d && earningsPayout != null) {
                issues.add(issue(
                        DividendQualityResponse.IssueSeverity.MEDIUM,
                        "earnings_payout_with_nonpositive_eps",
                        "Earnings payout ratio is defined even though diluted EPS is zero or negative.",
                        "earnings_payout",
                        periodEnd));
            }
            if (fcfPayout != null && (fcfPayout < 0d || fcfPayout > 2d)) {
                issues.add(issue(
                        fcfPayout < 0d ? DividendQualityResponse.IssueSeverity.HIGH : DividendQualityResponse.IssueSeverity.MEDIUM,
                        "fcf_payout_out_of_bounds",
                        "FCF payout ratio is outside expected bounds for dividend analysis.",
                        "fcf_payout",
                        periodEnd));
            }
            if (earningsPayout != null && (earningsPayout < 0d || earningsPayout > 2d)) {
                issues.add(issue(
                        earningsPayout < 0d ? DividendQualityResponse.IssueSeverity.HIGH : DividendQualityResponse.IssueSeverity.MEDIUM,
                        "earnings_payout_out_of_bounds",
                        "Earnings payout ratio is outside expected bounds for dividend analysis.",
                        "earnings_payout",
                        periodEnd));
            }
        }
    }

    private void checkSnapshotDpsConsistency(
            DividendOverviewResponse overview,
            DividendHistoryResponse.HistoryRow latestRow,
            List<DividendQualityResponse.QualityIssue> issues) {
        Double snapshotDps = overview.getSnapshot() != null ? overview.getSnapshot().getDpsLatest() : null;
        Double historyDps = latestRow != null ? latestRow.getMetrics().get("dps_declared") : null;
        if (snapshotDps != null && historyDps != null && Math.abs(snapshotDps - historyDps) > 0.02d) {
            issues.add(issue(
                    DividendQualityResponse.IssueSeverity.MEDIUM,
                    "snapshot_dps_mismatch",
                    String.format(Locale.ROOT,
                            "Snapshot DPS %.4f does not match latest history DPS %.4f.",
                            snapshotDps,
                            historyDps),
                    "dps_declared",
                    latestRow.getPeriodEnd()));
        }
    }

    private void checkFcfPayoutConsistency(
            DividendHistoryResponse.HistoryRow latestRow,
            List<DividendQualityResponse.QualityIssue> issues) {
        if (latestRow == null) {
            return;
        }
        Double freeCashFlow = latestRow.getMetrics().get("free_cash_flow");
        Double dividendsPaid = latestRow.getMetrics().get("dividends_paid");
        Double fcfPayout = latestRow.getMetrics().get("fcf_payout");
        if (freeCashFlow == null || dividendsPaid == null || fcfPayout == null || freeCashFlow <= 0d) {
            return;
        }

        double impliedPayout = dividendsPaid / freeCashFlow;
        if (Math.abs(impliedPayout - fcfPayout) > 0.05d) {
            issues.add(issue(
                    DividendQualityResponse.IssueSeverity.HIGH,
                    "fcf_payout_inconsistent",
                    String.format(Locale.ROOT,
                            "Implied FCF payout %.4f differs from stored ratio %.4f.",
                            impliedPayout,
                            fcfPayout),
                    "fcf_payout",
                    latestRow.getPeriodEnd()));
        }
    }

    private void checkEarningsPayoutConsistency(
            DividendHistoryResponse.HistoryRow latestRow,
            List<DividendQualityResponse.QualityIssue> issues) {
        if (latestRow == null) {
            return;
        }
        Double dps = latestRow.getMetrics().get("dps_declared");
        Double eps = latestRow.getMetrics().get("eps_diluted");
        Double earningsPayout = latestRow.getMetrics().get("earnings_payout");
        if (dps == null || eps == null || earningsPayout == null || eps <= 0d) {
            return;
        }

        double impliedPayout = dps / eps;
        if (Math.abs(impliedPayout - earningsPayout) > 0.05d) {
            issues.add(issue(
                    DividendQualityResponse.IssueSeverity.MEDIUM,
                    "earnings_payout_inconsistent",
                    String.format(Locale.ROOT,
                            "Implied earnings payout %.4f differs from stored ratio %.4f.",
                            impliedPayout,
                            earningsPayout),
                    "earnings_payout",
                    latestRow.getPeriodEnd()));
        }
    }

    private void checkStaleness(
            DividendOverviewResponse overview,
            List<DividendQualityResponse.QualityIssue> issues) {
        LocalDate lastFilingDate = overview.getCompany() != null ? overview.getCompany().getLastFilingDate() : null;
        if (lastFilingDate == null) {
            issues.add(issue(
                    DividendQualityResponse.IssueSeverity.MEDIUM,
                    "missing_last_filing_date",
                    "The latest filing date is unavailable, so freshness could not be verified.",
                    null,
                    null));
            return;
        }

        long ageDays = ChronoUnit.DAYS.between(lastFilingDate, LocalDate.now());
        if (ageDays > 430) {
            issues.add(issue(
                    DividendQualityResponse.IssueSeverity.HIGH,
                    "stale_filings",
                    "The most recent filing is older than 14 months.",
                    null,
                    lastFilingDate));
        } else if (ageDays > 370) {
            issues.add(issue(
                    DividendQualityResponse.IssueSeverity.MEDIUM,
                    "aging_filings",
                    "The most recent filing is older than 12 months.",
                    null,
                    lastFilingDate));
        }
    }

    private List<DividendQualityResponse.BenchmarkCheck> buildBenchmarkChecks(
            DividendOverviewResponse.CompanySummary company,
            DividendHistoryResponse history,
            List<String> warnings) {
        BenchmarkProfile profile = lookupBenchmark(company);
        if (profile == null) {
            warnings.add("No curated dividend benchmark is available for this company yet.");
            return List.of();
        }

        List<DividendHistoryResponse.HistoryRow> rows = history.getRows() != null ? history.getRows() : List.of();
        DividendHistoryResponse.HistoryRow benchmarkRow = rows.stream()
                .filter(Objects::nonNull)
                .filter(row -> row.getPeriodEnd() != null && row.getPeriodEnd().getYear() == profile.fiscalYear())
                .findFirst()
                .orElse(null);
        if (benchmarkRow == null) {
            warnings.add("Benchmark comparison could not find the expected fiscal year in the annual history response.");
            return List.of();
        }

        List<DividendQualityResponse.BenchmarkCheck> results = new ArrayList<>();
        for (Map.Entry<String, BenchmarkValue> entry : profile.metrics().entrySet()) {
            Double actualValue = benchmarkRow.getMetrics().get(entry.getKey());
            BenchmarkValue expected = entry.getValue();
            boolean passed = actualValue != null && Math.abs(actualValue - expected.value()) <= expected.tolerance();

            results.add(DividendQualityResponse.BenchmarkCheck.builder()
                    .metricId(entry.getKey())
                    .label(expected.label())
                    .expectedValue(expected.value())
                    .actualValue(actualValue)
                    .tolerance(expected.tolerance())
                    .passed(passed)
                    .build());
        }
        return results;
    }

    private DividendQualityResponse.QualityStatus determineOverallStatus(
            List<DividendQualityResponse.QualityIssue> issues,
            List<DividendQualityResponse.BenchmarkCheck> benchmarks) {
        boolean hasHighIssue = issues.stream()
                .anyMatch(issue -> issue.getSeverity() == DividendQualityResponse.IssueSeverity.HIGH);
        boolean hasFailedBenchmark = benchmarks.stream().anyMatch(check -> !check.isPassed());
        if (hasHighIssue || hasFailedBenchmark) {
            return DividendQualityResponse.QualityStatus.FAIL;
        }
        if (!issues.isEmpty()) {
            return DividendQualityResponse.QualityStatus.WARN;
        }
        return DividendQualityResponse.QualityStatus.PASS;
    }

    private BenchmarkProfile lookupBenchmark(DividendOverviewResponse.CompanySummary company) {
        if (company == null) {
            return null;
        }
        String tickerKey = normalizeKey(company.getTicker()).orElse(null);
        if (tickerKey != null && BENCHMARKS.containsKey(tickerKey)) {
            return BENCHMARKS.get(tickerKey);
        }
        String cikKey = normalizeKey(company.getCik()).orElse(null);
        return cikKey != null ? BENCHMARKS.get(cikKey) : null;
    }

    private DividendQualityResponse.QualityIssue issue(
            DividendQualityResponse.IssueSeverity severity,
            String code,
            String message,
            String metricId,
            LocalDate periodEnd) {
        return DividendQualityResponse.QualityIssue.builder()
                .severity(severity)
                .code(code)
                .message(message)
                .metricId(metricId)
                .periodEnd(periodEnd)
                .build();
    }

    private Optional<String> normalizeKey(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.isEmpty() ? Optional.empty() : Optional.of(normalized);
    }

    private static Map<String, BenchmarkProfile> createBenchmarks() {
        Map<String, BenchmarkProfile> benchmarks = new LinkedHashMap<>();

        BenchmarkProfile apple = new BenchmarkProfile(
                2023,
                Map.of(
                        "dps_declared", new BenchmarkValue("Dividend Per Share", 0.96d, 0.10d),
                        "fcf_payout", new BenchmarkValue("Free Cash Flow Payout Ratio", 0.159d, 0.05d)));
        BenchmarkProfile jnj = new BenchmarkProfile(
                2023,
                Map.of(
                        "dps_declared", new BenchmarkValue("Dividend Per Share", 4.70d, 0.35d),
                        "fcf_payout", new BenchmarkValue("Free Cash Flow Payout Ratio", 0.468d, 0.08d)));
        BenchmarkProfile msft = new BenchmarkProfile(
                2023,
                Map.of(
                        "dps_declared", new BenchmarkValue("Dividend Per Share", 2.72d, 0.25d),
                        "fcf_payout", new BenchmarkValue("Free Cash Flow Payout Ratio", 0.251d, 0.08d)));

        benchmarks.put("AAPL", apple);
        benchmarks.put("0000320193", apple);
        benchmarks.put("JNJ", jnj);
        benchmarks.put("0000200406", jnj);
        benchmarks.put("MSFT", msft);
        benchmarks.put("0000789019", msft);
        return Map.copyOf(benchmarks);
    }

    private record BenchmarkProfile(int fiscalYear, Map<String, BenchmarkValue> metrics) {
    }

    private record BenchmarkValue(String label, Double value, Double tolerance) {
    }
}

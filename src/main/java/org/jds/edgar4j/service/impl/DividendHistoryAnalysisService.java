package org.jds.edgar4j.service.impl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jds.edgar4j.dto.response.DividendAlertsResponse;
import org.jds.edgar4j.dto.response.DividendHistoryResponse;
import org.jds.edgar4j.dto.response.DividendOverviewResponse;
import org.jds.edgar4j.service.dividend.DividendAlertsService;
import org.jds.edgar4j.service.dividend.DividendMetricsService;
import org.jds.edgar4j.service.impl.DividendFilingAnalysisService.AnalyzedFilingData;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DividendHistoryAnalysisService {

    private final DividendMetricsService dividendMetricsService;
    private final DividendAlertsService dividendAlertsService;
    private final DividendFilingAnalysisService dividendFilingAnalysisService;

    public List<HistoryRowData> limitHistoryRows(List<HistoryRowData> historyRows, int years) {
        if (historyRows == null || historyRows.size() <= years) {
            return historyRows != null ? historyRows : List.of();
        }
        return historyRows.subList(historyRows.size() - years, historyRows.size());
    }

    public List<HistoryRowData> buildHistoryRows(
            List<AnalyzedFilingData> annualAnalyses,
            List<DividendOverviewResponse.TrendPoint> trend) {
        Map<LocalDate, DividendOverviewResponse.TrendPoint> trendByPeriodEnd = new LinkedHashMap<>();
        for (DividendOverviewResponse.TrendPoint point : trend) {
            if (point.getPeriodEnd() != null) {
                trendByPeriodEnd.put(point.getPeriodEnd(), point);
            }
        }

        return annualAnalyses.stream()
                .sorted(Comparator.comparing(dividendFilingAnalysisService::resolveSortableAnalysisDate))
                .map(annual -> toHistoryRowData(annual, trendByPeriodEnd.get(dividendFilingAnalysisService.resolvePeriodEnd(annual))))
                .filter(Objects::nonNull)
                .toList();
    }

    public DividendHistoryResponse.MetricSeries buildMetricSeries(
            String metric,
            List<HistoryRowData> rows,
            Map<String, HistoryMetricDefinition> definitions) {
        HistoryMetricDefinition definition = definitions.get(metric);
        List<DividendHistoryResponse.MetricPoint> points = rows.stream()
                .map(row -> DividendHistoryResponse.MetricPoint.builder()
                        .periodEnd(row.periodEnd())
                        .filingDate(row.filingDate())
                        .accessionNumber(row.accessionNumber())
                        .value(getHistoryMetricValue(row, metric))
                        .build())
                .filter(point -> point.getValue() != null)
                .toList();

        Double latestValue = points.isEmpty() ? null : points.get(points.size() - 1).getValue();
        Double cagr = dividendMetricsService.calculateMetricCagr(points);
        Double volatility = dividendMetricsService.calculateMetricVolatility(points);

        return DividendHistoryResponse.MetricSeries.builder()
                .metric(definition.id())
                .label(definition.label())
                .unit(definition.unit())
                .latestValue(latestValue)
                .cagr(cagr)
                .volatility(volatility)
                .trend(dividendMetricsService.determineMetricTrend(points, volatility))
                .points(points)
                .build();
    }

    public DividendHistoryResponse.HistoryRow toHistoryRow(HistoryRowData row, List<String> metrics) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (String metric : metrics) {
            values.put(metric, getHistoryMetricValue(row, metric));
        }

        return DividendHistoryResponse.HistoryRow.builder()
                .periodEnd(row.periodEnd())
                .filingDate(row.filingDate())
                .accessionNumber(row.accessionNumber())
                .metrics(values)
                .build();
    }

    public Double getHistoryMetricValue(HistoryRowData row, String metric) {
        if (row == null) {
            return null;
        }

        return switch (metric) {
            case "dps_declared" -> row.dividendsPerShare();
            case "eps_diluted" -> row.earningsPerShare();
            case "earnings_payout" -> row.earningsPayoutRatio();
            case "revenue" -> row.revenue();
            case "operating_cash_flow" -> row.operatingCashFlow();
            case "capital_expenditures" -> row.capitalExpenditures();
            case "free_cash_flow" -> row.freeCashFlow();
            case "dividends_paid" -> row.dividendsPaid();
            case "fcf_payout" -> row.fcfPayoutRatio();
            case "cash_coverage" -> row.cashCoverage();
            case "retained_cash" -> row.retainedCash();
            case "cash" -> row.cash();
            case "gross_debt" -> row.grossDebt();
            case "net_debt" -> row.netDebt();
            case "net_debt_to_ebitda" -> row.netDebtToEbitda();
            case "current_ratio" -> row.currentRatio();
            case "interest_coverage" -> row.interestCoverage();
            case "fcf_margin" -> row.fcfMargin();
            default -> null;
        };
    }

    public HistoryRowData latestHistoryRow(List<HistoryRowData> historyRows) {
        if (historyRows == null || historyRows.isEmpty()) {
            return null;
        }
        return historyRows.get(historyRows.size() - 1);
    }

    public List<DividendAlertsResponse.AlertEvent> buildHistoricalAlerts(
            List<HistoryRowData> historyRows,
            List<DividendOverviewResponse.Alert> activeAlerts,
            AnalyzedFilingData latestBalance,
            AnalyzedFilingData latestAnnual,
            LocalDate fallbackFilingDate,
            boolean activeOnly) {
        List<AlertEventData> events = new ArrayList<>();
        Set<String> activeAlertIds = activeAlerts.stream()
                .map(DividendOverviewResponse.Alert::getId)
                .collect(java.util.stream.Collectors.toSet());

        HistoryRowData previous = null;
        for (HistoryRowData row : historyRows) {
            events.addAll(buildAlertEventsForRow(previous, row));
            previous = row;
        }

        LocalDate currentPeriodEnd = firstNonNull(
                dividendFilingAnalysisService.resolvePeriodEnd(latestBalance),
                dividendFilingAnalysisService.resolvePeriodEnd(latestAnnual),
                fallbackFilingDate);
        LocalDate currentFilingDate = firstNonNull(
                latestBalance != null ? dividendFilingAnalysisService.toLocalDate(latestBalance.filing().getFillingDate()) : null,
                latestAnnual != null ? dividendFilingAnalysisService.toLocalDate(latestAnnual.filing().getFillingDate()) : null,
                fallbackFilingDate);
        String currentAccessionNumber = firstNonBlank(
                latestBalance != null ? latestBalance.filing().getAccessionNumber() : null,
                latestAnnual != null ? latestAnnual.filing().getAccessionNumber() : null);

        for (DividendOverviewResponse.Alert alert : activeAlerts) {
            boolean present = events.stream()
                    .anyMatch(event -> Objects.equals(event.id(), alert.getId())
                            && Objects.equals(event.periodEnd(), currentPeriodEnd));
            if (!present) {
                events.add(new AlertEventData(
                        alert.getId(),
                        alert.getSeverity(),
                        alert.getTitle(),
                        alert.getDescription(),
                        currentPeriodEnd,
                        currentFilingDate,
                        currentAccessionNumber));
            }
        }

        List<DividendAlertsResponse.AlertEvent> response = events.stream()
                .map(event -> DividendAlertsResponse.AlertEvent.builder()
                        .id(event.id())
                        .severity(event.severity())
                        .title(event.title())
                        .description(event.description())
                        .periodEnd(event.periodEnd())
                        .filingDate(event.filingDate())
                        .accessionNumber(event.accessionNumber())
                        .active(activeAlertIds.contains(event.id()) && Objects.equals(event.periodEnd(), currentPeriodEnd))
                        .build())
                .sorted(Comparator.comparing(DividendAlertsResponse.AlertEvent::getPeriodEnd,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(DividendAlertsResponse.AlertEvent::getFilingDate,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(DividendAlertsResponse.AlertEvent::getId))
                .toList();

        return activeOnly
                ? response.stream().filter(DividendAlertsResponse.AlertEvent::isActive).toList()
                : response;
    }

    private HistoryRowData toHistoryRowData(
            AnalyzedFilingData annual,
            DividendOverviewResponse.TrendPoint trendPoint) {
        LocalDate periodEnd = dividendFilingAnalysisService.resolvePeriodEnd(annual);
        if (annual == null || periodEnd == null) {
            return null;
        }

        Double revenue = dividendFilingAnalysisService.getMetric(annual,
                List.of("Revenue"),
                List.of("Revenues", "RevenueFromContractWithCustomerExcludingAssessedTax"));
        Double operatingCashFlow = dividendFilingAnalysisService.getMetric(annual,
                List.of("OperatingCashFlow"),
                List.of("NetCashProvidedByUsedInOperatingActivities"));
        Double capitalExpenditures = dividendMetricsService.magnitude(dividendFilingAnalysisService.getMetric(annual,
                List.of("CapitalExpenditures"),
                List.of("PaymentsToAcquirePropertyPlantAndEquipment", "PaymentsToAcquireProductiveAssets")));
        Double dividendsPaid = dividendMetricsService.magnitude(dividendFilingAnalysisService.getMetric(annual,
                List.of("DividendsPaid", "PaymentsOfDividendsCommonStock"),
                List.of("PaymentsOfDividendsCommonStock", "DividendsCommonStockCash", "PaymentsOfOrdinaryDividends")));
        Double freeCashFlow = operatingCashFlow != null && capitalExpenditures != null
                ? operatingCashFlow - capitalExpenditures
                : null;
        Double dividendsPerShare = trendPoint != null && trendPoint.getDividendsPerShare() != null
                ? trendPoint.getDividendsPerShare()
                : dividendFilingAnalysisService.getDividendsPerShare(annual);
        Double earningsPerShare = trendPoint != null && trendPoint.getEarningsPerShare() != null
                ? trendPoint.getEarningsPerShare()
                : dividendFilingAnalysisService.getMetric(annual, List.of("EarningsPerShareDiluted"), List.of("EarningsPerShareDiluted"));
        Double earningsPayoutRatio = earningsPerShare != null && earningsPerShare > 0d && dividendsPerShare != null
                ? dividendMetricsService.safeDivide(dividendsPerShare, earningsPerShare)
                : null;
        Double fcfPayoutRatio = freeCashFlow != null && freeCashFlow > 0d && dividendsPaid != null
                ? dividendMetricsService.safeDivide(dividendsPaid, freeCashFlow)
                : null;
        Double cashCoverage = dividendMetricsService.safeDivide(freeCashFlow, dividendsPaid);
        Double retainedCash = freeCashFlow != null && dividendsPaid != null ? freeCashFlow - dividendsPaid : null;
        Double cash = dividendFilingAnalysisService.getMetric(annual,
                List.of("Cash"),
                List.of("CashAndCashEquivalentsAtCarryingValue"));
        Double longTermDebt = dividendMetricsService.magnitude(dividendFilingAnalysisService.getMetric(annual,
                List.of("LongTermDebt"),
                List.of("LongTermDebt")));
        Double shortTermDebt = dividendMetricsService.magnitude(dividendFilingAnalysisService.getMetric(annual,
                List.of("DebtCurrent", "ShortTermDebt"),
                List.of("DebtCurrent", "LongTermDebtCurrent", "ShortTermBorrowings")));
        Double grossDebt = longTermDebt == null && shortTermDebt == null
                ? null
                : dividendMetricsService.defaultIfNull(longTermDebt) + dividendMetricsService.defaultIfNull(shortTermDebt);
        Double netDebt = grossDebt != null && cash != null ? grossDebt - cash : null;
        Double operatingIncome = dividendFilingAnalysisService.getMetric(annual,
                List.of("OperatingIncome"),
                List.of("OperatingIncomeLoss"));
        Double depreciationAmortization = dividendMetricsService.magnitude(dividendFilingAnalysisService.getMetric(annual,
                List.of("DepreciationAmortization"),
                List.of("DepreciationDepletionAndAmortization", "DepreciationAndAmortization")));
        Double ebitdaProxy = operatingIncome != null && depreciationAmortization != null
                ? operatingIncome + depreciationAmortization
                : null;
        Double interestExpense = dividendMetricsService.magnitude(dividendFilingAnalysisService.getMetric(annual,
                List.of("InterestExpense"),
                List.of("InterestExpense", "InterestExpenseDebt")));
        Double netDebtToEbitda = ebitdaProxy != null && ebitdaProxy > 0d ? dividendMetricsService.safeDivide(netDebt, ebitdaProxy) : null;
        Double currentAssets = dividendFilingAnalysisService.getMetric(annual,
                List.of("TotalCurrentAssets"),
                List.of("AssetsCurrent"));
        Double currentLiabilities = dividendFilingAnalysisService.getMetric(annual,
                List.of("TotalCurrentLiabilities"),
                List.of("LiabilitiesCurrent"));
        Double currentRatio = dividendMetricsService.safeDivide(currentAssets, currentLiabilities);
        Double interestCoverage = dividendMetricsService.safeDivide(operatingIncome, interestExpense);
        Double fcfMargin = dividendMetricsService.safeDivide(freeCashFlow, revenue);

        return new HistoryRowData(
                periodEnd,
                dividendFilingAnalysisService.toLocalDate(annual.filing().getFillingDate()),
                annual.filing().getAccessionNumber(),
                dividendsPerShare,
                earningsPerShare,
                earningsPayoutRatio,
                revenue,
                operatingCashFlow,
                capitalExpenditures,
                freeCashFlow,
                dividendsPaid,
                fcfPayoutRatio,
                cashCoverage,
                retainedCash,
                cash,
                grossDebt,
                netDebt,
                ebitdaProxy,
                netDebtToEbitda,
                currentRatio,
                interestCoverage,
                fcfMargin);
    }

    private List<AlertEventData> buildAlertEventsForRow(HistoryRowData previous, HistoryRowData current) {
        if (current == null) {
            return List.of();
        }

        List<AlertEventData> events = new ArrayList<>();

        if (previous != null
                && current.dividendsPerShare() != null
                && previous.dividendsPerShare() != null
                && previous.dividendsPerShare() > 0d
                && current.dividendsPerShare() < previous.dividendsPerShare()) {
            events.add(toAlertEventData(dividendAlertsService.alert(
                    "dividend-cut",
                    DividendOverviewResponse.AlertSeverity.HIGH,
                    "Dividend cut detected",
                    "The latest annual dividend-per-share value is below the prior year."),
                    current));
        }

        if (current.fcfPayoutRatio() != null && current.fcfPayoutRatio() > 0.85d) {
            events.add(toAlertEventData(dividendAlertsService.alert(
                    "fcf-payout",
                    current.fcfPayoutRatio() > 1d
                            ? DividendOverviewResponse.AlertSeverity.HIGH
                            : DividendOverviewResponse.AlertSeverity.MEDIUM,
                    "Elevated cash payout ratio",
                    "Dividends are consuming most of free cash flow."),
                    current));
        }

        if (current.currentRatio() != null && current.currentRatio() < 1d) {
            events.add(toAlertEventData(dividendAlertsService.alert(
                    "current-ratio",
                    current.currentRatio() < 0.8d
                            ? DividendOverviewResponse.AlertSeverity.HIGH
                            : DividendOverviewResponse.AlertSeverity.MEDIUM,
                    "Thin near-term liquidity",
                    "Current liabilities exceed or nearly exceed current assets."),
                    current));
        }

        if (current.netDebtToEbitda() != null && current.netDebtToEbitda() > 3.5d) {
            events.add(toAlertEventData(dividendAlertsService.alert(
                    "net-debt-to-ebitda",
                    current.netDebtToEbitda() > 5d
                            ? DividendOverviewResponse.AlertSeverity.HIGH
                            : DividendOverviewResponse.AlertSeverity.MEDIUM,
                    "Leverage is running hot",
                    "Net debt is elevated relative to EBITDA proxy."),
                    current));
        }

        if (current.interestCoverage() != null && current.interestCoverage() < 3d) {
            events.add(toAlertEventData(dividendAlertsService.alert(
                    "interest-coverage",
                    current.interestCoverage() < 2d
                            ? DividendOverviewResponse.AlertSeverity.HIGH
                            : DividendOverviewResponse.AlertSeverity.MEDIUM,
                    "Interest coverage is weak",
                    "Operating income has limited cushion versus interest expense."),
                    current));
        }

        return events;
    }

    private AlertEventData toAlertEventData(DividendOverviewResponse.Alert alert, HistoryRowData current) {
        return new AlertEventData(
                alert.getId(),
                alert.getSeverity(),
                alert.getTitle(),
                alert.getDescription(),
                current.periodEnd(),
                current.filingDate(),
                current.accessionNumber());
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
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

    public record HistoryMetricDefinition(String id, String label, String unit) {
    }

    public record HistoryRowData(
            LocalDate periodEnd,
            LocalDate filingDate,
            String accessionNumber,
            Double dividendsPerShare,
            Double earningsPerShare,
            Double earningsPayoutRatio,
            Double revenue,
            Double operatingCashFlow,
            Double capitalExpenditures,
            Double freeCashFlow,
            Double dividendsPaid,
            Double fcfPayoutRatio,
            Double cashCoverage,
            Double retainedCash,
            Double cash,
            Double grossDebt,
            Double netDebt,
            Double ebitdaProxy,
            Double netDebtToEbitda,
            Double currentRatio,
            Double interestCoverage,
            Double fcfMargin) {
    }

    private record AlertEventData(
            String id,
            DividendOverviewResponse.AlertSeverity severity,
            String title,
            String description,
            LocalDate periodEnd,
            LocalDate filingDate,
            String accessionNumber) {
    }
}

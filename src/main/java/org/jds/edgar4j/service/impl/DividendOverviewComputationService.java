package org.jds.edgar4j.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jds.edgar4j.dto.response.DividendOverviewResponse;
import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.service.dividend.DividendAlertsService;
import org.jds.edgar4j.service.dividend.DividendMetricsService;
import org.jds.edgar4j.service.impl.DividendFilingAnalysisService.AnalyzedFilingData;
import org.jds.edgar4j.service.impl.DividendFilingAnalysisService.DividendFactPoint;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DividendOverviewComputationService {

    private final DividendFilingAnalysisService dividendFilingAnalysisService;
    private final DividendMetricsService dividendMetricsService;
    private final DividendAlertsService dividendAlertsService;

    public OverviewComputation computeOverview(
            List<Filling> annualCandidates,
            List<AnalyzedFilingData> annualAnalyses,
            List<Filling> quarterlyCandidates,
            List<AnalyzedFilingData> quarterlyAnalyses,
            AnalyzedFilingData latestAnnual,
            AnalyzedFilingData latestBalance,
            List<DividendOverviewResponse.TrendPoint> trend,
            List<DividendFactPoint> dividendFacts,
            Double referencePrice,
            Double marketCap,
            String sector) {
        Double revenue = dividendFilingAnalysisService.getMetric(latestAnnual,
                List.of("Revenue"),
                List.of("Revenues", "RevenueFromContractWithCustomerExcludingAssessedTax"));
        Double operatingCashFlow = dividendFilingAnalysisService.getMetric(latestAnnual,
                List.of("OperatingCashFlow"),
                List.of("NetCashProvidedByUsedInOperatingActivities"));
        Double capitalExpenditures = dividendMetricsService.magnitude(dividendFilingAnalysisService.getMetric(latestAnnual,
                List.of("CapitalExpenditures"),
                List.of("PaymentsToAcquirePropertyPlantAndEquipment", "PaymentsToAcquireProductiveAssets")));
        Double dividendsPaid = dividendMetricsService.magnitude(dividendFilingAnalysisService.getMetric(latestAnnual,
                List.of("DividendsPaid", "PaymentsOfDividendsCommonStock"),
                List.of("PaymentsOfDividendsCommonStock", "DividendsCommonStockCash", "PaymentsOfOrdinaryDividends")));
        Double shareRepurchases = dividendMetricsService.magnitude(dividendFilingAnalysisService.getMetric(latestAnnual,
                List.of("ShareRepurchases"),
                List.of("PaymentsForRepurchaseOfCommonStock", "StockRepurchasedAndRetiredDuringPeriodValue")));
        Double shareIssuance = dividendMetricsService.magnitude(dividendFilingAnalysisService.getMetric(latestAnnual,
                List.of("ShareIssuance"),
                List.of("ProceedsFromIssuanceOfCommonStock", "StockIssuedDuringPeriodValue")));
        Double freeCashFlow = operatingCashFlow != null && capitalExpenditures != null
                ? operatingCashFlow - capitalExpenditures
                : null;

        Double cash = dividendFilingAnalysisService.getMetric(latestBalance,
                List.of("Cash"),
                List.of("CashAndCashEquivalentsAtCarryingValue"));
        Double longTermDebt = dividendMetricsService.magnitude(dividendFilingAnalysisService.getMetric(latestBalance,
                List.of("LongTermDebt"),
                List.of("LongTermDebt")));
        Double shortTermDebt = dividendMetricsService.magnitude(dividendFilingAnalysisService.getMetric(latestBalance,
                List.of("DebtCurrent", "ShortTermDebt"),
                List.of("DebtCurrent", "LongTermDebtCurrent", "ShortTermBorrowings")));
        Double grossDebt = longTermDebt == null && shortTermDebt == null
                ? null
                : dividendMetricsService.defaultIfNull(longTermDebt) + dividendMetricsService.defaultIfNull(shortTermDebt);
        Double netDebt = grossDebt != null && cash != null ? grossDebt - cash : null;
        Double operatingIncome = dividendFilingAnalysisService.getMetric(latestAnnual,
                List.of("OperatingIncome"),
                List.of("OperatingIncomeLoss"));
        Double depreciationAmortization = dividendMetricsService.magnitude(dividendFilingAnalysisService.getMetric(latestAnnual,
                List.of("DepreciationAmortization"),
                List.of("DepreciationDepletionAndAmortization", "DepreciationAndAmortization")));
        Double ebitdaProxy = operatingIncome != null && depreciationAmortization != null
                ? operatingIncome + depreciationAmortization
                : null;
        Double interestExpense = dividendMetricsService.magnitude(dividendFilingAnalysisService.getMetric(latestAnnual,
                List.of("InterestExpense"),
                List.of("InterestExpense", "InterestExpenseDebt")));
        Double currentAssets = dividendFilingAnalysisService.getMetric(latestBalance,
                List.of("TotalCurrentAssets"),
                List.of("AssetsCurrent"));
        Double currentLiabilities = dividendFilingAnalysisService.getMetric(latestBalance,
                List.of("TotalCurrentLiabilities"),
                List.of("LiabilitiesCurrent"));

        Double cashCoverage = dividendMetricsService.safeDivide(freeCashFlow, dividendsPaid);
        Double retainedCash = freeCashFlow != null && dividendsPaid != null ? freeCashFlow - dividendsPaid : null;
        Double netDebtToEbitda = ebitdaProxy != null && ebitdaProxy > 0d ? dividendMetricsService.safeDivide(netDebt, ebitdaProxy) : null;
        Double currentRatio = dividendMetricsService.safeDivide(currentAssets, currentLiabilities);
        Double interestCoverage = dividendMetricsService.safeDivide(operatingIncome, interestExpense);
        Double fcfMargin = dividendMetricsService.safeDivide(freeCashFlow, revenue);

        Double dpsLatest = dividendMetricsService.findLatestDividendPerShare(trend);
        Double dpsCagr5y = dividendMetricsService.calculateDividendCagr(trend, 5);
        Integer uninterruptedYears = dividendMetricsService.countUninterruptedYears(trend);
        Integer consecutiveRaises = dividendMetricsService.countConsecutiveRaises(trend);
        Double dividendYield = referencePrice != null && referencePrice > 0d && dpsLatest != null
                ? dpsLatest / referencePrice
                : null;
        Double resolvedMarketCap = firstNonNull(
                marketCap,
                referencePrice != null && referencePrice > 0d && latestAnnual != null
                        && latestAnnual.secMetadata() != null
                        && latestAnnual.secMetadata().getSharesOutstanding() != null
                                ? referencePrice * latestAnnual.secMetadata().getSharesOutstanding().doubleValue()
                                : null);
        Double netBuybacks = shareRepurchases != null || shareIssuance != null
                ? dividendMetricsService.defaultIfNull(shareRepurchases) - dividendMetricsService.defaultIfNull(shareIssuance)
                : null;
        Double buybackYield = resolvedMarketCap != null && resolvedMarketCap > 0d && netBuybacks != null
                ? dividendMetricsService.safeDivide(netBuybacks, resolvedMarketCap)
                : null;
        Double shareholderYield = resolvedMarketCap != null && resolvedMarketCap > 0d && dividendsPaid != null
                ? dividendMetricsService.safeDivide(dividendsPaid + dividendMetricsService.defaultIfNull(netBuybacks), resolvedMarketCap)
                : null;

        DividendOverviewResponse.Coverage coverage = DividendOverviewResponse.Coverage.builder()
                .revenue(revenue)
                .operatingCashFlow(operatingCashFlow)
                .capitalExpenditures(capitalExpenditures)
                .freeCashFlow(freeCashFlow)
                .dividendsPaid(dividendsPaid)
                .cashCoverage(cashCoverage)
                .retainedCash(retainedCash)
                .build();

        DividendOverviewResponse.Snapshot snapshot = DividendOverviewResponse.Snapshot.builder()
                .dpsLatest(dpsLatest)
                .dpsCagr5y(dpsCagr5y)
                .fcfPayoutRatio(freeCashFlow != null && freeCashFlow > 0d ? dividendMetricsService.safeDivide(dividendsPaid, freeCashFlow) : null)
                .uninterruptedYears(uninterruptedYears)
                .consecutiveRaises(consecutiveRaises)
                .netDebtToEbitda(netDebtToEbitda)
                .interestCoverage(interestCoverage)
                .currentRatio(currentRatio)
                .fcfMargin(fcfMargin)
                .dividendYield(dividendYield)
                .shareholderYield(shareholderYield)
                .buybackYield(buybackYield)
                .build();

        List<DividendOverviewResponse.Alert> alerts = dividendAlertsService.buildAlerts(trend, snapshot, coverage, sector);
        int score = dividendAlertsService.buildScore(snapshot, alerts);
        DividendOverviewResponse.DividendRating rating = dividendAlertsService.toRating(score);

        DividendOverviewResponse.Balance balance = DividendOverviewResponse.Balance.builder()
                .cash(cash)
                .grossDebt(grossDebt)
                .netDebt(netDebt)
                .ebitdaProxy(ebitdaProxy)
                .netDebtToEbitda(netDebtToEbitda)
                .currentRatio(currentRatio)
                .interestCoverage(interestCoverage)
                .build();

        Map<String, DividendOverviewResponse.MetricConfidence> confidence = buildConfidence(
                snapshot, trend, dividendFacts, latestAnnual, latestBalance, referencePrice);
        List<String> warnings = buildWarnings(
                annualCandidates, annualAnalyses, quarterlyCandidates, quarterlyAnalyses,
                latestAnnual, latestBalance, dividendFacts, referencePrice);

        return new OverviewComputation(snapshot, confidence, alerts, coverage, balance, warnings, score, rating);
    }

    private Map<String, DividendOverviewResponse.MetricConfidence> buildConfidence(
            DividendOverviewResponse.Snapshot snapshot,
            List<DividendOverviewResponse.TrendPoint> trend,
            List<DividendFactPoint> dividendFacts,
            AnalyzedFilingData latestAnnual,
            AnalyzedFilingData latestBalance,
            Double referencePrice) {
        Map<String, DividendOverviewResponse.MetricConfidence> confidence = new LinkedHashMap<>();

        int dividendPointCount = (int) trend.stream().filter(point -> point.getDividendsPerShare() != null).count();
        confidence.put("dpsLatest", !dividendFacts.isEmpty()
                ? DividendOverviewResponse.MetricConfidence.HIGH
                : dividendFilingAnalysisService.hasDirectDividendsPerShare(latestAnnual)
                        ? DividendOverviewResponse.MetricConfidence.MEDIUM
                        : snapshot.getDpsLatest() != null
                                ? DividendOverviewResponse.MetricConfidence.LOW_MEDIUM
                                : DividendOverviewResponse.MetricConfidence.LOW);
        confidence.put("dpsCagr5y", dividendPointCount >= 6
                ? (!dividendFacts.isEmpty()
                        ? DividendOverviewResponse.MetricConfidence.HIGH
                        : DividendOverviewResponse.MetricConfidence.MEDIUM)
                : DividendOverviewResponse.MetricConfidence.LOW);
        confidence.put("fcfPayoutRatio", snapshot.getFcfPayoutRatio() != null
                ? DividendOverviewResponse.MetricConfidence.MEDIUM
                : DividendOverviewResponse.MetricConfidence.LOW);
        confidence.put("uninterruptedYears", dividendPointCount >= 6
                ? DividendOverviewResponse.MetricConfidence.HIGH
                : dividendPointCount >= 2
                        ? DividendOverviewResponse.MetricConfidence.MEDIUM
                        : DividendOverviewResponse.MetricConfidence.LOW);
        confidence.put("consecutiveRaises", dividendPointCount >= 6
                ? DividendOverviewResponse.MetricConfidence.MEDIUM
                : dividendPointCount >= 2
                        ? DividendOverviewResponse.MetricConfidence.LOW_MEDIUM
                        : DividendOverviewResponse.MetricConfidence.LOW);
        confidence.put("netDebtToEbitda", snapshot.getNetDebtToEbitda() != null
                ? DividendOverviewResponse.MetricConfidence.LOW_MEDIUM
                : DividendOverviewResponse.MetricConfidence.LOW);
        confidence.put("interestCoverage", snapshot.getInterestCoverage() != null
                ? DividendOverviewResponse.MetricConfidence.MEDIUM
                : DividendOverviewResponse.MetricConfidence.LOW);
        confidence.put("currentRatio", latestBalance != null && snapshot.getCurrentRatio() != null
                ? DividendOverviewResponse.MetricConfidence.HIGH
                : DividendOverviewResponse.MetricConfidence.LOW);
        confidence.put("fcfMargin", snapshot.getFcfMargin() != null
                ? DividendOverviewResponse.MetricConfidence.MEDIUM
                : DividendOverviewResponse.MetricConfidence.LOW);
        confidence.put("dividendYield", referencePrice != null && snapshot.getDividendYield() != null
                ? DividendOverviewResponse.MetricConfidence.MEDIUM
                : DividendOverviewResponse.MetricConfidence.LOW);
        confidence.put("shareholderYield", snapshot.getShareholderYield() != null
                ? DividendOverviewResponse.MetricConfidence.LOW_MEDIUM
                : DividendOverviewResponse.MetricConfidence.LOW);
        confidence.put("buybackYield", snapshot.getBuybackYield() != null
                ? DividendOverviewResponse.MetricConfidence.LOW_MEDIUM
                : DividendOverviewResponse.MetricConfidence.LOW);

        return confidence;
    }

    private List<String> buildWarnings(
            List<Filling> annualCandidates,
            List<AnalyzedFilingData> annualAnalyses,
            List<Filling> quarterlyCandidates,
            List<AnalyzedFilingData> quarterlyAnalyses,
            AnalyzedFilingData latestAnnual,
            AnalyzedFilingData latestBalance,
            List<DividendFactPoint> dividendFacts,
            Double referencePrice) {
        List<String> warnings = new ArrayList<>();

        if (latestAnnual == null) {
            warnings.add("No analyzable annual XBRL filing was available, so payout and profitability metrics are limited.");
        } else if (annualAnalyses.size() < annualCandidates.size()) {
            warnings.add("Some recent annual filings could not be parsed for XBRL analysis and were excluded from the overview.");
        }

        if (annualAnalyses.size() < 6) {
            warnings.add("Fewer than six annual XBRL filings were available, so long-range dividend trend coverage is limited.");
        }

        if (quarterlyCandidates.isEmpty() || quarterlyAnalyses.isEmpty()) {
            if (latestBalance != null && latestAnnual != null && latestBalance == latestAnnual) {
                warnings.add("No recent quarterly balance-sheet filing was available, so liquidity and leverage use the latest annual report.");
            } else if (latestBalance == null) {
                warnings.add("No recent balance-sheet filing was available, so liquidity and leverage metrics could not be computed.");
            }
        }

        if (dividendFacts.isEmpty()) {
            warnings.add("SEC companyfacts dividend history was unavailable, so streak metrics rely on filing-level XBRL only.");
        }

        if (referencePrice == null) {
            warnings.add("Stored market-price data is unavailable, so dividend yield could not be estimated.");
        }

        return warnings;
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

    public record OverviewComputation(
            DividendOverviewResponse.Snapshot snapshot,
            Map<String, DividendOverviewResponse.MetricConfidence> confidence,
            List<DividendOverviewResponse.Alert> alerts,
            DividendOverviewResponse.Coverage coverage,
            DividendOverviewResponse.Balance balance,
            List<String> warnings,
            int score,
            DividendOverviewResponse.DividendRating rating) {
    }
}

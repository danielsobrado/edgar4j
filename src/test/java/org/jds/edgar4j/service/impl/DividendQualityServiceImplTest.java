package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.jds.edgar4j.dto.response.DividendHistoryResponse;
import org.jds.edgar4j.dto.response.DividendOverviewResponse;
import org.jds.edgar4j.dto.response.DividendQualityResponse;
import org.jds.edgar4j.service.DividendAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DividendQualityServiceImplTest {

    @Mock
    private DividendAnalysisService dividendAnalysisService;

    private DividendQualityServiceImpl dividendQualityService;

    @BeforeEach
    void setUp() {
        dividendQualityService = new DividendQualityServiceImpl(dividendAnalysisService);
    }

    @Test
    @DisplayName("assess should report pass when benchmark and consistency checks succeed")
    void assessShouldPassWhenBenchmarkAndConsistencyChecksSucceed() {
        when(dividendAnalysisService.getOverview("AAPL")).thenReturn(overview(
                "0000320193",
                "AAPL",
                LocalDate.now().minusDays(120),
                0.96d));
        when(dividendAnalysisService.getHistory("AAPL",
                List.of("dps_declared", "eps_diluted", "earnings_payout", "free_cash_flow", "dividends_paid", "fcf_payout"),
                "FY",
                15)).thenReturn(history(
                LocalDate.of(2021, 9, 25),
                Map.of(
                        "dps_declared", 0.88d,
                        "eps_diluted", 5.61d,
                        "earnings_payout", 0.157d,
                        "free_cash_flow", 95.0d,
                        "dividends_paid", 14.0d,
                        "fcf_payout", 0.147d),
                LocalDate.of(2022, 9, 24),
                Map.of(
                        "dps_declared", 0.92d,
                        "eps_diluted", 6.12d,
                        "earnings_payout", 0.150d,
                        "free_cash_flow", 96.0d,
                        "dividends_paid", 14.5d,
                        "fcf_payout", 0.151d),
                LocalDate.of(2023, 9, 30),
                Map.of(
                        "dps_declared", 0.96d,
                        "eps_diluted", 6.13d,
                        "earnings_payout", 0.157d,
                        "free_cash_flow", 93.0d,
                        "dividends_paid", 14.8d,
                        "fcf_payout", 0.159d)));

        DividendQualityResponse response = dividendQualityService.assess("AAPL");

        assertEquals(DividendQualityResponse.QualityStatus.PASS, response.getOverallStatus());
        assertTrue(response.isBenchmarkAvailable());
        assertEquals(2023, response.getBenchmarkFiscalYear());
        assertEquals(0, response.getIssues().size());
        assertEquals(2, response.getBenchmarks().size());
        assertTrue(response.getBenchmarks().stream().allMatch(DividendQualityResponse.BenchmarkCheck::isPassed));
    }

    @Test
    @DisplayName("assess should fail when benchmark values drift materially")
    void assessShouldFailWhenBenchmarkValuesDrift() {
        when(dividendAnalysisService.getOverview("AAPL")).thenReturn(overview(
                "0000320193",
                "AAPL",
                LocalDate.now().minusDays(30),
                0.50d));
        when(dividendAnalysisService.getHistory("AAPL",
                List.of("dps_declared", "eps_diluted", "earnings_payout", "free_cash_flow", "dividends_paid", "fcf_payout"),
                "FY",
                15)).thenReturn(history(
                LocalDate.of(2021, 9, 25),
                Map.of(
                        "dps_declared", 0.45d,
                        "eps_diluted", 1.20d,
                        "earnings_payout", 0.375d,
                        "free_cash_flow", 10.0d,
                        "dividends_paid", 2.0d,
                        "fcf_payout", 0.20d),
                LocalDate.of(2022, 9, 24),
                Map.of(
                        "dps_declared", 0.48d,
                        "eps_diluted", 1.30d,
                        "earnings_payout", 0.369d,
                        "free_cash_flow", 10.0d,
                        "dividends_paid", 2.0d,
                        "fcf_payout", 0.20d),
                LocalDate.of(2023, 9, 30),
                Map.of(
                        "dps_declared", 0.50d,
                        "eps_diluted", 1.00d,
                        "earnings_payout", 0.50d,
                        "free_cash_flow", 10.0d,
                        "dividends_paid", 4.5d,
                        "fcf_payout", 0.45d)));

        DividendQualityResponse response = dividendQualityService.assess("AAPL");

        assertEquals(DividendQualityResponse.QualityStatus.FAIL, response.getOverallStatus());
        assertTrue(response.getBenchmarks().stream().anyMatch(check -> !check.isPassed()));
    }

    @Test
    @DisplayName("assess should flag annual history gaps and impossible metric values")
    void assessShouldFlagHistoryGapsAndImpossibleMetricValues() {
        when(dividendAnalysisService.getOverview("TEST")).thenReturn(overview(
                "0000000001",
                "TEST",
                LocalDate.now().minusDays(40),
                0.25d));
        when(dividendAnalysisService.getHistory("TEST",
                List.of("dps_declared", "eps_diluted", "earnings_payout", "free_cash_flow", "dividends_paid", "fcf_payout"),
                "FY",
                15)).thenReturn(history(
                LocalDate.of(2021, 12, 31),
                Map.of(
                        "dps_declared", 0.30d,
                        "eps_diluted", 1.00d,
                        "earnings_payout", 0.30d,
                        "free_cash_flow", 100.0d,
                        "dividends_paid", 20.0d,
                        "fcf_payout", 0.20d),
                LocalDate.of(2023, 12, 31),
                Map.of(
                        "dps_declared", -0.25d,
                        "eps_diluted", 0.0d,
                        "earnings_payout", 0.25d,
                        "free_cash_flow", -10.0d,
                        "dividends_paid", 5.0d,
                        "fcf_payout", 2.50d),
                LocalDate.of(2024, 12, 31),
                Map.of(
                        "dps_declared", 0.25d,
                        "eps_diluted", 1.00d,
                        "earnings_payout", 0.25d,
                        "free_cash_flow", 50.0d,
                        "dividends_paid", 12.5d,
                        "fcf_payout", 0.25d)));

        DividendQualityResponse response = dividendQualityService.assess("TEST");

        assertEquals(DividendQualityResponse.QualityStatus.FAIL, response.getOverallStatus());
        assertTrue(hasIssue(response, "annual_history_gap"));
        assertTrue(hasIssue(response, "negative_dps"));
        assertTrue(hasIssue(response, "dividends_without_positive_fcf"));
        assertTrue(hasIssue(response, "fcf_payout_with_nonpositive_fcf"));
        assertTrue(hasIssue(response, "earnings_payout_with_nonpositive_eps"));
        assertTrue(hasIssue(response, "fcf_payout_out_of_bounds"));
    }

    @Test
    @DisplayName("assess should flag payout ratios defined against zero denominators")
    void assessShouldFlagPayoutRatiosDefinedAgainstZeroDenominators() {
        when(dividendAnalysisService.getOverview("ZERO")).thenReturn(overview(
                "0000000002",
                "ZERO",
                LocalDate.now().minusDays(40),
                0.10d));
        when(dividendAnalysisService.getHistory("ZERO",
                List.of("dps_declared", "eps_diluted", "earnings_payout", "free_cash_flow", "dividends_paid", "fcf_payout"),
                "FY",
                15)).thenReturn(history(
                LocalDate.of(2022, 12, 31),
                Map.of(
                        "dps_declared", 0.10d,
                        "eps_diluted", 1.00d,
                        "earnings_payout", 0.10d,
                        "free_cash_flow", 5.0d,
                        "dividends_paid", 0.5d,
                        "fcf_payout", 0.10d),
                LocalDate.of(2023, 12, 31),
                Map.of(
                        "dps_declared", 0.10d,
                        "eps_diluted", 0.0d,
                        "earnings_payout", 0.10d,
                        "free_cash_flow", 0.0d,
                        "dividends_paid", 0.0d,
                        "fcf_payout", 0.10d),
                LocalDate.of(2024, 12, 31),
                Map.of(
                        "dps_declared", 0.10d,
                        "eps_diluted", 1.00d,
                        "earnings_payout", 0.10d,
                        "free_cash_flow", 5.0d,
                        "dividends_paid", 0.5d,
                        "fcf_payout", 0.10d)));

        DividendQualityResponse response = dividendQualityService.assess("ZERO");

        assertEquals(DividendQualityResponse.QualityStatus.WARN, response.getOverallStatus());
        assertTrue(hasIssue(response, "fcf_payout_with_nonpositive_fcf"));
        assertTrue(hasIssue(response, "earnings_payout_with_nonpositive_eps"));
    }

    private boolean hasIssue(DividendQualityResponse response, String code) {
        return response.getIssues().stream().anyMatch(issue -> code.equals(issue.getCode()));
    }

    private DividendOverviewResponse overview(String cik, String ticker, LocalDate lastFilingDate, double dpsLatest) {
        return DividendOverviewResponse.builder()
                .company(DividendOverviewResponse.CompanySummary.builder()
                        .cik(cik)
                        .ticker(ticker)
                        .name(ticker)
                        .lastFilingDate(lastFilingDate)
                        .build())
                .snapshot(DividendOverviewResponse.Snapshot.builder()
                        .dpsLatest(dpsLatest)
                        .build())
                .warnings(List.of())
                .build();
    }

    private DividendHistoryResponse history(
            LocalDate firstPeriodEnd,
            Map<String, Double> firstMetrics,
            LocalDate secondPeriodEnd,
            Map<String, Double> secondMetrics,
            LocalDate thirdPeriodEnd,
            Map<String, Double> thirdMetrics) {
        return DividendHistoryResponse.builder()
                .rows(List.of(
                        DividendHistoryResponse.HistoryRow.builder()
                                .periodEnd(firstPeriodEnd)
                                .metrics(firstMetrics)
                                .build(),
                        DividendHistoryResponse.HistoryRow.builder()
                                .periodEnd(secondPeriodEnd)
                                .metrics(secondMetrics)
                                .build(),
                        DividendHistoryResponse.HistoryRow.builder()
                                .periodEnd(thirdPeriodEnd)
                                .metrics(thirdMetrics)
                                .build()))
                .warnings(List.of())
                .build();
    }
}

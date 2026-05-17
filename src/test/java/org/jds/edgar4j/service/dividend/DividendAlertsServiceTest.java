package org.jds.edgar4j.service.dividend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.jds.edgar4j.dto.response.DividendOverviewResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DividendAlertsServiceTest {

    private final DividendAlertsService service = new DividendAlertsService();

    @Test
    @DisplayName("buildAlerts should flag dividend cuts")
    void buildAlertsShouldFlagDividendCuts() {
        List<DividendOverviewResponse.Alert> alerts = service.buildAlerts(
                List.of(
                        trendPoint(LocalDate.of(2021, 12, 31), 2.08d),
                        trendPoint(LocalDate.of(2022, 12, 31), 1.11d)),
                DividendOverviewResponse.Snapshot.builder().build());

        assertTrue(alerts.stream().anyMatch(alert ->
                "dividend-cut".equals(alert.getId())
                        && alert.getSeverity() == DividendOverviewResponse.AlertSeverity.HIGH));
    }

    @Test
    @DisplayName("buildAlerts should flag dividends funded while free cash flow is negative")
    void buildAlertsShouldFlagDividendsFundedWhileFreeCashFlowIsNegative() {
        DividendOverviewResponse.Coverage coverage = DividendOverviewResponse.Coverage.builder()
                .freeCashFlow(-1_000d)
                .dividendsPaid(250d)
                .build();

        List<DividendOverviewResponse.Alert> alerts = service.buildAlerts(
                List.of(trendPoint(LocalDate.of(2025, 12, 31), 1.20d)),
                DividendOverviewResponse.Snapshot.builder().build(),
                coverage);

        assertTrue(alerts.stream().anyMatch(alert ->
                "dividend-funded-by-debt".equals(alert.getId())
                        && alert.getSeverity() == DividendOverviewResponse.AlertSeverity.HIGH));
    }

    @Test
    @DisplayName("buildScore and toRating should map healthy and stressed profiles")
    void buildScoreAndToRatingShouldMapHealthyAndStressedProfiles() {
        DividendOverviewResponse.Snapshot healthy = DividendOverviewResponse.Snapshot.builder()
                .dpsLatest(1.20d)
                .dpsCagr5y(0.06d)
                .fcfPayoutRatio(0.45d)
                .uninterruptedYears(12)
                .consecutiveRaises(6)
                .netDebtToEbitda(1.0d)
                .interestCoverage(10d)
                .currentRatio(1.8d)
                .fcfMargin(0.18d)
                .build();

        int healthyScore = service.buildScore(healthy, List.of());
        assertEquals(DividendOverviewResponse.DividendRating.SAFE, service.toRating(healthyScore));

        DividendOverviewResponse.Snapshot stressed = DividendOverviewResponse.Snapshot.builder()
                .dpsLatest(1.20d)
                .dpsCagr5y(-0.10d)
                .fcfPayoutRatio(1.40d)
                .netDebtToEbitda(6.0d)
                .interestCoverage(1.5d)
                .currentRatio(0.7d)
                .fcfMargin(0.02d)
                .build();
        List<DividendOverviewResponse.Alert> alerts = List.of(
                service.alert("dividend-funded-by-debt", DividendOverviewResponse.AlertSeverity.HIGH, "Debt funded", "Debt funded"),
                service.alert("fcf-payout", DividendOverviewResponse.AlertSeverity.HIGH, "High payout", "High payout"));

        int stressedScore = service.buildScore(stressed, alerts);
        assertEquals(DividendOverviewResponse.DividendRating.AT_RISK, service.toRating(stressedScore));
    }

    @Test
    @DisplayName("buildAlerts should apply REIT payout and leverage threshold overrides")
    void buildAlertsShouldApplyReitPayoutAndLeverageThresholdOverrides() {
        DividendOverviewResponse.Snapshot snapshot = DividendOverviewResponse.Snapshot.builder()
                .fcfPayoutRatio(1.10d)
                .netDebtToEbitda(5.50d)
                .build();

        List<DividendOverviewResponse.Alert> industrialAlerts = service.buildAlerts(
                List.of(),
                snapshot,
                null,
                "Industrial Manufacturing");
        List<DividendOverviewResponse.Alert> reitAlerts = service.buildAlerts(
                List.of(),
                snapshot,
                null,
                "Real Estate Investment Trusts");

        assertTrue(industrialAlerts.stream().anyMatch(alert -> "fcf-payout".equals(alert.getId())));
        assertTrue(industrialAlerts.stream().anyMatch(alert -> "net-debt-to-ebitda".equals(alert.getId())));
        assertTrue(reitAlerts.stream().noneMatch(alert -> "fcf-payout".equals(alert.getId())));
        assertTrue(reitAlerts.stream().noneMatch(alert -> "net-debt-to-ebitda".equals(alert.getId())));
    }

    private DividendOverviewResponse.TrendPoint trendPoint(LocalDate periodEnd, Double dividendsPerShare) {
        return DividendOverviewResponse.TrendPoint.builder()
                .periodEnd(periodEnd)
                .dividendsPerShare(dividendsPerShare)
                .build();
    }
}

package org.jds.edgar4j.service.dividend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.util.List;

import org.jds.edgar4j.dto.response.DividendHistoryResponse;
import org.jds.edgar4j.dto.response.DividendOverviewResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DividendMetricsServiceTest {

    private final DividendMetricsService service = new DividendMetricsService();

    @Test
    @DisplayName("calculateDividendCagr should reject non-positive base values")
    void calculateDividendCagrShouldRejectNonPositiveBaseValues() {
        List<DividendOverviewResponse.TrendPoint> trend = List.of(
                trendPoint(LocalDate.of(2020, 12, 31), -0.10d),
                trendPoint(LocalDate.of(2021, 12, 31), 0.20d),
                trendPoint(LocalDate.of(2022, 12, 31), 0.30d));

        assertNull(service.calculateDividendCagr(trend, 2));
    }

    @Test
    @DisplayName("calculateDividendCagr should compute rounded positive CAGR")
    void calculateDividendCagrShouldComputeRoundedPositiveCagr() {
        List<DividendOverviewResponse.TrendPoint> trend = List.of(
                trendPoint(LocalDate.of(2020, 12, 31), 1.00d),
                trendPoint(LocalDate.of(2021, 12, 31), 1.10d),
                trendPoint(LocalDate.of(2022, 12, 31), 1.21d));

        assertEquals(0.100000d, service.calculateDividendCagr(trend, 2), 0.000001d);
    }

    @Test
    @DisplayName("calculateMetricCagr should use period-end dates when available")
    void calculateMetricCagrShouldUsePeriodEndDatesWhenAvailable() {
        List<DividendHistoryResponse.MetricPoint> points = List.of(
                metricPoint(LocalDate.of(2020, 12, 31), 100d),
                metricPoint(LocalDate.of(2023, 12, 31), 133.1d));

        assertEquals(0.100000d, service.calculateMetricCagr(points), 0.000001d);
    }

    @Test
    @DisplayName("safeDivide should guard null, zero, and non-finite values")
    void safeDivideShouldGuardNullZeroAndNonFiniteValues() {
        assertNull(service.safeDivide(1d, 0d));
        assertNull(service.safeDivide(null, 1d));
        assertNull(service.roundDouble(Double.POSITIVE_INFINITY));
        assertEquals(0.333333d, service.safeDivide(1d, 3d), 0.000001d);
    }

    private DividendOverviewResponse.TrendPoint trendPoint(LocalDate periodEnd, Double dividendsPerShare) {
        return DividendOverviewResponse.TrendPoint.builder()
                .periodEnd(periodEnd)
                .dividendsPerShare(dividendsPerShare)
                .build();
    }

    private DividendHistoryResponse.MetricPoint metricPoint(LocalDate periodEnd, Double value) {
        return DividendHistoryResponse.MetricPoint.builder()
                .periodEnd(periodEnd)
                .value(value)
                .build();
    }
}

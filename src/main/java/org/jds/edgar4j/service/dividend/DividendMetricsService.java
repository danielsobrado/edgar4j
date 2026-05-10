package org.jds.edgar4j.service.dividend;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.jds.edgar4j.dto.response.DividendHistoryResponse;
import org.jds.edgar4j.dto.response.DividendOverviewResponse;
import org.springframework.stereotype.Service;

@Service
public class DividendMetricsService {

    public Double magnitude(Double value) {
        return value != null ? Math.abs(value) : null;
    }

    public Double safeDivide(Double numerator, Double denominator) {
        if (numerator == null || denominator == null || denominator == 0d) {
            return null;
        }

        return roundDouble(numerator / denominator);
    }

    public Double roundDouble(double value) {
        if (!Double.isFinite(value)) {
            return null;
        }

        return BigDecimal.valueOf(value)
                .setScale(6, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public Double defaultIfNull(Double value) {
        return value != null ? value : 0d;
    }

    public Double findLatestDividendPerShare(List<DividendOverviewResponse.TrendPoint> trend) {
        for (int index = trend.size() - 1; index >= 0; index--) {
            Double dividendsPerShare = trend.get(index).getDividendsPerShare();
            if (dividendsPerShare != null) {
                return dividendsPerShare;
            }
        }
        return null;
    }

    public Double calculateDividendCagr(List<DividendOverviewResponse.TrendPoint> trend, int years) {
        List<DividendOverviewResponse.TrendPoint> points = trend.stream()
                .filter(point -> point.getDividendsPerShare() != null)
                .toList();
        if (points.size() < years + 1) {
            return null;
        }

        List<DividendOverviewResponse.TrendPoint> relevant = points.subList(points.size() - (years + 1), points.size());
        Double first = relevant.get(0).getDividendsPerShare();
        Double last = relevant.get(relevant.size() - 1).getDividendsPerShare();
        if (first == null || last == null || first <= 0d || last <= 0d) {
            return null;
        }

        return roundDouble(Math.pow(last / first, 1d / years) - 1d);
    }

    public Integer countUninterruptedYears(List<DividendOverviewResponse.TrendPoint> trend) {
        int count = 0;
        for (int index = trend.size() - 1; index >= 0; index--) {
            Double dividendsPerShare = trend.get(index).getDividendsPerShare();
            if (dividendsPerShare == null || dividendsPerShare <= 0d) {
                break;
            }
            count++;
        }
        return count;
    }

    public Integer countConsecutiveRaises(List<DividendOverviewResponse.TrendPoint> trend) {
        int count = 0;
        for (int index = trend.size() - 1; index >= 1; index--) {
            Double current = trend.get(index).getDividendsPerShare();
            Double previous = trend.get(index - 1).getDividendsPerShare();
            if (current == null || previous == null || current <= previous) {
                break;
            }
            count++;
        }
        return count;
    }

    public Double calculateMetricCagr(List<DividendHistoryResponse.MetricPoint> points) {
        if (points.size() < 2) {
            return null;
        }

        DividendHistoryResponse.MetricPoint first = points.get(0);
        DividendHistoryResponse.MetricPoint last = points.get(points.size() - 1);
        if (first.getValue() == null || last.getValue() == null || first.getValue() <= 0d || last.getValue() <= 0d) {
            return null;
        }

        long years = 0;
        if (first.getPeriodEnd() != null && last.getPeriodEnd() != null) {
            years = java.time.temporal.ChronoUnit.YEARS.between(first.getPeriodEnd(), last.getPeriodEnd());
        }
        if (years <= 0) {
            years = points.size() - 1L;
        }
        if (years <= 0) {
            return null;
        }

        return roundDouble(Math.pow(last.getValue() / first.getValue(), 1d / years) - 1d);
    }

    public Double calculateMetricVolatility(List<DividendHistoryResponse.MetricPoint> points) {
        if (points.size() < 3) {
            return null;
        }

        List<Double> changes = new java.util.ArrayList<>();
        for (int index = 1; index < points.size(); index++) {
            Double previous = points.get(index - 1).getValue();
            Double current = points.get(index).getValue();
            if (previous == null || current == null || previous == 0d) {
                continue;
            }

            double change = (current - previous) / Math.abs(previous);
            if (Double.isFinite(change)) {
                changes.add(change);
            }
        }

        if (changes.size() < 2) {
            return null;
        }

        double mean = changes.stream().mapToDouble(Double::doubleValue).average().orElse(0d);
        double variance = changes.stream()
                .mapToDouble(change -> Math.pow(change - mean, 2d))
                .average()
                .orElse(0d);
        return roundDouble(Math.sqrt(variance));
    }

    public DividendHistoryResponse.TrendDirection determineMetricTrend(
            List<DividendHistoryResponse.MetricPoint> points,
            Double volatility) {
        if (points.size() < 2) {
            return DividendHistoryResponse.TrendDirection.INSUFFICIENT_DATA;
        }

        Double first = points.get(0).getValue();
        Double last = points.get(points.size() - 1).getValue();
        if (first == null || last == null) {
            return DividendHistoryResponse.TrendDirection.INSUFFICIENT_DATA;
        }

        if (volatility != null && volatility >= 0.35d) {
            return DividendHistoryResponse.TrendDirection.VOLATILE;
        }

        double baseline = Math.abs(first) > 0d ? Math.abs(first) : 1d;
        double change = (last - first) / baseline;
        if (change > 0.05d) {
            return DividendHistoryResponse.TrendDirection.UP;
        }
        if (change < -0.05d) {
            return DividendHistoryResponse.TrendDirection.DOWN;
        }
        return DividendHistoryResponse.TrendDirection.FLAT;
    }
}

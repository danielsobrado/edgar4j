package org.jds.edgar4j.service.dividend;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jds.edgar4j.dto.response.DividendOverviewResponse;
import org.springframework.stereotype.Service;

@Service
public class DividendAlertsService {

    private static final double DEFAULT_FCF_PAYOUT_WARNING_THRESHOLD = 0.85d;
    private static final double DEFAULT_FCF_PAYOUT_HIGH_THRESHOLD = 1.00d;
    private static final double REIT_FCF_PAYOUT_WARNING_THRESHOLD = 1.20d;
    private static final double REIT_FCF_PAYOUT_HIGH_THRESHOLD = 1.50d;
    private static final double DEFAULT_NET_DEBT_WARNING_THRESHOLD = 3.50d;
    private static final double DEFAULT_NET_DEBT_HIGH_THRESHOLD = 5.00d;
    private static final double REIT_NET_DEBT_WARNING_THRESHOLD = 6.00d;
    private static final double REIT_NET_DEBT_HIGH_THRESHOLD = 7.50d;

    public List<DividendOverviewResponse.Alert> buildAlerts(
            List<DividendOverviewResponse.TrendPoint> trend,
            DividendOverviewResponse.Snapshot snapshot) {
        return buildAlerts(trend, snapshot, null);
    }

    public List<DividendOverviewResponse.Alert> buildAlerts(
            List<DividendOverviewResponse.TrendPoint> trend,
            DividendOverviewResponse.Snapshot snapshot,
            DividendOverviewResponse.Coverage coverage) {
        return buildAlerts(trend, snapshot, coverage, null);
    }

    public List<DividendOverviewResponse.Alert> buildAlerts(
            List<DividendOverviewResponse.TrendPoint> trend,
            DividendOverviewResponse.Snapshot snapshot,
            DividendOverviewResponse.Coverage coverage,
            String sector) {
        List<DividendOverviewResponse.Alert> alerts = new ArrayList<>();
        DividendOverviewResponse.TrendPoint latest = trend.isEmpty() ? null : trend.get(trend.size() - 1);
        DividendOverviewResponse.TrendPoint previous = trend.size() > 1 ? trend.get(trend.size() - 2) : null;
        AlertThresholds thresholds = AlertThresholds.forSector(sector);

        if (latest != null
                && previous != null
                && latest.getDividendsPerShare() != null
                && previous.getDividendsPerShare() != null
                && previous.getDividendsPerShare() > 0d
                && latest.getDividendsPerShare() < previous.getDividendsPerShare()) {
            alerts.add(alert("dividend-cut", DividendOverviewResponse.AlertSeverity.HIGH,
                    "Dividend cut detected",
                    "The latest annual dividend-per-share value is below the prior year."));
        }

        if (coverage != null
                && coverage.getFreeCashFlow() != null
                && coverage.getDividendsPaid() != null
                && coverage.getFreeCashFlow() < 0d
                && coverage.getDividendsPaid() > 0d) {
            alerts.add(alert("dividend-funded-by-debt", DividendOverviewResponse.AlertSeverity.HIGH,
                    "Dividend is not covered by free cash flow",
                    "The company paid dividends while annual free cash flow was negative."));
        }

        if (snapshot.getFcfPayoutRatio() != null
                && snapshot.getFcfPayoutRatio() > thresholds.fcfPayoutWarningThreshold()) {
            alerts.add(alert("fcf-payout",
                    snapshot.getFcfPayoutRatio() > thresholds.fcfPayoutHighThreshold()
                            ? DividendOverviewResponse.AlertSeverity.HIGH
                            : DividendOverviewResponse.AlertSeverity.MEDIUM,
                    "Elevated cash payout ratio",
                    "Dividends are consuming most of free cash flow."));
        }

        if (snapshot.getCurrentRatio() != null && snapshot.getCurrentRatio() < 1d) {
            alerts.add(alert("current-ratio",
                    snapshot.getCurrentRatio() < 0.8d
                            ? DividendOverviewResponse.AlertSeverity.HIGH
                            : DividendOverviewResponse.AlertSeverity.MEDIUM,
                    "Thin near-term liquidity",
                    "Current liabilities exceed or nearly exceed current assets."));
        }

        if (snapshot.getNetDebtToEbitda() != null
                && snapshot.getNetDebtToEbitda() > thresholds.netDebtWarningThreshold()) {
            alerts.add(alert("net-debt-to-ebitda",
                    snapshot.getNetDebtToEbitda() > thresholds.netDebtHighThreshold()
                            ? DividendOverviewResponse.AlertSeverity.HIGH
                            : DividendOverviewResponse.AlertSeverity.MEDIUM,
                    "Leverage is running hot",
                    "Net debt is elevated relative to EBITDA proxy."));
        }

        if (snapshot.getInterestCoverage() != null && snapshot.getInterestCoverage() < 3d) {
            alerts.add(alert("interest-coverage",
                    snapshot.getInterestCoverage() < 2d
                            ? DividendOverviewResponse.AlertSeverity.HIGH
                            : DividendOverviewResponse.AlertSeverity.MEDIUM,
                    "Interest coverage is weak",
                    "Operating income has limited cushion versus interest expense."));
        }

        return alerts;
    }

    public int buildScore(
            DividendOverviewResponse.Snapshot snapshot,
            List<DividendOverviewResponse.Alert> alerts) {
        int score = 50;

        if (snapshot.getDpsLatest() != null && snapshot.getDpsLatest() > 0d) {
            score += 10;
        }
        if (snapshot.getDpsCagr5y() != null) {
            if (snapshot.getDpsCagr5y() >= 0.08d) {
                score += 12;
            } else if (snapshot.getDpsCagr5y() >= 0.03d) {
                score += 6;
            } else if (snapshot.getDpsCagr5y() < 0d) {
                score -= 10;
            }
        }
        if (snapshot.getFcfPayoutRatio() != null) {
            if (snapshot.getFcfPayoutRatio() <= 0.6d) {
                score += 14;
            } else if (snapshot.getFcfPayoutRatio() <= 0.8d) {
                score += 8;
            } else if (snapshot.getFcfPayoutRatio() > 1d) {
                score -= 12;
            } else {
                score -= 6;
            }
        }
        if (snapshot.getUninterruptedYears() != null) {
            if (snapshot.getUninterruptedYears() >= 10) {
                score += 10;
            } else if (snapshot.getUninterruptedYears() >= 5) {
                score += 5;
            }
        }
        if (snapshot.getConsecutiveRaises() != null) {
            if (snapshot.getConsecutiveRaises() >= 5) {
                score += 8;
            } else if (snapshot.getConsecutiveRaises() >= 1) {
                score += 4;
            }
        }
        if (snapshot.getNetDebtToEbitda() != null) {
            if (snapshot.getNetDebtToEbitda() <= 1.5d) {
                score += 10;
            } else if (snapshot.getNetDebtToEbitda() <= 3d) {
                score += 4;
            } else {
                score -= 8;
            }
        }
        if (snapshot.getInterestCoverage() != null) {
            if (snapshot.getInterestCoverage() >= 8d) {
                score += 8;
            } else if (snapshot.getInterestCoverage() >= 4d) {
                score += 4;
            } else if (snapshot.getInterestCoverage() < 2d) {
                score -= 8;
            }
        }
        if (snapshot.getCurrentRatio() != null) {
            if (snapshot.getCurrentRatio() >= 1.5d) {
                score += 5;
            } else if (snapshot.getCurrentRatio() < 1d) {
                score -= 5;
            }
        }
        if (snapshot.getFcfMargin() != null) {
            if (snapshot.getFcfMargin() >= 0.15d) {
                score += 5;
            } else if (snapshot.getFcfMargin() < 0.05d) {
                score -= 4;
            }
        }

        for (DividendOverviewResponse.Alert alert : alerts) {
            switch (alert.getSeverity()) {
                case HIGH -> score -= 8;
                case MEDIUM -> score -= 4;
                case LOW -> score -= 2;
            }
        }

        return Math.max(0, Math.min(100, score));
    }

    public DividendOverviewResponse.DividendRating toRating(int score) {
        if (score >= 80) {
            return DividendOverviewResponse.DividendRating.SAFE;
        }
        if (score >= 65) {
            return DividendOverviewResponse.DividendRating.STABLE;
        }
        if (score >= 45) {
            return DividendOverviewResponse.DividendRating.WATCH;
        }
        return DividendOverviewResponse.DividendRating.AT_RISK;
    }

    public DividendOverviewResponse.Alert alert(
            String id,
            DividendOverviewResponse.AlertSeverity severity,
            String title,
            String description) {
        return DividendOverviewResponse.Alert.builder()
                .id(id)
                .severity(severity)
                .title(title)
                .description(description)
                .build();
    }

    private record AlertThresholds(
            double fcfPayoutWarningThreshold,
            double fcfPayoutHighThreshold,
            double netDebtWarningThreshold,
            double netDebtHighThreshold) {

        private static AlertThresholds forSector(String sector) {
            if (isReitSector(sector)) {
                return new AlertThresholds(
                        REIT_FCF_PAYOUT_WARNING_THRESHOLD,
                        REIT_FCF_PAYOUT_HIGH_THRESHOLD,
                        REIT_NET_DEBT_WARNING_THRESHOLD,
                        REIT_NET_DEBT_HIGH_THRESHOLD);
            }
            return new AlertThresholds(
                    DEFAULT_FCF_PAYOUT_WARNING_THRESHOLD,
                    DEFAULT_FCF_PAYOUT_HIGH_THRESHOLD,
                    DEFAULT_NET_DEBT_WARNING_THRESHOLD,
                    DEFAULT_NET_DEBT_HIGH_THRESHOLD);
        }

        private static boolean isReitSector(String sector) {
            if (sector == null || sector.isBlank()) {
                return false;
            }
            String normalized = sector.toLowerCase(Locale.ROOT);
            return normalized.contains("reit")
                    || normalized.contains("real estate investment trust");
        }
    }
}

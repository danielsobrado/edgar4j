# Phase 5: Alert Rules Engine

## Objective

Build a configurable, threshold-based alert system that fires warnings when dividend viability indicators deteriorate. Each alert includes triggering metric values and links to supporting filing evidence.

## Alert Data Model

```
┌──────────────────────────────────────────────────────────────┐
│ dividend_alerts                                               │
├──────────────────────────────────────────────────────────────┤
│ id                BIGINT        PK                           │
│ cik               VARCHAR(10)   FK                            │
│ rule_id           VARCHAR(50)   -- alert rule identifier     │
│ severity          VARCHAR(10)   -- CRITICAL, HIGH, MEDIUM, LOW│
│ period_end        DATE                                        │
│ fired_at          TIMESTAMP                                   │
│ message           TEXT                                        │
│ metric_values     JSONB/TEXT    -- triggering metric snapshot │
│ supporting_accession VARCHAR(25)                              │
│ text_evidence     TEXT                                        │
│ is_active         BOOLEAN                                     │
│ acknowledged      BOOLEAN                                     │
│ created_at        TIMESTAMP                                   │
│ resolved_at       TIMESTAMP                                   │
│                                                               │
│ INDEX: (cik, is_active, severity)                            │
│ INDEX: (cik, rule_id, period_end)                            │
└──────────────────────────────────────────────────────────────┘
```

## Default Alert Rules

### Critical

| Rule ID | Condition | Lookback |
|---|---|---|
| `dividend_funded_by_debt` | `fcf < 0 AND total_dividends_paid > 0` | 1 FY |
| `unsustainable_fcf_payout` | `fcf_payout > 1.0` for 2 consecutive FY | 2 FY |
| `dividend_suspended` | Dividend event with type `SUSPENSION` | Event |

### High

| Rule ID | Condition | Lookback |
|---|---|---|
| `coverage_deterioration` | `cash_coverage` declining 2+ years AND < 1.0 | 3 FY |
| `leverage_stress` | `net_debt_to_ebitda > 4.0` | 1 FY |
| `dividend_cut` | `dps_declared` decreased > 5% YoY | 1 FY |

### Medium

| Rule ID | Condition | Lookback |
|---|---|---|
| `liquidity_stress` | `current_ratio < 1.0 OR cash_ratio < 0.2` | 1 FY |
| `growth_outpaces_fcf` | `dps_cagr_3y > fcf_cagr_3y + 5%` | 3 FY |
| `high_earnings_payout` | `earnings_payout > 0.80` for 2+ years | 2 FY |
| `interest_coverage_weak` | `interest_coverage < 3.0` | 1 FY |
| `policy_risk_language` | Text: "may suspend", "at discretion of board" | Event |

### Low

| Rule ID | Condition | Lookback |
|---|---|---|
| `dilution_while_paying` | `net_buyback < 0` for 3+ years AND `dividends > 0` | 3 FY |
| `fcf_volatility_high` | `fcf_volatility > 50%` | 10 FY |
| `negative_retained_earnings` | `RetainedEarnings < 0` | 1 FY |

## Alert Evaluation Service

```java
@Service
@RequiredArgsConstructor
public class AlertEvaluationService {

    private final MetricValueRepository metricValueRepository;
    private final DividendEventRepository dividendEventRepository;
    private final AlertRepository alertRepository;
    private final AlertRuleRepository ruleRepository;

    public List<DividendAlert> evaluateAlerts(String cik) {
        List<AlertRule> rules = ruleRepository.findByEnabledTrue();
        List<DividendAlert> firedAlerts = new ArrayList<>();

        for (AlertRule rule : rules) {
            Optional<DividendAlert> alert = evaluateRule(cik, rule);
            alert.ifPresent(a -> {
                if (!alertRepository.existsByCikAndRuleIdAndPeriodEnd(
                        cik, rule.getRuleId(), a.getPeriodEnd())) {
                    alertRepository.save(a);
                    firedAlerts.add(a);
                }
            });
        }
        resolveStaleAlerts(cik);
        return firedAlerts;
    }

    private void resolveStaleAlerts(String cik) {
        List<DividendAlert> activeAlerts = alertRepository.findByCikAndIsActiveTrue(cik);
        for (DividendAlert alert : activeAlerts) {
            AlertRule rule = ruleRepository.findById(alert.getRuleId()).orElse(null);
            if (rule == null) continue;
            Map<String, List<MetricValue>> currentMetrics = loadMetricHistory(
                    cik, rule.getMetricRefs(), 1);
            if (!evaluateExpression(rule.getExpression(), currentMetrics)) {
                alert.setIsActive(false);
                alert.setResolvedAt(Instant.now());
                alertRepository.save(alert);
            }
        }
    }
}
```

## Sector-Specific Threshold Overrides

```yaml
edgar4j:
  dividend:
    alerts:
      sector-overrides:
        REIT:
          unsustainable_fcf_payout:
            threshold: 1.5     # REITs required to distribute 90%+
          leverage_stress:
            threshold: 6.0
        Utilities:
          high_earnings_payout:
            threshold: 0.90
          interest_coverage_weak:
            threshold: 2.0
        BDC:
          unsustainable_fcf_payout:
            threshold: 1.3
```

## Composite Viability Score

```java
public enum ViabilityRating {
    SAFE,       // No active alerts, strong coverage & growth
    STABLE,     // Minor alerts only, adequate coverage
    CAUTION,    // Medium alerts, some metrics weakening
    AT_RISK,    // High alerts, multiple metrics in warning zone
    CRITICAL    // Critical alerts fired, dividend likely unsustainable
}

public ViabilityRating computeViabilityRating(String cik) {
    List<DividendAlert> active = alertRepository.findByCikAndIsActiveTrue(cik);
    long critical = active.stream().filter(a -> "CRITICAL".equals(a.getSeverity())).count();
    long high = active.stream().filter(a -> "HIGH".equals(a.getSeverity())).count();
    long medium = active.stream().filter(a -> "MEDIUM".equals(a.getSeverity())).count();

    if (critical > 0) return ViabilityRating.CRITICAL;
    if (high >= 2) return ViabilityRating.AT_RISK;
    if (high >= 1 || medium >= 3) return ViabilityRating.CAUTION;
    if (medium >= 1) return ViabilityRating.STABLE;
    return ViabilityRating.SAFE;
}
```

## Validation Checklist

- [ ] AT&T (T) triggers `dividend_cut` alert for 2022
- [ ] A company with FCF < 0 and dividends > 0 triggers `dividend_funded_by_debt`
- [ ] REIT sector overrides apply correctly
- [ ] Resolved alerts don't re-fire for the same period
- [ ] Composite viability rating matches manual assessment for 10 test companies

## Estimated Effort: 3-4 days

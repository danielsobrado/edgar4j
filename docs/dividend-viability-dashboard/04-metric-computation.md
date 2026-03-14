# Phase 4: Metric Computation Engine

## Objective

Compute all 65+ dividend viability metrics from the normalized fact store (Phase 2) and dividend events (Phase 3). Store results in a `metric_values` table for fast dashboard queries. Leverage the existing `MultiPeriodAnalyzer` for CAGR, trend detection, and volatility scoring.

## Design: Centralized Formula Registry

All metric formulas are defined in a single registry — preventing "ratio drift" where different parts of the code compute the same metric differently.

```java
@Component
public class MetricRegistry {

    private final Map<String, MetricDefinition> definitions = new LinkedHashMap<>();

    @PostConstruct
    void registerAll() {
        // ── Dividend History ────────────────────────────
        register("dps_declared", def("Dividends Per Share (Declared)",
            MetricGroup.DIVIDEND_HISTORY, Formula.directFact("DividendsPerShare"),
            Confidence.HIGH));

        register("dps_cash_paid", def("Dividends Per Share (Cash Paid)",
            MetricGroup.DIVIDEND_HISTORY, Formula.directFact("DividendsPerShareCashPaid"),
            Confidence.MEDIUM).withFallback(Formula.directFact("DividendsPerShare")));

        register("total_dividends_paid", def("Total Common Dividends Paid",
            MetricGroup.DIVIDEND_HISTORY, Formula.directFact("DividendsPaid"),
            Confidence.HIGH));

        // ── Payout & Coverage ───────────────────────────
        register("earnings_payout", def("Earnings Payout Ratio",
            MetricGroup.PAYOUT_COVERAGE, Formula.ratio("DividendsPaid", "NetIncome"),
            Confidence.MEDIUM));

        register("eps_payout", def("EPS Payout Ratio",
            MetricGroup.PAYOUT_COVERAGE, Formula.ratio("DividendsPerShare", "EarningsPerShareDiluted"),
            Confidence.MEDIUM));

        register("fcf", def("Free Cash Flow",
            MetricGroup.PAYOUT_COVERAGE, Formula.subtract("OperatingCashFlow", "CapitalExpenditures"),
            Confidence.MEDIUM));

        register("fcf_payout", def("FCF Payout Ratio",
            MetricGroup.PAYOUT_COVERAGE, Formula.ratio("DividendsPaid", "fcf"),
            Confidence.MEDIUM).undefinedWhen("fcf <= 0"));

        register("ocf_payout", def("OCF Payout Ratio",
            MetricGroup.PAYOUT_COVERAGE, Formula.ratio("DividendsPaid", "OperatingCashFlow"),
            Confidence.HIGH));

        register("cash_coverage", def("Cash Dividend Coverage",
            MetricGroup.PAYOUT_COVERAGE, Formula.ratio("fcf", "DividendsPaid"),
            Confidence.MEDIUM));

        register("retained_cash", def("Retained Cash After Dividends",
            MetricGroup.PAYOUT_COVERAGE, Formula.subtract("fcf", "DividendsPaid"),
            Confidence.MEDIUM));

        register("capex_adjusted_coverage", def("Capex-Adjusted Coverage",
            MetricGroup.PAYOUT_COVERAGE,
            Formula.custom(facts -> {
                BigDecimal ocf = facts.get("OperatingCashFlow");
                BigDecimal capex = facts.get("CapitalExpenditures");
                BigDecimal divs = facts.get("DividendsPaid");
                if (ocf == null || capex == null || divs == null) return null;
                BigDecimal denom = capex.abs().add(divs.abs());
                if (denom.compareTo(BigDecimal.ZERO) == 0) return null;
                return ocf.divide(denom, 4, RoundingMode.HALF_UP);
            }),
            Confidence.MEDIUM));

        // ── Leverage ────────────────────────────────────
        register("gross_debt", def("Gross Debt",
            MetricGroup.LEVERAGE, Formula.add("ShortTermDebt", "LongTermDebt"),
            Confidence.MEDIUM));

        register("net_debt", def("Net Debt",
            MetricGroup.LEVERAGE, Formula.subtract("gross_debt", "Cash"),
            Confidence.MEDIUM));

        register("ebitda_proxy", def("EBITDA (Proxy)",
            MetricGroup.LEVERAGE, Formula.add("OperatingIncome", "DepreciationAmortization"),
            Confidence.LOW_MEDIUM).withProxyWarning("EBITDA is typically non-GAAP"));

        register("net_debt_to_ebitda", def("Net Debt / EBITDA",
            MetricGroup.LEVERAGE, Formula.ratio("net_debt", "ebitda_proxy"),
            Confidence.LOW_MEDIUM));

        register("debt_to_equity", def("Debt / Equity",
            MetricGroup.LEVERAGE, Formula.ratio("gross_debt", "TotalEquity"),
            Confidence.MEDIUM));

        register("interest_coverage", def("Interest Coverage",
            MetricGroup.LEVERAGE,
            Formula.custom(facts -> {
                BigDecimal incBT = facts.get("IncomeBeforeTaxes");
                BigDecimal interest = facts.get("InterestExpense");
                if (interest == null || interest.compareTo(BigDecimal.ZERO) == 0) return null;
                if (incBT != null) {
                    BigDecimal ebit = incBT.add(interest.abs());
                    return ebit.divide(interest.abs(), 4, RoundingMode.HALF_UP);
                }
                BigDecimal opIncome = facts.get("OperatingIncome");
                if (opIncome == null) return null;
                return opIncome.divide(interest.abs(), 4, RoundingMode.HALF_UP);
            }),
            Confidence.MEDIUM));

        // ── Liquidity ───────────────────────────────────
        register("current_ratio", def("Current Ratio",
            MetricGroup.LIQUIDITY, Formula.ratio("TotalCurrentAssets", "TotalCurrentLiabilities"),
            Confidence.HIGH));

        register("cash_ratio", def("Cash Ratio",
            MetricGroup.LIQUIDITY,
            Formula.custom(facts -> {
                BigDecimal cash = facts.get("Cash");
                BigDecimal sti = facts.getOrDefault("ShortTermInvestments", BigDecimal.ZERO);
                BigDecimal cl = facts.get("TotalCurrentLiabilities");
                if (cash == null || cl == null || cl.compareTo(BigDecimal.ZERO) == 0) return null;
                return cash.add(sti).divide(cl, 4, RoundingMode.HALF_UP);
            }),
            Confidence.MEDIUM));

        // ── Profitability ───────────────────────────────
        register("fcf_margin", def("FCF Margin",
            MetricGroup.PROFITABILITY, Formula.ratio("fcf", "Revenue"),
            Confidence.MEDIUM));

        register("roe", def("Return on Equity",
            MetricGroup.PROFITABILITY, Formula.ratio("NetIncome", "TotalEquity"),
            Confidence.MEDIUM));

        register("roa", def("Return on Assets",
            MetricGroup.PROFITABILITY, Formula.ratio("NetIncome", "TotalAssets"),
            Confidence.MEDIUM));

        register("roic", def("Return on Invested Capital",
            MetricGroup.PROFITABILITY,
            Formula.custom(facts -> {
                BigDecimal opIncome = facts.get("OperatingIncome");
                BigDecimal taxExp = facts.get("IncomeTaxExpense");
                BigDecimal incBT = facts.get("IncomeBeforeTaxes");
                BigDecimal equity = facts.get("TotalEquity");
                BigDecimal debt = facts.getOrDefault("gross_debt", BigDecimal.ZERO);
                BigDecimal cash = facts.getOrDefault("Cash", BigDecimal.ZERO);
                if (opIncome == null || equity == null) return null;
                BigDecimal taxRate = BigDecimal.ZERO;
                if (taxExp != null && incBT != null && incBT.compareTo(BigDecimal.ZERO) > 0) {
                    taxRate = taxExp.divide(incBT, 4, RoundingMode.HALF_UP)
                            .max(BigDecimal.ZERO).min(BigDecimal.ONE);
                }
                BigDecimal nopat = opIncome.multiply(BigDecimal.ONE.subtract(taxRate));
                BigDecimal investedCapital = equity.add(debt).subtract(cash);
                if (investedCapital.compareTo(BigDecimal.ZERO) <= 0) return null;
                return nopat.divide(investedCapital, 4, RoundingMode.HALF_UP);
            }),
            Confidence.LOW_MEDIUM));

        // ── Shareholder Distribution ────────────────────
        register("net_buyback", def("Net Share Buybacks",
            MetricGroup.SHAREHOLDER_DISTRIBUTION, Formula.subtract("ShareRepurchases", "ShareIssuance"),
            Confidence.MEDIUM));

        register("total_shareholder_return_cash", def("Total Cash Returned to Shareholders",
            MetricGroup.SHAREHOLDER_DISTRIBUTION, Formula.add("DividendsPaid", "net_buyback"),
            Confidence.MEDIUM));
    }
}
```

## MetricComputationService

```java
@Service
@RequiredArgsConstructor
public class MetricComputationService {

    private final XbrlFactRepository factRepository;
    private final MetricRegistry metricRegistry;
    private final MetricValueRepository metricValueRepository;

    public ComputationResult computeAll(String cik) {
        List<LocalDate> periods = factRepository.getDistinctPeriodEnds(cik, "FY");
        int computed = 0;

        for (LocalDate periodEnd : periods) {
            Map<String, BigDecimal> periodFacts = factRepository
                    .getConceptsAtPeriod(cik, metricRegistry.getAllRequiredConcepts(), periodEnd);
            Map<String, BigDecimal> derivedMetrics = new LinkedHashMap<>();

            for (MetricDefinition metric : metricRegistry.getInDependencyOrder()) {
                BigDecimal value = metric.getFormula().compute(periodFacts, derivedMetrics);
                if (value == null && metric.getFallback() != null) {
                    value = metric.getFallback().compute(periodFacts, derivedMetrics);
                }
                if (value != null) {
                    derivedMetrics.put(metric.getId(), value);
                    metricValueRepository.save(MetricValue.builder()
                            .cik(cik).metricName(metric.getId())
                            .periodEnd(periodEnd).periodKind("FY")
                            .value(value).confidence(metric.getConfidence())
                            .build());
                    computed++;
                }
            }
        }
        computeTimeSeriesMetrics(cik);
        return new ComputationResult(cik, computed, periods.size());
    }

    private void computeTimeSeriesMetrics(String cik) {
        Map<LocalDate, BigDecimal> dpsHistory = factRepository.getAnnualTimeSeries(cik, "DividendsPerShare");
        if (dpsHistory.size() >= 2) {
            computeAndSaveCagr(cik, "dps_cagr_1y", dpsHistory, 1);
            computeAndSaveCagr(cik, "dps_cagr_3y", dpsHistory, 3);
            computeAndSaveCagr(cik, "dps_cagr_5y", dpsHistory, 5);
            computeAndSaveCagr(cik, "dps_cagr_10y", dpsHistory, 10);
        }
        for (String concept : List.of("Revenue", "OperatingCashFlow")) {
            Map<LocalDate, BigDecimal> series = factRepository.getAnnualTimeSeries(cik, concept);
            computeAndSaveCagr(cik, concept.toLowerCase() + "_cagr_5y", series, 5);
        }
        computeDividendStreaks(cik, dpsHistory);
        for (String concept : List.of("Revenue", "OperatingCashFlow", "DividendsPerShare")) {
            Map<LocalDate, BigDecimal> series = factRepository.getAnnualTimeSeries(cik, concept);
            computeVolatilityScore(cik, concept.toLowerCase() + "_volatility", series);
        }
    }

    private void computeDividendStreaks(String cik, Map<LocalDate, BigDecimal> dpsHistory) {
        if (dpsHistory.isEmpty()) return;
        List<Map.Entry<LocalDate, BigDecimal>> sorted = dpsHistory.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList();

        int uninterrupted = 0;
        for (int i = sorted.size() - 1; i >= 0; i--) {
            if (sorted.get(i).getValue().compareTo(BigDecimal.ZERO) > 0) uninterrupted++;
            else break;
        }
        int consecutiveRaises = 0;
        for (int i = sorted.size() - 1; i >= 1; i--) {
            if (sorted.get(i).getValue().compareTo(sorted.get(i - 1).getValue()) > 0) consecutiveRaises++;
            else break;
        }
        long cuts = 0;
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i - 1).getValue().compareTo(BigDecimal.ZERO) > 0
                    && sorted.get(i).getValue().compareTo(sorted.get(i - 1).getValue()) < 0) cuts++;
        }
        LocalDate latest = sorted.get(sorted.size() - 1).getKey();
        saveMetric(cik, "uninterrupted_years", latest, BigDecimal.valueOf(uninterrupted));
        saveMetric(cik, "consecutive_raises", latest, BigDecimal.valueOf(consecutiveRaises));
        saveMetric(cik, "total_cuts", latest, BigDecimal.valueOf(cuts));
    }
}
```

## Metric Values Table

```
┌──────────────────────────────────────────────────────────────┐
│ metric_values                                                 │
├──────────────────────────────────────────────────────────────┤
│ id                BIGINT       PK                            │
│ cik               VARCHAR(10)  FK                             │
│ metric_name       VARCHAR(50)  -- from MetricRegistry        │
│ period_end        DATE                                        │
│ period_kind       VARCHAR(4)   -- "FY", "TTM", "Q1"..       │
│ value             DECIMAL(20,4)                               │
│ confidence        VARCHAR(10)  -- HIGH, MEDIUM, LOW          │
│ as_of_accession   VARCHAR(25)                                │
│ computed_at       TIMESTAMP                                   │
│                                                               │
│ UNIQUE INDEX: (cik, metric_name, period_end, period_kind)   │
│ INDEX: (cik, metric_name)                                    │
└──────────────────────────────────────────────────────────────┘
```

## Metric Groups Summary

| Group | Count |
|---|---|
| `DIVIDEND_HISTORY` | 5 |
| `DIVIDEND_STREAKS` | 7 |
| `PAYOUT_COVERAGE` | 7 |
| `LEVERAGE` | 6 |
| `LIQUIDITY` | 3 |
| `PROFITABILITY` | 6 |
| `GROWTH` | 8 |
| `VOLATILITY` | 5 |
| `SHAREHOLDER_DIST` | 5 |
| **Total** | **52+** |

## Validation Checklist

- [ ] Apple (AAPL) metrics match known values (DPS ~$0.96, FCF payout ~15%)
- [ ] Johnson & Johnson (JNJ) shows 60+ uninterrupted years
- [ ] AT&T (T) shows dividend cut in 2022 correctly detected
- [ ] CAGR computation handles negative base values gracefully
- [ ] TTM computation correctly sums 4 quarters
- [ ] Metric dependency ordering prevents circular references
- [ ] Proxy metrics (EBITDA, ROIC) clearly labeled

## Estimated Effort: 5-6 days

# Phase 10: Testing, Validation & Data Quality

## Objective

Validate the entire dividend dashboard pipeline end-to-end against known company data, establish data quality monitoring, and build a benchmark suite that catches regressions. This phase transforms the dashboard from "probably correct" to "demonstrably correct."

## Current Implementation Status

Implemented in code:
- dividend quality endpoint runs benchmark, consistency, freshness, annual-gap, impossible-value, and denominator-safety checks
- curated benchmark checks exist for AAPL, JNJ, and MSFT with deterministic tolerances
- event extraction tests cover declaration, policy language, suspension, special dividends, false-positive yield text, and inline XBRL cleaning
- normalized companyfacts ingestion tests cover idempotency, current-best amendments, dimensional facts, unit normalization, and bounded bulk ingestion

Remaining operational validation:
- run benchmark comparisons against snapshotted or live SEC fixtures for the full 10-company benchmark set
- measure cached API and metric computation latency in a production-like runtime

## Testing Strategy

```
┌──────────────────────────────────────────────────────────────────┐
│ Level 1: Unit Tests                                               │
│   Formula correctness, tag mapping, pattern matching,            │
│   edge cases (negative FCF, missing data, zero denominators)     │
├──────────────────────────────────────────────────────────────────┤
│ Level 2: Integration Tests                                        │
│   SEC API responses → fact ingestion → metric computation        │
│   Using real SEC JSON fixtures (snapshotted, not live)            │
├──────────────────────────────────────────────────────────────────┤
│ Level 3: Benchmark Tests (Known Answers)                         │
│   Compare computed metrics against verified external sources     │
│   for a curated set of 10-20 companies                           │
├──────────────────────────────────────────────────────────────────┤
│ Level 4: Data Quality Monitoring                                  │
│   Ongoing validation rules that flag anomalies in production     │
└──────────────────────────────────────────────────────────────────┘
```

## 10.1 Unit Tests

### Formula Tests

```java
@Test
void fcfPayoutRatio_withPositiveFcf_computesCorrectly() {
    Map<String, BigDecimal> facts = Map.of(
        "OperatingCashFlow", new BigDecimal("104038000000"),
        "CapitalExpenditures", new BigDecimal("10959000000"),
        "DividendsPaid", new BigDecimal("14841000000")
    );
    // FCF = 104038 - 10959 = 93079
    // FCF Payout = 14841 / 93079 = 0.1595
    BigDecimal result = metricRegistry.get("fcf_payout").compute(facts, Map.of());
    assertThat(result).isCloseTo(new BigDecimal("0.1595"), within(new BigDecimal("0.001")));
}

@Test
void fcfPayoutRatio_withNegativeFcf_returnsNull() {
    Map<String, BigDecimal> facts = Map.of(
        "OperatingCashFlow", new BigDecimal("-5000000"),
        "CapitalExpenditures", new BigDecimal("10000000"),
        "DividendsPaid", new BigDecimal("3000000")
    );
    BigDecimal result = metricRegistry.get("fcf_payout").compute(facts, Map.of());
    assertThat(result).isNull();  // Undefined when FCF <= 0
}

@Test
void dividendStreaks_withCut_resetsConsecutiveRaises() {
    Map<LocalDate, BigDecimal> dpsHistory = new LinkedHashMap<>();
    dpsHistory.put(LocalDate.of(2018, 12, 31), new BigDecimal("1.00"));
    dpsHistory.put(LocalDate.of(2019, 12, 31), new BigDecimal("1.10"));
    dpsHistory.put(LocalDate.of(2020, 12, 31), new BigDecimal("1.20"));
    dpsHistory.put(LocalDate.of(2021, 12, 31), new BigDecimal("0.80"));  // CUT
    dpsHistory.put(LocalDate.of(2022, 12, 31), new BigDecimal("0.90"));
    dpsHistory.put(LocalDate.of(2023, 12, 31), new BigDecimal("1.00"));

    DividendStreaks streaks = computeStreaks(dpsHistory);
    assertThat(streaks.getUninterruptedYears()).isEqualTo(6);
    assertThat(streaks.getConsecutiveRaises()).isEqualTo(2);  // 2022→2023 only
    assertThat(streaks.getTotalCuts()).isEqualTo(1);
}
```

### Pattern Matching Tests

```java
@Test
void declarationPattern_matchesTypical8KText() {
    String text = "The Board of Directors of Apple Inc. declared a cash dividend of " +
                  "$0.25 per share of the Company's common stock. The dividend is " +
                  "payable on February 13, 2025 to shareholders of record as of " +
                  "the close of business on February 10, 2025.";

    List<DividendEvent> events = extractor.extract(text, "0000320193-25-000015",
            "8-K", "0000320193", LocalDate.of(2025, 1, 30));

    assertThat(events).hasSize(1);
    assertThat(events.get(0).getAmountPerShare()).isEqualByComparingTo("0.25");
    assertThat(events.get(0).getPayableDate()).isEqualTo(LocalDate.of(2025, 2, 13));
    assertThat(events.get(0).getRecordDate()).isEqualTo(LocalDate.of(2025, 2, 10));
    assertThat(events.get(0).getEventType()).isEqualTo(EventType.DECLARATION);
}

@Test
void suspensionPattern_matchesSuspensionLanguage() {
    String text = "Due to the unprecedented impact of COVID-19, the Company has " +
                  "suspended its quarterly dividend effective immediately.";

    List<DividendEvent> events = extractor.extract(text, "acc123", "8-K", "cik123",
            LocalDate.of(2020, 4, 1));

    assertThat(events).anyMatch(e -> e.getEventType() == EventType.SUSPENSION);
}

@Test
void declarationPattern_noFalsePositiveOnYieldMention() {
    String text = "The current dividend yield is approximately 2.5%, which is " +
                  "competitive with peers in the sector.";

    List<DividendEvent> events = extractor.extract(text, "acc123", "10-K", "cik123",
            LocalDate.of(2024, 12, 31));

    assertThat(events).isEmpty();  // No declaration, just a mention
}
```

### Edge Case Tests

```java
@Test
void currentBestSelection_prefersAmendmentOverOriginal() {
    // Insert 10-K fact for FY2023, then 10-K/A for same period
    // Assert 10-K/A is selected as current best
}

@Test
void cagrComputation_handlesNegativeBaseGracefully() {
    // Revenue went from -5M to +10M — CAGR is undefined for negative base
    assertThat(computeCagr(new BigDecimal("-5"), new BigDecimal("10"), 3)).isNull();
}

@Test
void unitNormalization_handlesAllVariants() {
    assertThat(UnitNormalizer.normalize("usd")).isEqualTo("USD");
    assertThat(UnitNormalizer.normalize("USD/shares")).isEqualTo("USD-per-shares");
    assertThat(UnitNormalizer.normalize("usd-per-shares")).isEqualTo("USD-per-shares");
    assertThat(UnitNormalizer.normalize("pure")).isEqualTo("PURE");
    assertThat(UnitNormalizer.normalize(null)).isEqualTo("USD");
}
```

## 10.2 Integration Tests

Use snapshotted SEC JSON responses (checked into `src/test/resources/fixtures/`):

```java
@SpringBootTest
@TestPropertySource(properties = "edgar4j.sec.use-fixtures=true")
class CompanyFactsIngestionIntegrationTest {

    @Autowired CompanyFactsIngestionService ingestionService;
    @Autowired XbrlFactRepository factRepository;

    @Test
    void ingestApple_producesExpectedFactCount() {
        // Uses fixture: src/test/resources/fixtures/companyfacts/CIK0000320193.json
        IngestionResult result = ingestionService.ingest("320193");

        assertThat(result.getInserted()).isGreaterThan(100);
        assertThat(result.getSkipped()).isEqualTo(0);

        // Verify specific dividend facts exist
        List<XbrlFactEntity> dpsFacts = factRepository
                .findCurrentBest("0000320193", "DividendsPerShare");
        assertThat(dpsFacts).isNotEmpty();
        assertThat(dpsFacts.stream().map(XbrlFactEntity::getFiscalPeriod))
                .contains("FY");
    }

    @Test
    void reingestApple_isIdempotent() {
        ingestionService.ingest("320193");
        IngestionResult second = ingestionService.ingest("320193");

        assertThat(second.getInserted()).isEqualTo(0);
        assertThat(second.getUpdated()).isEqualTo(0);
    }
}
```

## 10.3 Benchmark Tests (Known Answers)

Compare computed metrics against verified values from multiple sources. These are the **most critical tests** — they prove the dashboard produces correct numbers.

### Benchmark Company Set

| Company | CIK | Why Selected |
|---|---|---|
| Apple (AAPL) | 320193 | Large cap, consistent dividend growth, high FCF |
| Johnson & Johnson (JNJ) | 200406 | 60+ year dividend streak, Dividend King |
| AT&T (T) | 732717 | Dividend cut in 2022 — tests cut detection |
| Realty Income (O) | 726728 | REIT — monthly dividend, high payout ratio |
| Costco (COST) | 909832 | Special dividends — tests special detection |
| Ford (F) | 37996 | Suspended dividend 2020, reinstated 2021 |
| Microsoft (MSFT) | 789019 | Tech, growing dividend, share buybacks |
| Exxon (XOM) | 34088 | Cyclical, maintained dividend through downturn |
| Main Street Capital (MAIN) | 1396440 | BDC — high payout, monthly dividend |
| Duke Energy (DUK) | 1326160 | Utility — regulated, stable, high payout |

### Benchmark Test Structure

```java
@ParameterizedTest
@CsvSource({
    "320193,  2023, 0.96,  0.159,  12, 12",   // Apple FY2023
    "200406,  2023, 4.70,  0.468,  61, 61",   // JNJ FY2023
    "789019,  2023, 2.72,  0.251,  18, 18",   // MSFT FY2023
})
void verifyDividendMetrics(String cik, int fy, double expectedDps,
        double expectedFcfPayout, int expectedUninterrupted, int expectedRaises) {

    metricComputationService.computeAll(cik);

    LocalDate fyEnd = getFiscalYearEnd(cik, fy);

    BigDecimal dps = metricValueRepository.getValue(cik, "dps_declared", fyEnd);
    BigDecimal fcfPayout = metricValueRepository.getValue(cik, "fcf_payout", fyEnd);
    BigDecimal uninterrupted = metricValueRepository.getLatestValue(cik, "uninterrupted_years");
    BigDecimal raises = metricValueRepository.getLatestValue(cik, "consecutive_raises");

    // Allow 5% tolerance for rounding differences
    assertThat(dps.doubleValue()).isCloseTo(expectedDps, within(expectedDps * 0.05));
    assertThat(fcfPayout.doubleValue()).isCloseTo(expectedFcfPayout, within(0.03));
    assertThat(uninterrupted.intValue()).isGreaterThanOrEqualTo(expectedUninterrupted - 1);
    assertThat(raises.intValue()).isGreaterThanOrEqualTo(expectedRaises - 1);
}
```

### Alert Benchmark Tests

```java
@Test
void attDividendCut_triggersAlert() {
    metricComputationService.computeAll("732717");
    alertEvaluationService.evaluateAlerts("732717");

    List<DividendAlert> alerts = alertRepository.findByCik("732717");
    assertThat(alerts)
            .anyMatch(a -> "dividend_cut".equals(a.getRuleId())
                    && a.getPeriodEnd().getYear() == 2022);
}

@Test
void fordSuspension_triggersAlert() {
    // Ford suspended dividend in March 2020
    eventSync.syncDividendEvents("37996", LocalDate.of(2020, 1, 1));
    alertEvaluationService.evaluateAlerts("37996");

    List<DividendAlert> alerts = alertRepository.findByCik("37996");
    assertThat(alerts)
            .anyMatch(a -> "dividend_suspended".equals(a.getRuleId()));
}
```

## 10.4 Data Quality Monitoring

### Ongoing Validation Rules

Run these after each sync cycle to catch data quality issues:

```java
@Component
@RequiredArgsConstructor
public class DataQualityValidator {

    /**
     * Run all data quality checks for a company.
     */
    public List<DataQualityIssue> validate(String cik) {
        List<DataQualityIssue> issues = new ArrayList<>();

        // 1. Completeness: all expected annual periods have key facts
        issues.addAll(checkCompleteness(cik));

        // 2. Consistency: balance sheet equation holds
        issues.addAll(checkBalanceSheetEquation(cik));

        // 3. Temporal consistency: no impossible YoY changes
        issues.addAll(checkTemporalConsistency(cik));

        // 4. Cross-metric consistency: DPS × shares ≈ total dividends paid
        issues.addAll(checkCrossMetricConsistency(cik));

        // 5. Staleness: data older than expected
        issues.addAll(checkStaleness(cik));

        return issues;
    }

    /**
     * Check that key concepts have data for every expected fiscal year.
     */
    private List<DataQualityIssue> checkCompleteness(String cik) {
        List<DataQualityIssue> issues = new ArrayList<>();

        for (String concept : List.of("Revenue", "NetIncome", "OperatingCashFlow")) {
            Map<LocalDate, BigDecimal> series = factRepository.getAnnualTimeSeries(cik, concept);

            // Check for gaps in annual data
            List<Integer> years = series.keySet().stream()
                    .map(LocalDate::getYear)
                    .sorted()
                    .toList();

            for (int i = 1; i < years.size(); i++) {
                if (years.get(i) - years.get(i - 1) > 1) {
                    issues.add(new DataQualityIssue(
                            Severity.MEDIUM,
                            String.format("Gap in %s data: missing year %d",
                                    concept, years.get(i - 1) + 1)));
                }
            }
        }
        return issues;
    }

    /**
     * Verify DPS × shares ≈ total dividends paid (within 10%).
     */
    private List<DataQualityIssue> checkCrossMetricConsistency(String cik) {
        List<DataQualityIssue> issues = new ArrayList<>();

        Map<LocalDate, BigDecimal> dps = factRepository.getAnnualTimeSeries(cik, "DividendsPerShare");
        Map<LocalDate, BigDecimal> totalPaid = factRepository.getAnnualTimeSeries(cik, "DividendsPaid");
        Map<LocalDate, BigDecimal> shares = factRepository.getAnnualTimeSeries(cik, "SharesOutstanding");

        for (LocalDate period : dps.keySet()) {
            BigDecimal dpsVal = dps.get(period);
            BigDecimal totalVal = totalPaid.get(period);
            BigDecimal sharesVal = shares.get(period);

            if (dpsVal != null && totalVal != null && sharesVal != null
                    && sharesVal.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal impliedTotal = dpsVal.multiply(sharesVal);
                BigDecimal ratio = impliedTotal.divide(totalVal, 4, RoundingMode.HALF_UP);

                // Should be close to 1.0 (within 10%)
                if (ratio.doubleValue() < 0.9 || ratio.doubleValue() > 1.1) {
                    issues.add(new DataQualityIssue(
                            Severity.LOW,
                            String.format("DPS × shares / total dividends = %.2f for period %s " +
                                    "(expected ~1.0)", ratio.doubleValue(), period)));
                }
            }
        }
        return issues;
    }

    /**
     * Flag impossible YoY changes (e.g., revenue jumping 1000%).
     */
    private List<DataQualityIssue> checkTemporalConsistency(String cik) {
        List<DataQualityIssue> issues = new ArrayList<>();

        for (String concept : List.of("Revenue", "NetIncome", "DividendsPerShare")) {
            Map<LocalDate, BigDecimal> series = factRepository.getAnnualTimeSeries(cik, concept);
            List<Map.Entry<LocalDate, BigDecimal>> sorted = series.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .toList();

            for (int i = 1; i < sorted.size(); i++) {
                BigDecimal prev = sorted.get(i - 1).getValue();
                BigDecimal curr = sorted.get(i).getValue();
                if (prev != null && curr != null && prev.compareTo(BigDecimal.ZERO) != 0) {
                    double change = curr.subtract(prev).divide(prev.abs(), 4, RoundingMode.HALF_UP).doubleValue();
                    if (Math.abs(change) > 10.0) {  // > 1000% change
                        issues.add(new DataQualityIssue(
                                Severity.HIGH,
                                String.format("%s changed %.0f%% from %s to %s — possible data error",
                                        concept, change * 100, sorted.get(i - 1).getKey(),
                                        sorted.get(i).getKey())));
                    }
                }
            }
        }
        return issues;
    }
}
```

### Data Quality Dashboard Endpoint

```java
@GetMapping("/api/dividend/{tickerOrCik}/quality")
public Mono<DataQualityReport> getDataQuality(@PathVariable String tickerOrCik) {
    String cik = resolveIdentifier(tickerOrCik);
    List<DataQualityIssue> issues = dataQualityValidator.validate(cik);
    return Mono.just(new DataQualityReport(cik, issues,
            issues.isEmpty() ? "CLEAN" : "ISSUES_FOUND"));
}
```

## 10.5 Test Fixtures Management

Store snapshotted SEC responses for reproducible testing:

```
src/test/resources/fixtures/
├── companyfacts/
│   ├── CIK0000320193.json    # Apple
│   ├── CIK0000200406.json    # JNJ
│   ├── CIK0000732717.json    # AT&T
│   └── ...
├── submissions/
│   ├── CIK0000320193.json
│   └── ...
├── filings/
│   ├── 8k-dividend-declaration.html
│   ├── 8k-dividend-suspension.html
│   └── 8k-special-dividend.html
└── expected/
    ├── apple-metrics-fy2023.json
    ├── jnj-metrics-fy2023.json
    └── ...
```

### Fixture Update Script

```java
/**
 * Utility to refresh test fixtures from live SEC data.
 * Run manually when you need to update benchmark data.
 * NOT part of the automated test suite (hits live SEC).
 */
@Test
@Disabled("Manual: run to refresh fixtures from live SEC")
void refreshFixtures() {
    for (String cik : BENCHMARK_CIKS) {
        String json = secApiClient.fetchCompanyFacts(cik);
        Files.writeString(
            Path.of("src/test/resources/fixtures/companyfacts/CIK" + normalizeCik(cik) + ".json"),
            json);
    }
}
```

## 10.6 Performance Benchmarks

```java
@Test
void ingestionPerformance_1000Companies_under30minutes() {
    // Using fixtures to avoid SEC rate limits
    List<String> ciks = loadTestCiks(1000);
    long start = System.currentTimeMillis();

    for (String cik : ciks) {
        ingestionService.ingest(cik);
    }

    long elapsed = System.currentTimeMillis() - start;
    assertThat(elapsed).isLessThan(30 * 60 * 1000);  // 30 minutes
}

@Test
void metricComputation_singleCompany_under2seconds() {
    ingestionService.ingest("320193");
    long start = System.currentTimeMillis();

    metricComputationService.computeAll("320193");

    long elapsed = System.currentTimeMillis() - start;
    assertThat(elapsed).isLessThan(2000);
}

@Test
void dashboardApiResponse_under200ms() {
    // Pre-populate data
    ingestionService.ingest("320193");
    metricComputationService.computeAll("320193");

    long start = System.currentTimeMillis();
    webTestClient.get().uri("/api/dividend/AAPL")
            .exchange()
            .expectStatus().isOk();
    long elapsed = System.currentTimeMillis() - start;

    assertThat(elapsed).isLessThan(200);
}
```

## Validation Checklist

- [ ] All 52+ metric formulas have unit tests
- [x] Edge cases covered: negative FCF, zero denominators, missing data
- [x] Regex patterns tested against representative 8-K filing text
- [x] No false positives in dividend event extraction
- [ ] Integration tests pass with snapshotted fixtures
- [ ] Benchmark tests match known values for 10 companies (within 5% tolerance)
- [ ] AT&T dividend cut correctly detected and alerted
- [x] Ford-style suspension language correctly detected
- [x] Data quality validator catches gaps and impossible values
- [x] Ingestion is idempotent (re-run produces zero changes)
- [ ] API response times < 200ms (cached data)
- [ ] Metric computation < 2s per company

## Estimated Effort: 4-5 days

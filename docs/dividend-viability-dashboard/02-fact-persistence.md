# Phase 2: XBRL Fact Persistence & Normalization

## Objective

Create a persistent data model and ingestion pipeline that transforms raw SEC Company Facts JSON into a normalized, queryable fact store. This is the numerical backbone of the dividend dashboard — every metric computation in Phase 4 queries this layer.

## Design Principle: Incremental, Idempotent, Restatement-Aware

- **Store all fact versions** keyed by accession number
- **Select "current best"** per (cik, tag, unit, period) using a deterministic policy
- **Idempotent writes** — re-ingesting the same companyfacts JSON produces zero duplicates
- **Extend existing ConceptStandardizer** with additional dividend/leverage/coverage tags

## Data Model

### 2.1 Core Tables / Documents

```
┌──────────────────────────────────────────────────────────────┐
│ dim_dividend_company                                          │
├──────────────────────────────────────────────────────────────┤
│ cik                VARCHAR(10)   PK                          │
│ entity_name        VARCHAR(255)                               │
│ ticker             VARCHAR(10)                                │
│ exchange           VARCHAR(10)                                │
│ sic_code           VARCHAR(10)                                │
│ sector             VARCHAR(100)                               │
│ industry           VARCHAR(100)                               │
│ fiscal_year_end    VARCHAR(4)     -- "1231", "0930", etc.    │
│ last_facts_sync    TIMESTAMP                                  │
│ created_at         TIMESTAMP                                  │
│ updated_at         TIMESTAMP                                  │
└──────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│ fact_xbrl                                                     │
│ (All XBRL numeric facts, all versions)                        │
├──────────────────────────────────────────────────────────────┤
│ id                 BIGINT        PK (auto)                   │
│ cik                VARCHAR(10)   FK → dim_dividend_company   │
│ taxonomy           VARCHAR(50)   -- "us-gaap", "dei", "ifrs" │
│ tag                VARCHAR(255)  -- concept local name       │
│ standard_concept   VARCHAR(100)  -- ConceptStandardizer name │
│ unit               VARCHAR(50)   -- "USD", "USD-per-shares"  │
│ period_end         DATE                                       │
│ period_start       DATE          -- NULL for instant facts   │
│ value              DECIMAL(20,4)                              │
│ accession          VARCHAR(25)   -- filing accession number  │
│ form               VARCHAR(10)   -- "10-K", "10-Q", "8-K"   │
│ fiscal_year        INT                                        │
│ fiscal_period      VARCHAR(4)    -- "FY", "Q1", "Q2", etc.  │
│ filed_date         DATE                                       │
│ frame              VARCHAR(20)   -- "CY2023Q4I", etc.        │
│ dimensions_hash    VARCHAR(64)   -- SHA-256 of dimensions    │
│ is_current_best    BOOLEAN       -- TRUE for selected version│
│ created_at         TIMESTAMP                                  │
│ updated_at         TIMESTAMP                                  │
│                                                               │
│ UNIQUE INDEX: (cik, taxonomy, tag, unit, period_end,         │
│                period_start, dimensions_hash, accession)     │
│ INDEX: (cik, standard_concept, period_end)                   │
│ INDEX: (cik, tag, period_end)                                │
│ INDEX: (cik, is_current_best, standard_concept)              │
└──────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│ dim_filing                                                    │
├──────────────────────────────────────────────────────────────┤
│ accession          VARCHAR(25)   PK                          │
│ cik                VARCHAR(10)   FK                           │
│ form               VARCHAR(10)                                │
│ filed_date         DATE                                       │
│ report_period_end  DATE                                       │
│ is_amendment       BOOLEAN       -- TRUE for 10-K/A, etc.   │
│ primary_document   VARCHAR(255)                               │
│ filing_url         VARCHAR(500)                               │
│ created_at         TIMESTAMP                                  │
└──────────────────────────────────────────────────────────────┘
```

### 2.2 MongoDB Document Alternative

```java
@Document(collection = "xbrl_facts")
@CompoundIndex(def = "{'cik': 1, 'taxonomy': 1, 'tag': 1, 'unit': 1, 'periodEnd': 1, 'accession': 1}", unique = true)
@CompoundIndex(def = "{'cik': 1, 'standardConcept': 1, 'periodEnd': 1, 'isCurrentBest': 1}")
public class XbrlFactEntity {
    @Id
    private String id;
    private String cik;
    private String taxonomy;
    private String tag;
    private String standardConcept;
    private String unit;
    private LocalDate periodEnd;
    private LocalDate periodStart;
    private BigDecimal value;
    private String accession;
    private String form;
    private int fiscalYear;
    private String fiscalPeriod;
    private LocalDate filedDate;
    private String frame;
    private String dimensionsHash;
    private boolean isCurrentBest;
    private Instant createdAt;
    private Instant updatedAt;
}
```

## 2.3 Extended Concept Mappings

Add these to `ConceptStandardizer` (extending the existing 20+ mappings):

```java
// ── Dividend-Specific ──────────────────────────────────
"DividendsPerShareCashPaid"  → [CommonStockDividendsPerShareCashPaid]
"DividendsPayable"           → [DividendsPayableCurrent, DividendsPayableCurrentAndNoncurrent, DistributionPayable]
"PreferredDividendsPaid"     → [PaymentsOfDividendsPreferredStockAndPreferenceStock, PreferredStockDividendsAndOtherAdjustments]
"TotalDividendsPaid"         → [PaymentsOfDividends]

// ── Leverage & Debt ────────────────────────────────────
"ShortTermDebt"              → [DebtCurrent, ShortTermBorrowings, ShortTermDebt]
"InterestExpense"            → [InterestExpense, InterestExpenseDebt, InterestPaid]

// ── Income Taxes ───────────────────────────────────────
"IncomeTaxExpense"           → [IncomeTaxExpenseBenefit]
"IncomeBeforeTaxes"          → [IncomeLossFromContinuingOperationsBeforeIncomeTaxes...]

// ── Depreciation & Amortization ────────────────────────
"DepreciationAmortization"   → [DepreciationDepletionAndAmortization, DepreciationAndAmortization, Depreciation]

// ── Share Buybacks ─────────────────────────────────────
"ShareRepurchases"           → [PaymentsForRepurchaseOfCommonStock, StockRepurchasedAndRetiredDuringPeriodValue]
"ShareIssuance"              → [ProceedsFromIssuanceOfCommonStock, StockIssuedDuringPeriodValue]
"WeightedAvgSharesDiluted"   → [WeightedAverageNumberOfDilutedSharesOutstanding]
"WeightedAvgSharesBasic"     → [WeightedAverageNumberOfSharesOutstandingBasic]

// ── Receivables (for quick ratio) ──────────────────────
"AccountsReceivable"         → [AccountsReceivableNetCurrent, ReceivablesNetCurrent]
"ShortTermInvestments"       → [ShortTermInvestments, AvailableForSaleSecuritiesCurrent, MarketableSecuritiesCurrent]
```

## 2.4 Ingestion Pipeline

### CompanyFactsIngestionService

```java
@Service
@RequiredArgsConstructor
public class CompanyFactsIngestionService {

    private final SecApiClient secApiClient;
    private final ConceptStandardizer conceptStandardizer;
    private final XbrlFactRepository xbrlFactRepository;
    private final ObjectMapper objectMapper;

    /**
     * Ingest all XBRL facts for a company from the SEC Company Facts API.
     * Idempotent: safe to call multiple times for the same CIK.
     */
    public IngestionResult ingest(String cik) {
        // 1. Fetch raw JSON (cached by SecApiClient)
        String json = secApiClient.fetchCompanyFacts(cik);
        CompanyFactsResponse response = objectMapper.readValue(json, CompanyFactsResponse.class);

        // 2. Iterate taxonomy → tag → unit → facts
        int inserted = 0, updated = 0, skipped = 0;

        for (var taxonomyEntry : response.getFacts().entrySet()) {
            String taxonomy = taxonomyEntry.getKey();
            for (var tagEntry : taxonomyEntry.getValue().entrySet()) {
                String tag = tagEntry.getKey();
                if (!RELEVANT_TAGS.contains(tag)) continue;  // Filter to relevant concepts

                String standardConcept = conceptStandardizer.mapToStandard(tag);

                for (var unitEntry : tagEntry.getValue().getUnits().entrySet()) {
                    String unit = unitEntry.getKey();
                    for (var factEntry : unitEntry.getValue()) {
                        XbrlFactEntity fact = buildFact(cik, taxonomy, tag,
                                standardConcept, unit, factEntry);
                        UpsertResult result = xbrlFactRepository.upsert(fact);
                        switch (result) {
                            case INSERTED -> inserted++;
                            case UPDATED -> updated++;
                            case SKIPPED -> skipped++;
                        }
                    }
                }
            }
        }

        // 3. Recompute "current best" flags for this CIK
        recomputeCurrentBest(cik);
        return new IngestionResult(cik, inserted, updated, skipped);
    }
}
```

### Current-Best Selection Policy

```java
/**
 * Select the "current best" fact for each (cik, tag, unit, period) combination.
 *
 * Policy (deterministic):
 * 1. Prefer latest filed_date
 * 2. Prefer amendment forms (10-K/A > 10-K) for same period
 * 3. Prefer the accession that sorts last (tie-breaker)
 * 4. Prefer consolidated (dimensions_hash = empty/null) over segmented
 */
private void recomputeCurrentBest(String cik) {
    xbrlFactRepository.clearCurrentBest(cik);
    xbrlFactRepository.markCurrentBest(cik);
}
```

### Relevant Tag Filtering

Not all ~10,000 tags in a typical companyfacts response are needed:

```java
private static final Set<String> RELEVANT_TAGS = Set.of(
    // Dividends
    "CommonStockDividendsPerShareDeclared", "CommonStockDividendsPerShareCashPaid",
    "CommonStockDividendsPerShareDeclaredAndPaid", "PaymentsOfDividendsCommonStock",
    "PaymentsOfDividends", "DividendsCommonStockCash", "DividendsPayableCurrent",
    "PaymentsOfDividendsPreferredStockAndPreferenceStock",
    // Income Statement
    "Revenues", "RevenueFromContractWithCustomerExcludingAssessedTax",
    "NetIncomeLoss", "NetIncomeLossAvailableToCommonStockholdersBasic",
    "OperatingIncomeLoss", "EarningsPerShareBasic", "EarningsPerShareDiluted",
    "IncomeTaxExpenseBenefit", "InterestExpense", "DepreciationDepletionAndAmortization",
    // Cash Flow
    "NetCashProvidedByUsedInOperatingActivities", "PaymentsToAcquirePropertyPlantAndEquipment",
    "NetCashProvidedByUsedInFinancingActivities", "PaymentsForRepurchaseOfCommonStock",
    "ProceedsFromIssuanceOfCommonStock",
    // Balance Sheet
    "Assets", "AssetsCurrent", "Liabilities", "LiabilitiesCurrent", "StockholdersEquity",
    "CashAndCashEquivalentsAtCarryingValue", "LongTermDebt", "LongTermDebtNoncurrent",
    "DebtCurrent", "AccountsReceivableNetCurrent", "ShortTermInvestments",
    // Shares
    "CommonStockSharesOutstanding", "WeightedAverageNumberOfSharesOutstandingBasic",
    "WeightedAverageNumberOfDilutedSharesOutstanding",
    // DEI
    "EntityCommonStockSharesOutstanding"
);
```

## 2.5 Repository Interface

```java
public interface XbrlFactRepository {
    UpsertResult upsert(XbrlFactEntity fact);
    IngestionResult upsertAll(List<XbrlFactEntity> facts);
    List<XbrlFactEntity> findCurrentBest(String cik, String standardConcept);
    List<XbrlFactEntity> findCurrentBest(String cik, String standardConcept, String fiscalPeriod);
    Map<LocalDate, BigDecimal> getAnnualTimeSeries(String cik, String standardConcept);
    Optional<BigDecimal> getLatestValue(String cik, String standardConcept);
    Map<String, BigDecimal> getConceptsAtPeriod(String cik, List<String> standardConcepts, LocalDate periodEnd);
    void clearCurrentBest(String cik);
    void markCurrentBest(String cik);
    List<String> getAllTrackedCiks();
    Optional<Instant> getLastSyncTime(String cik);
    List<LocalDate> getDistinctPeriodEnds(String cik, String periodKind);
}
```

## 2.6 Unit Normalization

```java
public class UnitNormalizer {
    private static final Map<String, String> CANONICAL_UNITS = Map.of(
        "usd", "USD",
        "usd/shares", "USD-per-shares",
        "USD/shares", "USD-per-shares",
        "usd-per-shares", "USD-per-shares",
        "pure", "PURE",
        "shares", "SHARES"
    );

    public static String normalize(String unit) {
        if (unit == null) return "USD";
        return CANONICAL_UNITS.getOrDefault(unit.toLowerCase(), unit.toUpperCase());
    }
}
```

## 2.7 Period Kind Detection

```java
public static String detectPeriodKind(CompanyFactsResponse.FactEntry entry) {
    if ("FY".equals(entry.getFp())) return "FY";
    if (entry.getFp() != null && entry.getFp().startsWith("Q")) return entry.getFp();
    // Fallback: infer from period length
    if (entry.getStart() != null && entry.getEnd() != null) {
        long days = ChronoUnit.DAYS.between(
            LocalDate.parse(entry.getStart()), LocalDate.parse(entry.getEnd()));
        if (days > 350) return "FY";
        if (days > 80 && days < 100) return "Q";
    }
    return "UNKNOWN";
}
```

## Validation Checklist

- [ ] Ingesting Apple (CIK 320193) populates `DividendsPerShare` facts from 2012+
- [ ] Re-ingesting the same CIK produces zero duplicate rows
- [ ] `is_current_best` correctly selects latest filing for each period
- [ ] Amendment forms (10-K/A) override originals
- [ ] Annual vs quarterly facts are correctly classified
- [ ] Unit normalization handles "USD", "USD/shares", "USD-per-shares" consistently
- [ ] Standard concept mapping covers all 30+ dividend-relevant tags
- [ ] Ingestion of 1000+ companies completes without hitting rate limits

## Estimated Effort: 5-6 days

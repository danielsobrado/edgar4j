Dividend Viability Dashboard — Implementation Plan

## Executive Summary

Build a long-term dividend-viability dashboard using SEC/EDGAR as the primary data source, leveraging edgar4j's existing XBRL parsing, SEC API integration, concept standardization, and multi-period analysis infrastructure. The dashboard answers one question: **"Can this company keep paying — and growing — its dividend for the next 10+ years?"**

The architecture treats **XBRL facts as the canonical numerical layer** and **filing text as the event/policy layer**, with external market data filling the yield and valuation gap that EDGAR cannot cover.

---

## What Already Exists (Reusable Infrastructure)

The edgar4j codebase provides a **very strong foundation**. This table maps existing infrastructure to dashboard needs:

| Existing Component | Location | What It Provides for This Dashboard |
|---|---|---|
| `XbrlService` | `xbrl/XbrlService.java` | XBRL parsing, key financials extraction (already extracts dividend concepts), comprehensive analysis |
| `ConceptStandardizer` | `xbrl/standardization/ConceptStandardizer.java` | Dividend tag mappings already defined (`DividendsPerShare`, `DividendsPaid`), cross-company normalization |
| `MultiPeriodAnalyzer` | `xbrl/analysis/MultiPeriodAnalyzer.java` | Time series stitching, CAGR, YoY growth, trend detection, anomaly detection, volatility scoring |
| `SecApiClient` | `integration/SecApiClient.java` | Submissions, tickers, filing fetch, EFTS search, rate limiting, caching |
| `SecRateLimiter` | `integration/SecRateLimiter.java` | 10 rps token bucket (SEC compliant) |
| `DownloadedResourceStore` | `storage/DownloadedResourceStore.java` | Persistent disk cache for SEC responses |
| `SecFilingExtractor` | `xbrl/sec/SecFilingExtractor.java` | Filing metadata, form type, period, amendment detection |
| `MarketDataProvider` | `service/provider/MarketDataProvider.java` | Price, market cap, `dividendYield` already in `FinancialMetrics` DTO |
| `StatementReconstructor` | `xbrl/statement/StatementReconstructor.java` | Balance sheet, income statement, cash flow reconstruction |
| `Form4/8K models` | `model/`, `repository/` | Filing storage, accession-based lookup |
| `CalculationValidator` | `xbrl/validation/CalculationValidator.java` | XBRL calculation arc validation |

### Key Existing Concept Mappings (ConceptStandardizer)

Already mapped and ready to use:
- `DividendsPerShare` → `CommonStockDividendsPerShareDeclared`, `...CashPaid`, `...DeclaredAndPaid`
- `DividendsPaid` → `PaymentsOfDividendsCommonStock`, `DividendsCommonStockCash`, `PaymentsOfOrdinaryDividends`
- `NetIncome`, `EarningsPerShareDiluted`, `OperatingIncome`, `Revenue`
- `OperatingCashFlow`, `CapitalExpenditures`, `FinancingCashFlow`
- `TotalAssets`, `TotalLiabilities`, `TotalEquity`, `Cash`, `LongTermDebt`
- `TotalCurrentAssets`, `TotalCurrentLiabilities`, `RetainedEarnings`
- `SharesOutstanding`

### What Needs to Be Built

| Component | Why It's Needed | Phase |
|---|---|---|
| CompanyFacts API client methods | `companyfacts/CIK####.json` and `companyconcept/` endpoints not yet exposed | 1 |
| XBRL fact persistence layer | Store normalized facts by company/period/concept for time-series queries | 2 |
| Dividend event text extractor | Parse 8-K Item 8.01 / Exhibit 99.1 for declarations, suspensions, policy language | 3 |
| Extended concept mappings | ~30 additional dividend/leverage/coverage tags not yet in ConceptStandardizer | 2 |
| Metric computation engine | Payout ratios, coverage, leverage, ROIC, shareholder yield | 4 |
| Alert rules engine | Configurable threshold-based warnings | 5 |
| Dashboard REST API | Query endpoints for all metrics, alerts, filing evidence | 6 |
| Incremental sync jobs | Change detection, idempotent ingestion, restatement handling | 7 |
| External data integration | Treasury yield curve, corporate spreads | 8 |

---

## Data Architecture: Bronze → Silver → Gold

```
┌─────────────────────────────────────────────────────────────────────┐
│ BRONZE (Raw Storage)                                                │
│                                                                     │
│  companyfacts JSON ─────┐                                          │
│  companyconcept JSON ───┤                                          │
│  submissions JSON ──────┤── DownloadedResourceStore (existing)     │
│  8-K/10-K HTML/XML ─────┤                                          │
│  bulk notes datasets ───┘                                          │
└─────────────────┬───────────────────────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│ SILVER (Normalized Facts)                                           │
│                                                                     │
│  fact_xbrl table ─────── Normalized XBRL numeric facts             │
│  dividend_events table── Extracted dividend declarations/policy     │
│  dim_company table ───── CIK + metadata + sector                   │
│  dim_filing table ────── Accession + form type + dates             │
│                                                                     │
│  ConceptStandardizer (existing) → maps to canonical names          │
│  Restatement-aware: keeps all versions, selects "current best"     │
└─────────────────┬───────────────────────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│ GOLD (Derived Metrics)                                              │
│                                                                     │
│  metric_values table ─── Computed ratios per CIK/period            │
│  alerts table ────────── Fired warning rules                       │
│  dividend_scores table── Composite viability scores                │
│                                                                     │
│  MultiPeriodAnalyzer (existing) → CAGR, trends, volatility        │
│  MarketDataProvider (existing) → yield, market cap overlay         │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Phase Overview

| Phase | Title | Scope | Est. Days | Dependencies |
|---|---|---|---|---|
| [Phase 1](01-sec-api-extension.md) | SEC Company Facts API Extension | Add companyfacts/companyconcept endpoints to SecApiClient | 3-4 | None |
| [Phase 2](02-fact-persistence.md) | XBRL Fact Persistence & Normalization | Data model, repositories, ingestion pipeline for normalized facts | 5-6 | Phase 1 |
| [Phase 3](03-dividend-event-extraction.md) | Dividend Event Text Extraction | 8-K/10-K parsing for declarations, suspensions, policy language | 5-6 | Phase 1 |
| [Phase 4](04-metric-computation.md) | Metric Computation Engine | All payout, coverage, leverage, growth, volatility metrics | 5-6 | Phase 2 |
| [Phase 5](05-alert-rules-engine.md) | Alert Rules Engine | Configurable threshold-based warnings with filing evidence | 3-4 | Phase 4 |
| [Phase 6](06-dashboard-api.md) | Dashboard REST API & UX | Query endpoints, company view, peer comparison | 5-6 | Phase 4, 5 |
| [Phase 7](07-incremental-sync.md) | Incremental Sync & Change Detection | Scheduled jobs, idempotent writes, restatement handling | 4-5 | Phase 2, 3 |
| [Phase 8](08-external-data.md) | External Data: Yields & Market Data | Treasury yield curve, corporate spreads, dividend yield overlay | 3-4 | Phase 4 |
| [Phase 9](09-bulk-datasets.md) | SEC Bulk Notes Datasets (Optional) | Monthly bulk ingestion for wide coverage | 4-5 | Phase 2 |
| [Phase 10](10-testing-and-validation.md) | Testing, Validation & Data Quality | End-to-end validation, known-company benchmarks | 4-5 | Phase 6 |

**Total estimated: 43-51 days (~9-11 weeks)**

**Critical path**: Phase 1 → Phase 2 → Phase 4 → Phase 6

---

## Metric Catalogue Summary

### Confidence Levels

- **High**: Widely used standard XBRL tags; minimal modeling ambiguity
- **Medium**: Standard tags exist but issuer practice varies; normalization needed
- **Low**: No reliable standard tag; depends on text parsing or extensions

### Metric Groups (65+ metrics)

| Group | Metric Count | Avg Confidence |
|---|---|---|
| Dividend History & Policy | 10 | High-Medium |
| Payout & Coverage | 9 | Medium |
| Balance Sheet & Leverage | 10 | Medium |
| Liquidity | 4 | High-Medium |
| Growth & Profitability | 10 | Medium |
| Shareholder Distribution | 5 | Medium-Low |
| Volatility & Durability | 6 | Medium |
| Warning Flags | 8 | Medium |
| Market-Based (external) | 3+ | Medium |

---

## Key Design Decisions

1. **XBRL Company Facts API as primary data backbone** — one API call per CIK returns all standardized concepts across all filings. Avoids per-filing parsing for the numerical layer.

2. **Filing text for events only** — parse 8-K Item 8.01 + Exhibit 99.1 for dividend declarations/suspensions. Don't try to extract numbers from text when XBRL provides them.

3. **Restatement-aware fact selection** — store all versions keyed by accession; select "current best" using `(filed_date DESC, amendment_form_priority, accession DESC)`.

4. **Idempotent writes** — composite key `(cik, taxonomy, tag, unit, period_end, period_start, dimensions_hash)` prevents duplicates across retries and re-ingestion.

5. **Extend existing infrastructure** — add to `SecApiClient`, `ConceptStandardizer`, and `MultiPeriodAnalyzer` rather than building parallel systems.

6. **Market data as overlay, not core** — EDGAR fundamentals are the source of truth; external prices provide yield/valuation context only.

---

## Full Tag → Metric Mapping Table

| Metric / Component | Primary SEC tag(s) | Fallback | Units | Freq | Confidence |
|---|---|---|---|---|---|
| DPS (declared) | `us-gaap:CommonStockDividendsPerShareDeclared` | Derive: `PaymentsOfDividendsCommonStock ÷ shares` | USD-per-shares | Q & FY | High |
| DPS (cash paid) | `us-gaap:CommonStockDividendsPerShareCashPaid` | Use declared DPS | USD-per-shares | Q & FY | Medium |
| Total common divs paid | `us-gaap:PaymentsOfDividendsCommonStock` | `DividendsCommonStockCash` | USD | Q & FY | High |
| Total preferred divs | `us-gaap:PaymentsOfDividendsPreferredStockAndPreferenceStock` | Bulk notes dataset | USD | Q & FY | Medium |
| Dividend payable | `us-gaap:DividendsPayableCurrent` | `DistributionPayable` | USD (instant) | Q & FY | Medium |
| Net income | `us-gaap:NetIncomeLoss` | `NetIncomeLossAvailableToCommonStockholdersBasic` | USD | Q & FY | High |
| Diluted EPS | `us-gaap:EarningsPerShareDiluted` | `EarningsPerShareBasic` | USD-per-shares | Q & FY | High |
| OCF | `us-gaap:NetCashProvidedByUsedInOperatingActivities` | — | USD | Q & FY | High |
| Capex | `us-gaap:PaymentsToAcquirePropertyPlantAndEquipment` | `PaymentsToAcquireProductiveAssets` | USD | Q & FY | Medium |
| Total debt | `us-gaap:DebtCurrent` + `LongTermDebt` | Lease-adjusted variants | USD | Q & FY | Medium |
| Cash | `us-gaap:CashAndCashEquivalentsAtCarryingValue` | Restricted cash variants | USD | Q & FY | High |
| Equity | `us-gaap:StockholdersEquity` | `...IncludingPortionAttributableToNoncontrollingInterest` | USD | Q & FY | High |
| Interest expense | `us-gaap:InterestExpense` | `InterestExpenseDebt` | USD | Q & FY | Medium |
| D&A | `us-gaap:DepreciationDepletionAndAmortization` | `DepreciationAndAmortization` | USD | Q & FY | Medium |
| Revenue | `us-gaap:Revenues` | `RevenueFromContractWithCustomerExcludingAssessedTax` | USD | Q & FY | High |
| Share repurchases | `us-gaap:PaymentsForRepurchaseOfCommonStock` | `StockRepurchasedAndRetiredDuringPeriodValue` | USD | Q & FY | Medium |
| Share issuance | `us-gaap:ProceedsFromIssuanceOfCommonStock` | `StockIssuedDuringPeriodValue` | USD | Q & FY | Medium |
| Shares outstanding | `us-gaap:CommonStockSharesOutstanding` | `WeightedAverageNumberOfSharesOutstandingBasic` | shares | Q & FY | High |

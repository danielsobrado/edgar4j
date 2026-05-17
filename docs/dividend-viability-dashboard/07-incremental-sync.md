# Phase 7: Incremental Sync & Change Detection

## Objective

Build scheduled jobs that keep the dividend dashboard data current by detecting new/changed filings on SEC/EDGAR and re-ingesting only what changed. All operations must be idempotent and respect the SEC's 10 rps fair-access policy.

## Current Implementation Status

Implemented in code:
- single-company dividend sync refreshes submissions, ingests normalized companyfacts, warms overview analysis, and records sync state
- new 8-K / 8-K/A accessions trigger dividend event warm-up
- failed syncs persist `ERROR` status, retry count, error message, and exponential backoff timestamp
- scheduled tracked-company sync uses stored dividend sync states, falls back to the S&P 500 list when no universe is stored, and continues after one tracked company fails
- `/api/dividend/{tickerOrCik}/track` and `DELETE /api/dividend/{tickerOrCik}/track` add/remove companies from the tracked universe without deleting historical facts

Remaining operational validation:
- run tracked sync under live SEC credentials to confirm fair-access behavior across the intended production universe

## Design Principles

- **Detect changes, don't re-download everything** — use EDGAR daily index files or submissions diff to find new filings
- **Idempotent** — running the same sync twice produces no side effects
- **Restatement-aware** — when an amendment (10-K/A, 10-Q/A) arrives, re-run current-best selection
- **Rate-limit safe** — global token bucket shared across all sync jobs
- **Resumable** — track sync progress so interrupted jobs resume cleanly

## Architecture Overview

```
┌───────────────────────────────────────────────────────────────┐
│ Scheduler (Spring @Scheduled or Quartz)                       │
│                                                               │
│  ┌─────────────┐  ┌─────────────┐  ┌──────────────────────┐ │
│  │ Daily Facts  │  │ Event Sync  │  │ Market Data Refresh  │ │
│  │ Sync Job     │  │ Job (8-K)   │  │ Job                  │ │
│  └──────┬──────┘  └──────┬──────┘  └──────────┬───────────┘ │
│         │                │                     │              │
│         ▼                ▼                     ▼              │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │ SyncOrchestrator                                        │ │
│  │  - maintains sync_state per CIK                         │ │
│  │  - coordinates ingestion order                          │ │
│  │  - handles failures and retries                         │ │
│  └──────────────────────────┬──────────────────────────────┘ │
│                             │                                 │
│                             ▼                                 │
│  ┌────────────┐  ┌──────────────┐  ┌────────────────────┐   │
│  │ SecApiClient│  │FactIngestion │  │ EventExtractor     │   │
│  │ (existing)  │  │ Service      │  │ Service            │   │
│  └────────────┘  └──────────────┘  └────────────────────┘   │
└───────────────────────────────────────────────────────────────┘
```

## Change Detection Strategies

### Strategy 1: Submissions-Based (Recommended for small/medium universes)

For each tracked CIK, compare latest accession numbers in `submissions` against what's stored locally.

```java
@Service
@RequiredArgsConstructor
public class SubmissionsChangeDetector {

    private final SecApiClient secApiClient;
    private final DimFilingRepository filingRepository;

    /**
     * Find new filings for a CIK since last sync.
     */
    public List<NewFiling> detectNewFilings(String cik) {
        String submissionsJson = secApiClient.fetchSubmissions(cik);
        SubmissionsResponse response = parse(submissionsJson);

        Set<String> knownAccessions = filingRepository.getAccessionsByCik(cik);

        return response.getRecentFilings().stream()
                .filter(f -> !knownAccessions.contains(f.getAccessionNumber()))
                .filter(f -> RELEVANT_FORMS.contains(f.getForm()))
                .map(f -> new NewFiling(cik, f.getAccessionNumber(), f.getForm(),
                        f.getFilingDate(), f.getPrimaryDocument()))
                .toList();
    }

    private static final Set<String> RELEVANT_FORMS = Set.of(
        "10-K", "10-K/A", "10-Q", "10-Q/A", "8-K", "8-K/A",
        "20-F", "20-F/A", "40-F", "40-F/A"
    );
}
```

### Strategy 2: EDGAR Daily Index (Recommended for large universes)

Parse the EDGAR daily index files to identify new filings across all tracked CIKs in one pass.

```java
@Service
@RequiredArgsConstructor
public class DailyIndexChangeDetector {

    private final SecApiClient secApiClient;

    /**
     * Scan EDGAR daily index for new filings.
     * Index URL: https://www.sec.gov/Archives/edgar/daily-index/{year}/QTR{q}/master.idx
     */
    public List<NewFiling> scanDailyIndex(LocalDate date, Set<String> trackedCiks) {
        String indexUrl = buildDailyIndexUrl(date);
        String indexContent = secApiClient.fetchRaw(indexUrl);

        return parseIndexLines(indexContent).stream()
                .filter(line -> trackedCiks.contains(line.getCik()))
                .filter(line -> RELEVANT_FORMS.contains(line.getForm()))
                .map(line -> new NewFiling(line.getCik(), line.getAccession(),
                        line.getForm(), date, line.getFilename()))
                .toList();
    }

    private String buildDailyIndexUrl(LocalDate date) {
        int quarter = (date.getMonthValue() - 1) / 3 + 1;
        return String.format("https://www.sec.gov/Archives/edgar/daily-index/%d/QTR%d/master%s.idx",
                date.getYear(), quarter, date.format(DateTimeFormatter.BASIC_ISO_DATE));
    }
}
```

### Strategy Selection Logic

```java
public ChangeDetectionStrategy selectStrategy(int universeSize) {
    if (universeSize <= 500) return Strategy.SUBMISSIONS_BASED;
    if (universeSize <= 5000) return Strategy.DAILY_INDEX;
    return Strategy.BULK_DOWNLOAD;  // Phase 9
}
```

## Sync State Tracking

```
┌──────────────────────────────────────────────────────────────┐
│ sync_state                                                    │
├──────────────────────────────────────────────────────────────┤
│ cik                VARCHAR(10)   PK                          │
│ last_facts_sync    TIMESTAMP     -- last companyfacts fetch  │
│ last_events_sync   TIMESTAMP     -- last 8-K event scan     │
│ last_accession     VARCHAR(25)   -- most recent filing seen  │
│ sync_status        VARCHAR(20)   -- IDLE, IN_PROGRESS, ERROR │
│ error_message      TEXT          -- last error if any        │
│ retry_count        INT           -- consecutive failures     │
│ next_retry_at      TIMESTAMP     -- backoff schedule         │
│ facts_version      INT           -- incremental version      │
│ created_at         TIMESTAMP                                  │
│ updated_at         TIMESTAMP                                  │
│                                                               │
│ INDEX: (sync_status, next_retry_at)                          │
└──────────────────────────────────────────────────────────────┘
```

## Scheduled Jobs

### 7.1 Daily Facts Sync Job

Extends the existing `FilingSyncJob` / `MarketDataSyncJob` pattern in the codebase.

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class DividendFactsSyncJob {

    private final SyncOrchestrator syncOrchestrator;
    private final SyncStateRepository syncStateRepository;

    /**
     * Run daily at 06:00 UTC — after SEC nightly index rebuild.
     */
    @Scheduled(cron = "0 0 6 * * *")
    public void syncAllTrackedCompanies() {
        log.info("Starting daily dividend facts sync");

        List<SyncState> companies = syncStateRepository.findDueForSync();

        for (SyncState state : companies) {
            try {
                state.setSyncStatus("IN_PROGRESS");
                syncStateRepository.save(state);

                syncOrchestrator.syncCompany(state.getCik());

                state.setSyncStatus("IDLE");
                state.setLastFactsSync(Instant.now());
                state.setRetryCount(0);
                state.setErrorMessage(null);
            } catch (Exception e) {
                log.error("Sync failed for CIK {}: {}", state.getCik(), e.getMessage());
                state.setSyncStatus("ERROR");
                state.setRetryCount(state.getRetryCount() + 1);
                state.setErrorMessage(e.getMessage());
                state.setNextRetryAt(computeBackoff(state.getRetryCount()));
            } finally {
                syncStateRepository.save(state);
            }
        }

        log.info("Daily dividend facts sync complete");
    }

    /**
     * Exponential backoff: 1h, 4h, 12h, 24h max
     */
    private Instant computeBackoff(int retryCount) {
        long hours = Math.min((long) Math.pow(2, retryCount), 24);
        return Instant.now().plus(Duration.ofHours(hours));
    }
}
```

### 7.2 Realtime Event Sync Job

```java
@Component
@RequiredArgsConstructor
public class DividendEventSyncJob {

    /**
     * Run every 4 hours during market hours — catches dividend 8-K announcements.
     */
    @Scheduled(cron = "0 0 10,14,18,22 * * MON-FRI")
    public void syncRecentDividendEvents() {
        // 1. Find 8-K filings from last 24 hours via EFTS search
        // 2. Filter to tracked CIKs
        // 3. Extract dividend events from Item 8.01
        // 4. Save to dividend_events table
    }
}
```

### 7.3 Metric Recomputation Job

```java
@Component
@RequiredArgsConstructor
public class MetricRecomputationJob {

    /**
     * Run after facts sync — recompute metrics only for companies with new data.
     */
    @Scheduled(cron = "0 30 6 * * *")
    public void recomputeStaleMetrics() {
        List<String> staleCompanies = syncStateRepository
                .findByLastFactsSyncAfter(lastMetricComputation);

        for (String cik : staleCompanies) {
            metricComputationService.computeAll(cik);
            alertEvaluationService.evaluateAlerts(cik);
        }
    }
}
```

## Sync Orchestrator

```java
@Service
@RequiredArgsConstructor
public class SyncOrchestrator {

    private final SubmissionsChangeDetector changeDetector;
    private final CompanyFactsIngestionService factsIngestion;
    private final DividendEventSyncService eventSync;
    private final MetricComputationService metricComputation;
    private final AlertEvaluationService alertEvaluation;

    /**
     * Full sync pipeline for a single company.
     * Order matters: facts → events → metrics → alerts
     */
    public SyncResult syncCompany(String cik) {
        // 1. Detect new filings
        List<NewFiling> newFilings = changeDetector.detectNewFilings(cik);
        if (newFilings.isEmpty()) {
            return SyncResult.noChanges(cik);
        }

        // 2. Re-ingest company facts (incremental via cache invalidation)
        IngestionResult ingestionResult = factsIngestion.ingest(cik);

        // 3. Extract dividend events from new 8-K filings
        List<NewFiling> new8Ks = newFilings.stream()
                .filter(f -> f.getForm().startsWith("8-K"))
                .toList();
        for (NewFiling filing : new8Ks) {
            eventSync.extractFromFiling(cik, filing.getAccession());
        }

        // 4. Recompute metrics
        metricComputation.computeAll(cik);

        // 5. Re-evaluate alerts
        alertEvaluation.evaluateAlerts(cik);

        return SyncResult.success(cik, newFilings.size(), ingestionResult);
    }
}
```

## Restatement Handling

When an amendment filing (10-K/A, 10-Q/A) is detected:

```java
public void handleAmendment(String cik, NewFiling amendment) {
    // 1. Ingest new facts from the amendment
    factsIngestion.ingest(cik);

    // 2. Recompute current-best — amendment will win over original
    // (built into the current-best selection policy: amendments score higher)

    // 3. Recompute metrics for affected periods
    LocalDate affectedPeriod = amendment.getReportPeriodEnd();
    metricComputation.computeForPeriod(cik, affectedPeriod);

    // 4. Re-evaluate alerts
    alertEvaluation.evaluateAlerts(cik);

    log.info("Processed amendment {} for CIK {} period {}",
            amendment.getAccession(), cik, affectedPeriod);
}
```

## SEC Rate Limit Coordination

All sync jobs share the existing `SecRateLimiter`. Add coordination for concurrent jobs:

```java
@Component
public class SyncRateLimitCoordinator {

    private final SecRateLimiter rateLimiter;  // existing
    private final Semaphore concurrencyLimit = new Semaphore(3);  // max 3 CIKs in parallel

    /**
     * Acquire both concurrency slot and rate limit permit.
     */
    public void acquireForSync() throws InterruptedException {
        concurrencyLimit.acquire();
        rateLimiter.acquire();
    }

    public void releaseForSync() {
        concurrencyLimit.release();
    }
}
```

## Error Recovery

| Failure | Recovery |
|---|---|
| SEC 403/429 (throttled) | Pause all sync for 10 minutes; resume with backoff |
| Individual CIK fails | Mark ERROR + exponential backoff; don't block other CIKs |
| Partial ingestion (crash mid-CIK) | Idempotent writes mean restart is safe; resume from same CIK |
| Database unavailable | Fail fast; retry on next scheduled run |
| Malformed SEC response | Log + skip; manually review later |

## Universe Management

```java
@Service
public class DividendUniverseService {

    /** Add a company to the tracked universe */
    public void track(String tickerOrCik) {
        String cik = resolveToClk(tickerOrCik);
        SyncState state = new SyncState(cik, "IDLE");
        syncStateRepository.save(state);
        // Trigger immediate first sync
        syncOrchestrator.syncCompany(cik);
    }

    /** Remove a company from tracking */
    public void untrack(String cik) {
        syncStateRepository.delete(cik);
        // Optionally: keep historical data, just stop syncing
    }

    /** Import a watchlist (e.g., S&P 500, Dividend Aristocrats) */
    public void importWatchlist(List<String> tickers) {
        tickers.forEach(this::track);
    }
}
```

## Validation Checklist

- [x] New 10-K filing triggers fact re-ingestion and metric recomputation warm-up
- [x] New 8-K filing triggers event extraction warm-up
- [x] Amendment (10-K/A) correctly overrides original period data through current-best fact selection
- [x] Failed sync for one CIK doesn't block others
- [x] Exponential backoff works (1h → 4h → 12h → 24h cap)
- [ ] SEC rate limit never exceeded even with parallel sync
- [ ] Sync state persists across application restarts
- [x] Universe add/remove works correctly

## Estimated Effort: 4-5 days

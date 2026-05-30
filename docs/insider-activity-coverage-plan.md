# Insider Activity â€” Data Coverage Heatmap (form by form)

Status: **planned** Â· Owner: TBD Â· Last updated: 2026-05-30

Extend the existing "Data Coverage" heatmap (already shipped on USAspending and Political Trades)
to the **Insider Activity** screen. Because insider/ownership data spans three separate SEC forms,
each with its own collection, date field, and access port, this is delivered **one form at a time**:
**Form 4 first**, then **Form 3**, then **Form 5**.

---

## 1. Goal

On the Insider Activity page, add a button-toggled coverage heatmap (months Ã— dayâ€‘ofâ€‘month grid)
that:

1. Shows, per day, **how many filings of the selected form we have in the DB** (intensity shading,
   gaps = days with zero filings) â€” a true heatmap, like Political Trades.
2. Lets the user **switch which form** the heatmap reflects (Form 4 / 3 / 5) via a small selector.
3. Lets the user **drag-select a date range over the gaps and download that form for those dates
   into the DB** via the existing `REMOTE_FILINGS_SYNC` job. This is genuinely dateâ€‘scoped (unlike
   the Political "sync"), so the selection maps directly to a download.

Nonâ€‘goal: changing how the screener itself works, or backfilling historical data automatically.

---

## 2. What already exists (the template to copy)

### Coverage feature, backend
- USAspending: `UsaSpendingCoverageResponse` (covered ranges) + `getUsaSpendingCoverage(from,to)` in
  `DownloadJobServiceImpl` + `GET /api/downloads/usaspending/coverage`.
- Political: `PoliticalTradeCoverageResponse` (**perâ€‘day counts**) + `coverage(from,to)` in
  `PoliticalTradeServiceImpl` + `GET /api/political-trades/coverage`. **This is the closer template**
  (counts, not ranges).

### Coverage feature, frontend
- `frontend/src/app/components/coverage/CoverageHeatmapGrid.tsx` â€” reusable presentational grid
  (months Ã— days, dragâ€‘select, controlled `selection`, `getDay`/`levelClass` hooks, `todayIso`/`ymd`
  helpers). **Reuse asâ€‘is.**
- `frontend/src/app/components/political/PoliticalCoverageHeatmap.tsx` â€” thin wrapper: fetches
  coverage, buckets counts to intensity levels, renders the grid, manages year nav + selection +
  action buttons. **Copy this as the starting point for the insider wrapper.**
- Toggleâ€‘button + conditional render pattern in `frontend/src/app/pages/PoliticalTrades.tsx`
  (`showCoverage` state, `Data Coverage` button, `<PoliticalCoverageHeatmap .../>`).

### Insider Activity, current state
- `InsiderActivityController` (`/api/insider-activity`), `InsiderActivityService` /
  `InsiderActivityServiceImpl` â€” **Form 4 only**, injected `Form4DataPort form4Repository`.
- Frontend page `frontend/src/app/pages/InsiderActivity.tsx`, API
  `frontend/src/app/api/endpoints/insiderActivity.ts`, types in `frontend/src/app/api/types.ts`.

### The download-to-DB mechanism (date-scoped, per form)
- `DownloadType.REMOTE_FILINGS_SYNC` with `DownloadRequest{ formType, dateFrom, dateTo }`.
- `POST /api/downloads/remote-filings` (`DownloadController.downloadRemoteFilings`).
- Frontend `downloadsApi.downloadRemoteFilings({ formType, dateFrom, dateTo, remoteFilingSyncMode? })`.
- Executor: `DownloadJobExecutor.executeRemoteFilingSync` finds companies via
  `remoteEdgarService.findMatchingCompanyCiks(formType, from, to)`, then per company runs a sync
  chosen by `request.getRemoteFilingSyncMode()`:
  - `FILING_DATE` â†’ `downloadSubmissions(cik, formType, from, to)` â€” **form + dateâ€‘scoped** (precise
    gapâ€‘filling; coverage downloads should request this mode).
  - default â†’ `downloadSubmissions(cik)` â€” pulls the company's full submissions (broad backfill).
  - âš ï¸ Scoping is by **filing date**, while Form 4 coverage is shaded by **transaction date**, so a
    small lag applies (see Â§8). For large windows, wrap this in the chunked/throttled loop in Â§6.

---

## 3. Per-form data facts (why this is "form by form")

| Form | Collection | Port | Coverage date axis | Range query available |
|------|-----------|------|--------------------|-----------------------|
| 4    | `form4`   | `Form4DataPort` | `transactionDate` (matches the screener) | `findByTransactionDateBetween(start,end,Pageable)` |
| 3    | `form3`   | `Form3DataPort extends SimpleAccessionedFilingDataPort<Form3>` | `filedDate` | `findByFiledDateBetween(start,end,Pageable)` |
| 5    | `form5`   | `Form5DataPort extends SimpleAccessionedFilingDataPort<Form5>` | `filedDate` | `findByFiledDateBetween(start,end,Pageable)` |

Notes:
- Form 4 has **no `filedDate`** field â€” only `transactionDate` + `periodOfReport`. Use
  `transactionDate` so the heatmap matches what the screener shows.
- Form 3/5 expose `filedDate` (and `periodOfReport`) and a `findByFiledDateBetween` query already.
- All three ports extend `BaseDocumentDataPort` and work in both Mongo and file storage modes â€” use
  the **range query + inâ€‘service grouping** approach (no new aggregation), mirroring
  `PoliticalTradeServiceImpl.coverage`.

**Decision â€” date axis:** heatmap shows form 4 by `transactionDate`, forms 3/5 by `filedDate`. The
download action always syncs by the selected calendar range (filingâ€‘date semantics on EDGAR). Call
this out in the UI copy so the small transactionâ€‘vsâ€‘filingâ€‘date lag isn't surprising (Â§8).

---

## 4. API shape (single parameterized endpoint)

One endpoint on the insider controller, parameterized by form, returns perâ€‘day counts (Political
template):

```
GET /api/insider-activity/coverage?form=4&from=2026-01-01&to=2026-12-31
```

Response (`InsiderActivityCoverageResponse`, camelCase JSON):
```json
{ "form": "4", "from": "2026-01-01", "to": "2026-12-31",
  "totalFilings": 1234,
  "days": [ { "date": "2026-01-03", "count": 12 }, ... ] }
```

`form` accepts `3` | `4` | `5`. Phase 1 implements `4`; Phases 2â€“3 add `3` and `5`.

---

## 5. Phase 1 â€” Form 4 coverage (ship first)

### 5.1 Backend

**Step 1 â€” DTO.** New `src/main/java/org/jds/edgar4j/dto/response/InsiderActivityCoverageResponse.java`
(copy `PoliticalTradeCoverageResponse`, add a `form` field):

```java
@Data @Builder
public class InsiderActivityCoverageResponse {
    private String form;          // "3" | "4" | "5"
    private LocalDate from;
    private LocalDate to;
    private long totalFilings;
    private List<DayCount> days;

    @Data @Builder
    public static class DayCount { private LocalDate date; private long count; }
}
```

**Step 2 â€” Service interface.** Add to `InsiderActivityService`:
```java
InsiderActivityCoverageResponse coverage(String form, LocalDate from, LocalDate to);
```

**Step 3 â€” Service impl.** In `InsiderActivityServiceImpl`:
- For Phase 1, only `"4"` is supported; throw `IllegalArgumentException` for others (Phases 2â€“3 fill
  in `"3"`/`"5"`).
- Implementation (Form 4, by `transactionDate`):
```java
@Override
public InsiderActivityCoverageResponse coverage(String form, LocalDate from, LocalDate to) {
    validateWindow(from, to);                       // from/to non-null, to >= from
    String normalized = normalizeForm(form);        // default "4"; Phase 1 only "4"
    if (!"4".equals(normalized)) {
        throw new IllegalArgumentException("Unsupported insider form: " + form);
    }
    Map<LocalDate, Long> counts = form4Repository
            .findByTransactionDateBetween(from, to, Pageable.unpaged())
            .getContent().stream()
            .map(Form4::getTransactionDate)
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    return toResponse(normalized, from, to, counts);  // sort by date, sum total
}
```
- Add a private `toResponse(form, from, to, Map<LocalDate,Long>)` helper that maps to sorted
  `DayCount`s + `totalFilings` (identical shape to `PoliticalTradeServiceImpl.coverage`).
- Imports to add: `java.time.LocalDate`, `java.util.Map`, `java.util.function.Function`,
  `org.springframework.data.domain.Pageable`, the new DTO. (`Objects`, `Collectors`, `Form4` already
  imported.)

**Step 4 â€” Controller.** In `InsiderActivityController` add:
```java
@GetMapping("/coverage")
public ResponseEntity<ApiResponse<InsiderActivityCoverageResponse>> coverage(
        @RequestParam(defaultValue = "4") String form,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    if (to.isBefore(from)) {
        return ResponseEntity.badRequest().body(ApiResponse.error("to must be on or after from"));
    }
    return ResponseEntity.ok(ApiResponse.success(insiderActivityService.coverage(form, from, to)));
}
```
(`LocalDate`, `DateTimeFormat`, `RequestParam` are already imported in this controller.)

**Step 5 â€” Verify backend.**
```
./mvnw -o -f pom.xml compile -DskipTests
```
Optional unit test: `InsiderActivityServiceImplCoverageTest` â€” mock `Form4DataPort.findByTransactionDateBetween`
to return forms across 3 days; assert counts/total/sorting; assert nonâ€‘"4" form throws.

### 5.2 Frontend

**Step 6 â€” Types.** In `frontend/src/app/api/types.ts` add:
```ts
export interface InsiderActivityCoverageDay { date: string; count: number; }
export interface InsiderActivityCoverage {
  form: string; from: string; to: string; totalFilings: number;
  days: InsiderActivityCoverageDay[];
}
```

**Step 7 â€” API method.** In `frontend/src/app/api/endpoints/insiderActivity.ts` (import the type and
add):
```ts
coverage: (form: string, from: string, to: string): Promise<InsiderActivityCoverage> =>
  apiClient.get<InsiderActivityCoverage>(
    `/insider-activity/coverage?form=${form}&from=${from}&to=${to}`),
```

**Step 8 â€” Heatmap component.** New
`frontend/src/app/components/insider/InsiderCoverageHeatmap.tsx`, copied from
`PoliticalCoverageHeatmap.tsx`, with these differences:
- Props:
  ```ts
  interface Props {
    onSelectRange: (from: string, to: string) => void;  // set the screener date filter
    onDownload: (form: string, from: string, to: string) => void; // queue REMOTE_FILINGS_SYNC
    downloading: boolean;
    refreshKey: string; // bump after a download completes to refetch coverage
  }
  ```
- Internal `const [form, setForm] = useState<'4'|'3'|'5'>('4')` with a small segmented control in the
  header. **Phase 1: render only the `4` option** (or render 3/5 disabled with a "coming soon"
  title); enable them in Phases 2â€“3.
- Fetch via `insiderActivityApi.coverage(form, \`${year}-01-01\`, \`${year}-12-31\`)`; refetch effect
  depends on `[year, form, refreshKey]`.
- Reuse `CoverageHeatmapGrid`, `bucket(count)`, and the `LEVEL_CLASSES` intensity scale from the
  political component verbatim. `getDay` title: `\`${date} â€” ${count} Form ${form} filing(s)\``.
- Action row buttons:
  - **Download to DB** (primary) â€” enabled only when a selection exists; calls
    `onDownload(form, selection.start, selection.end)`. Spinner while `downloading`.
  - **Filter screener to selection** â€” calls `onSelectRange(start,end)`.
  - **Clear**.
- Copy under the title: "Counts are filings already in the DB. Drag the gaps and Download to fetch
  that form for those dates into the database (synced by filing date)."

**Step 9 â€” Page integration** (`frontend/src/app/pages/InsiderActivity.tsx`), mirroring
`PoliticalTrades.tsx`:
- Add `CalendarRange` to the lucide import; import `InsiderCoverageHeatmap`; import `downloadsApi`.
- State: `const [showCoverage, setShowCoverage] = useState(false)` and
  `const [coverageRefreshKey, setCoverageRefreshKey] = useState(0)` and
  `const [downloading, setDownloading] = useState(false)`.
- Add a **Data Coverage** toggle button in the header actions (same styling as Political).
- Define a download handler:
  ```ts
  const downloadForm = async (form: string, from: string, to: string) => {
    setDownloading(true);
    try {
      await downloadsApi.downloadRemoteFilings({
        formType: form, dateFrom: from, dateTo: to,
        remoteFilingSyncMode: 'FILING_DATE',   // form + date-scoped, precise gap-fill
        // chunkDays / pauseSeconds optional â€” omit to use server defaults (see Â§6 throttling)
      });
      showSuccess('Download queued', `Form ${form} sync queued for ${from} â†’ ${to}. Track it on Downloads.`);
    } catch (e) { showError('Download failed', e instanceof Error ? e.message : 'Failed'); }
    finally { setDownloading(false); }
  };
  ```
  (Use the page's existing notification helper, matching how InsiderActivity already surfaces errors.
  `downloadsApi.downloadRemoteFilings` must forward `remoteFilingSyncMode` and the optional
  `chunkDays`/`pauseSeconds` â€” extend its request type if not already present.)
- Wire selection â†’ the screener's date filter. The screener filter keys are `dateFrom`/`dateTo`
  (see `InsiderActivity.tsx` / `insiderActivity.ts` query builder) â€” call the page's existing
  filterâ€‘update path with `{ dateFrom: from, dateTo: to }`.
- Render:
  ```tsx
  {showCoverage && (
    <InsiderCoverageHeatmap
      onSelectRange={(from, to) => updateFilter({ dateFrom: from, dateTo: to })}
      onDownload={(form, from, to) => void downloadForm(form, from, to)}
      downloading={downloading}
      refreshKey={String(coverageRefreshKey)}
    />
  )}
  ```
  Note: the REMOTE_FILINGS_SYNC job runs async and its status shows on the **Downloads** page; there
  is no live job state on the insider page. After queueing, optionally bump `coverageRefreshKey`
  on a delay or add a manual Refresh (the heatmap already has a Refresh button) so the user can
  re-pull coverage once the job finishes.

**Step 10 â€” Verify frontend.**
```
cd frontend && npm run build      # tsc + vite
npx vitest run src/app/pages/InsiderActivity.test.tsx
```
- If `InsiderActivity.test.tsx` mocks `insiderActivityApi`, add `coverage: vi.fn().mockResolvedValue(...)`
  to the mock (the heatmap defaults hidden, so it shouldn't be required, but add it if the test
  toggles coverage).

### 5.3 Phase 1 acceptance
- "Data Coverage" button toggles a Form 4 heatmap shaded by daily Form 4 transaction count.
- Dragging a gap range + "Download to DB" queues a `REMOTE_FILINGS_SYNC` job (mode `FILING_DATE`) for
  `formType=4` and that date range, **chunked + throttled** per Â§6; toast points to the Downloads page.
- "Filter screener to selection" narrows the table to the dragged range.
- `mvnw compile`, `npm run build`, and insider tests all green.

> Throttling (Â§6) is a **shared** change to the remote-sync path. Land it alongside or just before
> the Phase 1 download wiring so coverage downloads are polite to EDGAR from day one.

---

## 6. Throttled, chunked downloads (configurable)

This is a shared change to the `REMOTE_FILINGS_SYNC` path for all remote sync callers. See the companion design doc for full details: [`remote-sync-throttling.md`](./remote-sync-throttling.md).

---
## 7. Phase 2 (Form 3) and Phase 3 (Form 5) â€” deltas only

These reuse everything from Phase 1; only the service branch and the form selector change.

**Backend (`InsiderActivityServiceImpl.coverage`):**
- Inject `Form3DataPort` and `Form5DataPort` (add to constructor â€” note the impl has a 2â€‘arg test
  constructor + `@Autowired` ctor; extend both).
- Add branches:
  ```java
  case "3" -> countByDate(form3Repository.findByFiledDateBetween(from, to, Pageable.unpaged())
                  .getContent().stream().map(Form3::getFiledDate));
  case "5" -> countByDate(form5Repository.findByFiledDateBetween(from, to, Pageable.unpaged())
                  .getContent().stream().map(Form5::getFiledDate));
  ```
  Extract a small `countByDate(Stream<LocalDate>)` helper to share with the Form 4 branch.
- Remove the Phaseâ€‘1 "unsupported form" guard.

**Frontend:** enable the `3` and `5` options in the `InsiderCoverageHeatmap` form selector. No other
changes (the component is already formâ€‘parameterized).

**Perâ€‘phase verification:** same commands as Phase 1; manually confirm switching the selector
reâ€‘fetches and reâ€‘shades, and that "Download to DB" sends the right `formType`.

**Form 3/5 caveats:**
- Forms 3/5 are far sparser than Form 4 (Form 3 = initial ownership; Form 5 = annual), so most days
  are legitimately gaps â€” keep the gap color subtle and consider a "0 is normal for this form" note.
- They key on `filedDate`, so their heatmap and the download window already share filingâ€‘date
  semantics (cleaner than Form 4).

---

## 8. Accuracy notes & decisions to confirm

1. **Transaction date vs filing date (Form 4).** Heatmap = `transactionDate`; download window =
   filing date (EDGAR). A trade on day X is typically filed within ~2 business days, so syncing the
   exact selected range can miss filings whose *transaction* fell inâ€‘range but were *filed* just
   after. **Options:** (a) accept the small lag (simplest); (b) pad the download `dateTo` by a few
   days; (c) add a future targeted sync (see #3). Recommend (a) for Phase 1, document it in the UI.
2. **REMOTE_FILINGS_SYNC is companyâ€‘scoped.** It backfills wholeâ€‘company submissions for companies
   active in the window, not just the one form/date. It will fill the gap (and more). Acceptable;
   note it in the toast/help text.
3. **(Future) targeted sync.** If precise perâ€‘form/date filling is wanted, add a new job type or a
   parameter to `executeRemoteFilingSync` that pulls only the requested form in the window via the
   EDGAR fullâ€‘text/daily index, instead of full company submissions. Out of scope here; tracked as a
   followâ€‘up.
4. **Heatmap window.** One calendar year per view with prev/next year nav (matches existing
   components). Coverage endpoint caps at a 1â€‘year window like the others (optional 366â€‘day guard).
5. **Coverage refresh after download.** The sync is async with status on the Downloads page. Phase 1
   relies on the heatmap's manual Refresh button. A nicer followâ€‘up: have the insider page poll the
   queued job (reuse `useDownloadJob`) and bump `coverageRefreshKey` on completion, exactly like the
   USAspending heatmap does.

---

## 9. File checklist

**Throttling â€” shared remote-sync change (land first/alongside Phase 1)**
- [ ] See: [`remote-sync-throttling.md`](./remote-sync-throttling.md)

**Phase 1 (Form 4)**
- [ ] `src/main/java/org/jds/edgar4j/dto/response/InsiderActivityCoverageResponse.java` (new)
- [ ] `src/main/java/org/jds/edgar4j/service/InsiderActivityService.java` (+`coverage`)
- [ ] `src/main/java/org/jds/edgar4j/service/impl/InsiderActivityServiceImpl.java` (+`coverage`, Form 4 branch)
- [ ] `src/main/java/org/jds/edgar4j/controller/InsiderActivityController.java` (+`GET /coverage`)
- [ ] `frontend/src/app/api/types.ts` (+coverage types)
- [ ] `frontend/src/app/api/endpoints/insiderActivity.ts` (+`coverage`)
- [ ] `frontend/src/app/components/insider/InsiderCoverageHeatmap.tsx` (new)
- [ ] `frontend/src/app/pages/InsiderActivity.tsx` (toggle button + render + handlers)
- [ ] (optional) `InsiderActivityServiceImplCoverageTest`

**Phase 2 (Form 3) / Phase 3 (Form 5)**
- [ ] `InsiderActivityServiceImpl` (+`Form3DataPort`/`Form5DataPort` ctor deps + branches)
- [ ] `InsiderCoverageHeatmap.tsx` (enable form `3` / `5` in selector)

**Reused unchanged**
- `frontend/src/app/components/coverage/CoverageHeatmapGrid.tsx`
- `downloadsApi.downloadRemoteFilings` + `POST /api/downloads/remote-filings`

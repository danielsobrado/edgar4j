# Throttled Remote FILINGS sync

Shared change for all callers of `DownloadRequest` + `DownloadType.REMOTE_FILINGS_SYNC`.
Chunk large ranges into small batches with a pause between batches, keep the job cancellable,
and add optional per-request overrides for pacing.

## Behavior
- Split `[dateFrom, dateTo]` into consecutive `chunkDays` windows.
- For each window, run the existing remote-filings sync flow (find matching CIKs and sync per CIK).
- Sleep `pauseSeconds` between windows (no sleep after the last window).
- Check cancellation at chunk boundaries and during sleep.

## Configuration
- Defaults: new `edgar4j.remote-sync` block in config bound to `Edgar4JProperties`:
  - `chunk-days` default `7`
  - `pause-seconds` default `5`
- Request overrides on `DownloadRequest`:
  - `chunkDays` and `pauseSeconds` optional integers.
  - Clamps: `chunkDays <= 0` means no chunking (single window); max `366`.
  - `pauseSeconds` clamps to `[0, 3600]`.
- Resolve in executor from request first, then properties defaults.

## Executor
- Refactor `DownloadJobExecutor.executeRemoteFilingSync` into:
  1) build windows with `splitIntoChunks`,
  2) call `syncRange` for each window,
  3) advance progress after each window,
  4) call `sleepInterruptibly` between windows.
- Add helper methods:
  - `splitIntoChunks(LocalDate from, LocalDate to, int chunkDays)` -> `DateRange(start, end)`
  - `syncRange(...)` (existing body moved here, now per chunk)
  - `sleepInterruptibly(long pauseMs, String jobId)` using short sleeps + cancel checks
  - small `record DateRange(LocalDate start, LocalDate end)`.

## Frontend
- Extend request type and payload in `downloadsApi.downloadRemoteFilings` with:
  - `chunkDays?: number`
  - `pauseSeconds?: number`
- Add optional advanced controls in Remote EDGAR filing sync UI:
  - `Advanced` / `Hide Advanced` toggle
  - numeric `Days per chunk` and `Pause (seconds)` inputs.
- Omit both fields to use server defaults.

## Verify
- `./mvnw -q -DskipTests compile`
- `./mvnw -Dtest=DownloadJobExecutorTest test`
- `npm --prefix frontend run build`
- Manual check: request a multi-month range and confirm search window is split in logs and job pauses.

## File checklist
- [ ] `Edgar4JProperties` + `application.yml` (`remote-sync.chunk-days`, `remote-sync.pause-seconds`)
- [ ] `DownloadRequest` (add `chunkDays`, `pauseSeconds`)
- [ ] `DownloadController.downloadRemoteFilings` (request accepted; existing validations retained)
- [ ] `DownloadJobExecutor` (`executeRemoteFilingSync` + helpers + property injection)
- [ ] `frontend/src/app/api/types.ts` + `frontend/src/app/api/endpoints/downloads.ts`
- [ ] `frontend/src/app/pages/RemoteEdgar.tsx` (optional advanced inputs + pass-through)
- [ ] `src/test/java/.../DownloadJobExecutorTest.java` (chunk split + cancellation coverage)

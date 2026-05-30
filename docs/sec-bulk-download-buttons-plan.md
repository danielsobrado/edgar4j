# Fix SEC Bulk Download Buttons

## Summary

The SEC bulk buttons should keep the existing server-side job model, but the UI needs to make the job start, progress, failure, and saved ZIP location obvious. The previous `Download ZIP` label implied a browser download, while the code actually queues `POST /api/downloads/bulk` jobs for `BULK_COMPANY_FACTS` and `BULK_SUBMISSIONS`.

## Key Changes

- Rename the two SEC bulk button labels from `Download ZIP` to `Queue Download`.
- Refresh download jobs immediately after a bulk job starts.
- Add per-button loading and disabled state for the specific SEC bulk type being queued.
- Include the queued job id/type in success feedback and state that archives are saved on the backend.
- Show completed bulk job metadata in the job card: saved ZIP path, source URL, and imported/saved count.
- Show failed bulk job errors prominently while preserving Retry.
- Populate bulk job metadata on the backend: `sourceUrl`, `outputPath`, `filesDownloaded`, and `totalFiles`.
- Return a metadata result from the bulk download service instead of a raw count.

## Test Plan

- Frontend tests cover queueing both bulk actions, per-button loading, completed metadata display, failed error display, and Retry.
- Backend tests cover the bulk service result metadata and job executor persistence of completed bulk metadata.
- Manual acceptance: open `/downloads`, click each SEC bulk button, confirm the job appears in Download Status, and confirm completed/failed state is visible.

## Assumptions

- Desired behavior is server-side queued download/import, not direct browser download.
- Existing `/api/downloads/bulk` endpoint remains the public API.
- SEC bulk archives are large, so browser download and automatic client-side file save are intentionally not added.

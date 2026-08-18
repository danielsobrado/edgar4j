# Distributed Mobile Worker Implementation Status

This document records the implementation state of `distributed-mobile-worker-plan.md`.

## Runtime default

Distributed workers remain **disabled by default**.

Enable the feature with:

```yaml
edgar4j:
  distributed-workers:
    enabled: true
```

The existing environment variable remains available:

```text
DISTRIBUTED_WORKERS_ENABLED=true
```

The server remains fully functional with zero remote workers.

---

## Phase 0 - Contracts and configuration

**Implemented.**

- Versioned worker protocol.
- Worker task/session models and lifecycle enums.
- Download-only V1 capability model.
- SEC source allowlist model.
- Typed worker configuration and validation.
- UTC clock abstraction.
- Conservative defaults; feature disabled by default.

---

## Phase 1 - Durable coordinator

**Implemented.**

- `WorkerTaskDataPort` and `WorkerSessionDataPort`.
- Low-resource file adapters with single-process atomic mutation.
- High-resource Mongo adapters with compare-and-set leasing.
- Idempotent task creation by logical resource key.
- Random session/lease credentials; only hashes are persisted.
- Lease expiry, heartbeat extension, abandonment and bounded retry.
- Content-addressed SHA-256 artifact store.
- Independent server-side checksum, size and content-shape verification.
- Staging and ingress cleanup jobs.
- Queue/worker metrics without task/session identifiers.
- Restart, stale-token, concurrency and artifact verification tests.

---

## Phase 2 - Server worker and pilot flow

**Implemented.**

- Server worker consumes the same `WorkerTask` lifecycle as remote workers.
- Server source fetches reuse typed `SecApiClient` behavior and the existing SEC limiter.
- Distributed work planner creates small idempotent download tasks.
- Verified artifact acquisition is separated from parsing/persistence.
- Daily SEC ticker resources are the first pilot flow.
- Existing direct SEC ticker acquisition remains the safety fallback.
- Parent `DownloadJob` cancellation propagates to worker tasks.
- Worker-task progress updates parent job progress without corrupting business record counts.
- Background server execution uses the existing bounded download executor.

---

## Phase 3 - Remote mobile/web worker

**Implemented as an opt-in mobile-web worker.**

There is no Android/iOS native shell in this repository, so V1 uses a same-origin mobile browser entry point rather than introducing a second client stack.

Entry point:

```text
/worker
```

Worker protocol:

```text
POST   /api/workers/session
POST   /api/workers/tasks/lease
POST   /api/workers/tasks/{taskId}/heartbeat
PUT    /api/workers/tasks/{taskId}/artifact
POST   /api/workers/tasks/{taskId}/failure
POST   /api/workers/tasks/{taskId}/abandon
DELETE /api/workers/session
GET    /api/workers/status
```

Mobile-web behavior:

- Explicit Start/Stop opt-in.
- Runs only while the page remains open.
- Wi-Fi-only and charging-only defaults.
- Battery/storage policy checks when browser APIs are available.
- Exact HTTPS SEC host allowlist.
- Redirects disabled for source fetches.
- Bounded source reads and bounded server ingress.
- Heartbeat while work is active.
- Local Web Crypto SHA-256 before upload.
- Server independently verifies the same artifact again.
- Session token kept in session storage, not persistent local storage.
- Failure reporting and automatic session recovery.

Browser CORS policy can prevent a phone from fetching a source directly. This is a normal worker failure: the task returns to the queue and server fallback preserves correctness.

---

## Phase 4 - Device-local cache

**Implemented.**

The mobile-web worker uses IndexedDB as an opportunistic content cache:

- Keyed by logical `resourceId`.
- Stores bytes, SHA-256, content type, size and last-access time.
- Recomputes SHA-256 before cached content is reused.
- Rejects stale content when an expected checksum differs.
- Bounded to a 100 MB local cache with least-recently-used eviction.
- Cache failures never fail the worker task; acquisition falls back to the source.
- Cache correctness is never trusted by the server.

---

## Phase 5 - Reliability, fairness and operations

**Implemented for the V1 scope.**

- Aggregate diagnostics endpoint at `/api/workers/status`.
- Concurrent low-mode lease simulation.
- Explicit high-mode atomic `findAndModify` claim test.
- Stale lease rejection after reassignment.
- Session restart/expiry coverage.
- Exact-host SSRF/open-proxy regression tests.
- Shared SEC dispatch limiter regression coverage.
- Mobile-presence registry.
- Mobile-first server fallback wrapper:
  - zero active phones -> server executes immediately;
  - recently active phone -> background server drain yields;
  - synchronous acquisition gives the phone a bounded claim/completion window;
  - failed/requeued mobile work returns to normal server fallback.
- Mobile-assist timings are externally configured in `worker-mobile-assist.yml`.

The implementation intentionally does not add resumable transfer complexity for the current small-artifact pilot. Add it only if production measurements show transfer size/failure rates justify it.

---

## Phase 6 - Optional compute delegation

**Not activated by design.**

The architecture plan makes compute delegation conditional on measurements showing that network acquisition is no longer the primary bottleneck and that mobile compute produces a meaningful improvement.

No such benchmark evidence exists in the current implementation, so adding parsing/indexing/embedding tasks would violate YAGNI and enlarge the trust surface without a demonstrated benefit.

If the benchmark gate is later met, create a new protocol version/capability and keep all compute results independently verifiable before persistence.

---

## Production rollout order

1. Deploy with `distributed-workers.enabled=false`.
2. Verify normal server-only ticker/download behavior.
3. Enable workers in a non-production environment.
4. Open `/worker` on one device and confirm session, lease, upload and verification metrics.
5. Test device suspension/disconnection and lease reclamation.
6. Test corrupted artifact rejection.
7. Add several devices and verify aggregate SEC request pressure stays within configured limits.
8. Enable a small production cohort.
9. Compare source throughput, server bandwidth, failure rate and queue age before expanding eligibility.

Do not expand source types or enable compute tasks until the preceding rollout produces stable measurements.

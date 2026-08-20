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
- Download/SHA-256 capability model plus server-only `TRUSTED_SOURCE` capability.
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
- Checksum-less SEC tasks dynamically require `TRUSTED_SOURCE`, including records persisted before the capability was introduced.

---

## Phase 2 - Server worker and pilot flow

**Implemented.**

- Server worker consumes the same `WorkerTask` lifecycle as remote workers.
- Server source fetches reuse typed `SecApiClient` behavior and the SEC limiter.
- Distributed work planner creates small idempotent download tasks.
- Verified artifact acquisition is separated from parsing/persistence.
- Daily SEC ticker resources are the first pilot flow.
- Mutable/checksum-less ticker resources are server-trusted tasks and are not delegated to untrusted remote clients.
- Existing direct SEC ticker acquisition remains the safety fallback.
- Parent `DownloadJob` cancellation propagates to worker tasks.
- Worker-task progress updates parent job progress without corrupting business record counts.
- Background server execution uses the existing bounded download executor.

---

## Phase 3 - Remote workers

**Implemented for native Android and opt-in web clients.**

Native Android project:

```text
mobile-worker-android/
```

Same-origin browser entry point:

```text
/worker
```

Worker protocol:

```text
POST   /api/workers/session
POST   /api/workers/tasks/lease
POST   /api/workers/tasks/{taskId}/heartbeat
POST   /api/workers/tasks/{taskId}/source-permit
PUT    /api/workers/tasks/{taskId}/artifact
POST   /api/workers/tasks/{taskId}/failure
POST   /api/workers/tasks/{taskId}/abandon
DELETE /api/workers/session
GET    /api/workers/status
```

Remote-worker behavior:

- Explicit opt-in.
- Native Android background execution uses WorkManager constraints.
- Web execution runs only while the page/app context remains active.
- Wi-Fi/unmetered and charging defaults are conservative.
- Battery/storage policy is checked before leasing.
- Clients advertise no more artifact capacity than their configured local limit.
- Exact HTTPS SEC host allowlist with standard HTTPS port, no embedded credentials, no fragments and no source redirects.
- Source downloads are bounded before upload.
- Active leases are heartbeated and cancelled work is abandoned when possible.
- SHA-256 is computed before upload and independently recomputed by the server.
- Remote sessions cannot register as `SERVER` or claim `TRUSTED_SOURCE`.
- Checksum-less SEC tasks remain server-only.

The native Android worker streams source content to app cache while hashing and stores its optional HTTP Basic password with Android Keystore encryption.

Browser CORS policy can prevent a browser worker from fetching a source directly. This is a normal worker failure: the task returns to the queue and server fallback preserves correctness.

---

## Phase 4 - Device-local cache

**Implemented for the browser worker.**

The mobile-web worker uses IndexedDB as an opportunistic content cache:

- Keyed by logical `resourceId`.
- Stores bytes, SHA-256, content type, size and last-access time.
- Recomputes SHA-256 before cached content is reused.
- Rejects stale content when an expected checksum differs.
- Bounded to a 100 MB local cache with least-recently-used eviction.
- Cache failures never fail the worker task; acquisition falls back to the source.
- Cache correctness is never trusted by the server.

The native Android worker currently uses bounded temporary staging rather than a persistent content cache.

---

## Phase 5 - Reliability, fairness and operations

**Implemented for the V1 scope.**

- Aggregate diagnostics endpoint at `/api/workers/status`.
- Concurrent low-mode lease simulation.
- Explicit high-mode atomic `findAndModify` claim test.
- Stale lease rejection after reassignment.
- Session restart/expiry coverage.
- Exact-host SSRF/open-proxy regression tests.
- Just-in-time source permit before each remote SEC request.
- SEC request starts are evenly spaced by the application rate limiter instead of fixed-window bursts.
- Mobile-presence registry.
- Mobile-first server fallback wrapper:
  - zero active phones -> server executes immediately;
  - recently active phone -> background server drain yields;
  - synchronous acquisition gives the phone a bounded claim/completion window;
  - `TRUSTED_SOURCE` tasks bypass mobile waiting;
  - failed/requeued mobile work returns to normal server fallback.
- Mobile-assist timings are externally configured in `worker-mobile-assist.yml`.
- Native APK CI runs unit tests, build-type-specific Lint and signed-release verification.

The implementation intentionally does not add resumable transfer complexity for the current small-artifact scope. Add it only if production measurements show transfer size/failure rates justify it.

### Deployment boundary

`SecRateLimiter` is process-local. A single Edgar4j server process coordinates all connected mobile workers correctly. If multiple Edgar4j server replicas are deployed behind a load balancer, introduce a shared/distributed SEC rate limiter before allowing more than one replica to dispatch SEC requests.

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
4. Install the Android debug APK on one device and verify session/lease/permit/upload metrics.
5. Test device suspension/disconnection and lease reclamation.
6. Test corrupted artifact rejection and trusted-source isolation.
7. Add several devices and verify aggregate SEC request pressure stays within configured limits.
8. Enable a small production cohort.
9. Compare source throughput, server bandwidth, failure rate and queue age before expanding eligibility.

Do not expand source types, deploy multiple dispatching server replicas, or enable compute tasks until the preceding rollout produces stable measurements.

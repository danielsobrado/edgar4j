# Distributed Mobile Worker Architecture Plan

## Status

**Proposed implementation plan** for extending Edgar4j so connected mobile devices can optionally contribute download capacity while continuing to act as normal clients.

This plan extends the existing download/sync architecture. It does **not** replace `DownloadJob`, `DownloadJobExecutor`, the dual-mode storage abstraction, or the existing SEC/source throttling behavior.

---

## 1. Decision

Edgar4j should support a **server-controlled distributed worker model**:

- The server remains the authoritative control plane.
- Mobile devices are optional, untrusted, opportunistic workers.
- The server itself is always a worker/fallback.
- Desktop/native clients can implement the same worker protocol later.
- Workers **pull** small independent tasks from the server.
- V1 mobile workers download raw source artifacts and return them to the server.
- Parsing, normalization, persistence, indexing, and business rules remain server-side in V1.
- All accepted artifacts are independently verified by the server before becoming authoritative.
- Source-wide rate limits and concurrency limits remain global across **all** workers.
- No peer-to-peer mobile-to-mobile transport is required.

The number of connected devices may improve throughput and reduce server network work, but must never affect correctness or availability.

```text
0 mobile workers   -> server performs all eligible work
1 mobile worker    -> mobile can assist
N mobile workers   -> work is distributed within global source limits
mobile disconnects -> leases expire and work returns to the queue
```

---

## 2. Why this fits Edgar4j

Edgar4j already has user-facing `DownloadJob` orchestration, `DownloadJobExecutor`, scheduled sync jobs, remote sync chunking/throttling, and data-port abstractions for low/high resource modes.

The distributed design should therefore be added **below the user-facing download job layer**, not as another competing download system.

Important separation:

```text
DownloadJob
  = user/business operation and progress

WorkerTask
  = small retryable unit of distributed execution
```

A single `DownloadJob` may create zero, one, or many `WorkerTask` records. Worker lease expiry, reassignment, duplicate delivery, or retry must not corrupt the parent `DownloadJob` state.

Do **not** overload `DownloadJobExecutor` with device registration, leases, heartbeat handling, artifact upload, and worker policy. That would turn an already central executor into a second scheduler and violate SRP. Introduce a coordinator behind small ports instead.

---

## 3. Goals

1. Allow opted-in mobile clients to download data required by Edgar4j.
2. Safely distribute eligible downloads across multiple connected devices.
3. Preserve a fully functional server-only mode.
4. Reclaim work automatically when a mobile disconnects or is suspended.
5. Prevent duplicate downloads from corrupting data.
6. Verify all worker output before persistence/import.
7. Reuse downloaded data in a device-local cache when useful to that client.
8. Work in both Edgar4j low-resource and high-resource profiles through ports.
9. Keep the transport protocol platform-neutral.
10. Keep all operational limits in configuration rather than hardcoded in services.
11. Provide enough observability to understand queue depth, throughput, retries, failures, and source pressure.
12. Preserve upstream provider terms, throttling, and fair-use requirements globally.

---

## 4. Non-goals for V1

- No peer-to-peer device discovery or device-to-device transfer.
- No blockchain, consensus, or distributed database.
- No redundant download by multiple devices merely to establish trust.
- No general-purpose remote execution.
- No arbitrary URLs supplied by users or workers.
- No mobile parsing/indexing/embedding in V1.
- No requirement that a mobile app remain alive in the background.
- No attempt to use multiple public IP addresses to bypass source limits.
- No requirement for object storage; filesystem storage remains valid through an abstraction.
- No distributed execution of very large bulk archives until resumability and size policies justify it.

These can be reconsidered only when measurements show a real need.

---

## 5. Target Architecture

```text
                           Edgar4j Server
┌──────────────────────────────────────────────────────────────────────┐
│                                                                      │
│  REST/UI request                                                     │
│       │                                                              │
│       v                                                              │
│  DownloadJob / scheduled sync                                        │
│       │                                                              │
│       v                                                              │
│  DistributedWorkPlanner                                              │
│       │                                                              │
│       v                                                              │
│  WorkerTaskDataPort <-----> durable task state                       │
│       │                                                              │
│       v                                                              │
│  WorkerCoordinator                                                   │
│       ├──────── SourcePolicy / global throttling                     │
│       ├──────── lease / retry / expiry                               │
│       └──────── ArtifactVerifier -> ArtifactStorePort -> import      │
│                                                                      │
│                 generic worker protocol                              │
└───────────────────────┬──────────────────────────────────────────────┘
                        │ HTTPS pull / heartbeat / upload
          ┌─────────────┼───────────────────┐
          │             │                   │
          v             v                   v
   ServerWorker   MobileWorker A      MobileWorker B
          │             │                   │
          └─────────────┼───────────────────┘
                        │
                        v
                allowlisted data sources
```

### Core invariant

The coordinator controls **which work is eligible to leave the server**. A worker is never trusted to decide what URL to fetch, what data is authoritative, or whether its result should be persisted.

---

## 6. Execution Model

### 6.1 Pull, not push

Workers request work:

```text
worker -> server: I am available for DOWNLOAD tasks
server -> worker: lease task X until T
```

The server must not require inbound connectivity to a mobile device. This avoids NAT, carrier firewall, push-delivery, and transient-connectivity complexity.

When no work exists, the server returns a configurable retry hint. The client polls again using bounded exponential backoff plus jitter.

### 6.2 Small independent tasks

A V1 `DOWNLOAD` task should represent **one independently verifiable source artifact**, not an entire multi-month sync or thousands of filings.

Examples:

- one SEC submissions JSON resource;
- one filing document/resource;
- one small dataset file;
- one bounded archive only when its size is within configured worker policy.

The existing remote sync chunking can remain the business-level planner. Each chunk can identify resources that are then converted into small worker tasks.

### 6.3 Server worker uses the same task contract

The server fallback should consume the same logical `WorkerTask` type rather than maintain a separate distributed code path.

```text
WorkerTaskHandler
    ^
    ├── ServerDownloadWorker
    └── Mobile worker protocol
```

The implementation does not need to share runtime code with the mobile application, but it must share task semantics and verification rules.

---

## 7. Task Lifecycle and Leasing

Recommended lifecycle:

```text
PENDING
   |
   | atomic lease
   v
LEASED
   |
   | worker uploads result
   v
VERIFYING
   |
   | valid
   v
COMPLETED
```

Failure paths:

```text
LEASED -- lease expires ----------> PENDING
LEASED -- worker abandons --------> PENDING
LEASED -- retryable failure ------> PENDING (attempt + 1, with backoff)
VERIFYING -- invalid artifact ----> PENDING or FAILED
PENDING -- max attempts exceeded -> FAILED
any non-terminal state -- cancel -> CANCELLED
```

### Lease rules

- Leasing must be atomic.
- A lease belongs to one worker session and has a random lease token.
- Store only a hash of the lease token when practical.
- Completion/failure/upload calls must present the active lease token.
- Stale lease tokens must be rejected.
- Lease extension is explicit through heartbeat/progress calls.
- Lease duration, heartbeat cadence, retry delay, and max attempts are configuration.
- Completing the same valid lease more than once is idempotent.
- A late result from an expired lease must never overwrite an already accepted result.

Do not permanently assign tasks to devices. Mobile workers are inherently transient.

---

## 8. Proposed Data Model

### 8.1 `WorkerTask`

Conceptual fields:

```text
id
parentDownloadJobId       optional
resourceId                 stable logical resource identity
type                       DOWNLOAD initially
source                     SEC_EDGAR, ...
sourceResource             canonical source descriptor
status                     PENDING/LEASED/VERIFYING/COMPLETED/FAILED/CANCELLED
priority
notBefore
expectedSha256             optional when known
expectedSizeBytes          optional when known
contentType                optional
leaseOwnerSessionId        nullable
leaseTokenHash             nullable
leaseExpiresAt             nullable
attemptCount
maxAttempts
lastErrorCode              nullable
createdAt
updatedAt
completedAt                nullable
artifactId                 nullable after verification
```

Do not persist current battery percentage, network type, or similar volatile mobile state on the task.

### 8.2 `WorkerSession`

A worker session is short-lived and may contain:

```text
sessionId
user/device identity reference
protocolVersion
clientVersion
platform
capabilities
maxConcurrentTasks
lastSeenAt
expiresAt
```

Current network/power/storage state should normally be supplied with lease/heartbeat requests and treated as ephemeral scheduling input.

### 8.3 Artifact identity

Use SHA-256 content identity for downloaded bytes.

```text
sha256(bytes) -> artifact content id
```

This provides corruption detection, deduplication, safe retries, and deterministic comparison. Logical resource metadata remains separate because two source resources can legitimately contain identical bytes.

---

## 9. Storage Abstraction and Dual-Mode Compatibility

Introduce ports rather than coupling the coordinator to MongoDB, PostgreSQL, or files:

```text
WorkerTaskDataPort
ArtifactStorePort
```

Suggested responsibilities:

### `WorkerTaskDataPort`

- create tasks idempotently;
- find eligible task candidates;
- atomically lease a task;
- extend a lease;
- release/requeue expired leases;
- transition to verification/completion/failure;
- query task counts/progress for the parent job.

### `ArtifactStorePort`

- create/write a staging artifact;
- stream/read staged content for verification;
- atomically promote verified content;
- resolve an artifact by content id;
- delete abandoned staging content.

### Low-resource mode

- file-backed task persistence is acceptable for a single Edgar4j process;
- protect lease transitions with a process-level lock/single-writer mechanism;
- persist state using temp-file + atomic replace or the existing low-mode persistence pattern;
- make it explicit that multi-server distributed coordination is unsupported in low mode.

### High-resource mode

Use a datastore operation that can atomically select/claim a pending task. Do not implement leasing as `find -> mutate -> save` without a compare-and-set condition, because concurrent lease requests could receive the same task.

Object storage is optional. If later added, it should be another `ArtifactStorePort` implementation, not a worker-protocol dependency.

---

## 10. Worker Capabilities and Eligibility

A worker advertises capabilities, while the **device owner controls whether contribution is allowed**.

V1 capability example:

```json
{
  "capabilities": ["DOWNLOAD", "SHA256"],
  "maxConcurrentTasks": 2,
  "runtime": {
    "networkType": "WIFI",
    "metered": false,
    "charging": true,
    "batteryPercent": 84,
    "freeStorageBytes": 5368709120
  }
}
```

The exact DTO should use enums/validated fields rather than free-form strings.

### V1 eligibility

Keep scheduling simple:

```text
worker enabled
AND capability supported
AND network policy allows work
AND battery/power policy allows work
AND enough temporary storage is available
AND worker has free concurrency capacity
AND task size/source is eligible for mobile execution
AND source policy currently permits dispatch
```

Do not add a complex worker reputation/scoring system initially.

A future scheduler may consider measured reliability, throughput, latency, or historical failure rate, but only after real data exists.

---

## 11. Mobile Policy

Contribution must be opt-in and visible to the user.

Recommended client settings:

- Enable distributed worker: off by default until the feature is mature.
- Wi-Fi only: default on.
- Allow metered/mobile data: default off.
- Require charging: configurable.
- Minimum battery percentage: configurable.
- Maximum temporary storage: configurable.
- Maximum concurrent downloads: configurable and server-capped.
- Optional daily transfer cap.

The client should stop requesting new work immediately when policy no longer allows it. An already running task may either finish or be abandoned according to client policy and remaining size.

### Background execution

The server contract must assume the mobile OS can suspend or kill the application at any time.

- Android may later use WorkManager or the platform-equivalent constrained background mechanism.
- iOS may later use the appropriate background processing/download APIs.
- A browser/PWA worker must **not** be assumed to support reliable background execution or unrestricted cross-origin fetching.

Therefore lease expiry/retry is correctness behavior, not an exceptional condition.

---

## 12. Source Policy and Rate Limiting

This is a hard requirement.

Distributed workers must **not** multiply the effective request rate to SEC or any other provider. Multiple mobile IP addresses are not a mechanism for bypassing provider limits, anti-abuse controls, or fair-use policies.

### One logical source budget

All execution paths must share the same source policy concept:

```text
scheduled server sync -----┐
manual server download ----┤
ServerWorker --------------┼--> global SourcePolicy --> source
MobileWorker A ------------┤
MobileWorker B ------------┘
```

Do not create a separate mobile-specific SEC limiter.

The distributed coordinator should integrate with the existing SEC integration/rate-limiting policy so task dispatch cannot increase total allowed source pressure.

For direct mobile fetches, the official client must only perform a source request for a currently valid server-issued task/permit. Task issuance should be paced by the global source scheduler, and assignments may carry `notBefore`/expiry boundaries when needed to prevent bursts.

A modified/untrusted client cannot be cryptographically forced to respect an external website after receiving its URL; therefore Edgar4j's server must never intentionally dispatch work to evade upstream rules, and source hosts must retain their own protections.

### Source configuration

Rate, concurrency, retry, backoff, host allowlists, and eligible task sizes belong in typed configuration. Existing source-specific settings should be reused rather than duplicated.

---

## 13. Security Model

Mobile workers are untrusted.

### Authentication and authorization

- Worker APIs require authenticated Edgar4j users/devices according to the deployment security model.
- Establish a short-lived `WorkerSession` token rather than using long-lived credentials in task payloads.
- Lease tokens are task-scoped and expire with the lease.
- A worker may only update tasks currently leased to its session.
- Administrative/user APIs must never accept worker lease tokens as normal authentication.

### Prevent an open proxy / SSRF primitive

The server must construct source resource descriptors itself.

Do not allow a normal API caller or worker to submit an arbitrary URL that another worker will fetch.

For each source:

- require HTTPS unless a deliberately supported source says otherwise;
- validate host against a configured allowlist;
- reject redirects to non-allowlisted hosts;
- reject private/local/link-local destinations;
- cap redirect count;
- cap response size;
- apply connection/read timeouts;
- validate expected media/content shape.

The mobile client should also reject tasks outside the protocol's allowlisted source types/hosts as defense in depth.

### Secrets

Workers must never receive server database credentials, cloud credentials, SEC administrative credentials, or internal service tokens. A task should contain only what is required to fetch that public/source artifact plus a task-scoped upload/lease token.

---

## 14. Artifact Upload and Verification

Recommended V1 flow:

```text
1. worker leases DOWNLOAD task
2. worker fetches source bytes
3. worker computes SHA-256 while streaming
4. worker uploads result to Edgar4j staging storage
5. server independently validates metadata/content/hash
6. server atomically promotes verified artifact
7. normal server parser/importer consumes artifact
8. task becomes COMPLETED
```

The server must not persist parsed business data merely because a worker reports success.

Validation should include, as applicable:

- maximum byte size;
- actual byte count;
- server-side or trusted storage checksum verification;
- expected hash when known;
- content type/signature;
- ZIP/archive integrity and decompression limits;
- JSON/XML/document parseability;
- expected logical resource identity, such as CIK/accession/resource path;
- source-specific schema/basic sanity validation.

Write to staging first. Promote to authoritative artifact storage only after verification.

### Duplicate work

If two attempts eventually produce the same verified SHA-256, the artifact store may deduplicate them. Only the task with the currently valid lease may transition task state; an old attempt must not overwrite newer state.

---

## 15. Local Mobile Cache

A mobile device may reuse downloaded content locally when it also needs the same resource for its user-facing experience.

Possible path:

```text
client needs resource
       |
       +--> local verified cache hit -> use locally
       |
       +--> no cache -> download once
                         |
                         +--> use locally
                         +--> satisfy leased worker task when identities match
```

Keep local cache identity content-addressed where practical.

The server remains authoritative. A local cache miss, eviction, or corruption must never prevent the normal client from operating through the server.

Do not implement device-to-device cache exchange in V1.

---

## 16. Proposed REST Protocol

Use versioned HTTPS JSON endpoints. Exact DTO names can follow current controller conventions.

### Start/refresh worker session

```http
POST /api/workers/session
```

Supplies protocol/client information and returns a short-lived worker session.

### Lease work

```http
POST /api/workers/tasks/lease
```

Example request:

```json
{
  "protocolVersion": 1,
  "capabilities": ["DOWNLOAD", "SHA256"],
  "maxTasks": 2,
  "runtime": {
    "networkType": "WIFI",
    "metered": false,
    "charging": true,
    "batteryPercent": 84,
    "freeStorageBytes": 5368709120
  }
}
```

Example response:

```json
{
  "tasks": [
    {
      "id": "task-123",
      "type": "DOWNLOAD",
      "resourceId": "sec:submissions:0000320193",
      "source": "SEC_EDGAR",
      "sourceUrl": "https://data.sec.gov/...",
      "leaseToken": "opaque-token",
      "leaseExpiresAt": "2026-08-18T10:05:00Z",
      "notBefore": "2026-08-18T10:00:00Z",
      "maxBytes": 10485760
    }
  ],
  "retryAfterSeconds": 15
}
```

The values above are protocol examples, not configuration defaults.

### Heartbeat / extend lease

```http
POST /api/workers/tasks/{taskId}/heartbeat
```

Return the renewed expiry or reject a stale lease.

### Upload artifact

```http
POST /api/workers/tasks/{taskId}/artifact
```

V1 can use streaming/multipart upload to server-controlled staging storage. Keep the service interface compatible with a future direct-to-object-store upload flow without requiring it now.

### Report failure / abandon

```http
POST /api/workers/tasks/{taskId}/failure
POST /api/workers/tasks/{taskId}/abandon
```

Use stable machine-readable failure codes plus a bounded diagnostic message.

Do not expose stack traces to workers.

### Completion

Completion can be implicit after successful artifact upload + server verification, which is preferable to trusting a separate client `complete` statement. If an explicit completion endpoint is retained, it must remain idempotent and verification-gated.

---

## 17. Configuration

Operational constants belong in YAML and should bind to a validated configuration object, preferably under `Edgar4JProperties` or a dedicated configuration-properties type if that class becomes too broad.

Illustrative structure:

```yaml
edgar4j:
  distributed-workers:
    enabled: false

    coordinator:
      lease-duration: 5m
      heartbeat-extension: 5m
      max-attempts: 3
      retry-backoff: 30s
      max-lease-batch: 2
      idle-retry-min: 5s
      idle-retry-max: 60s

    artifact:
      max-mobile-bytes: 50MB
      staging-retention: 1h

    server-worker:
      enabled: true
      max-concurrency: 4
```

These are illustrative values only. Final defaults must be selected from tests/benchmarks and kept out of service code.

Source throttling must continue to use the existing source/SEC policy configuration rather than introduce competing values under `distributed-workers`.

Use `@ConfigurationProperties` + validation for durations, sizes, positive bounds, and concurrency rather than scattered `@Value` fields.

---

## 18. Service and Package Plan

Keep classes small and responsibilities explicit.

Suggested backend additions:

```text
src/main/java/org/jds/edgar4j/
├── controller/
│   └── WorkerController.java
├── dto/worker/
│   ├── WorkerSessionRequest.java
│   ├── WorkerSessionResponse.java
│   ├── WorkerLeaseRequest.java
│   ├── WorkerLeaseResponse.java
│   ├── WorkerTaskResponse.java
│   ├── WorkerHeartbeatRequest.java
│   └── WorkerFailureRequest.java
├── model/
│   ├── WorkerTask.java
│   ├── WorkerTaskStatus.java
│   ├── WorkerTaskType.java
│   └── WorkerCapability.java
├── port/
│   ├── WorkerTaskDataPort.java
│   └── ArtifactStorePort.java
├── service/
│   ├── DistributedWorkPlanner.java
│   ├── WorkerCoordinatorService.java
│   ├── WorkerTaskHandler.java
│   └── ArtifactVerificationService.java
└── service/impl/
    ├── DistributedWorkPlannerImpl.java
    ├── WorkerCoordinatorServiceImpl.java
    ├── ServerDownloadWorker.java
    └── ArtifactVerificationServiceImpl.java
```

Exact storage-adapter locations should follow the completed dual-mode architecture. Avoid putting persistence-specific code in `WorkerCoordinatorServiceImpl`.

### Existing code integration

- `DownloadJobExecutor` should ask `DistributedWorkPlanner` to create distributed work only for download types explicitly migrated to the new path.
- Existing direct service calls remain the fallback until each operation is migrated and proven.
- `FilingSyncJob` continues to represent scheduled business behavior; it must not become a worker scheduler.
- Existing `DownloadSubmissionsService` should eventually be split so **raw acquisition** can be satisfied by a worker while **parse/import** remains reusable server logic.
- The existing source rate limiter remains the authority for SEC/source pacing.

Do not migrate all download types at once.

---

## 19. Planning Download Work

A clean boundary is:

```text
business request
    |
    v
DistributedWorkPlanner
    |
    +--> already have verified artifact? -> reuse
    |
    +--> resource eligible for mobile? -> WorkerTask
    |
    +--> not eligible -> ServerWorker task
```

Task creation must be idempotent using a stable logical key such as:

```text
source + resourceId + requiredVersion/variant
```

Do not create duplicate pending tasks every time a user refreshes a page or a scheduled sync repeats.

---

## 20. Server Worker

Implement the server worker **before** the mobile worker.

This proves:

- task creation;
- atomic leasing;
- retries;
- lease expiry;
- artifact staging;
- verification;
- parent `DownloadJob` progress;
- source throttling;
- cancellation;
- observability.

Only when the server worker can execute the new task path reliably should a remote device be allowed to lease the same tasks.

The server worker must remain available even when distributed mobile workers are disabled.

---

## 21. Mobile Worker V1

V1 mobile worker responsibilities should remain intentionally small:

```text
lease
-> policy check
-> stream download
-> compute SHA-256
-> stage locally
-> upload
-> wait for server acknowledgement
-> delete/retain local artifact according to cache policy
```

It should **not** contain SEC parsing/business rules. This prevents client/server parser-version drift and makes malicious output much less useful.

Suggested client modules:

```text
WorkerApiClient
WorkerPolicy
TaskLeaseManager
DownloadTaskExecutor
LocalArtifactCache
WorkerTelemetry
```

The mobile implementation should support cancellation and stream content to disk rather than buffer large artifacts in memory.

---

## 22. Large Files and Resumability

Do not send every bulk download to phones in the first version.

For V1:

- enforce a configurable `max-mobile-bytes`;
- prefer resources with known or bounded size;
- keep very large bulk archives server-only;
- delete abandoned temporary files according to retention policy.

Later, if measurements justify it, add:

- HTTP range/resumable source downloads where the source supports them;
- resumable/chunked artifact upload;
- persisted partial-download metadata;
- task reassignment using verified chunks.

Chunk-level distributed reconstruction is explicitly deferred because it adds substantial integrity and coordination complexity.

---

## 23. Parent Download Job Progress

`DownloadJob` progress should derive from child task outcomes, not mobile-reported percentages alone.

For example:

```text
planned tasks:   100
verified:         62
active leases:     8
pending:          27
terminal failed:   3
```

The existing UI can continue showing business-level progress while a future diagnostic/admin view may expose worker-level details.

If tasks vary heavily in size, count-based progress will be misleading. Add byte-weighted progress only when reliable expected sizes are available; otherwise report completed resource counts.

Cancellation of a parent job should prevent new child leases and mark/release its non-terminal tasks according to cancellation policy.

---

## 24. Error Handling

Use machine-readable error categories. Suggested categories:

```text
SOURCE_TIMEOUT
SOURCE_RATE_LIMITED
SOURCE_NOT_FOUND
SOURCE_REJECTED
NETWORK_UNAVAILABLE
POLICY_CHANGED
INSUFFICIENT_STORAGE
CHECKSUM_MISMATCH
CONTENT_INVALID
UPLOAD_FAILED
LEASE_EXPIRED
WORKER_CANCELLED
INTERNAL_ERROR
```

Retry only errors classified as retryable.

Rules:

- exponential backoff is bounded and configured;
- source `Retry-After` should be respected when applicable;
- checksum/content validation failures are logged and bounded by max attempts;
- repeated deterministic `NOT_FOUND`/validation failures should not retry forever;
- worker diagnostic strings are bounded/sanitized before logging or persistence;
- never log tokens or secrets.

---

## 25. Observability

Add structured logs and metrics for:

- registered/active worker sessions;
- queue depth by task type/source/status;
- leases issued;
- active leases;
- lease expirations;
- retries;
- tasks completed/failed/cancelled;
- bytes downloaded by server workers vs remote workers;
- artifact upload bytes;
- checksum/validation failures;
- deduplication hits;
- task duration;
- source dispatch rate/concurrency;
- server fallback utilization.

Useful dimensions should remain low-cardinality: source, task type, worker platform, result category. Do not use worker IDs, resource IDs, CIKs, or task IDs as metric labels.

Logs may include task IDs for diagnostics but must not include lease/session tokens.

---

## 26. Test Strategy

### Unit tests

- task lifecycle transitions;
- lease eligibility;
- lease expiry and requeue;
- retry/max-attempt behavior;
- idempotent task creation;
- idempotent completion;
- stale lease rejection;
- checksum mismatch;
- artifact promotion only after verification;
- worker policy decisions;
- source host allowlist/redirect rules;
- configuration validation.

### Concurrency tests

- two workers racing to lease one task -> exactly one wins;
- heartbeat racing with lease expiry;
- old worker upload after reassignment -> rejected;
- duplicate upload/completion -> one authoritative artifact/state transition.

### Integration tests

Use simulated workers, not physical phones:

1. server-only worker completes all work;
2. two remote workers split independent tasks;
3. one remote worker disappears and its task is reclaimed;
4. worker returns corrupted bytes and server rejects them;
5. mobile workers disabled -> current behavior remains functional;
6. parent download cancellation stops future leasing;
7. verified duplicate content deduplicates correctly;
8. low-resource persistence survives restart;
9. high-resource atomic leasing survives concurrent requests.

### Source-policy tests

This is a release gate:

- N server/mobile workers together must not exceed configured global source request/concurrency limits;
- adding more devices must not change the source-policy ceiling;
- rate-limited responses must propagate backoff to task dispatch where appropriate.

### Load tests

Simulate at least representative 1, 10, and high-count worker populations. Verify that idle polling, lease operations, DB/file writes, and metrics remain bounded. Determine actual production limits from measurements rather than assumptions.

---

## 27. Rollout Plan

### Phase 0 - Protocol and boundaries

- [ ] Define `WorkerTask` lifecycle and transition rules.
- [ ] Define `WorkerTaskDataPort` and `ArtifactStorePort`.
- [ ] Define versioned worker DTOs/protocol.
- [ ] Add validated `distributed-workers` configuration.
- [ ] Document source eligibility and host allowlists.
- [ ] Keep feature disabled by default.

**Exit:** contracts are testable without a mobile client.

### Phase 1 - Durable coordinator

- [ ] Implement task persistence for the active resource profiles.
- [ ] Implement atomic lease/release/expiry.
- [ ] Implement retry/backoff/max-attempt policy.
- [ ] Implement worker sessions and task-scoped lease tokens.
- [ ] Implement staging + artifact verification.
- [ ] Add metrics/logging.

**Exit:** coordinator passes lifecycle, concurrency, restart, and security tests.

### Phase 2 - Server worker first

- [ ] Implement `ServerDownloadWorker` using the same task semantics.
- [ ] Migrate one low-risk existing download flow to `DistributedWorkPlanner`.
- [ ] Preserve direct execution fallback behind configuration during migration.
- [ ] Verify parent `DownloadJob` progress/cancellation.
- [ ] Verify global source throttling with the server worker.

**Exit:** distributed task architecture adds no dependency on a mobile worker.

### Phase 3 - Mobile download worker

- [ ] Implement opt-in worker session/lease client.
- [ ] Implement Wi-Fi/metered/battery/charging/storage policy.
- [ ] Implement streaming source download + SHA-256.
- [ ] Implement secure artifact upload.
- [ ] Implement lease heartbeat/abandon/failure handling.
- [ ] Implement bounded local staging cleanup.
- [ ] Add protocol compatibility tests.

**Exit:** one or more mobile devices can safely assist while server fallback remains complete.

### Phase 4 - Local cache integration

- [ ] Use verified content-addressed cache entries where useful.
- [ ] Allow a normal client fetch to satisfy an equivalent leased task without a duplicate source download.
- [ ] Add eviction/storage-cap behavior.
- [ ] Measure cache hit/deduplication benefit.

**Exit:** cache saves measurable transfers without changing correctness.

### Phase 5 - Reliability and scale

- [ ] Add admin/diagnostic visibility for queue and workers if operationally useful.
- [ ] Tune lease/poll/backoff/concurrency values from metrics.
- [ ] Load-test larger worker populations.
- [ ] Add resumable transfer only if failures/large artifacts justify it.
- [ ] Expand eligible download types one at a time.

### Phase 6 - Optional compute contribution

Only if benchmarks show a worthwhile benefit:

- [ ] Add new versioned capability types such as `DECOMPRESS`, `PARSE`, `EXTRACT`, `NORMALIZE`, or `EMBED`.
- [ ] Define deterministic input/output schemas per task type.
- [ ] Add stronger validation/sandbox/resource limits.
- [ ] Keep server implementation as fallback.

Do not implement this phase merely because the protocol can support it.

---

## 28. Initial Migration Candidate

Start with a download whose result is a self-contained, easily verified raw artifact and whose existing import/parsing can remain server-side.

Do **not** start with the largest SEC bulk archives or a flow that performs many dependent source requests inside one task.

For filing/submissions work, first separate acquisition from parse/persist so the same verified raw bytes can come from either:

```text
ServerDownloadWorker
or
MobileDownloadWorker
```

and then feed the existing server-side import path.

This is the highest-leverage refactor because it removes network location from business logic without duplicating parsing logic on mobile.

---

## 29. Production Acceptance Criteria

The first production-capable release is complete only when all are true:

- [ ] Edgar4j works normally with zero remote workers connected.
- [ ] Distributed workers can be disabled entirely with configuration.
- [ ] A disconnected worker's tasks are automatically reclaimed.
- [ ] Concurrent workers cannot successfully lease the same active task.
- [ ] Stale leases cannot commit results.
- [ ] Worker output is verified before import/persistence.
- [ ] Corrupt/oversized/unexpected artifacts are rejected.
- [ ] Task creation and completion are idempotent.
- [ ] Parent download cancellation prevents further work.
- [ ] All source requests remain inside the configured global source policy.
- [ ] Increasing worker count does not increase the upstream policy ceiling.
- [ ] The worker API cannot be used as an arbitrary URL fetch/open-proxy service.
- [ ] Tokens/secrets are not exposed in logs or task payloads.
- [ ] Mobile contribution is explicitly user-controlled.
- [ ] Metered data is not used unless the user enables it.
- [ ] Temporary storage is bounded and cleaned.
- [ ] Low-resource mode remains single-server and functional.
- [ ] High-resource mode leases atomically under concurrency.
- [ ] Metrics expose queue depth, completion, retry, failure, expiry, and bytes transferred.
- [ ] Automated tests cover worker loss, corrupted data, duplicate delivery, and source throttling.

---

## 30. Deferred Decisions

Do not decide these until implementation measurements require them:

1. WebSocket/SSE worker notification vs HTTP polling. Start with polling.
2. Object storage vs filesystem artifact storage. Keep the port abstraction.
3. Sophisticated worker scoring/reputation. Start with eligibility + fair/simple scheduling.
4. Resumable chunk transfer. Start with bounded individual artifacts.
5. Mobile parsing/compute. Start with raw download only.
6. Peer-to-peer transfer. Keep out of scope unless a future requirement is compelling.
7. Redundant multi-worker verification. Server verification is sufficient for V1.

---

## 31. Recommended Implementation Order

The shortest safe path is:

```text
separate acquisition from import
        -> WorkerTask + ports
        -> atomic coordinator
        -> artifact verification
        -> ServerDownloadWorker
        -> integrate one download flow
        -> worker REST protocol
        -> mobile DOWNLOAD worker
        -> local cache
        -> measure
        -> expand only where useful
```

This keeps the design KISS: centralized authority, small leased tasks, untrusted workers, one verification path, one source policy, and a guaranteed server fallback.

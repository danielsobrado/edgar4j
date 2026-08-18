package org.jds.edgar4j.service.impl;

import static org.jds.edgar4j.constants.WorkerErrorCodes.ARTIFACT_UPLOAD_FAILED;
import static org.jds.edgar4j.constants.WorkerErrorCodes.DISABLED;
import static org.jds.edgar4j.constants.WorkerErrorCodes.LEASE_INVALID;
import static org.jds.edgar4j.constants.WorkerErrorCodes.PROTOCOL_UNSUPPORTED;
import static org.jds.edgar4j.constants.WorkerErrorCodes.SESSION_INVALID;
import static org.jds.edgar4j.constants.WorkerErrorCodes.SOURCE_DISPATCH_UNAVAILABLE;
import static org.jds.edgar4j.constants.WorkerErrorCodes.TASK_NOT_FOUND;
import static org.jds.edgar4j.constants.WorkerProtocolConstants.CURRENT_VERSION;
import static org.jds.edgar4j.constants.WorkerProtocolConstants.MAX_DIAGNOSTIC_MESSAGE_LENGTH;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.jds.edgar4j.dto.worker.WorkerFailureRequest;
import org.jds.edgar4j.dto.worker.WorkerHeartbeatRequest;
import org.jds.edgar4j.dto.worker.WorkerHeartbeatResponse;
import org.jds.edgar4j.dto.worker.WorkerLeaseRequest;
import org.jds.edgar4j.dto.worker.WorkerLeaseResponse;
import org.jds.edgar4j.dto.worker.WorkerSessionRequest;
import org.jds.edgar4j.dto.worker.WorkerSessionResponse;
import org.jds.edgar4j.dto.worker.WorkerTaskResponse;
import org.jds.edgar4j.exception.WorkerArtifactException;
import org.jds.edgar4j.exception.WorkerCoordinatorException;
import org.jds.edgar4j.model.WorkerCapability;
import org.jds.edgar4j.model.WorkerFailureCode;
import org.jds.edgar4j.model.WorkerPlatform;
import org.jds.edgar4j.model.WorkerSession;
import org.jds.edgar4j.model.WorkerSource;
import org.jds.edgar4j.model.WorkerTask;
import org.jds.edgar4j.port.ArtifactStorePort;
import org.jds.edgar4j.port.ArtifactStorePort.StagedArtifact;
import org.jds.edgar4j.port.ArtifactStorePort.VerifiedArtifact;
import org.jds.edgar4j.port.WorkerSessionDataPort;
import org.jds.edgar4j.port.WorkerTaskDataPort;
import org.jds.edgar4j.port.WorkerTaskDataPort.LeaseCriteria;
import org.jds.edgar4j.properties.DistributedWorkerProperties;
import org.jds.edgar4j.service.ArtifactVerificationService;
import org.jds.edgar4j.service.WorkerCoordinatorService;
import org.jds.edgar4j.service.WorkerMetrics;
import org.jds.edgar4j.service.WorkerRetryPolicy;
import org.jds.edgar4j.service.WorkerSourceDispatchPolicy;
import org.jds.edgar4j.service.WorkerSourceResourcePolicy;
import org.jds.edgar4j.service.WorkerTokenService;
import org.jds.edgar4j.service.WorkerTokenService.IssuedToken;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class WorkerCoordinatorServiceImpl implements WorkerCoordinatorService {

    private final WorkerTaskDataPort taskDataPort;
    private final WorkerSessionDataPort sessionDataPort;
    private final ArtifactStorePort artifactStore;
    private final ArtifactVerificationService artifactVerificationService;
    private final WorkerTokenService tokenService;
    private final WorkerSourceDispatchPolicy sourceDispatchPolicy;
    private final WorkerSourceResourcePolicy sourceResourcePolicy;
    private final WorkerRetryPolicy retryPolicy;
    private final WorkerMetrics metrics;
    private final DistributedWorkerProperties properties;
    private final Clock clock;

    public WorkerCoordinatorServiceImpl(
            WorkerTaskDataPort taskDataPort,
            WorkerSessionDataPort sessionDataPort,
            ArtifactStorePort artifactStore,
            ArtifactVerificationService artifactVerificationService,
            WorkerTokenService tokenService,
            WorkerSourceDispatchPolicy sourceDispatchPolicy,
            WorkerSourceResourcePolicy sourceResourcePolicy,
            WorkerRetryPolicy retryPolicy,
            WorkerMetrics metrics,
            DistributedWorkerProperties properties,
            Clock clock) {
        this.taskDataPort = taskDataPort;
        this.sessionDataPort = sessionDataPort;
        this.artifactStore = artifactStore;
        this.artifactVerificationService = artifactVerificationService;
        this.tokenService = tokenService;
        this.sourceDispatchPolicy = sourceDispatchPolicy;
        this.sourceResourcePolicy = sourceResourcePolicy;
        this.retryPolicy = retryPolicy;
        this.metrics = metrics;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public WorkerSessionResponse openSession(String principalId, WorkerSessionRequest request) {
        ensureEnabled();
        Objects.requireNonNull(request, "request");
        requireProtocol(request.protocolVersion());
        if (principalId == null || principalId.isBlank()) {
            throw new WorkerCoordinatorException("Authenticated worker principal is required", SESSION_INVALID);
        }

        Instant now = clock.instant();
        IssuedToken token = tokenService.issue();
        WorkerSession session = WorkerSession.builder()
                .id(UUID.randomUUID().toString())
                .principalId(principalId)
                .tokenHash(token.hash())
                .protocolVersion(CURRENT_VERSION)
                .clientVersion(request.clientVersion())
                .platform(request.platform())
                .capabilities(Set.copyOf(request.capabilities()))
                .maxConcurrentTasks(request.maxConcurrentTasks())
                .createdAt(now)
                .lastSeenAt(now)
                .expiresAt(now.plus(properties.getCoordinator().getSessionDuration()))
                .build();
        WorkerSession saved = sessionDataPort.save(session);
        metrics.sessionOpened();
        log.info("Opened worker session {} for platform {}", saved.getId(), saved.getPlatform());
        return new WorkerSessionResponse(saved.getId(), token.value(), CURRENT_VERSION, saved.getExpiresAt());
    }

    @Override
    public WorkerLeaseResponse lease(
            String sessionId,
            String sessionToken,
            WorkerLeaseRequest request) {
        ensureEnabled();
        Objects.requireNonNull(request, "request");
        requireProtocol(request.protocolVersion());

        Instant now = clock.instant();
        AuthenticatedSession authenticated = authenticate(sessionId, sessionToken, now);
        WorkerSession session = authenticated.session();
        if (!session.getCapabilities().containsAll(request.capabilities())) {
            throw new WorkerCoordinatorException("Requested capabilities exceed the registered worker session", SESSION_INVALID);
        }

        long activeLeases = taskDataPort.countActiveLeasesBySessionId(sessionId, now);
        long remainingConcurrency = Math.max(0L, (long) session.getMaxConcurrentTasks() - activeLeases);
        int requested = Math.min(request.maxTasks(), properties.getCoordinator().getMaxLeaseBatch());
        int leaseCount = (int) Math.min(remainingConcurrency, requested);
        if (leaseCount <= 0) {
            touchSession(authenticated, now);
            return new WorkerLeaseResponse(List.of(), retryAfterSeconds(properties.getCoordinator().getIdleRetryMin()));
        }

        long maxBytes = resolveWorkerMaxBytes(session, request);
        if (maxBytes <= 0) {
            touchSession(authenticated, now);
            return new WorkerLeaseResponse(List.of(), retryAfterSeconds(properties.getCoordinator().getIdleRetryMin()));
        }

        Set<WorkerSource> allowedSources = allowedSources(session);
        List<WorkerTaskResponse> leasedTasks = new ArrayList<>(leaseCount);
        for (int i = 0; i < leaseCount; i++) {
            IssuedToken leaseToken = tokenService.issue();
            Instant leaseExpiresAt = now.plus(properties.getCoordinator().getLeaseDuration());
            LeaseCriteria criteria = new LeaseCriteria(
                    sessionId,
                    leaseToken.hash(),
                    now,
                    leaseExpiresAt,
                    request.capabilities(),
                    allowedSources,
                    maxBytes);

            var leased = taskDataPort.leaseNext(criteria);
            if (leased.isEmpty()) {
                break;
            }

            WorkerTask task = leased.get();
            try {
                sourceResourcePolicy.validate(task);
                if (session.getPlatform() != WorkerPlatform.SERVER) {
                    sourceDispatchPolicy.reserveRemoteDispatch(task.getSource());
                }
            } catch (RuntimeException e) {
                requeueDispatchFailure(task, sessionId, leaseToken.hash(), now);
                if (e instanceof WorkerCoordinatorException) {
                    log.warn("Rejected distributed worker task {} by source policy: {}", task.getId(), e.getMessage());
                    continue;
                }
                throw new WorkerCoordinatorException(
                        "Unable to reserve source dispatch capacity",
                        SOURCE_DISPATCH_UNAVAILABLE,
                        e);
            }

            leasedTasks.add(toResponse(task, leaseToken.value()));
            metrics.leaseIssued(task.getSource(), session.getPlatform());
        }

        touchSession(authenticated, now);
        int retryAfter = leasedTasks.isEmpty()
                ? retryAfterSeconds(properties.getCoordinator().getIdleRetryMin())
                : 0;
        return new WorkerLeaseResponse(List.copyOf(leasedTasks), retryAfter);
    }

    @Override
    public WorkerHeartbeatResponse heartbeat(
            String sessionId,
            String sessionToken,
            String taskId,
            WorkerHeartbeatRequest request) {
        ensureEnabled();
        Objects.requireNonNull(request, "request");
        Instant now = clock.instant();
        AuthenticatedSession authenticated = authenticate(sessionId, sessionToken, now);
        String leaseTokenHash = tokenService.hash(request.leaseToken());
        Instant leaseExpiresAt = now.plus(properties.getCoordinator().getHeartbeatExtension());

        WorkerTask task = taskDataPort.extendLease(
                taskId,
                sessionId,
                leaseTokenHash,
                now,
                leaseExpiresAt)
                .orElseThrow(() -> new WorkerCoordinatorException("Worker lease is no longer active", LEASE_INVALID));
        touchSession(authenticated, now);
        return new WorkerHeartbeatResponse(task.getLeaseExpiresAt());
    }

    @Override
    public void reportFailure(
            String sessionId,
            String sessionToken,
            String taskId,
            WorkerFailureRequest request) {
        ensureEnabled();
        Objects.requireNonNull(request, "request");
        Instant now = clock.instant();
        AuthenticatedSession authenticated = authenticate(sessionId, sessionToken, now);
        String leaseTokenHash = tokenService.hash(request.leaseToken());
        String message = sanitizeDiagnostic(request.message());

        boolean transitioned;
        if (retryPolicy.isRetryable(request.code())) {
            Instant notBefore = retryNotBefore(taskId, now);
            transitioned = taskDataPort.requeueLease(
                    taskId,
                    sessionId,
                    leaseTokenHash,
                    now,
                    notBefore,
                    request.code(),
                    message)
                    .isPresent();
        } else {
            transitioned = taskDataPort.markFailed(
                    taskId,
                    sessionId,
                    leaseTokenHash,
                    request.code(),
                    message,
                    now)
                    .isPresent();
        }

        if (!transitioned) {
            throw new WorkerCoordinatorException("Worker lease is no longer active", LEASE_INVALID);
        }
        metrics.taskFailed(request.code());
        touchSession(authenticated, now);
        log.info("Worker task {} reported failure {}", taskId, request.code());
    }

    @Override
    public void abandon(
            String sessionId,
            String sessionToken,
            String taskId,
            String leaseToken) {
        ensureEnabled();
        Instant now = clock.instant();
        AuthenticatedSession authenticated = authenticate(sessionId, sessionToken, now);
        String leaseTokenHash = tokenService.hash(leaseToken);
        boolean transitioned = taskDataPort.requeueLease(
                taskId,
                sessionId,
                leaseTokenHash,
                now,
                retryNotBefore(taskId, now),
                WorkerFailureCode.WORKER_CANCELLED,
                "Worker abandoned task")
                .isPresent();
        if (!transitioned) {
            throw new WorkerCoordinatorException("Worker lease is no longer active", LEASE_INVALID);
        }
        touchSession(authenticated, now);
        log.info("Worker task {} was abandoned and requeued", taskId);
    }

    @Override
    public VerifiedArtifact acceptArtifact(
            String sessionId,
            String sessionToken,
            String taskId,
            String leaseToken,
            String claimedSha256,
            String contentType,
            InputStream input) {
        ensureEnabled();
        Instant now = clock.instant();
        AuthenticatedSession authenticated = authenticate(sessionId, sessionToken, now);
        String leaseTokenHash = tokenService.hash(leaseToken);
        WorkerTask task = taskDataPort.findById(taskId)
                .orElseThrow(() -> new WorkerCoordinatorException("Worker task not found", TASK_NOT_FOUND));
        if (!task.hasActiveLease(sessionId, leaseTokenHash, now)) {
            throw new WorkerCoordinatorException("Worker lease is no longer active", LEASE_INVALID);
        }

        StagedArtifact stagedArtifact;
        try {
            stagedArtifact = artifactStore.stage(taskId, input, artifactUploadLimit(task));
        } catch (IOException e) {
            requeueUploadFailure(task, sessionId, leaseTokenHash, now);
            throw new WorkerCoordinatorException("Failed to stage worker artifact", ARTIFACT_UPLOAD_FAILED, e);
        }

        Instant verificationExpiry = now.plus(properties.getCoordinator().getHeartbeatExtension());
        if (taskDataPort.extendLease(taskId, sessionId, leaseTokenHash, now, verificationExpiry).isEmpty()
                || taskDataPort.markVerifying(taskId, sessionId, leaseTokenHash, now).isEmpty()) {
            deleteStagedQuietly(stagedArtifact.stagingId());
            throw new WorkerCoordinatorException("Worker lease expired before verification", LEASE_INVALID);
        }

        try {
            VerifiedArtifact verified = artifactVerificationService.verifyAndPromote(
                    task,
                    stagedArtifact,
                    claimedSha256,
                    contentType,
                    now);
            WorkerTask completed = taskDataPort.markCompleted(
                    taskId,
                    sessionId,
                    leaseTokenHash,
                    verified.artifactId(),
                    now)
                    .orElseThrow(() -> new WorkerCoordinatorException(
                            "Worker lease expired before completion",
                            LEASE_INVALID));
            metrics.artifactAccepted(verified.sizeBytes());
            metrics.taskCompleted(completed.getSource());
            touchSession(authenticated, now);
            log.info("Verified worker artifact for task {} with {} bytes", taskId, verified.sizeBytes());
            return verified;
        } catch (WorkerArtifactException e) {
            metrics.artifactRejected(e.getFailureCode());
            transitionVerificationFailure(taskId, sessionId, leaseTokenHash, e, now);
            throw e;
        }
    }

    @Override
    public boolean revokeSession(String sessionId, String sessionToken) {
        ensureEnabled();
        Instant now = clock.instant();
        String tokenHash = tokenService.hash(sessionToken);
        boolean revoked = sessionDataPort.revoke(sessionId, tokenHash, now);
        if (revoked) {
            log.info("Revoked worker session {}", sessionId);
        }
        return revoked;
    }

    @Override
    public int reclaimExpiredLeases() {
        if (!properties.isEnabled()) {
            return 0;
        }
        Instant now = clock.instant();
        int count = taskDataPort.requeueExpiredLeases(
                now,
                now.plus(properties.getCoordinator().getRetryBackoff()));
        metrics.leasesExpired(count);
        if (count > 0) {
            log.info("Reclaimed {} expired worker leases", count);
        }
        return count;
    }

    @Override
    public int cleanupExpiredSessions() {
        if (!properties.isEnabled()) {
            return 0;
        }
        return sessionDataPort.deleteExpired(clock.instant());
    }

    @Override
    public int cleanupStagedArtifacts() {
        if (!properties.isEnabled()) {
            return 0;
        }
        Instant cutoff = clock.instant().minus(properties.getArtifact().getStagingRetention());
        try {
            return artifactStore.deleteStagedOlderThan(cutoff);
        } catch (IOException e) {
            throw new WorkerCoordinatorException("Failed to clean staged worker artifacts", ARTIFACT_UPLOAD_FAILED, e);
        }
    }

    private AuthenticatedSession authenticate(String sessionId, String sessionToken, Instant now) {
        if (sessionId == null || sessionId.isBlank() || sessionToken == null || sessionToken.isBlank()) {
            throw new WorkerCoordinatorException("Worker session credentials are required", SESSION_INVALID);
        }
        String tokenHash = tokenService.hash(sessionToken);
        WorkerSession session = sessionDataPort.findActive(sessionId, tokenHash, now)
                .orElseThrow(() -> new WorkerCoordinatorException("Worker session is invalid or expired", SESSION_INVALID));
        return new AuthenticatedSession(session, tokenHash);
    }

    private void touchSession(AuthenticatedSession authenticated, Instant now) {
        sessionDataPort.extend(
                authenticated.session().getId(),
                authenticated.tokenHash(),
                now,
                now.plus(properties.getCoordinator().getSessionDuration()));
    }

    private long resolveWorkerMaxBytes(WorkerSession session, WorkerLeaseRequest request) {
        if (session.getPlatform() == WorkerPlatform.SERVER) {
            return Long.MAX_VALUE;
        }
        long configured = properties.getArtifact().getMaxMobileBytes().toBytes();
        return Math.min(configured, request.runtime().freeStorageBytes());
    }

    private Set<WorkerSource> allowedSources(WorkerSession session) {
        if (session.getPlatform() == WorkerPlatform.SERVER) {
            return EnumSet.allOf(WorkerSource.class);
        }
        return Set.copyOf(properties.getSourcePolicy().getMobileEligibleSources());
    }

    private void requeueDispatchFailure(
            WorkerTask task,
            String sessionId,
            String leaseTokenHash,
            Instant now) {
        taskDataPort.requeueLease(
                task.getId(),
                sessionId,
                leaseTokenHash,
                now,
                retryNotBefore(task, now),
                WorkerFailureCode.SOURCE_RATE_LIMITED,
                "Source dispatch capacity unavailable");
    }

    private void requeueUploadFailure(
            WorkerTask task,
            String sessionId,
            String leaseTokenHash,
            Instant now) {
        taskDataPort.requeueLease(
                task.getId(),
                sessionId,
                leaseTokenHash,
                now,
                retryNotBefore(task, now),
                WorkerFailureCode.UPLOAD_FAILED,
                "Worker artifact upload failed");
        metrics.taskFailed(WorkerFailureCode.UPLOAD_FAILED);
    }

    private void transitionVerificationFailure(
            String taskId,
            String sessionId,
            String leaseTokenHash,
            WorkerArtifactException failure,
            Instant now) {
        if (retryPolicy.isRetryable(failure.getFailureCode())) {
            taskDataPort.requeueLease(
                    taskId,
                    sessionId,
                    leaseTokenHash,
                    now,
                    retryNotBefore(taskId, now),
                    failure.getFailureCode(),
                    sanitizeDiagnostic(failure.getMessage()));
        } else {
            taskDataPort.markFailed(
                    taskId,
                    sessionId,
                    leaseTokenHash,
                    failure.getFailureCode(),
                    sanitizeDiagnostic(failure.getMessage()),
                    now);
        }
        metrics.taskFailed(failure.getFailureCode());
    }

    private Instant retryNotBefore(String taskId, Instant now) {
        WorkerTask task = taskDataPort.findById(taskId)
                .orElseThrow(() -> new WorkerCoordinatorException("Worker task not found", TASK_NOT_FOUND));
        return retryNotBefore(task, now);
    }

    private Instant retryNotBefore(WorkerTask task, Instant now) {
        Duration base = properties.getCoordinator().getRetryBackoff();
        int exponent = Math.max(0, Math.min(task.getAttemptCount() - 1, 6));
        long multiplier = 1L << exponent;
        Duration delay;
        try {
            delay = base.multipliedBy(multiplier);
        } catch (ArithmeticException e) {
            delay = base.multipliedBy(64);
        }
        return now.plus(delay);
    }

    private long artifactUploadLimit(WorkerTask task) {
        long configured = properties.getArtifact().getMaxMobileBytes().toBytes();
        if (task.getMaxBytes() == null || task.getMaxBytes() <= 0) {
            return configured;
        }
        return Math.min(configured, task.getMaxBytes());
    }

    private static WorkerTaskResponse toResponse(WorkerTask task, String leaseToken) {
        return new WorkerTaskResponse(
                task.getId(),
                task.getType(),
                task.getResourceId(),
                task.getSource(),
                task.getSourceUrl(),
                leaseToken,
                task.getLeaseExpiresAt(),
                task.getNotBefore(),
                task.getMaxBytes() == null ? Long.MAX_VALUE : task.getMaxBytes(),
                task.getExpectedSha256(),
                task.getContentType());
    }

    private static int retryAfterSeconds(Duration duration) {
        long seconds = Math.max(1L, duration.toSeconds());
        return seconds > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) seconds;
    }

    private static String sanitizeDiagnostic(String message) {
        if (message == null) {
            return null;
        }
        String sanitized = message.replace('\n', ' ').replace('\r', ' ').trim();
        if (sanitized.length() <= MAX_DIAGNOSTIC_MESSAGE_LENGTH) {
            return sanitized;
        }
        return sanitized.substring(0, MAX_DIAGNOSTIC_MESSAGE_LENGTH);
    }

    private void deleteStagedQuietly(String stagingId) {
        try {
            artifactStore.deleteStaged(stagingId);
        } catch (IOException e) {
            log.warn("Failed to clean staged artifact after stale worker lease", e);
        }
    }

    private static void requireProtocol(int protocolVersion) {
        if (protocolVersion != CURRENT_VERSION) {
            throw new WorkerCoordinatorException("Unsupported worker protocol version", PROTOCOL_UNSUPPORTED);
        }
    }

    private void ensureEnabled() {
        if (!properties.isEnabled()) {
            throw new WorkerCoordinatorException("Distributed workers are disabled", DISABLED);
        }
    }

    private record AuthenticatedSession(WorkerSession session, String tokenHash) {
    }
}

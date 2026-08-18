package org.jds.edgar4j.service.impl;

import static org.jds.edgar4j.constants.WorkerErrorCodes.LEASE_INVALID;
import static org.jds.edgar4j.constants.WorkerErrorCodes.SERVER_EXECUTION_FAILED;
import static org.jds.edgar4j.constants.WorkerErrorCodes.TASK_NOT_FOUND;
import static org.jds.edgar4j.constants.WorkerProtocolConstants.MAX_DIAGNOSTIC_MESSAGE_LENGTH;
import static org.jds.edgar4j.constants.WorkerProtocolConstants.SERVER_SESSION_PREFIX;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jds.edgar4j.exception.WorkerArtifactException;
import org.jds.edgar4j.exception.WorkerCoordinatorException;
import org.jds.edgar4j.model.WorkerCapability;
import org.jds.edgar4j.model.WorkerFailureCode;
import org.jds.edgar4j.model.WorkerSource;
import org.jds.edgar4j.model.WorkerTask;
import org.jds.edgar4j.model.WorkerTaskStatus;
import org.jds.edgar4j.port.ArtifactStorePort;
import org.jds.edgar4j.port.ArtifactStorePort.StagedArtifact;
import org.jds.edgar4j.port.ArtifactStorePort.VerifiedArtifact;
import org.jds.edgar4j.port.WorkerTaskDataPort;
import org.jds.edgar4j.port.WorkerTaskDataPort.LeaseCriteria;
import org.jds.edgar4j.properties.DistributedWorkerProperties;
import org.jds.edgar4j.service.ArtifactVerificationService;
import org.jds.edgar4j.service.ServerDownloadWorker;
import org.jds.edgar4j.service.WorkerMetrics;
import org.jds.edgar4j.service.WorkerParentJobService;
import org.jds.edgar4j.service.WorkerRetryPolicy;
import org.jds.edgar4j.service.WorkerRetrySchedule;
import org.jds.edgar4j.service.WorkerSourceFailureClassifier;
import org.jds.edgar4j.service.WorkerSourceFetcher;
import org.jds.edgar4j.service.WorkerSourceResourcePolicy;
import org.jds.edgar4j.service.WorkerTokenService;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ServerDownloadWorkerImpl implements ServerDownloadWorker {

    private final WorkerTaskDataPort taskDataPort;
    private final ArtifactStorePort artifactStore;
    private final ArtifactVerificationService verificationService;
    private final WorkerTokenService tokenService;
    private final WorkerSourceResourcePolicy sourceResourcePolicy;
    private final WorkerSourceFailureClassifier failureClassifier;
    private final WorkerRetryPolicy retryPolicy;
    private final WorkerRetrySchedule retrySchedule;
    private final WorkerParentJobService parentJobService;
    private final WorkerMetrics metrics;
    private final DistributedWorkerProperties properties;
    private final List<WorkerSourceFetcher> sourceFetchers;
    private final Clock clock;
    private final String sessionId = SERVER_SESSION_PREFIX + UUID.randomUUID();

    public ServerDownloadWorkerImpl(
            WorkerTaskDataPort taskDataPort,
            ArtifactStorePort artifactStore,
            ArtifactVerificationService verificationService,
            WorkerTokenService tokenService,
            WorkerSourceResourcePolicy sourceResourcePolicy,
            WorkerSourceFailureClassifier failureClassifier,
            WorkerRetryPolicy retryPolicy,
            WorkerRetrySchedule retrySchedule,
            WorkerParentJobService parentJobService,
            WorkerMetrics metrics,
            DistributedWorkerProperties properties,
            List<WorkerSourceFetcher> sourceFetchers,
            Clock clock) {
        this.taskDataPort = taskDataPort;
        this.artifactStore = artifactStore;
        this.verificationService = verificationService;
        this.tokenService = tokenService;
        this.sourceResourcePolicy = sourceResourcePolicy;
        this.failureClassifier = failureClassifier;
        this.retryPolicy = retryPolicy;
        this.retrySchedule = retrySchedule;
        this.parentJobService = parentJobService;
        this.metrics = metrics;
        this.properties = properties;
        this.sourceFetchers = List.copyOf(sourceFetchers);
        this.clock = clock;
    }

    @Override
    public Optional<VerifiedArtifact> execute(String taskId) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        WorkerTask existing = taskDataPort.findById(taskId)
                .orElseThrow(() -> new WorkerCoordinatorException("Worker task not found", TASK_NOT_FOUND));
        if (cancelIfParentCancelled(existing)) {
            return Optional.empty();
        }
        if (existing.getStatus() == WorkerTaskStatus.COMPLETED) {
            return resolveCompletedArtifact(existing);
        }
        if (existing.getStatus() != WorkerTaskStatus.PENDING) {
            return Optional.empty();
        }

        Lease lease = newLease(clock.instant());
        Optional<WorkerTask> leased = taskDataPort.leaseById(taskId, lease.criteria());
        if (leased.isEmpty()) {
            return Optional.empty();
        }
        if (cancelIfParentCancelled(leased.get())) {
            return Optional.empty();
        }
        return Optional.of(executeLeased(leased.get(), lease.tokenHash()));
    }

    @Override
    public int drain(int maxTasks) {
        if (!isEnabled() || maxTasks <= 0) {
            return 0;
        }
        int completed = 0;
        int limit = Math.min(maxTasks, properties.getServerWorker().getMaxConcurrency());
        for (int i = 0; i < limit; i++) {
            Lease lease = newLease(clock.instant());
            Optional<WorkerTask> leased = taskDataPort.leaseNext(lease.criteria());
            if (leased.isEmpty()) {
                break;
            }
            if (cancelIfParentCancelled(leased.get())) {
                continue;
            }
            try {
                executeLeased(leased.get(), lease.tokenHash());
                completed++;
            } catch (RuntimeException e) {
                log.warn("Server worker failed task {}: {}", leased.get().getId(), e.getMessage());
            }
        }
        return completed;
    }

    private VerifiedArtifact executeLeased(WorkerTask task, String leaseTokenHash) {
        try {
            sourceResourcePolicy.validate(task);
        } catch (RuntimeException e) {
            transitionFailure(task, leaseTokenHash, WorkerFailureCode.POLICY_CHANGED, e.getMessage());
            throw new WorkerCoordinatorException("Server worker source policy rejected task", SERVER_EXECUTION_FAILED, e);
        }

        WorkerSourceFetcher fetcher = sourceFetchers.stream()
                .filter(candidate -> candidate.supports(task))
                .findFirst()
                .orElse(null);
        if (fetcher == null) {
            transitionFailure(task, leaseTokenHash, WorkerFailureCode.SOURCE_REJECTED, "No server source fetcher supports task");
            throw new WorkerCoordinatorException("No server source fetcher supports task", SERVER_EXECUTION_FAILED);
        }

        byte[] bytes;
        try {
            bytes = fetcher.fetch(task);
        } catch (RuntimeException e) {
            WorkerFailureCode failureCode = failureClassifier.classify(e);
            transitionFailure(task, leaseTokenHash, failureCode, e.getMessage());
            throw new WorkerCoordinatorException("Server worker source fetch failed", SERVER_EXECUTION_FAILED, e);
        }

        if (cancelIfParentCancelled(task)) {
            throw new WorkerCoordinatorException("Parent download job was cancelled", SERVER_EXECUTION_FAILED);
        }

        StagedArtifact staged;
        try {
            staged = artifactStore.stage(
                    task.getId(),
                    new ByteArrayInputStream(bytes),
                    taskLimit(task));
        } catch (IOException e) {
            transitionFailure(task, leaseTokenHash, WorkerFailureCode.UPLOAD_FAILED, e.getMessage());
            throw new WorkerCoordinatorException("Server worker failed to stage artifact", SERVER_EXECUTION_FAILED, e);
        }

        Instant transitionTime = clock.instant();
        Instant verificationExpiry = transitionTime.plus(properties.getCoordinator().getHeartbeatExtension());
        if (taskDataPort.extendLease(
                        task.getId(),
                        sessionId,
                        leaseTokenHash,
                        transitionTime,
                        verificationExpiry)
                        .isEmpty()
                || taskDataPort.markVerifying(
                        task.getId(),
                        sessionId,
                        leaseTokenHash,
                        transitionTime)
                        .isEmpty()) {
            deleteStagedQuietly(staged.stagingId());
            throw new WorkerCoordinatorException("Server worker lease expired before verification", LEASE_INVALID);
        }

        try {
            VerifiedArtifact verified = verificationService.verifyAndPromote(
                    task,
                    staged,
                    null,
                    task.getContentType(),
                    transitionTime);
            Instant completionTime = clock.instant();
            WorkerTask completed = taskDataPort.markCompleted(
                    task.getId(),
                    sessionId,
                    leaseTokenHash,
                    verified.artifactId(),
                    completionTime)
                    .orElseThrow(() -> new WorkerCoordinatorException(
                            "Server worker lease expired before completion",
                            LEASE_INVALID));
            metrics.artifactAccepted(verified.sizeBytes());
            metrics.taskCompleted(completed.getSource());
            parentJobService.refreshProgress(completed.getParentDownloadJobId());
            log.info("Server worker completed task {} with {} bytes", task.getId(), verified.sizeBytes());
            return verified;
        } catch (WorkerArtifactException e) {
            transitionFailure(task, leaseTokenHash, e.getFailureCode(), e.getMessage());
            metrics.artifactRejected(e.getFailureCode());
            throw e;
        }
    }

    private Optional<VerifiedArtifact> resolveCompletedArtifact(WorkerTask task) {
        if (task.getArtifactId() == null || task.getArtifactId().isBlank()) {
            throw new WorkerCoordinatorException("Completed worker task has no artifact", SERVER_EXECUTION_FAILED);
        }
        return artifactStore.findVerified(task.getArtifactId());
    }

    private Lease newLease(Instant now) {
        var token = tokenService.issue();
        LeaseCriteria criteria = new LeaseCriteria(
                sessionId,
                token.hash(),
                now,
                now.plus(properties.getCoordinator().getLeaseDuration()),
                EnumSet.allOf(WorkerCapability.class),
                EnumSet.allOf(WorkerSource.class),
                Long.MAX_VALUE);
        return new Lease(criteria, token.hash());
    }

    private void transitionFailure(
            WorkerTask task,
            String leaseTokenHash,
            WorkerFailureCode failureCode,
            String failureMessage) {
        Instant now = clock.instant();
        String message = sanitize(failureMessage);
        if (retryPolicy.isRetryable(failureCode)) {
            taskDataPort.requeueLease(
                    task.getId(),
                    sessionId,
                    leaseTokenHash,
                    now,
                    retrySchedule.nextAttemptAt(task, now),
                    failureCode,
                    message);
        } else {
            taskDataPort.markFailed(
                    task.getId(),
                    sessionId,
                    leaseTokenHash,
                    failureCode,
                    message,
                    now);
        }
        metrics.taskFailed(failureCode);
        parentJobService.refreshProgress(task.getParentDownloadJobId());
    }

    private boolean cancelIfParentCancelled(WorkerTask task) {
        String parentJobId = task.getParentDownloadJobId();
        if (!parentJobService.isCancelled(parentJobId)) {
            return false;
        }
        taskDataPort.cancelByParentDownloadJobId(parentJobId, clock.instant());
        parentJobService.refreshProgress(parentJobId);
        log.info("Cancelled worker task {} because parent job {} is cancelled", task.getId(), parentJobId);
        return true;
    }

    private long taskLimit(WorkerTask task) {
        if (task.getMaxBytes() != null && task.getMaxBytes() > 0) {
            return task.getMaxBytes();
        }
        return properties.getArtifact().getMaxMobileBytes().toBytes();
    }

    private boolean isEnabled() {
        return properties.isEnabled() && properties.getServerWorker().isEnabled();
    }

    private void deleteStagedQuietly(String stagingId) {
        try {
            artifactStore.deleteStaged(stagingId);
        } catch (IOException e) {
            log.warn("Failed to delete stale server-worker staging artifact", e);
        }
    }

    private static String sanitize(String message) {
        if (message == null) {
            return null;
        }
        String value = message.replace('\n', ' ').replace('\r', ' ').trim();
        return value.length() <= MAX_DIAGNOSTIC_MESSAGE_LENGTH
                ? value
                : value.substring(0, MAX_DIAGNOSTIC_MESSAGE_LENGTH);
    }

    private record Lease(LeaseCriteria criteria, String tokenHash) {
    }
}

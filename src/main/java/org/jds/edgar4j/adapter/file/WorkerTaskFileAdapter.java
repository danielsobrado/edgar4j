package org.jds.edgar4j.adapter.file;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jds.edgar4j.model.WorkerFailureCode;
import org.jds.edgar4j.model.WorkerTask;
import org.jds.edgar4j.model.WorkerTaskStatus;
import org.jds.edgar4j.port.WorkerTaskDataPort;
import org.jds.edgar4j.storage.file.FileCollection;
import org.jds.edgar4j.storage.file.FileFormat;
import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-low")
public class WorkerTaskFileAdapter implements WorkerTaskDataPort {

    private final FileCollection<WorkerTask> collection;
    private final Object mutationLock = new Object();

    public WorkerTaskFileAdapter(FileStorageEngine storageEngine) {
        this.collection = storageEngine.registerCollection(
                "worker_tasks",
                WorkerTask.class,
                FileFormat.JSONL,
                WorkerTask::getId,
                WorkerTask::setId);
        collection.registerIndex("logicalKey", WorkerTask::getLogicalKey);
    }

    @Override
    public WorkerTask createIfAbsent(WorkerTask task) {
        Objects.requireNonNull(task, "task");
        if (task.getLogicalKey() == null || task.getLogicalKey().isBlank()) {
            throw new IllegalArgumentException("Worker task logicalKey is required");
        }

        synchronized (mutationLock) {
            Optional<WorkerTask> existing = findByLogicalKey(task.getLogicalKey());
            if (existing.isPresent()) {
                return existing.get();
            }
            return collection.save(task);
        }
    }

    @Override
    public Optional<WorkerTask> findById(String taskId) {
        return collection.findById(taskId);
    }

    @Override
    public Optional<WorkerTask> findByLogicalKey(String logicalKey) {
        if (logicalKey == null) {
            return Optional.empty();
        }
        return collection.findIndexedFirst("logicalKey", logicalKey);
    }

    @Override
    public Optional<WorkerTask> leaseNext(LeaseCriteria criteria) {
        Objects.requireNonNull(criteria, "criteria");
        synchronized (mutationLock) {
            return collection.findAllMatching(task -> isLeaseCandidate(task, criteria)).stream()
                    .sorted(Comparator.comparingInt(WorkerTask::getPriority).reversed()
                            .thenComparing(WorkerTask::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                    .findFirst()
                    .map(task -> lease(task, criteria));
        }
    }

    @Override
    public Optional<WorkerTask> extendLease(
            String taskId,
            String sessionId,
            String leaseTokenHash,
            Instant now,
            Instant leaseExpiresAt) {
        synchronized (mutationLock) {
            return findById(taskId)
                    .filter(task -> task.hasActiveLease(sessionId, leaseTokenHash, now))
                    .map(task -> {
                        task.setLeaseExpiresAt(leaseExpiresAt);
                        task.setUpdatedAt(now);
                        return collection.save(task);
                    });
        }
    }

    @Override
    public Optional<WorkerTask> requeueLease(
            String taskId,
            String sessionId,
            String leaseTokenHash,
            Instant now,
            Instant notBefore,
            WorkerFailureCode failureCode,
            String failureMessage) {
        synchronized (mutationLock) {
            return findById(taskId)
                    .filter(task -> task.hasActiveLease(sessionId, leaseTokenHash, now))
                    .map(task -> requeueOrFail(task, now, notBefore, failureCode, failureMessage));
        }
    }

    @Override
    public int requeueExpiredLeases(Instant now, Instant retryNotBefore) {
        synchronized (mutationLock) {
            List<WorkerTask> expired = collection.findAllMatching(task ->
                    (task.getStatus() == WorkerTaskStatus.LEASED || task.getStatus() == WorkerTaskStatus.VERIFYING)
                            && task.getLeaseExpiresAt() != null
                            && !task.getLeaseExpiresAt().isAfter(now));
            expired.forEach(task -> requeueOrFail(
                    task,
                    now,
                    retryNotBefore,
                    WorkerFailureCode.LEASE_EXPIRED,
                    "Worker lease expired"));
            return expired.size();
        }
    }

    @Override
    public Optional<WorkerTask> markVerifying(
            String taskId,
            String sessionId,
            String leaseTokenHash,
            Instant now) {
        synchronized (mutationLock) {
            return findById(taskId)
                    .filter(task -> task.hasActiveLease(sessionId, leaseTokenHash, now))
                    .map(task -> {
                        task.setStatus(WorkerTaskStatus.VERIFYING);
                        task.setUpdatedAt(now);
                        return collection.save(task);
                    });
        }
    }

    @Override
    public Optional<WorkerTask> markCompleted(
            String taskId,
            String sessionId,
            String leaseTokenHash,
            String artifactId,
            Instant now) {
        synchronized (mutationLock) {
            return findById(taskId)
                    .filter(task -> task.getStatus() == WorkerTaskStatus.VERIFYING)
                    .filter(task -> hasValidLeaseCredentials(task, sessionId, leaseTokenHash, now))
                    .map(task -> {
                        task.setStatus(WorkerTaskStatus.COMPLETED);
                        task.setArtifactId(artifactId);
                        task.setCompletedAt(now);
                        task.setUpdatedAt(now);
                        clearLease(task);
                        return collection.save(task);
                    });
        }
    }

    @Override
    public Optional<WorkerTask> markFailed(
            String taskId,
            String sessionId,
            String leaseTokenHash,
            WorkerFailureCode failureCode,
            String failureMessage,
            Instant now) {
        synchronized (mutationLock) {
            return findById(taskId)
                    .filter(task -> task.getStatus() == WorkerTaskStatus.LEASED
                            || task.getStatus() == WorkerTaskStatus.VERIFYING)
                    .filter(task -> hasValidLeaseCredentials(task, sessionId, leaseTokenHash, now))
                    .map(task -> {
                        task.setStatus(WorkerTaskStatus.FAILED);
                        task.setLastErrorCode(failureCode);
                        task.setLastErrorMessage(failureMessage);
                        task.setCompletedAt(now);
                        task.setUpdatedAt(now);
                        clearLease(task);
                        return collection.save(task);
                    });
        }
    }

    @Override
    public int cancelByParentDownloadJobId(String parentDownloadJobId, Instant now) {
        if (parentDownloadJobId == null) {
            return 0;
        }
        synchronized (mutationLock) {
            List<WorkerTask> tasks = collection.findAllMatching(task ->
                    parentDownloadJobId.equals(task.getParentDownloadJobId())
                            && task.getStatus() != null
                            && !task.getStatus().isTerminal());
            tasks.forEach(task -> {
                task.setStatus(WorkerTaskStatus.CANCELLED);
                task.setCompletedAt(now);
                task.setUpdatedAt(now);
                clearLease(task);
                collection.save(task);
            });
            return tasks.size();
        }
    }

    @Override
    public WorkerTaskCounts countByParentDownloadJobId(String parentDownloadJobId) {
        List<WorkerTask> tasks = collection.findAllMatching(task ->
                Objects.equals(parentDownloadJobId, task.getParentDownloadJobId()));
        return new WorkerTaskCounts(
                countStatus(tasks, WorkerTaskStatus.PENDING),
                countStatus(tasks, WorkerTaskStatus.LEASED),
                countStatus(tasks, WorkerTaskStatus.VERIFYING),
                countStatus(tasks, WorkerTaskStatus.COMPLETED),
                countStatus(tasks, WorkerTaskStatus.FAILED),
                countStatus(tasks, WorkerTaskStatus.CANCELLED));
    }

    private WorkerTask lease(WorkerTask task, LeaseCriteria criteria) {
        task.setStatus(WorkerTaskStatus.LEASED);
        task.setLeaseOwnerSessionId(criteria.sessionId());
        task.setLeaseTokenHash(criteria.leaseTokenHash());
        task.setLeaseExpiresAt(criteria.leaseExpiresAt());
        task.setAttemptCount(task.getAttemptCount() + 1);
        task.setLastErrorCode(null);
        task.setLastErrorMessage(null);
        task.setUpdatedAt(criteria.now());
        return collection.save(task);
    }

    private WorkerTask requeueOrFail(
            WorkerTask task,
            Instant now,
            Instant notBefore,
            WorkerFailureCode failureCode,
            String failureMessage) {
        boolean exhausted = task.getAttemptCount() >= task.getMaxAttempts();
        task.setStatus(exhausted ? WorkerTaskStatus.FAILED : WorkerTaskStatus.PENDING);
        task.setNotBefore(exhausted ? task.getNotBefore() : notBefore);
        task.setLastErrorCode(failureCode);
        task.setLastErrorMessage(failureMessage);
        task.setCompletedAt(exhausted ? now : null);
        task.setUpdatedAt(now);
        clearLease(task);
        return collection.save(task);
    }

    private static boolean isLeaseCandidate(WorkerTask task, LeaseCriteria criteria) {
        if (!task.isEligibleForLease(criteria.now())) {
            return false;
        }
        if (task.getSource() == null || criteria.allowedSources() == null
                || !criteria.allowedSources().contains(task.getSource())) {
            return false;
        }
        if (criteria.capabilities() == null || !criteria.capabilities().containsAll(task.getRequiredCapabilities())) {
            return false;
        }
        long taskLimit = task.getMaxBytes() == null ? Long.MAX_VALUE : task.getMaxBytes();
        long expectedSize = task.getExpectedSizeBytes() == null ? 0L : task.getExpectedSizeBytes();
        return taskLimit <= criteria.maxBytes() && expectedSize <= criteria.maxBytes();
    }

    private static boolean hasValidLeaseCredentials(
            WorkerTask task,
            String sessionId,
            String leaseTokenHash,
            Instant now) {
        return Objects.equals(sessionId, task.getLeaseOwnerSessionId())
                && Objects.equals(leaseTokenHash, task.getLeaseTokenHash())
                && task.getLeaseExpiresAt() != null
                && task.getLeaseExpiresAt().isAfter(now);
    }

    private static void clearLease(WorkerTask task) {
        task.setLeaseOwnerSessionId(null);
        task.setLeaseTokenHash(null);
        task.setLeaseExpiresAt(null);
    }

    private static long countStatus(List<WorkerTask> tasks, WorkerTaskStatus status) {
        return tasks.stream().filter(task -> task.getStatus() == status).count();
    }
}

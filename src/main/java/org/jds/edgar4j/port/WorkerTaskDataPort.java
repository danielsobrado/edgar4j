package org.jds.edgar4j.port;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import org.jds.edgar4j.model.WorkerCapability;
import org.jds.edgar4j.model.WorkerFailureCode;
import org.jds.edgar4j.model.WorkerSource;
import org.jds.edgar4j.model.WorkerTask;

public interface WorkerTaskDataPort {

    WorkerTask createIfAbsent(WorkerTask task);

    Optional<WorkerTask> findById(String taskId);

    Optional<WorkerTask> findByLogicalKey(String logicalKey);

    Optional<WorkerTask> leaseNext(LeaseCriteria criteria);

    Optional<WorkerTask> extendLease(
            String taskId,
            String sessionId,
            String leaseTokenHash,
            Instant now,
            Instant leaseExpiresAt);

    Optional<WorkerTask> requeueLease(
            String taskId,
            String sessionId,
            String leaseTokenHash,
            Instant now,
            Instant notBefore,
            WorkerFailureCode failureCode,
            String failureMessage);

    int requeueExpiredLeases(Instant now, Instant retryNotBefore);

    Optional<WorkerTask> markVerifying(
            String taskId,
            String sessionId,
            String leaseTokenHash,
            Instant now);

    Optional<WorkerTask> markCompleted(
            String taskId,
            String sessionId,
            String leaseTokenHash,
            String artifactId,
            Instant now);

    Optional<WorkerTask> markFailed(
            String taskId,
            String sessionId,
            String leaseTokenHash,
            WorkerFailureCode failureCode,
            String failureMessage,
            Instant now);

    int cancelByParentDownloadJobId(String parentDownloadJobId, Instant now);

    WorkerTaskCounts countByParentDownloadJobId(String parentDownloadJobId);

    record LeaseCriteria(
            String sessionId,
            String leaseTokenHash,
            Instant now,
            Instant leaseExpiresAt,
            Set<WorkerCapability> capabilities,
            Set<WorkerSource> allowedSources,
            long maxBytes) {
    }

    record WorkerTaskCounts(
            long pending,
            long leased,
            long verifying,
            long completed,
            long failed,
            long cancelled) {

        public long total() {
            return pending + leased + verifying + completed + failed + cancelled;
        }
    }
}

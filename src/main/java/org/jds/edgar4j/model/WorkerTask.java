package org.jds.edgar4j.model;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "worker_tasks")
public class WorkerTask {

    @Id
    private String id;

    private String parentDownloadJobId;
    private String logicalKey;
    private String resourceId;
    private WorkerTaskType type;
    private WorkerSource source;
    private String sourceUrl;

    @Builder.Default
    private WorkerTaskStatus status = WorkerTaskStatus.PENDING;

    private int priority;
    private Instant notBefore;
    private String expectedSha256;
    private Long expectedSizeBytes;
    private String contentType;
    private Long maxBytes;

    @Builder.Default
    private Set<WorkerCapability> requiredCapabilities = new LinkedHashSet<>();

    private String leaseOwnerSessionId;
    private String leaseTokenHash;
    private Instant leaseExpiresAt;
    private int attemptCount;
    private int maxAttempts;
    private WorkerFailureCode lastErrorCode;
    private String lastErrorMessage;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;
    private String artifactId;

    public Set<WorkerCapability> getRequiredCapabilities() {
        LinkedHashSet<WorkerCapability> capabilities = new LinkedHashSet<>();
        if (requiredCapabilities != null) {
            capabilities.addAll(requiredCapabilities);
        }
        if (type == WorkerTaskType.DOWNLOAD
                && source == WorkerSource.SEC_EDGAR
                && (expectedSha256 == null || expectedSha256.isBlank())) {
            capabilities.add(WorkerCapability.TRUSTED_SOURCE);
        }
        return Set.copyOf(capabilities);
    }

    public boolean isEligibleForLease(Instant now) {
        Objects.requireNonNull(now, "now");
        return status == WorkerTaskStatus.PENDING
                && (notBefore == null || !notBefore.isAfter(now))
                && attemptCount < maxAttempts;
    }

    public boolean hasExpiredLease(Instant now) {
        Objects.requireNonNull(now, "now");
        return status == WorkerTaskStatus.LEASED
                && leaseExpiresAt != null
                && !leaseExpiresAt.isAfter(now);
    }

    public boolean hasActiveLease(String sessionId, String tokenHash, Instant now) {
        Objects.requireNonNull(now, "now");
        return status == WorkerTaskStatus.LEASED
                && Objects.equals(leaseOwnerSessionId, sessionId)
                && Objects.equals(leaseTokenHash, tokenHash)
                && leaseExpiresAt != null
                && leaseExpiresAt.isAfter(now);
    }
}

package org.jds.edgar4j.model;

import java.time.Instant;
import java.util.LinkedHashSet;
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
@Document(collection = "worker_sessions")
public class WorkerSession {

    @Id
    private String id;

    private String principalId;
    private String tokenHash;
    private int protocolVersion;
    private String clientVersion;
    private WorkerPlatform platform;

    @Builder.Default
    private Set<WorkerCapability> capabilities = new LinkedHashSet<>();

    private int maxConcurrentTasks;
    private Instant createdAt;
    private Instant lastSeenAt;
    private Instant expiresAt;
    private Instant revokedAt;

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt != null && expiresAt.isAfter(now);
    }
}

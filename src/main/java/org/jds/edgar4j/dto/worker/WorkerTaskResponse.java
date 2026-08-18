package org.jds.edgar4j.dto.worker;

import java.time.Instant;

import org.jds.edgar4j.model.WorkerSource;
import org.jds.edgar4j.model.WorkerTaskType;

public record WorkerTaskResponse(
        String id,
        WorkerTaskType type,
        String resourceId,
        WorkerSource source,
        String sourceUrl,
        String leaseToken,
        Instant leaseExpiresAt,
        Instant notBefore,
        long maxBytes,
        String expectedSha256,
        String contentType) {
}

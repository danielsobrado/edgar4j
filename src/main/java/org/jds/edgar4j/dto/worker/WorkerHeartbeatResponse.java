package org.jds.edgar4j.dto.worker;

import java.time.Instant;

public record WorkerHeartbeatResponse(Instant leaseExpiresAt) {
}

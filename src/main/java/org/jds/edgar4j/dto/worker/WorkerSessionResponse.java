package org.jds.edgar4j.dto.worker;

import java.time.Instant;

public record WorkerSessionResponse(
        String sessionId,
        String sessionToken,
        int protocolVersion,
        Instant expiresAt) {
}

package org.jds.edgar4j.port;

import java.time.Instant;
import java.util.Optional;

import org.jds.edgar4j.model.WorkerSession;

public interface WorkerSessionDataPort {

    WorkerSession save(WorkerSession session);

    Optional<WorkerSession> findActive(String sessionId, String tokenHash, Instant now);

    Optional<WorkerSession> extend(
            String sessionId,
            String tokenHash,
            Instant now,
            Instant expiresAt);

    boolean revoke(String sessionId, String tokenHash, Instant now);

    int deleteExpired(Instant now);
}

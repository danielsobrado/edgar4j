package org.jds.edgar4j.service;

import java.time.Duration;
import java.time.Instant;

public interface WorkerPresenceRegistry {

    void markSeen(String sessionId, Instant seenAt);

    void remove(String sessionId);

    boolean hasRecentWorker(Instant now, Duration recentWindow);
}

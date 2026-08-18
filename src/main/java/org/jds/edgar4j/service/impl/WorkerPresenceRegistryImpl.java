package org.jds.edgar4j.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jds.edgar4j.service.WorkerPresenceRegistry;
import org.springframework.stereotype.Service;

@Service
public class WorkerPresenceRegistryImpl implements WorkerPresenceRegistry {

    private final Map<String, Instant> lastSeenBySession = new ConcurrentHashMap<>();

    @Override
    public void markSeen(String sessionId, Instant seenAt) {
        if (sessionId == null || sessionId.isBlank() || seenAt == null) {
            return;
        }
        lastSeenBySession.put(sessionId, seenAt);
    }

    @Override
    public void remove(String sessionId) {
        if (sessionId != null) {
            lastSeenBySession.remove(sessionId);
        }
    }

    @Override
    public boolean hasRecentWorker(Instant now, Duration recentWindow) {
        Instant cutoff = now.minus(recentWindow);
        lastSeenBySession.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
        return !lastSeenBySession.isEmpty();
    }
}

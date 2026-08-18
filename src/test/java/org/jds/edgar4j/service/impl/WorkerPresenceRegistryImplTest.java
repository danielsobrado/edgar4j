package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class WorkerPresenceRegistryImplTest {

    private static final Instant NOW = Instant.parse("2026-08-18T10:00:00Z");

    @Test
    void recentWorkerPresenceExpiresWithoutHeartbeatTraffic() {
        WorkerPresenceRegistryImpl registry = new WorkerPresenceRegistryImpl();
        registry.markSeen("session-1", NOW);

        assertTrue(registry.hasRecentWorker(NOW.plusSeconds(20), Duration.ofSeconds(30)));
        assertFalse(registry.hasRecentWorker(NOW.plusSeconds(31), Duration.ofSeconds(30)));
    }

    @Test
    void revokedSessionIsRemovedImmediately() {
        WorkerPresenceRegistryImpl registry = new WorkerPresenceRegistryImpl();
        registry.markSeen("session-1", NOW);
        registry.remove("session-1");

        assertFalse(registry.hasRecentWorker(NOW, Duration.ofSeconds(30)));
    }
}

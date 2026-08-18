package org.jds.edgar4j.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class WorkerTaskTest {

    private static final Instant NOW = Instant.parse("2026-08-18T10:00:00Z");

    @Test
    void pendingTaskWithinAttemptLimitIsEligible() {
        WorkerTask task = WorkerTask.builder()
                .status(WorkerTaskStatus.PENDING)
                .attemptCount(0)
                .maxAttempts(3)
                .notBefore(NOW.minusSeconds(1))
                .build();

        assertTrue(task.isEligibleForLease(NOW));
    }

    @Test
    void taskScheduledForLaterIsNotEligible() {
        WorkerTask task = WorkerTask.builder()
                .status(WorkerTaskStatus.PENDING)
                .attemptCount(0)
                .maxAttempts(3)
                .notBefore(NOW.plusSeconds(1))
                .build();

        assertFalse(task.isEligibleForLease(NOW));
    }

    @Test
    void exhaustedTaskIsNotEligible() {
        WorkerTask task = WorkerTask.builder()
                .status(WorkerTaskStatus.PENDING)
                .attemptCount(3)
                .maxAttempts(3)
                .build();

        assertFalse(task.isEligibleForLease(NOW));
    }

    @Test
    void expiredAndActiveLeaseChecksUseAbsoluteTime() {
        WorkerTask task = WorkerTask.builder()
                .status(WorkerTaskStatus.LEASED)
                .leaseOwnerSessionId("session")
                .leaseTokenHash("hash")
                .leaseExpiresAt(NOW.plusSeconds(30))
                .build();

        assertTrue(task.hasActiveLease("session", "hash", NOW));
        assertFalse(task.hasExpiredLease(NOW));
        assertTrue(task.hasExpiredLease(NOW.plusSeconds(30)));
    }
}

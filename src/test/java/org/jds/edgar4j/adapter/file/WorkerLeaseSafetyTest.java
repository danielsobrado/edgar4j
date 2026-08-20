package org.jds.edgar4j.adapter.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumSet;

import org.jds.edgar4j.model.WorkerCapability;
import org.jds.edgar4j.model.WorkerSource;
import org.jds.edgar4j.model.WorkerTask;
import org.jds.edgar4j.model.WorkerTaskStatus;
import org.jds.edgar4j.model.WorkerTaskType;
import org.jds.edgar4j.port.WorkerTaskDataPort.LeaseCriteria;
import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.jds.edgar4j.storage.file.FileStorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

class WorkerLeaseSafetyTest {

    private static final Instant NOW = Instant.parse("2026-08-18T10:00:00Z");
    private static final String EXPECTED_SHA256 = "a".repeat(64);

    @TempDir
    Path tempDir;

    @Test
    void expiredLeaseCannotMutateTaskAfterReassignment() {
        WorkerTaskFileAdapter adapter = newAdapter();
        WorkerTask task = adapter.createIfAbsent(task());
        WorkerTask firstLease = adapter.leaseById(task.getId(), criteria("session-a", "token-a", NOW)).orElseThrow();
        Instant firstExpiry = firstLease.getLeaseExpiresAt();

        adapter.requeueExpiredLeases(firstExpiry, firstExpiry);
        WorkerTask secondLease = adapter.leaseById(
                task.getId(),
                criteria("session-b", "token-b", firstExpiry.plusMillis(1)))
                .orElseThrow();

        assertTrue(adapter.markVerifying(
                task.getId(),
                "session-a",
                "token-a",
                firstExpiry.plusMillis(1)).isEmpty());
        assertTrue(adapter.markCompleted(
                task.getId(),
                "session-a",
                "token-a",
                "artifact-old",
                firstExpiry.plusMillis(1)).isEmpty());

        WorkerTask verifying = adapter.markVerifying(
                task.getId(),
                "session-b",
                "token-b",
                firstExpiry.plusMillis(1)).orElseThrow();
        assertEquals(WorkerTaskStatus.VERIFYING, verifying.getStatus());
        assertEquals(secondLease.getAttemptCount(), verifying.getAttemptCount());
    }

    private WorkerTaskFileAdapter newAdapter() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setBasePath(tempDir.toString());
        properties.setFlushOnWrite(false);
        return new WorkerTaskFileAdapter(new FileStorageEngine(
                properties,
                new ObjectMapper().findAndRegisterModules()));
    }

    private static WorkerTask task() {
        return WorkerTask.builder()
                .logicalKey("stale-lease")
                .resourceId("sec:stale-lease")
                .type(WorkerTaskType.DOWNLOAD)
                .source(WorkerSource.SEC_EDGAR)
                .sourceUrl("https://data.sec.gov/submissions/CIK0000320193.json")
                .status(WorkerTaskStatus.PENDING)
                .expectedSha256(EXPECTED_SHA256)
                .requiredCapabilities(EnumSet.of(WorkerCapability.DOWNLOAD, WorkerCapability.SHA256))
                .maxBytes(1024L)
                .maxAttempts(3)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }

    private static LeaseCriteria criteria(String sessionId, String tokenHash, Instant now) {
        return new LeaseCriteria(
                sessionId,
                tokenHash,
                now,
                now.plusSeconds(60),
                EnumSet.of(WorkerCapability.DOWNLOAD, WorkerCapability.SHA256),
                EnumSet.of(WorkerSource.SEC_EDGAR),
                1024L);
    }
}

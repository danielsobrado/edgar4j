package org.jds.edgar4j.adapter.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

class WorkerTaskFileAdapterTest {

    private static final Instant NOW = Instant.parse("2026-08-18T10:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void concurrentLeaseRequestsCanOnlyClaimTaskOnce() throws Exception {
        WorkerTaskFileAdapter adapter = newAdapter();
        adapter.createIfAbsent(task("logical-1"));

        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Optional<WorkerTask>> first = executor.submit(() -> {
                start.await();
                return adapter.leaseNext(criteria("session-a", "hash-a"));
            });
            Future<Optional<WorkerTask>> second = executor.submit(() -> {
                start.await();
                return adapter.leaseNext(criteria("session-b", "hash-b"));
            });
            start.countDown();

            int successfulClaims = (first.get().isPresent() ? 1 : 0) + (second.get().isPresent() ? 1 : 0);
            assertEquals(1, successfulClaims);
        }

        WorkerTask stored = adapter.findByLogicalKey("logical-1").orElseThrow();
        assertEquals(WorkerTaskStatus.LEASED, stored.getStatus());
        assertEquals(1, stored.getAttemptCount());
    }

    @Test
    void createIfAbsentIsIdempotentByLogicalKey() {
        WorkerTaskFileAdapter adapter = newAdapter();

        WorkerTask first = adapter.createIfAbsent(task("logical-1"));
        WorkerTask second = adapter.createIfAbsent(task("logical-1"));

        assertEquals(first.getId(), second.getId());
        assertEquals(1, adapter.countByStatus(WorkerTaskStatus.PENDING));
    }

    @Test
    void expiredLeaseIsRequeued() {
        WorkerTaskFileAdapter adapter = newAdapter();
        adapter.createIfAbsent(task("logical-1"));
        WorkerTask leased = adapter.leaseNext(criteria("session-a", "hash-a")).orElseThrow();

        int reclaimed = adapter.requeueExpiredLeases(
                leased.getLeaseExpiresAt(),
                leased.getLeaseExpiresAt().plusSeconds(30));

        assertEquals(1, reclaimed);
        assertTrue(adapter.findById(leased.getId()).orElseThrow().isEligibleForLease(
                leased.getLeaseExpiresAt().plusSeconds(31)));
    }

    private WorkerTaskFileAdapter newAdapter() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setBasePath(tempDir.toString());
        properties.setFlushOnWrite(true);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        FileStorageEngine storageEngine = new FileStorageEngine(properties, objectMapper);
        return new WorkerTaskFileAdapter(storageEngine);
    }

    private static WorkerTask task(String logicalKey) {
        return WorkerTask.builder()
                .logicalKey(logicalKey)
                .resourceId("sec:test:" + logicalKey)
                .type(WorkerTaskType.DOWNLOAD)
                .source(WorkerSource.SEC_EDGAR)
                .sourceUrl("https://data.sec.gov/submissions/CIK0000320193.json")
                .status(WorkerTaskStatus.PENDING)
                .requiredCapabilities(EnumSet.of(WorkerCapability.DOWNLOAD, WorkerCapability.SHA256))
                .maxBytes(1024L)
                .attemptCount(0)
                .maxAttempts(3)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }

    private static LeaseCriteria criteria(String sessionId, String tokenHash) {
        return new LeaseCriteria(
                sessionId,
                tokenHash,
                NOW,
                NOW.plusSeconds(60),
                EnumSet.of(WorkerCapability.DOWNLOAD, WorkerCapability.SHA256),
                EnumSet.of(WorkerSource.SEC_EDGAR),
                1024L);
    }
}

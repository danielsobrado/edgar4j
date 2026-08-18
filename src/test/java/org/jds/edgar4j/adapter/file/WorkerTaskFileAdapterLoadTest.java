package org.jds.edgar4j.adapter.file;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

class WorkerTaskFileAdapterLoadTest {

    private static final int TASK_COUNT = 128;
    private static final int WORKER_COUNT = 16;
    private static final Instant NOW = Instant.parse("2026-08-18T10:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void concurrentWorkersLeaseEveryTaskExactlyOnce() throws Exception {
        WorkerTaskFileAdapter adapter = newAdapter();
        for (int i = 0; i < TASK_COUNT; i++) {
            adapter.createIfAbsent(task(i));
        }

        Set<String> leasedTaskIds = ConcurrentHashMap.newKeySet();
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(WORKER_COUNT)) {
            for (int worker = 0; worker < WORKER_COUNT; worker++) {
                int workerId = worker;
                executor.submit(() -> {
                    start.await();
                    while (true) {
                        var leased = adapter.leaseNext(criteria(workerId));
                        if (leased.isEmpty()) {
                            break;
                        }
                        leasedTaskIds.add(leased.get().getId());
                    }
                    return null;
                });
            }
            start.countDown();
            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Worker load simulation did not terminate");
            }
        }

        assertEquals(TASK_COUNT, leasedTaskIds.size());
        assertEquals(TASK_COUNT, adapter.countByStatus(WorkerTaskStatus.LEASED));
        assertEquals(0, adapter.countByStatus(WorkerTaskStatus.PENDING));
    }

    private WorkerTaskFileAdapter newAdapter() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setBasePath(tempDir.toString());
        properties.setFlushOnWrite(false);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        return new WorkerTaskFileAdapter(new FileStorageEngine(properties, objectMapper));
    }

    private static WorkerTask task(int index) {
        return WorkerTask.builder()
                .logicalKey("load-" + index)
                .resourceId("sec:load:" + index)
                .type(WorkerTaskType.DOWNLOAD)
                .source(WorkerSource.SEC_EDGAR)
                .sourceUrl("https://data.sec.gov/submissions/CIK0000320193.json")
                .status(WorkerTaskStatus.PENDING)
                .requiredCapabilities(EnumSet.of(WorkerCapability.DOWNLOAD, WorkerCapability.SHA256))
                .maxBytes(1024L)
                .maxAttempts(3)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }

    private static LeaseCriteria criteria(int workerId) {
        return new LeaseCriteria(
                "worker-" + workerId,
                "token-" + workerId,
                NOW,
                NOW.plusSeconds(300),
                EnumSet.of(WorkerCapability.DOWNLOAD, WorkerCapability.SHA256),
                EnumSet.of(WorkerSource.SEC_EDGAR),
                1024L);
    }
}

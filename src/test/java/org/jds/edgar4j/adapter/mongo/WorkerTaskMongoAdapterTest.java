package org.jds.edgar4j.adapter.mongo;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.EnumSet;

import org.jds.edgar4j.model.WorkerCapability;
import org.jds.edgar4j.model.WorkerSource;
import org.jds.edgar4j.model.WorkerTask;
import org.jds.edgar4j.model.WorkerTaskStatus;
import org.jds.edgar4j.model.WorkerTaskType;
import org.jds.edgar4j.port.WorkerTaskDataPort.LeaseCriteria;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@ExtendWith(MockitoExtension.class)
class WorkerTaskMongoAdapterTest {

    private static final Instant NOW = Instant.parse("2026-08-18T10:00:00Z");

    @Mock
    private MongoTemplate mongoTemplate;

    @Test
    void targetedLeaseUsesAtomicFindAndModifyClaim() {
        WorkerTaskMongoAdapter adapter = new WorkerTaskMongoAdapter(mongoTemplate);
        WorkerTask pending = WorkerTask.builder()
                .id("task-1")
                .logicalKey("logical-1")
                .resourceId("sec:test:1")
                .type(WorkerTaskType.DOWNLOAD)
                .source(WorkerSource.SEC_EDGAR)
                .status(WorkerTaskStatus.PENDING)
                .requiredCapabilities(EnumSet.of(WorkerCapability.DOWNLOAD, WorkerCapability.SHA256))
                .maxBytes(1024L)
                .attemptCount(0)
                .maxAttempts(3)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
        WorkerTask leased = WorkerTask.builder()
                .id("task-1")
                .status(WorkerTaskStatus.LEASED)
                .attemptCount(1)
                .leaseOwnerSessionId("session-1")
                .leaseTokenHash("lease-hash")
                .leaseExpiresAt(NOW.plusSeconds(60))
                .build();
        LeaseCriteria criteria = new LeaseCriteria(
                "session-1",
                "lease-hash",
                NOW,
                NOW.plusSeconds(60),
                EnumSet.of(WorkerCapability.DOWNLOAD, WorkerCapability.SHA256),
                EnumSet.of(WorkerSource.SEC_EDGAR),
                1024L);

        when(mongoTemplate.findById("task-1", WorkerTask.class)).thenReturn(pending);
        when(mongoTemplate.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(WorkerTask.class)))
                .thenReturn(leased);

        assertTrue(adapter.leaseById("task-1", criteria).isPresent());

        verify(mongoTemplate).findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(WorkerTask.class));
    }
}

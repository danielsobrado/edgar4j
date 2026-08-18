package org.jds.edgar4j.service.impl;

import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.jds.edgar4j.model.WorkerTask;
import org.jds.edgar4j.port.ArtifactStorePort.StagedArtifact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskBoundArtifactVerificationServiceTest {

    @Mock
    private ArtifactVerificationServiceImpl delegate;

    @Test
    void taskOwnedContentTypeOverridesWorkerSuppliedType() {
        TaskBoundArtifactVerificationService service = new TaskBoundArtifactVerificationService(delegate);
        WorkerTask task = WorkerTask.builder()
                .id("task-1")
                .contentType("application/json")
                .build();
        StagedArtifact staged = new StagedArtifact("staging-1", 2);
        Instant now = Instant.parse("2026-08-18T10:00:00Z");

        service.verifyAndPromote(
                task,
                staged,
                null,
                "text/plain",
                now);

        verify(delegate).verifyAndPromote(
                task,
                staged,
                null,
                "application/json",
                now);
    }
}

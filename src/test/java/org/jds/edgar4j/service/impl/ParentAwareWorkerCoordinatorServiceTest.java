package org.jds.edgar4j.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Optional;

import org.jds.edgar4j.model.WorkerTask;
import org.jds.edgar4j.port.ArtifactStorePort.VerifiedArtifact;
import org.jds.edgar4j.port.WorkerTaskDataPort;
import org.jds.edgar4j.service.WorkerParentJobService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParentAwareWorkerCoordinatorServiceTest {

    @Mock
    private WorkerCoordinatorServiceImpl delegate;
    @Mock
    private WorkerTaskDataPort taskDataPort;
    @Mock
    private WorkerParentJobService parentJobService;

    @Test
    void acceptedRemoteArtifactRefreshesParentProgress() {
        WorkerTask task = WorkerTask.builder()
                .id("task-1")
                .parentDownloadJobId("job-1")
                .build();
        VerifiedArtifact artifact = new VerifiedArtifact(
                "a".repeat(64),
                "a".repeat(64),
                2,
                "application/json",
                Instant.parse("2026-08-18T10:00:00Z"));
        when(taskDataPort.findById("task-1")).thenReturn(Optional.of(task));
        when(delegate.acceptArtifact(
                eq("session-1"),
                eq("session-token"),
                eq("task-1"),
                eq("lease-token"),
                eq("a".repeat(64)),
                eq("application/json"),
                any()))
                .thenReturn(artifact);
        ParentAwareWorkerCoordinatorService service = new ParentAwareWorkerCoordinatorService(
                delegate,
                taskDataPort,
                parentJobService);

        service.acceptArtifact(
                "session-1",
                "session-token",
                "task-1",
                "lease-token",
                "a".repeat(64),
                "application/json",
                new ByteArrayInputStream("{}".getBytes()));

        verify(parentJobService).refreshProgress("job-1");
    }
}

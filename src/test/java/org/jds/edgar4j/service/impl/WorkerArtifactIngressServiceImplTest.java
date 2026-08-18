package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.file.Path;

import org.jds.edgar4j.dto.worker.WorkerHeartbeatRequest;
import org.jds.edgar4j.exception.WorkerHttpException;
import org.jds.edgar4j.properties.DistributedWorkerProperties;
import org.jds.edgar4j.service.WorkerCoordinatorService;
import org.jds.edgar4j.storage.file.FileStorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.util.unit.DataSize;

import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class WorkerArtifactIngressServiceImplTest {

    @TempDir
    Path tempDir;

    @Mock
    private WorkerCoordinatorService coordinatorService;

    @Test
    void uploadLargerThanConfiguredLimitIsRejectedBeforeVerification() {
        DistributedWorkerProperties properties = new DistributedWorkerProperties();
        properties.getArtifact().setMaxMobileBytes(DataSize.ofBytes(10));
        FileStorageProperties storageProperties = new FileStorageProperties();
        storageProperties.setBasePath(tempDir.toString());
        WorkerArtifactIngressServiceImpl service = new WorkerArtifactIngressServiceImpl(
                coordinatorService,
                properties,
                storageProperties);
        var buffer = DefaultDataBufferFactory.sharedInstance.wrap(new byte[11]);

        WorkerHttpException failure = assertThrows(
                WorkerHttpException.class,
                () -> service.accept(
                                "session-1",
                                "session-token",
                                "task-1",
                                "lease-token",
                                null,
                                "application/json",
                                Flux.just(buffer))
                        .block());

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, failure.getStatus());
        verify(coordinatorService).heartbeat(
                "session-1",
                "session-token",
                "task-1",
                new WorkerHeartbeatRequest("lease-token", null));
        verify(coordinatorService, never()).acceptArtifact(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any());
    }
}

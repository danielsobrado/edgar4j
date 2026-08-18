package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.jds.edgar4j.model.WorkerSession;
import org.jds.edgar4j.model.WorkerTask;
import org.jds.edgar4j.model.WorkerTaskStatus;
import org.jds.edgar4j.port.ArtifactStorePort;
import org.jds.edgar4j.port.ArtifactStorePort.VerifiedArtifact;
import org.jds.edgar4j.port.WorkerSessionDataPort;
import org.jds.edgar4j.port.WorkerTaskDataPort;
import org.jds.edgar4j.service.WorkerTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;

import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class IdempotentWorkerArtifactIngressServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-18T10:00:00Z");
    private static final String SHA256 = "a".repeat(64);

    @Mock
    private WorkerArtifactIngressServiceImpl delegate;
    @Mock
    private WorkerTaskDataPort taskDataPort;
    @Mock
    private WorkerSessionDataPort sessionDataPort;
    @Mock
    private WorkerTokenService tokenService;
    @Mock
    private ArtifactStorePort artifactStore;

    @Test
    void completedArtifactRetryReturnsExistingVerifiedReceipt() {
        when(tokenService.hash("session-token")).thenReturn("session-hash");
        when(sessionDataPort.findActive("session-1", "session-hash", NOW))
                .thenReturn(Optional.of(WorkerSession.builder().id("session-1").build()));
        when(taskDataPort.findById("task-1")).thenReturn(Optional.of(WorkerTask.builder()
                .id("task-1")
                .status(WorkerTaskStatus.COMPLETED)
                .artifactId(SHA256)
                .build()));
        VerifiedArtifact artifact = new VerifiedArtifact(
                SHA256,
                SHA256,
                2,
                "application/json",
                NOW);
        when(artifactStore.findVerified(SHA256)).thenReturn(Optional.of(artifact));
        IdempotentWorkerArtifactIngressService service = new IdempotentWorkerArtifactIngressService(
                delegate,
                taskDataPort,
                sessionDataPort,
                tokenService,
                artifactStore,
                Clock.fixed(NOW, ZoneOffset.UTC));
        var body = Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(new byte[] {'{', '}'}));

        VerifiedArtifact result = service.accept(
                        "session-1",
                        "session-token",
                        "task-1",
                        "expired-lease-token",
                        SHA256,
                        "application/json",
                        body)
                .block();

        assertEquals(artifact, result);
        verify(delegate, never()).accept(any(), any(), any(), any(), any(), any(), any());
    }
}

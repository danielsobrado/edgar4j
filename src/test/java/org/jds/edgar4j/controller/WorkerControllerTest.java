package org.jds.edgar4j.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

import org.jds.edgar4j.dto.worker.WorkerLeaseRequest;
import org.jds.edgar4j.dto.worker.WorkerLeaseResponse;
import org.jds.edgar4j.dto.worker.WorkerRuntimeState;
import org.jds.edgar4j.model.WorkerCapability;
import org.jds.edgar4j.model.WorkerNetworkType;
import org.jds.edgar4j.port.ArtifactStorePort.VerifiedArtifact;
import org.jds.edgar4j.service.WorkerArtifactIngressService;
import org.jds.edgar4j.service.WorkerCoordinatorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class WorkerControllerTest {

    @Mock
    private WorkerCoordinatorService coordinatorService;

    @Mock
    private WorkerArtifactIngressService artifactIngressService;

    @Test
    void leasePassesSessionHeadersToCoordinator() {
        WorkerController controller = new WorkerController(coordinatorService, artifactIngressService);
        WorkerLeaseRequest request = new WorkerLeaseRequest(
                1,
                EnumSet.of(WorkerCapability.DOWNLOAD, WorkerCapability.SHA256),
                1,
                new WorkerRuntimeState(WorkerNetworkType.WIFI, false, true, 80, 10_000_000));
        WorkerLeaseResponse expected = new WorkerLeaseResponse(List.of(), 10);
        when(coordinatorService.lease("session-1", "session-token", request)).thenReturn(expected);

        var response = controller.lease("session-1", "session-token", request)
                .block(Duration.ofSeconds(1));

        assertNotNull(response);
        assertEquals(expected, response.getBody().getData());
        verify(coordinatorService).lease("session-1", "session-token", request);
    }

    @Test
    void artifactUploadStreamsRequestBodyToIngressService() {
        WorkerController controller = new WorkerController(coordinatorService, artifactIngressService);
        byte[] payload = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.put("/api/workers/tasks/task-1/artifact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new String(payload, StandardCharsets.UTF_8)));
        VerifiedArtifact artifact = new VerifiedArtifact(
                "a".repeat(64),
                "a".repeat(64),
                payload.length,
                MediaType.APPLICATION_JSON_VALUE,
                Instant.parse("2026-08-18T10:00:00Z"));
        when(artifactIngressService.accept(
                eq("session-1"),
                eq("session-token"),
                eq("task-1"),
                eq("lease-token"),
                eq("a".repeat(64)),
                eq(MediaType.APPLICATION_JSON_VALUE),
                any(Flux.class)))
                .thenReturn(Mono.just(artifact));

        var response = controller.uploadArtifact(
                        "session-1",
                        "session-token",
                        "lease-token",
                        "a".repeat(64),
                        "task-1",
                        exchange)
                .block(Duration.ofSeconds(1));

        assertNotNull(response);
        assertEquals(artifact.artifactId(), response.getBody().getData().artifactId());
        verify(artifactIngressService).accept(
                eq("session-1"),
                eq("session-token"),
                eq("task-1"),
                eq("lease-token"),
                eq("a".repeat(64)),
                eq(MediaType.APPLICATION_JSON_VALUE),
                any(Flux.class));
    }
}

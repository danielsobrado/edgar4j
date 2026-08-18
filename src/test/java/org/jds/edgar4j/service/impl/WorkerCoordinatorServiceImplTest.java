package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Optional;

import org.jds.edgar4j.dto.worker.WorkerLeaseRequest;
import org.jds.edgar4j.dto.worker.WorkerRuntimeState;
import org.jds.edgar4j.dto.worker.WorkerSessionRequest;
import org.jds.edgar4j.exception.WorkerCoordinatorException;
import org.jds.edgar4j.model.WorkerCapability;
import org.jds.edgar4j.model.WorkerNetworkType;
import org.jds.edgar4j.model.WorkerPlatform;
import org.jds.edgar4j.model.WorkerSession;
import org.jds.edgar4j.model.WorkerSource;
import org.jds.edgar4j.model.WorkerTask;
import org.jds.edgar4j.model.WorkerTaskStatus;
import org.jds.edgar4j.model.WorkerTaskType;
import org.jds.edgar4j.port.ArtifactStorePort;
import org.jds.edgar4j.port.WorkerSessionDataPort;
import org.jds.edgar4j.port.WorkerTaskDataPort;
import org.jds.edgar4j.properties.DistributedWorkerProperties;
import org.jds.edgar4j.service.ArtifactVerificationService;
import org.jds.edgar4j.service.WorkerMetrics;
import org.jds.edgar4j.service.WorkerRetryPolicy;
import org.jds.edgar4j.service.WorkerSourceDispatchPolicy;
import org.jds.edgar4j.service.WorkerSourceResourcePolicy;
import org.jds.edgar4j.service.WorkerTokenService;
import org.jds.edgar4j.service.WorkerTokenService.IssuedToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkerCoordinatorServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-08-18T10:00:00Z");

    @Mock
    private WorkerTaskDataPort taskDataPort;
    @Mock
    private WorkerSessionDataPort sessionDataPort;
    @Mock
    private ArtifactStorePort artifactStore;
    @Mock
    private ArtifactVerificationService artifactVerificationService;
    @Mock
    private WorkerTokenService tokenService;
    @Mock
    private WorkerSourceDispatchPolicy sourceDispatchPolicy;
    @Mock
    private WorkerSourceResourcePolicy sourceResourcePolicy;
    @Mock
    private WorkerRetryPolicy retryPolicy;
    @Mock
    private WorkerMetrics metrics;

    private DistributedWorkerProperties properties;
    private WorkerCoordinatorServiceImpl coordinator;

    @BeforeEach
    void setUp() {
        properties = new DistributedWorkerProperties();
        properties.setEnabled(true);
        coordinator = new WorkerCoordinatorServiceImpl(
                taskDataPort,
                sessionDataPort,
                artifactStore,
                artifactVerificationService,
                tokenService,
                sourceDispatchPolicy,
                sourceResourcePolicy,
                retryPolicy,
                metrics,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void openSessionReturnsRawTokenButPersistsOnlyHash() {
        when(tokenService.issue()).thenReturn(new IssuedToken("raw-session-token", "session-hash"));
        when(sessionDataPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = coordinator.openSession(
                "user@example.com",
                new WorkerSessionRequest(
                        1,
                        WorkerPlatform.ANDROID,
                        "1.0.0",
                        EnumSet.of(WorkerCapability.DOWNLOAD, WorkerCapability.SHA256),
                        2));

        ArgumentCaptor<WorkerSession> captor = ArgumentCaptor.forClass(WorkerSession.class);
        verify(sessionDataPort).save(captor.capture());
        assertEquals("raw-session-token", response.sessionToken());
        assertEquals("session-hash", captor.getValue().getTokenHash());
    }

    @Test
    void leaseRespectsSessionConcurrency() {
        WorkerSession session = activeSession(1);
        when(tokenService.hash("session-token")).thenReturn("session-hash");
        when(sessionDataPort.findActive("session-1", "session-hash", NOW)).thenReturn(Optional.of(session));
        when(taskDataPort.countActiveLeasesBySessionId("session-1", NOW)).thenReturn(1L);

        var response = coordinator.lease("session-1", "session-token", leaseRequest(2));

        assertTrue(response.tasks().isEmpty());
        verify(taskDataPort, never()).leaseNext(any());
    }

    @Test
    void deterministicSourcePolicyFailureTerminatesLeasedTask() {
        WorkerSession session = activeSession(2);
        WorkerTask task = task();
        when(tokenService.hash("session-token")).thenReturn("session-hash");
        when(tokenService.issue()).thenReturn(new IssuedToken("raw-lease-token", "lease-hash"));
        when(sessionDataPort.findActive("session-1", "session-hash", NOW)).thenReturn(Optional.of(session));
        when(taskDataPort.countActiveLeasesBySessionId("session-1", NOW)).thenReturn(0L);
        when(taskDataPort.leaseNext(any())).thenReturn(Optional.of(task), Optional.empty());
        org.mockito.Mockito.doThrow(new WorkerCoordinatorException("rejected", "WORKER_SOURCE_RESOURCE_REJECTED"))
                .when(sourceResourcePolicy)
                .validate(task);

        var response = coordinator.lease("session-1", "session-token", leaseRequest(1));

        assertTrue(response.tasks().isEmpty());
        verify(taskDataPort).markFailed(
                eq("task-1"),
                eq("session-1"),
                eq("lease-hash"),
                eq(org.jds.edgar4j.model.WorkerFailureCode.POLICY_CHANGED),
                eq("rejected"),
                eq(NOW));
        verify(sourceDispatchPolicy, never()).reserveRemoteDispatch(any());
    }

    private static WorkerSession activeSession(int maxConcurrentTasks) {
        return WorkerSession.builder()
                .id("session-1")
                .tokenHash("session-hash")
                .protocolVersion(1)
                .platform(WorkerPlatform.ANDROID)
                .capabilities(EnumSet.of(WorkerCapability.DOWNLOAD, WorkerCapability.SHA256))
                .maxConcurrentTasks(maxConcurrentTasks)
                .createdAt(NOW)
                .lastSeenAt(NOW)
                .expiresAt(NOW.plusSeconds(1800))
                .build();
    }

    private static WorkerLeaseRequest leaseRequest(int maxTasks) {
        return new WorkerLeaseRequest(
                1,
                EnumSet.of(WorkerCapability.DOWNLOAD, WorkerCapability.SHA256),
                maxTasks,
                new WorkerRuntimeState(WorkerNetworkType.WIFI, false, true, 90, 10_000_000));
    }

    private static WorkerTask task() {
        return WorkerTask.builder()
                .id("task-1")
                .logicalKey("logical-1")
                .resourceId("sec:test:1")
                .type(WorkerTaskType.DOWNLOAD)
                .source(WorkerSource.SEC_EDGAR)
                .sourceUrl("https://data.sec.gov/submissions/CIK0000320193.json")
                .status(WorkerTaskStatus.LEASED)
                .requiredCapabilities(EnumSet.of(WorkerCapability.DOWNLOAD, WorkerCapability.SHA256))
                .maxBytes(1024L)
                .leaseOwnerSessionId("session-1")
                .leaseTokenHash("lease-hash")
                .leaseExpiresAt(NOW.plusSeconds(300))
                .attemptCount(1)
                .maxAttempts(3)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }
}

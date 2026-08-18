package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Optional;

import org.jds.edgar4j.model.WorkerCapability;
import org.jds.edgar4j.model.WorkerTask;
import org.jds.edgar4j.port.ArtifactStorePort;
import org.jds.edgar4j.port.ArtifactStorePort.VerifiedArtifact;
import org.jds.edgar4j.port.WorkerTaskDataPort;
import org.jds.edgar4j.properties.WorkerMobileAssistProperties;
import org.jds.edgar4j.service.WorkerPresenceRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MobileFirstTrustedSourceFallbackTest {

    private static final Instant NOW = Instant.parse("2026-08-18T10:00:00Z");

    @Mock
    private ServerDownloadWorkerImpl delegate;
    @Mock
    private WorkerPresenceRegistry presenceRegistry;
    @Mock
    private WorkerTaskDataPort taskDataPort;
    @Mock
    private ArtifactStorePort artifactStore;

    @Test
    void trustedSourceTaskExecutesImmediatelyEvenWhenPhoneIsActive() {
        WorkerTask task = WorkerTask.builder()
                .id("task-1")
                .requiredCapabilities(EnumSet.of(
                        WorkerCapability.DOWNLOAD,
                        WorkerCapability.SHA256,
                        WorkerCapability.TRUSTED_SOURCE))
                .build();
        VerifiedArtifact artifact = new VerifiedArtifact(
                "a".repeat(64),
                "a".repeat(64),
                2,
                "application/json",
                NOW);
        when(taskDataPort.findById("task-1")).thenReturn(Optional.of(task));
        when(delegate.execute("task-1")).thenReturn(Optional.of(artifact));
        MobileFirstServerDownloadWorker worker = new MobileFirstServerDownloadWorker(
                delegate,
                presenceRegistry,
                taskDataPort,
                artifactStore,
                new WorkerMobileAssistProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertEquals(Optional.of(artifact), worker.execute("task-1"));
        verify(delegate).execute("task-1");
        verify(presenceRegistry, never()).hasRecentWorker(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }
}

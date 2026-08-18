package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.jds.edgar4j.port.ArtifactStorePort;
import org.jds.edgar4j.port.WorkerTaskDataPort;
import org.jds.edgar4j.properties.WorkerMobileAssistProperties;
import org.jds.edgar4j.service.WorkerPresenceRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MobileFirstServerDownloadWorkerTest {

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
    void backgroundDrainYieldsWhileRemoteWorkerIsRecentlyActive() {
        WorkerMobileAssistProperties properties = new WorkerMobileAssistProperties();
        when(presenceRegistry.hasRecentWorker(NOW, properties.getRecentPresenceWindow())).thenReturn(true);
        MobileFirstServerDownloadWorker worker = worker(properties);

        assertEquals(0, worker.drain(4));
        verify(delegate, never()).drain(4);
    }

    @Test
    void backgroundDrainRemainsImmediateWhenNoRemoteWorkerIsPresent() {
        WorkerMobileAssistProperties properties = new WorkerMobileAssistProperties();
        when(presenceRegistry.hasRecentWorker(NOW, properties.getRecentPresenceWindow())).thenReturn(false);
        when(delegate.drain(4)).thenReturn(3);
        MobileFirstServerDownloadWorker worker = worker(properties);

        assertEquals(3, worker.drain(4));
        verify(delegate).drain(4);
    }

    private MobileFirstServerDownloadWorker worker(WorkerMobileAssistProperties properties) {
        return new MobileFirstServerDownloadWorker(
                delegate,
                presenceRegistry,
                taskDataPort,
                artifactStore,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }
}

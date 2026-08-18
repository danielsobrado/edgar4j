package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.jds.edgar4j.model.WorkerTaskStatus;
import org.jds.edgar4j.port.WorkerTaskDataPort;
import org.jds.edgar4j.properties.DistributedWorkerProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkerDiagnosticsServiceImplTest {

    @Mock
    private WorkerTaskDataPort taskDataPort;

    @Test
    void diagnosticsExposeOnlyAggregateQueueState() {
        DistributedWorkerProperties properties = new DistributedWorkerProperties();
        properties.setEnabled(true);
        when(taskDataPort.countByStatus(WorkerTaskStatus.PENDING)).thenReturn(4L);
        when(taskDataPort.countByStatus(WorkerTaskStatus.LEASED)).thenReturn(2L);
        when(taskDataPort.countByStatus(WorkerTaskStatus.VERIFYING)).thenReturn(1L);
        when(taskDataPort.countByStatus(WorkerTaskStatus.COMPLETED)).thenReturn(10L);
        when(taskDataPort.countByStatus(WorkerTaskStatus.FAILED)).thenReturn(3L);
        when(taskDataPort.countByStatus(WorkerTaskStatus.CANCELLED)).thenReturn(1L);

        var result = new WorkerDiagnosticsServiceImpl(taskDataPort, properties).getDiagnostics();

        assertTrue(result.enabled());
        assertEquals(4, result.pending());
        assertEquals(2, result.leased());
        assertEquals(1, result.verifying());
        assertEquals(10, result.completed());
        assertEquals(3, result.failed());
        assertEquals(1, result.cancelled());
    }
}

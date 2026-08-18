package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.jds.edgar4j.dto.worker.WorkerFailureRequest;
import org.jds.edgar4j.model.WorkerFailureCode;
import org.jds.edgar4j.model.WorkerTask;
import org.jds.edgar4j.port.WorkerTaskDataPort;
import org.jds.edgar4j.service.WorkerParentJobService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RemoteWorkerFailureAdvisoryTest {

    @Mock
    private WorkerCoordinatorServiceImpl delegate;
    @Mock
    private WorkerTaskDataPort taskDataPort;
    @Mock
    private WorkerParentJobService parentJobService;

    @Test
    void deterministicRemoteFailureIsConvertedToRetryableAdvisoryFailure() {
        WorkerTask task = WorkerTask.builder()
                .id("task-1")
                .parentDownloadJobId("job-1")
                .build();
        when(taskDataPort.findById("task-1")).thenReturn(Optional.of(task));
        ParentAwareWorkerCoordinatorService service = new ParentAwareWorkerCoordinatorService(
                delegate,
                taskDataPort,
                parentJobService);
        WorkerFailureRequest report = new WorkerFailureRequest(
                "lease-token",
                WorkerFailureCode.SOURCE_NOT_FOUND,
                "browser observed 404");

        service.reportFailure("session-1", "session-token", "task-1", report);

        ArgumentCaptor<WorkerFailureRequest> captor = ArgumentCaptor.forClass(WorkerFailureRequest.class);
        verify(delegate).reportFailure(
                org.mockito.ArgumentMatchers.eq("session-1"),
                org.mockito.ArgumentMatchers.eq("session-token"),
                org.mockito.ArgumentMatchers.eq("task-1"),
                captor.capture());
        assertEquals(WorkerFailureCode.NETWORK_UNAVAILABLE, captor.getValue().code());
        assertEquals("lease-token", captor.getValue().leaseToken());
        verify(parentJobService).refreshProgress("job-1");
    }
}

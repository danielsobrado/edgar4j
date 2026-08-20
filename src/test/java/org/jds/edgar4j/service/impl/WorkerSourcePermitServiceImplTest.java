package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import java.util.Optional;

import org.jds.edgar4j.model.WorkerCapability;
import org.jds.edgar4j.model.WorkerSource;
import org.jds.edgar4j.model.WorkerTask;
import org.jds.edgar4j.model.WorkerTaskStatus;
import org.jds.edgar4j.port.WorkerTaskDataPort;
import org.jds.edgar4j.service.WorkerCoordinatorService;
import org.jds.edgar4j.service.WorkerSourceDispatchPolicy;
import org.jds.edgar4j.service.WorkerSourceResourcePolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkerSourcePermitServiceImplTest {

    @Mock
    private WorkerCoordinatorService coordinatorService;
    @Mock
    private WorkerTaskDataPort taskDataPort;
    @Mock
    private WorkerSourceResourcePolicy sourceResourcePolicy;
    @Mock
    private WorkerSourceDispatchPolicy sourceDispatchPolicy;

    @Test
    void validLeaseReservesSourceImmediatelyBeforeRequest() {
        WorkerTask task = task(EnumSet.of(WorkerCapability.DOWNLOAD, WorkerCapability.SHA256));
        when(taskDataPort.findById("task-1")).thenReturn(Optional.of(task));
        WorkerSourcePermitServiceImpl service = service();

        service.reserve("session-1", "session-token", "task-1", "lease-token");

        verify(coordinatorService).heartbeat(
                org.mockito.ArgumentMatchers.eq("session-1"),
                org.mockito.ArgumentMatchers.eq("session-token"),
                org.mockito.ArgumentMatchers.eq("task-1"),
                any());
        verify(sourceResourcePolicy).validate(task);
        verify(sourceDispatchPolicy).reserveSourceRequest(WorkerSource.SEC_EDGAR);
    }

    @Test
    void trustedSourceTaskCannotReceiveRemotePermit() {
        WorkerTask task = task(EnumSet.of(
                WorkerCapability.DOWNLOAD,
                WorkerCapability.SHA256,
                WorkerCapability.TRUSTED_SOURCE));
        when(taskDataPort.findById("task-1")).thenReturn(Optional.of(task));
        WorkerSourcePermitServiceImpl service = service();

        assertThrows(
                org.jds.edgar4j.exception.WorkerCoordinatorException.class,
                () -> service.reserve("session-1", "session-token", "task-1", "lease-token"));

        verify(sourceDispatchPolicy, never()).reserveSourceRequest(any());
    }

    private WorkerSourcePermitServiceImpl service() {
        return new WorkerSourcePermitServiceImpl(
                coordinatorService,
                taskDataPort,
                sourceResourcePolicy,
                sourceDispatchPolicy);
    }

    private static WorkerTask task(EnumSet<WorkerCapability> capabilities) {
        return WorkerTask.builder()
                .id("task-1")
                .source(WorkerSource.SEC_EDGAR)
                .sourceUrl("https://data.sec.gov/submissions/CIK0000320193.json")
                .status(WorkerTaskStatus.LEASED)
                .requiredCapabilities(capabilities)
                .build();
    }
}

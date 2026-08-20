package org.jds.edgar4j.service.impl;

import static org.jds.edgar4j.constants.WorkerErrorCodes.SOURCE_DISPATCH_UNAVAILABLE;
import static org.jds.edgar4j.constants.WorkerErrorCodes.TASK_NOT_FOUND;

import org.jds.edgar4j.dto.worker.WorkerHeartbeatRequest;
import org.jds.edgar4j.exception.WorkerCoordinatorException;
import org.jds.edgar4j.model.WorkerCapability;
import org.jds.edgar4j.model.WorkerTask;
import org.jds.edgar4j.port.WorkerTaskDataPort;
import org.jds.edgar4j.service.WorkerCoordinatorService;
import org.jds.edgar4j.service.WorkerSourceDispatchPolicy;
import org.jds.edgar4j.service.WorkerSourcePermitService;
import org.jds.edgar4j.service.WorkerSourceResourcePolicy;
import org.springframework.stereotype.Service;

@Service
public class WorkerSourcePermitServiceImpl implements WorkerSourcePermitService {

    private final WorkerCoordinatorService coordinatorService;
    private final WorkerTaskDataPort taskDataPort;
    private final WorkerSourceResourcePolicy sourceResourcePolicy;
    private final WorkerSourceDispatchPolicy sourceDispatchPolicy;

    public WorkerSourcePermitServiceImpl(
            WorkerCoordinatorService coordinatorService,
            WorkerTaskDataPort taskDataPort,
            WorkerSourceResourcePolicy sourceResourcePolicy,
            WorkerSourceDispatchPolicy sourceDispatchPolicy) {
        this.coordinatorService = coordinatorService;
        this.taskDataPort = taskDataPort;
        this.sourceResourcePolicy = sourceResourcePolicy;
        this.sourceDispatchPolicy = sourceDispatchPolicy;
    }

    @Override
    public void reserve(
            String sessionId,
            String sessionToken,
            String taskId,
            String leaseToken) {
        coordinatorService.heartbeat(
                sessionId,
                sessionToken,
                taskId,
                new WorkerHeartbeatRequest(leaseToken, null));

        WorkerTask task = taskDataPort.findById(taskId)
                .orElseThrow(() -> new WorkerCoordinatorException("Worker task not found", TASK_NOT_FOUND));
        if (task.getRequiredCapabilities() != null
                && task.getRequiredCapabilities().contains(WorkerCapability.TRUSTED_SOURCE)) {
            throw new WorkerCoordinatorException(
                    "Trusted source tasks cannot be dispatched to remote workers",
                    SOURCE_DISPATCH_UNAVAILABLE);
        }

        sourceResourcePolicy.validate(task);
        try {
            sourceDispatchPolicy.reserveSourceRequest(task.getSource());
        } catch (RuntimeException e) {
            throw new WorkerCoordinatorException(
                    "Unable to reserve source request capacity",
                    SOURCE_DISPATCH_UNAVAILABLE,
                    e);
        }
    }
}

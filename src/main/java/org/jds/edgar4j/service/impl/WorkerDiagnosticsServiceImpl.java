package org.jds.edgar4j.service.impl;

import org.jds.edgar4j.dto.worker.WorkerDiagnosticsResponse;
import org.jds.edgar4j.model.WorkerTaskStatus;
import org.jds.edgar4j.port.WorkerTaskDataPort;
import org.jds.edgar4j.properties.DistributedWorkerProperties;
import org.jds.edgar4j.service.WorkerDiagnosticsService;
import org.springframework.stereotype.Service;

@Service
public class WorkerDiagnosticsServiceImpl implements WorkerDiagnosticsService {

    private final WorkerTaskDataPort taskDataPort;
    private final DistributedWorkerProperties properties;

    public WorkerDiagnosticsServiceImpl(
            WorkerTaskDataPort taskDataPort,
            DistributedWorkerProperties properties) {
        this.taskDataPort = taskDataPort;
        this.properties = properties;
    }

    @Override
    public WorkerDiagnosticsResponse getDiagnostics() {
        return new WorkerDiagnosticsResponse(
                properties.isEnabled(),
                properties.getServerWorker().isEnabled(),
                properties.getServerWorker().getMaxConcurrency(),
                properties.getArtifact().getMaxMobileBytes().toBytes(),
                taskDataPort.countByStatus(WorkerTaskStatus.PENDING),
                taskDataPort.countByStatus(WorkerTaskStatus.LEASED),
                taskDataPort.countByStatus(WorkerTaskStatus.VERIFYING),
                taskDataPort.countByStatus(WorkerTaskStatus.COMPLETED),
                taskDataPort.countByStatus(WorkerTaskStatus.FAILED),
                taskDataPort.countByStatus(WorkerTaskStatus.CANCELLED));
    }
}

package org.jds.edgar4j.job;

import org.jds.edgar4j.properties.DistributedWorkerProperties;
import org.jds.edgar4j.service.WorkerCoordinatorService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class WorkerMaintenanceJob {

    private final WorkerCoordinatorService coordinatorService;
    private final DistributedWorkerProperties properties;

    public WorkerMaintenanceJob(
            WorkerCoordinatorService coordinatorService,
            DistributedWorkerProperties properties) {
        this.coordinatorService = coordinatorService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "#{@distributedWorkerProperties.coordinator.maintenanceInterval.toMillis()}")
    public void maintain() {
        if (!properties.isEnabled()) {
            return;
        }

        runSafely("lease reclamation", coordinatorService::reclaimExpiredLeases);
        runSafely("session cleanup", coordinatorService::cleanupExpiredSessions);
        runSafely("artifact staging cleanup", coordinatorService::cleanupStagedArtifacts);
    }

    private void runSafely(String operation, MaintenanceOperation maintenanceOperation) {
        try {
            maintenanceOperation.run();
        } catch (RuntimeException e) {
            log.error("Distributed worker maintenance failed during {}", operation, e);
        }
    }

    @FunctionalInterface
    private interface MaintenanceOperation {
        int run();
    }
}

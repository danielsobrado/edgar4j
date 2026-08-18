package org.jds.edgar4j.service.impl;

import static org.jds.edgar4j.constants.WorkerErrorCodes.SERVER_EXECUTION_FAILED;

import java.io.IOException;
import java.io.InputStream;

import org.jds.edgar4j.exception.WorkerCoordinatorException;
import org.jds.edgar4j.model.WorkerTask;
import org.jds.edgar4j.model.WorkerTaskStatus;
import org.jds.edgar4j.port.ArtifactStorePort;
import org.jds.edgar4j.port.ArtifactStorePort.VerifiedArtifact;
import org.jds.edgar4j.port.WorkerTaskDataPort;
import org.jds.edgar4j.service.DistributedResourceAcquisitionService;
import org.jds.edgar4j.service.DistributedWorkPlanner;
import org.jds.edgar4j.service.DistributedWorkPlanner.DownloadTaskSpec;
import org.jds.edgar4j.service.ServerDownloadWorker;
import org.springframework.stereotype.Service;

@Service
public class DistributedResourceAcquisitionServiceImpl implements DistributedResourceAcquisitionService {

    private final DistributedWorkPlanner workPlanner;
    private final WorkerTaskDataPort taskDataPort;
    private final ServerDownloadWorker serverDownloadWorker;
    private final ArtifactStorePort artifactStore;

    public DistributedResourceAcquisitionServiceImpl(
            DistributedWorkPlanner workPlanner,
            WorkerTaskDataPort taskDataPort,
            ServerDownloadWorker serverDownloadWorker,
            ArtifactStorePort artifactStore) {
        this.workPlanner = workPlanner;
        this.taskDataPort = taskDataPort;
        this.serverDownloadWorker = serverDownloadWorker;
        this.artifactStore = artifactStore;
    }

    @Override
    public byte[] acquire(DownloadTaskSpec specification) {
        WorkerTask task = workPlanner.planDownload(specification);
        VerifiedArtifact artifact = resolveExisting(task);
        if (artifact == null) {
            artifact = serverDownloadWorker.execute(task.getId()).orElse(null);
        }
        if (artifact == null) {
            WorkerTask current = taskDataPort.findById(task.getId()).orElse(task);
            artifact = resolveExisting(current);
        }
        if (artifact == null) {
            throw new WorkerCoordinatorException(
                    "Distributed resource is currently assigned to another worker or unavailable",
                    SERVER_EXECUTION_FAILED);
        }
        if (artifact.sizeBytes() > specification.maxBytes()) {
            throw new WorkerCoordinatorException(
                    "Verified artifact exceeds acquisition limit",
                    SERVER_EXECUTION_FAILED);
        }

        try (InputStream input = artifactStore.openVerified(artifact.artifactId())) {
            byte[] bytes = input.readAllBytes();
            if (bytes.length != artifact.sizeBytes()) {
                throw new WorkerCoordinatorException(
                        "Verified artifact size changed after promotion",
                        SERVER_EXECUTION_FAILED);
            }
            return bytes;
        } catch (IOException e) {
            throw new WorkerCoordinatorException(
                    "Failed to read verified distributed artifact",
                    SERVER_EXECUTION_FAILED,
                    e);
        }
    }

    private VerifiedArtifact resolveExisting(WorkerTask task) {
        if (task.getStatus() != WorkerTaskStatus.COMPLETED
                || task.getArtifactId() == null
                || task.getArtifactId().isBlank()) {
            return null;
        }
        return artifactStore.findVerified(task.getArtifactId()).orElse(null);
    }
}

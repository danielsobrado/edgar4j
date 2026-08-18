package org.jds.edgar4j.service.impl;

import java.io.InputStream;

import org.jds.edgar4j.dto.worker.WorkerFailureRequest;
import org.jds.edgar4j.dto.worker.WorkerHeartbeatRequest;
import org.jds.edgar4j.dto.worker.WorkerHeartbeatResponse;
import org.jds.edgar4j.dto.worker.WorkerLeaseRequest;
import org.jds.edgar4j.dto.worker.WorkerLeaseResponse;
import org.jds.edgar4j.dto.worker.WorkerSessionRequest;
import org.jds.edgar4j.dto.worker.WorkerSessionResponse;
import org.jds.edgar4j.port.ArtifactStorePort.VerifiedArtifact;
import org.jds.edgar4j.port.WorkerTaskDataPort;
import org.jds.edgar4j.service.WorkerCoordinatorService;
import org.jds.edgar4j.service.WorkerParentJobService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Primary
@Service
public class ParentAwareWorkerCoordinatorService implements WorkerCoordinatorService {

    private final WorkerCoordinatorServiceImpl delegate;
    private final WorkerTaskDataPort taskDataPort;
    private final WorkerParentJobService parentJobService;

    public ParentAwareWorkerCoordinatorService(
            WorkerCoordinatorServiceImpl delegate,
            WorkerTaskDataPort taskDataPort,
            WorkerParentJobService parentJobService) {
        this.delegate = delegate;
        this.taskDataPort = taskDataPort;
        this.parentJobService = parentJobService;
    }

    @Override
    public WorkerSessionResponse openSession(String principalId, WorkerSessionRequest request) {
        return delegate.openSession(principalId, request);
    }

    @Override
    public WorkerLeaseResponse lease(String sessionId, String sessionToken, WorkerLeaseRequest request) {
        return delegate.lease(sessionId, sessionToken, request);
    }

    @Override
    public WorkerHeartbeatResponse heartbeat(
            String sessionId,
            String sessionToken,
            String taskId,
            WorkerHeartbeatRequest request) {
        return delegate.heartbeat(sessionId, sessionToken, taskId, request);
    }

    @Override
    public void reportFailure(
            String sessionId,
            String sessionToken,
            String taskId,
            WorkerFailureRequest request) {
        String parentJobId = parentJobId(taskId);
        delegate.reportFailure(sessionId, sessionToken, taskId, request);
        parentJobService.refreshProgress(parentJobId);
    }

    @Override
    public void abandon(
            String sessionId,
            String sessionToken,
            String taskId,
            String leaseToken) {
        String parentJobId = parentJobId(taskId);
        delegate.abandon(sessionId, sessionToken, taskId, leaseToken);
        parentJobService.refreshProgress(parentJobId);
    }

    @Override
    public VerifiedArtifact acceptArtifact(
            String sessionId,
            String sessionToken,
            String taskId,
            String leaseToken,
            String claimedSha256,
            String contentType,
            InputStream input) {
        String parentJobId = parentJobId(taskId);
        VerifiedArtifact artifact = delegate.acceptArtifact(
                sessionId,
                sessionToken,
                taskId,
                leaseToken,
                claimedSha256,
                contentType,
                input);
        parentJobService.refreshProgress(parentJobId);
        return artifact;
    }

    @Override
    public boolean revokeSession(String sessionId, String sessionToken) {
        return delegate.revokeSession(sessionId, sessionToken);
    }

    @Override
    public int reclaimExpiredLeases() {
        return delegate.reclaimExpiredLeases();
    }

    @Override
    public int cleanupExpiredSessions() {
        return delegate.cleanupExpiredSessions();
    }

    @Override
    public int cleanupStagedArtifacts() {
        return delegate.cleanupStagedArtifacts();
    }

    private String parentJobId(String taskId) {
        return taskDataPort.findById(taskId)
                .map(task -> task.getParentDownloadJobId())
                .orElse(null);
    }
}

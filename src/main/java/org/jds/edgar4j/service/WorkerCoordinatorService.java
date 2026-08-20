package org.jds.edgar4j.service;

import java.io.InputStream;

import org.jds.edgar4j.dto.worker.WorkerFailureRequest;
import org.jds.edgar4j.dto.worker.WorkerHeartbeatRequest;
import org.jds.edgar4j.dto.worker.WorkerHeartbeatResponse;
import org.jds.edgar4j.dto.worker.WorkerLeaseRequest;
import org.jds.edgar4j.dto.worker.WorkerLeaseResponse;
import org.jds.edgar4j.dto.worker.WorkerSessionRequest;
import org.jds.edgar4j.dto.worker.WorkerSessionResponse;
import org.jds.edgar4j.dto.worker.WorkerSourcePermitRequest;
import org.jds.edgar4j.port.ArtifactStorePort.VerifiedArtifact;

public interface WorkerCoordinatorService {

    WorkerSessionResponse openSession(String principalId, WorkerSessionRequest request);

    WorkerLeaseResponse lease(
            String sessionId,
            String sessionToken,
            WorkerLeaseRequest request);

    WorkerHeartbeatResponse heartbeat(
            String sessionId,
            String sessionToken,
            String taskId,
            WorkerHeartbeatRequest request);

    void reserveSource(
            String sessionId,
            String sessionToken,
            String taskId,
            WorkerSourcePermitRequest request);

    void reportFailure(
            String sessionId,
            String sessionToken,
            String taskId,
            WorkerFailureRequest request);

    void abandon(
            String sessionId,
            String sessionToken,
            String taskId,
            String leaseToken);

    VerifiedArtifact acceptArtifact(
            String sessionId,
            String sessionToken,
            String taskId,
            String leaseToken,
            String claimedSha256,
            String contentType,
            InputStream input);

    boolean revokeSession(String sessionId, String sessionToken);

    int reclaimExpiredLeases();

    int cleanupExpiredSessions();

    int cleanupStagedArtifacts();
}

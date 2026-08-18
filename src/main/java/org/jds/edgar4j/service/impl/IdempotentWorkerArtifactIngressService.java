package org.jds.edgar4j.service.impl;

import java.time.Clock;

import org.jds.edgar4j.model.WorkerTaskStatus;
import org.jds.edgar4j.port.ArtifactStorePort;
import org.jds.edgar4j.port.ArtifactStorePort.VerifiedArtifact;
import org.jds.edgar4j.port.WorkerSessionDataPort;
import org.jds.edgar4j.port.WorkerTaskDataPort;
import org.jds.edgar4j.service.WorkerArtifactIngressService;
import org.jds.edgar4j.service.WorkerTokenService;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Primary
@Service
public class IdempotentWorkerArtifactIngressService implements WorkerArtifactIngressService {

    private final WorkerArtifactIngressServiceImpl delegate;
    private final WorkerTaskDataPort taskDataPort;
    private final WorkerSessionDataPort sessionDataPort;
    private final WorkerTokenService tokenService;
    private final ArtifactStorePort artifactStore;
    private final Clock clock;

    public IdempotentWorkerArtifactIngressService(
            WorkerArtifactIngressServiceImpl delegate,
            WorkerTaskDataPort taskDataPort,
            WorkerSessionDataPort sessionDataPort,
            WorkerTokenService tokenService,
            ArtifactStorePort artifactStore,
            Clock clock) {
        this.delegate = delegate;
        this.taskDataPort = taskDataPort;
        this.sessionDataPort = sessionDataPort;
        this.tokenService = tokenService;
        this.artifactStore = artifactStore;
        this.clock = clock;
    }

    @Override
    public Mono<VerifiedArtifact> accept(
            String sessionId,
            String sessionToken,
            String taskId,
            String leaseToken,
            String claimedSha256,
            String contentType,
            Flux<DataBuffer> body) {
        VerifiedArtifact completed = findCompletedArtifact(
                sessionId,
                sessionToken,
                taskId,
                claimedSha256);
        if (completed == null) {
            return delegate.accept(
                    sessionId,
                    sessionToken,
                    taskId,
                    leaseToken,
                    claimedSha256,
                    contentType,
                    body);
        }

        return body.doOnNext(DataBufferUtils::release)
                .then(Mono.just(completed));
    }

    private VerifiedArtifact findCompletedArtifact(
            String sessionId,
            String sessionToken,
            String taskId,
            String claimedSha256) {
        if (sessionId == null
                || sessionToken == null
                || claimedSha256 == null
                || claimedSha256.isBlank()) {
            return null;
        }

        String sessionTokenHash = tokenService.hash(sessionToken);
        if (sessionDataPort.findActive(sessionId, sessionTokenHash, clock.instant()).isEmpty()) {
            return null;
        }

        return taskDataPort.findById(taskId)
                .filter(task -> task.getStatus() == WorkerTaskStatus.COMPLETED)
                .filter(task -> claimedSha256.equalsIgnoreCase(task.getArtifactId()))
                .flatMap(task -> artifactStore.findVerified(task.getArtifactId()))
                .orElse(null);
    }
}

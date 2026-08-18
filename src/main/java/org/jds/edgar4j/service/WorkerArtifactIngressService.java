package org.jds.edgar4j.service;

import org.jds.edgar4j.port.ArtifactStorePort.VerifiedArtifact;
import org.springframework.core.io.buffer.DataBuffer;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface WorkerArtifactIngressService {

    Mono<VerifiedArtifact> accept(
            String sessionId,
            String sessionToken,
            String taskId,
            String leaseToken,
            String claimedSha256,
            String contentType,
            Flux<DataBuffer> body);
}

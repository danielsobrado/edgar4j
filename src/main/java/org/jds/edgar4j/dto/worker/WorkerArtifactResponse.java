package org.jds.edgar4j.dto.worker;

import java.time.Instant;

import org.jds.edgar4j.port.ArtifactStorePort.VerifiedArtifact;

public record WorkerArtifactResponse(
        String artifactId,
        String sha256,
        long sizeBytes,
        String contentType,
        Instant verifiedAt) {

    public static WorkerArtifactResponse from(VerifiedArtifact artifact) {
        return new WorkerArtifactResponse(
                artifact.artifactId(),
                artifact.sha256(),
                artifact.sizeBytes(),
                artifact.contentType(),
                artifact.verifiedAt());
    }
}

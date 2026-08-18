package org.jds.edgar4j.port;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;

public interface ArtifactStorePort {

    StagedArtifact stage(String taskId, InputStream input, long maxBytes) throws IOException;

    InputStream openStaged(String stagingId) throws IOException;

    InputStream openVerified(String artifactId) throws IOException;

    VerifiedArtifact promote(
            String stagingId,
            String sha256,
            long sizeBytes,
            String contentType,
            Instant verifiedAt) throws IOException;

    Optional<VerifiedArtifact> findVerified(String artifactId);

    void deleteStaged(String stagingId) throws IOException;

    int deleteStagedOlderThan(Instant cutoff) throws IOException;

    record StagedArtifact(String stagingId, long sizeBytes) {
    }

    record VerifiedArtifact(
            String artifactId,
            String sha256,
            long sizeBytes,
            String contentType,
            Instant verifiedAt) {
    }
}

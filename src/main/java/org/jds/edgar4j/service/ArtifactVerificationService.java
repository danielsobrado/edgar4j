package org.jds.edgar4j.service;

import java.time.Instant;

import org.jds.edgar4j.model.WorkerTask;
import org.jds.edgar4j.port.ArtifactStorePort.StagedArtifact;
import org.jds.edgar4j.port.ArtifactStorePort.VerifiedArtifact;

public interface ArtifactVerificationService {

    VerifiedArtifact verifyAndPromote(
            WorkerTask task,
            StagedArtifact stagedArtifact,
            String claimedSha256,
            String contentType,
            Instant verifiedAt);
}

package org.jds.edgar4j.service.impl;

import java.time.Instant;

import org.jds.edgar4j.model.WorkerTask;
import org.jds.edgar4j.port.ArtifactStorePort.StagedArtifact;
import org.jds.edgar4j.port.ArtifactStorePort.VerifiedArtifact;
import org.jds.edgar4j.service.ArtifactVerificationService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Primary
@Service
public class TaskBoundArtifactVerificationService implements ArtifactVerificationService {

    private final ArtifactVerificationServiceImpl delegate;

    public TaskBoundArtifactVerificationService(ArtifactVerificationServiceImpl delegate) {
        this.delegate = delegate;
    }

    @Override
    public VerifiedArtifact verifyAndPromote(
            WorkerTask task,
            StagedArtifact stagedArtifact,
            String claimedSha256,
            String suppliedContentType,
            Instant verifiedAt) {
        String taskContentType = task != null ? normalize(task.getContentType()) : null;
        String effectiveContentType = taskContentType != null ? taskContentType : normalize(suppliedContentType);
        return delegate.verifyAndPromote(
                task,
                stagedArtifact,
                claimedSha256,
                effectiveContentType,
                verifiedAt);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }
}

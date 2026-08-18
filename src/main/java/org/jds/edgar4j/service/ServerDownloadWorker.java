package org.jds.edgar4j.service;

import java.util.Optional;

import org.jds.edgar4j.port.ArtifactStorePort.VerifiedArtifact;

public interface ServerDownloadWorker {

    Optional<VerifiedArtifact> execute(String taskId);

    int drain(int maxTasks);
}

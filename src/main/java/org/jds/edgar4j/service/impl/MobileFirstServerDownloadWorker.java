package org.jds.edgar4j.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.jds.edgar4j.model.WorkerTask;
import org.jds.edgar4j.model.WorkerTaskStatus;
import org.jds.edgar4j.port.ArtifactStorePort;
import org.jds.edgar4j.port.ArtifactStorePort.VerifiedArtifact;
import org.jds.edgar4j.port.WorkerTaskDataPort;
import org.jds.edgar4j.properties.WorkerMobileAssistProperties;
import org.jds.edgar4j.service.ServerDownloadWorker;
import org.jds.edgar4j.service.WorkerPresenceRegistry;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Primary
@Service
public class MobileFirstServerDownloadWorker implements ServerDownloadWorker {

    private final ServerDownloadWorkerImpl delegate;
    private final WorkerPresenceRegistry presenceRegistry;
    private final WorkerTaskDataPort taskDataPort;
    private final ArtifactStorePort artifactStore;
    private final WorkerMobileAssistProperties properties;
    private final Clock clock;

    public MobileFirstServerDownloadWorker(
            ServerDownloadWorkerImpl delegate,
            WorkerPresenceRegistry presenceRegistry,
            WorkerTaskDataPort taskDataPort,
            ArtifactStorePort artifactStore,
            WorkerMobileAssistProperties properties,
            Clock clock) {
        this.delegate = delegate;
        this.presenceRegistry = presenceRegistry;
        this.taskDataPort = taskDataPort;
        this.artifactStore = artifactStore;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public Optional<VerifiedArtifact> execute(String taskId) {
        if (!properties.isEnabled() || !hasRecentMobileWorker()) {
            return delegate.execute(taskId);
        }

        Instant initialDeadline = clock.instant().plus(properties.getInitialClaimWindow());
        while (clock.instant().isBefore(initialDeadline)) {
            Optional<VerifiedArtifact> completed = resolveCompleted(taskId);
            if (completed.isPresent()) {
                return completed;
            }

            WorkerTask task = taskDataPort.findById(taskId).orElse(null);
            if (task == null || task.getStatus() == WorkerTaskStatus.FAILED || task.getStatus() == WorkerTaskStatus.CANCELLED) {
                return Optional.empty();
            }
            if (task.getStatus() == WorkerTaskStatus.LEASED || task.getStatus() == WorkerTaskStatus.VERIFYING) {
                return waitForRemoteCompletion(taskId);
            }
            if (!sleep(properties.getPollInterval())) {
                return Optional.empty();
            }
        }

        return resolveCompleted(taskId).or(() -> delegate.execute(taskId));
    }

    @Override
    public int drain(int maxTasks) {
        if (properties.isEnabled() && hasRecentMobileWorker()) {
            return 0;
        }
        return delegate.drain(maxTasks);
    }

    private Optional<VerifiedArtifact> waitForRemoteCompletion(String taskId) {
        Instant deadline = clock.instant().plus(properties.getLeasedCompletionWindow());
        while (clock.instant().isBefore(deadline)) {
            Optional<VerifiedArtifact> completed = resolveCompleted(taskId);
            if (completed.isPresent()) {
                return completed;
            }

            WorkerTask task = taskDataPort.findById(taskId).orElse(null);
            if (task == null || task.getStatus() == WorkerTaskStatus.FAILED || task.getStatus() == WorkerTaskStatus.CANCELLED) {
                return Optional.empty();
            }
            if (task.getStatus() == WorkerTaskStatus.PENDING) {
                return delegate.execute(taskId);
            }
            if (!sleep(properties.getPollInterval())) {
                return Optional.empty();
            }
        }
        log.warn("Mobile worker did not complete task {} within the assist window", taskId);
        return resolveCompleted(taskId);
    }

    private Optional<VerifiedArtifact> resolveCompleted(String taskId) {
        WorkerTask task = taskDataPort.findById(taskId).orElse(null);
        if (task == null || task.getStatus() != WorkerTaskStatus.COMPLETED || task.getArtifactId() == null) {
            return Optional.empty();
        }
        return artifactStore.findVerified(task.getArtifactId());
    }

    private boolean hasRecentMobileWorker() {
        return presenceRegistry.hasRecentWorker(clock.instant(), properties.getRecentPresenceWindow());
    }

    private static boolean sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}

package org.jds.edgar4j.job;

import static org.jds.edgar4j.constants.WorkerStorageConstants.ARTIFACT_ROOT_DIRECTORY;
import static org.jds.edgar4j.constants.WorkerStorageConstants.INGRESS_DIRECTORY;
import static org.jds.edgar4j.constants.WorkerStorageConstants.STAGING_SUFFIX;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;

import org.jds.edgar4j.properties.DistributedWorkerProperties;
import org.jds.edgar4j.storage.file.FileStorageProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class WorkerIngressMaintenanceJob {

    private final DistributedWorkerProperties properties;
    private final Clock clock;
    private final Path ingressDirectory;

    public WorkerIngressMaintenanceJob(
            DistributedWorkerProperties properties,
            FileStorageProperties storageProperties,
            Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.ingressDirectory = storageProperties.resolveBaseDirectory()
                .resolve(ARTIFACT_ROOT_DIRECTORY)
                .resolve(INGRESS_DIRECTORY)
                .normalize();
    }

    @Scheduled(fixedDelayString = "#{@distributedWorkerProperties.coordinator.maintenanceInterval.toMillis()}")
    public void cleanup() {
        if (!properties.isEnabled() || !Files.isDirectory(ingressDirectory)) {
            return;
        }

        Instant cutoff = clock.instant().minus(properties.getArtifact().getStagingRetention());
        int deleted = 0;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(ingressDirectory, "*" + STAGING_SUFFIX)) {
            for (Path entry : entries) {
                if (Files.isRegularFile(entry)
                        && !Files.getLastModifiedTime(entry).toInstant().isAfter(cutoff)
                        && Files.deleteIfExists(entry)) {
                    deleted++;
                }
            }
        } catch (IOException e) {
            log.warn("Failed to clean stale worker ingress files", e);
            return;
        }

        if (deleted > 0) {
            log.info("Deleted {} stale worker ingress files", deleted);
        }
    }
}

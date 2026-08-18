package org.jds.edgar4j.service.impl;

import static org.jds.edgar4j.constants.WorkerProtocolConstants.MAX_RESOURCE_ID_LENGTH;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;

import org.jds.edgar4j.model.WorkerCapability;
import org.jds.edgar4j.model.WorkerTask;
import org.jds.edgar4j.model.WorkerTaskStatus;
import org.jds.edgar4j.model.WorkerTaskType;
import org.jds.edgar4j.port.WorkerTaskDataPort;
import org.jds.edgar4j.properties.DistributedWorkerProperties;
import org.jds.edgar4j.service.DistributedWorkPlanner;
import org.jds.edgar4j.service.WorkerParentJobService;
import org.jds.edgar4j.service.WorkerSourceResourcePolicy;
import org.springframework.stereotype.Service;

@Service
public class DistributedWorkPlannerImpl implements DistributedWorkPlanner {

    private final WorkerTaskDataPort taskDataPort;
    private final WorkerSourceResourcePolicy sourceResourcePolicy;
    private final WorkerParentJobService parentJobService;
    private final DistributedWorkerProperties properties;
    private final Clock clock;

    public DistributedWorkPlannerImpl(
            WorkerTaskDataPort taskDataPort,
            WorkerSourceResourcePolicy sourceResourcePolicy,
            WorkerParentJobService parentJobService,
            DistributedWorkerProperties properties,
            Clock clock) {
        this.taskDataPort = taskDataPort;
        this.sourceResourcePolicy = sourceResourcePolicy;
        this.parentJobService = parentJobService;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public WorkerTask planDownload(DownloadTaskSpec specification) {
        Objects.requireNonNull(specification, "specification");
        validate(specification);

        Instant now = clock.instant();
        WorkerTask task = WorkerTask.builder()
                .parentDownloadJobId(normalize(specification.parentDownloadJobId()))
                .logicalKey(logicalKey(specification))
                .resourceId(specification.resourceId().trim())
                .type(WorkerTaskType.DOWNLOAD)
                .source(specification.source())
                .sourceUrl(specification.sourceUrl().trim())
                .status(WorkerTaskStatus.PENDING)
                .priority(specification.priority())
                .expectedSha256(normalize(specification.expectedSha256()))
                .expectedSizeBytes(specification.expectedSizeBytes())
                .contentType(normalize(specification.contentType()))
                .maxBytes(specification.maxBytes())
                .requiredCapabilities(EnumSet.of(WorkerCapability.DOWNLOAD, WorkerCapability.SHA256))
                .attemptCount(0)
                .maxAttempts(properties.getCoordinator().getMaxAttempts())
                .createdAt(now)
                .updatedAt(now)
                .build();
        sourceResourcePolicy.validate(task);
        WorkerTask saved = taskDataPort.createIfAbsent(task);
        parentJobService.refreshProgress(saved.getParentDownloadJobId());
        return saved;
    }

    @Override
    public int cancelParent(String parentDownloadJobId) {
        if (parentDownloadJobId == null || parentDownloadJobId.isBlank()) {
            return 0;
        }
        return taskDataPort.cancelByParentDownloadJobId(parentDownloadJobId.trim(), clock.instant());
    }

    private static void validate(DownloadTaskSpec specification) {
        if (specification.resourceId() == null || specification.resourceId().isBlank()) {
            throw new IllegalArgumentException("Worker resourceId is required");
        }
        if (specification.resourceId().length() > MAX_RESOURCE_ID_LENGTH) {
            throw new IllegalArgumentException("Worker resourceId is too long");
        }
        Objects.requireNonNull(specification.source(), "Worker source is required");
        if (specification.sourceUrl() == null || specification.sourceUrl().isBlank()) {
            throw new IllegalArgumentException("Worker sourceUrl is required");
        }
        if (specification.maxBytes() <= 0) {
            throw new IllegalArgumentException("Worker maxBytes must be positive");
        }
        if (specification.expectedSizeBytes() != null
                && (specification.expectedSizeBytes() < 0
                        || specification.expectedSizeBytes() > specification.maxBytes())) {
            throw new IllegalArgumentException("Expected resource size exceeds task limit");
        }
    }

    private static String logicalKey(DownloadTaskSpec specification) {
        return WorkerTaskType.DOWNLOAD.name()
                + "|"
                + specification.source().name()
                + "|"
                + specification.resourceId().trim();
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }
}

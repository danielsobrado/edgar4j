package org.jds.edgar4j.dto.worker;

import java.util.Set;

import org.jds.edgar4j.constants.WorkerProtocolConstants;
import org.jds.edgar4j.model.WorkerCapability;
import org.jds.edgar4j.model.WorkerPlatform;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WorkerSessionRequest(
        @Min(1) int protocolVersion,
        @NotNull WorkerPlatform platform,
        @Size(min = 1, max = WorkerProtocolConstants.MAX_CLIENT_VERSION_LENGTH) String clientVersion,
        @NotEmpty Set<WorkerCapability> capabilities,
        @Min(1) @Max(WorkerProtocolConstants.MAX_TASKS_PER_LEASE_REQUEST) int maxConcurrentTasks) {
}

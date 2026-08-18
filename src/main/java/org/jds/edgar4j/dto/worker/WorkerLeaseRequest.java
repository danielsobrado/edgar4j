package org.jds.edgar4j.dto.worker;

import java.util.Set;

import org.jds.edgar4j.constants.WorkerProtocolConstants;
import org.jds.edgar4j.model.WorkerCapability;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record WorkerLeaseRequest(
        @Min(1) int protocolVersion,
        @NotEmpty Set<WorkerCapability> capabilities,
        @Min(1) @Max(WorkerProtocolConstants.MAX_TASKS_PER_LEASE_REQUEST) int maxTasks,
        @Valid @NotNull WorkerRuntimeState runtime) {
}

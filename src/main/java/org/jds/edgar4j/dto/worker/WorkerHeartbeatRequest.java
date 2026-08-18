package org.jds.edgar4j.dto.worker;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record WorkerHeartbeatRequest(
        @NotBlank String leaseToken,
        @Valid WorkerRuntimeState runtime) {
}

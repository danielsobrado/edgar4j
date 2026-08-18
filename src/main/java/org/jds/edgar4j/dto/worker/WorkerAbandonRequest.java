package org.jds.edgar4j.dto.worker;

import jakarta.validation.constraints.NotBlank;

public record WorkerAbandonRequest(@NotBlank String leaseToken) {
}

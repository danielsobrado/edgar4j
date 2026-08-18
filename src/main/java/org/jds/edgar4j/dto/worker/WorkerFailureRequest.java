package org.jds.edgar4j.dto.worker;

import org.jds.edgar4j.constants.WorkerProtocolConstants;
import org.jds.edgar4j.model.WorkerFailureCode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WorkerFailureRequest(
        @NotBlank String leaseToken,
        @NotNull WorkerFailureCode code,
        @Size(max = WorkerProtocolConstants.MAX_DIAGNOSTIC_MESSAGE_LENGTH) String message) {
}

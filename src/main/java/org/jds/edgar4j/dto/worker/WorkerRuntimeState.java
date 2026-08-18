package org.jds.edgar4j.dto.worker;

import org.jds.edgar4j.model.WorkerNetworkType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record WorkerRuntimeState(
        @NotNull WorkerNetworkType networkType,
        boolean metered,
        boolean charging,
        @Min(0) @Max(100) Integer batteryPercent,
        @PositiveOrZero long freeStorageBytes) {
}

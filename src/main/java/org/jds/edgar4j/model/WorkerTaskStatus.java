package org.jds.edgar4j.model;

import java.util.EnumSet;
import java.util.Set;

public enum WorkerTaskStatus {
    PENDING,
    LEASED,
    VERIFYING,
    COMPLETED,
    FAILED,
    CANCELLED;

    private static final Set<WorkerTaskStatus> TERMINAL = EnumSet.of(COMPLETED, FAILED, CANCELLED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean canTransitionTo(WorkerTaskStatus target) {
        if (target == null || target == this || isTerminal()) {
            return false;
        }

        return switch (this) {
            case PENDING -> target == LEASED || target == FAILED || target == CANCELLED;
            case LEASED -> target == PENDING || target == VERIFYING || target == FAILED || target == CANCELLED;
            case VERIFYING -> target == PENDING || target == COMPLETED || target == FAILED || target == CANCELLED;
            case COMPLETED, FAILED, CANCELLED -> false;
        };
    }
}

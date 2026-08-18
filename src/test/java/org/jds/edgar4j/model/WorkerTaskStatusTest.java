package org.jds.edgar4j.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WorkerTaskStatusTest {

    @Test
    void pendingCanOnlyEnterActiveOrTerminalFailureStates() {
        assertTrue(WorkerTaskStatus.PENDING.canTransitionTo(WorkerTaskStatus.LEASED));
        assertTrue(WorkerTaskStatus.PENDING.canTransitionTo(WorkerTaskStatus.FAILED));
        assertTrue(WorkerTaskStatus.PENDING.canTransitionTo(WorkerTaskStatus.CANCELLED));
        assertFalse(WorkerTaskStatus.PENDING.canTransitionTo(WorkerTaskStatus.COMPLETED));
        assertFalse(WorkerTaskStatus.PENDING.canTransitionTo(WorkerTaskStatus.VERIFYING));
    }

    @Test
    void leasedTaskMustVerifyBeforeCompletion() {
        assertTrue(WorkerTaskStatus.LEASED.canTransitionTo(WorkerTaskStatus.VERIFYING));
        assertTrue(WorkerTaskStatus.LEASED.canTransitionTo(WorkerTaskStatus.PENDING));
        assertFalse(WorkerTaskStatus.LEASED.canTransitionTo(WorkerTaskStatus.COMPLETED));
    }

    @Test
    void verifyingTaskCanCompleteOrRetry() {
        assertTrue(WorkerTaskStatus.VERIFYING.canTransitionTo(WorkerTaskStatus.COMPLETED));
        assertTrue(WorkerTaskStatus.VERIFYING.canTransitionTo(WorkerTaskStatus.PENDING));
    }

    @Test
    void terminalStatesCannotTransition() {
        for (WorkerTaskStatus status : new WorkerTaskStatus[] {
                WorkerTaskStatus.COMPLETED,
                WorkerTaskStatus.FAILED,
                WorkerTaskStatus.CANCELLED }) {
            assertTrue(status.isTerminal());
            for (WorkerTaskStatus target : WorkerTaskStatus.values()) {
                assertFalse(status.canTransitionTo(target));
            }
        }
    }
}

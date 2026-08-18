package org.jds.edgar4j.exception;

import org.jds.edgar4j.model.WorkerFailureCode;

public class WorkerArtifactException extends Edgar4jException {

    private final WorkerFailureCode failureCode;

    public WorkerArtifactException(String message, WorkerFailureCode failureCode) {
        super(message, "WORKER_" + failureCode.name());
        this.failureCode = failureCode;
    }

    public WorkerArtifactException(String message, WorkerFailureCode failureCode, Throwable cause) {
        super(message, "WORKER_" + failureCode.name(), cause);
        this.failureCode = failureCode;
    }

    public WorkerFailureCode getFailureCode() {
        return failureCode;
    }
}

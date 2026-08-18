package org.jds.edgar4j.exception;

public class WorkerCoordinatorException extends Edgar4jException {

    public WorkerCoordinatorException(String message, String errorCode) {
        super(message, errorCode);
    }

    public WorkerCoordinatorException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, cause);
    }
}

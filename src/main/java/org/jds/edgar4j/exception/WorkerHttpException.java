package org.jds.edgar4j.exception;

import org.springframework.http.HttpStatus;

public class WorkerHttpException extends Edgar4jException {

    private final HttpStatus status;

    public WorkerHttpException(String message, String errorCode, HttpStatus status) {
        super(message, errorCode);
        this.status = status;
    }

    public WorkerHttpException(String message, String errorCode, HttpStatus status, Throwable cause) {
        super(message, errorCode, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

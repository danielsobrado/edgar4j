package org.jds.edgar4j.exception;

public class Edgar4jException extends RuntimeException {

    private final String errorCode;

    public Edgar4jException(String message) {
        super(message);
        this.errorCode = "EDGAR4J_ERROR";
    }

    public Edgar4jException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public Edgar4jException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "EDGAR4J_ERROR";
    }

    public Edgar4jException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

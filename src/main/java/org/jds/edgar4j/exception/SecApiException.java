package org.jds.edgar4j.exception;

public class SecApiException extends RuntimeException {

    public SecApiException(String message) {
        super(message);
    }

    public SecApiException(String message, Throwable cause) {
        super(message, cause);
    }
}

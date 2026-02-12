package org.jds.edgar4j.exception;

/**
 * Exception thrown when Form 4 XML parsing fails
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
public class Form4ParsingException extends RuntimeException {

    public Form4ParsingException(String message) {
        super(message);
    }

    public Form4ParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
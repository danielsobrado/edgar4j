package org.jds.edgar4j.exception;

public class Form4DocumentProcessingException extends Edgar4jException {

    public Form4DocumentProcessingException(String accessionNumber, Throwable cause) {
        super("Failed to process Form 4 document " + accessionNumber, "FORM4_DOCUMENT_PROCESSING_FAILED", cause);
    }
}
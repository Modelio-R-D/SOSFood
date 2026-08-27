package org.eclipse.foodity.elasticsearch.service;

public class FileProcessingException extends RuntimeException {

    public FileProcessingException() {
        super();
    }

    public FileProcessingException(String message) {
        super(message);
    }

    public FileProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}


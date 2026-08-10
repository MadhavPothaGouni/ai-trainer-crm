package com.aitrainercrm.platform.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Root of the platform's own exception hierarchy. Every subclass carries a
 * stable {@code errorCode} (for client-side branching) and the HTTP status
 * it should map to, so GlobalExceptionHandler doesn't need a giant
 * instanceof chain to figure out what to return.
 */
public class BusinessException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus status;

    public BusinessException(String errorCode, String message, HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

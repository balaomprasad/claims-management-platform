package com.claimsphere.common.exception;

/**
 * Thrown when a request conflicts with the current state of a resource (mapped to HTTP 409).
 */
public class ConflictException extends RuntimeException {

    private final String errorCode;

    public ConflictException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

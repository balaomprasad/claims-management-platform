package com.claimsphere.common.exception;

/**
 * Thrown when a request is well-formed but violates a business rule (mapped to HTTP 422).
 */
public class BusinessRuleException extends RuntimeException {

    private final String errorCode;

    public BusinessRuleException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

package com.party.exception;

public class ResourceInUseException extends RuntimeException {
    private final String errorCode;

    public ResourceInUseException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

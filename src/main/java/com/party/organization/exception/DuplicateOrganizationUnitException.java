package com.party.organization.exception;

public class DuplicateOrganizationUnitException extends RuntimeException {
    public DuplicateOrganizationUnitException(String message) {
        super(message);
    }
}

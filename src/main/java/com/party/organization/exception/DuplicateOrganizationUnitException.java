package com.party.organization.exception;

import com.party.exception.DuplicateResourceException;

public class DuplicateOrganizationUnitException extends DuplicateResourceException {
    public DuplicateOrganizationUnitException(String message) {

        super("DUPLICATE_ORGANIZATION_UNIT",message);
    }
}

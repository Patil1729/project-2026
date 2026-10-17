package com.party.organization.exception;

import com.party.exception.ResourceInUseException;

public class OrganizationUnitInUseException extends ResourceInUseException {
    public OrganizationUnitInUseException(String message) {
        super("ORGANIZATION_UNIT_IN_USE", message);
    }
}

package com.party.organization.exception;


import com.party.exception.ResourceNotFoundException;

public class OrganizationUnitNotFoundException extends ResourceNotFoundException {


    public OrganizationUnitNotFoundException(String message) {
        super("ORGANIZATION_UNIT_NOT_FOUND",message);
    }
}

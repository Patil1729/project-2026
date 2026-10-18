package com.party.electoral.exception;

import com.party.exception.ResourceNotFoundException;

public class AssemblyConstituencyNotFoundException extends ResourceNotFoundException {

    public AssemblyConstituencyNotFoundException(String message) {
        super("ASSEMBLY_CONSTITUENCY_NOT_FOUND", message);
    }
}

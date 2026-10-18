package com.party.electoral.exception;

import com.party.exception.DuplicateResourceException;

public class DuplicateAssemblyConstituencyException extends DuplicateResourceException {

    public DuplicateAssemblyConstituencyException(String message) {
        super("DUPLICATE_ASSEMBLY_CONSTITUENCY", message);
    }
}

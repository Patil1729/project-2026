package com.party.electoral.exception;

import com.party.exception.ResourceInUseException;

public class AssemblyConstituencyInUseException extends ResourceInUseException {
    public AssemblyConstituencyInUseException(String message) {
        super("ASSEMBLY_CONSTITUENCY_INACTIVE",message);
    }
}

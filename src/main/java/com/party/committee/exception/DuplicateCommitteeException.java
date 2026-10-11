package com.party.committee.exception;

import com.party.exception.DuplicateResourceException;

public class DuplicateCommitteeException extends DuplicateResourceException {
    public DuplicateCommitteeException(String message){
        super("DUPLICATE_COMMITTEE",message);
    }
}

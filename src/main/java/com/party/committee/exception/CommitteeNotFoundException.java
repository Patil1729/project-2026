package com.party.committee.exception;

import com.party.exception.ResourceNotFoundException;

public class CommitteeNotFoundException extends ResourceNotFoundException {
    public CommitteeNotFoundException(String message) {
        super("COMMITTEE_NOT_FOUND",message);
    }
}

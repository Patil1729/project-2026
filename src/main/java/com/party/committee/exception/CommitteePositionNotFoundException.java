package com.party.committee.exception;

import com.party.exception.ResourceNotFoundException;

public class CommitteePositionNotFoundException extends ResourceNotFoundException {
    public CommitteePositionNotFoundException(String message) {
        super("COMMITTEE_POSITION_NOT_FOUND",message);
    }
}

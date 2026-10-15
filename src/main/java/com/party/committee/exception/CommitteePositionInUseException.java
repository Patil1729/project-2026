package com.party.committee.exception;

import com.party.exception.ResourceInUseException;

public class CommitteePositionInUseException extends ResourceInUseException {
    public CommitteePositionInUseException(String message) {
        super("COMMITTEE_POSITION_IN_USE",message);
    }
}

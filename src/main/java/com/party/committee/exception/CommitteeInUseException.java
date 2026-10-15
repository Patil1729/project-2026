package com.party.committee.exception;

import com.party.exception.ResourceInUseException;

public class CommitteeInUseException extends ResourceInUseException {
    public CommitteeInUseException(String message) {
        super("COMMITTEE_IN_USE",message);
    }
}

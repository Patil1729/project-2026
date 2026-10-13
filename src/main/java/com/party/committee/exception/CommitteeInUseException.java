package com.party.committee.exception;

public class CommitteeInUseException extends RuntimeException {
    public CommitteeInUseException(String message) {
        super(message);
    }
}

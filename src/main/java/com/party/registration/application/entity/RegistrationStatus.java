package com.party.registration.application.entity;

public enum RegistrationStatus {
    AWAITING_RECOMMENDATION,
    UNDER_REVIEW,
    APPROVED,
    REJECTED;

    public boolean isTerminal() {
        return this == APPROVED || this == REJECTED;
    }
}

package com.party.registration.approval.entity;

public enum ApprovalStatus {
    WAITING,
    PENDING,
    OVERDUE,
    APPROVED,
    REJECTED;

    public boolean canBeDecided() {
        return this == PENDING || this == OVERDUE;
    }

    public boolean isTerminal() {
        return this == APPROVED || this == REJECTED;
    }
}

package com.party.registration.escalation.dto;

public record ApprovalEscalationOutcome(
        boolean overdueMarked,
        boolean escalated,
        String missingReason,
        boolean missingRecorded,
        boolean skipped) {
    public static ApprovalEscalationOutcome skip() {
        return new ApprovalEscalationOutcome(false, false, null, false, true);
    }
}

package com.party.registration.escalation.dto;

public record ApprovalEscalationBatchResult(
        int scanned,
        int overdueMarked,
        int escalated,
        int unresolved,
        int skipped,
        int failed,
        long lastSeenStepId) {
}

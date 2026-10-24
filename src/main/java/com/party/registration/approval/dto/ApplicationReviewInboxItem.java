package com.party.registration.approval.dto;

import com.party.registration.application.dto.RegistrationApplicationResponse;

public record ApplicationReviewInboxItem(
        RegistrationApplicationResponse application,
        ApprovalStepResponse step,
        boolean overdue) {
}

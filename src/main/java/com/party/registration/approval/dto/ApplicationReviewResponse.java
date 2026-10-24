package com.party.registration.approval.dto;

import com.party.registration.application.dto.RegistrationApplicationResponse;

import java.util.List;

public record ApplicationReviewResponse(
        RegistrationApplicationResponse application,
        Long assignmentId,
        List<ApprovalStepResponse> approvalSteps) {
}

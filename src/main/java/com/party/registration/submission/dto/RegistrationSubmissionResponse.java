package com.party.registration.submission.dto;

import com.party.registration.application.entity.RegistrationStatus;
import com.party.registration.approval.dto.ApprovalStepResponse;
import com.party.registration.document.entity.DocumentType;

import java.time.Instant;
import java.util.List;

public record RegistrationSubmissionResponse(
        Long applicationId,
        String applicationNumber,
        RegistrationStatus status,
        Instant submittedAt,
        List<DocumentType> requiredDocumentTypes,
        List<ApprovalStepResponse> approvalSteps) {
}

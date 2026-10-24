package com.party.registration.approval.service;

import com.party.registration.approval.dto.ApplicationApprovalRequest;
import com.party.registration.approval.dto.ApplicationRejectionRequest;
import com.party.registration.approval.dto.ApplicationReviewInboxResponse;
import com.party.registration.approval.dto.ApplicationReviewResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public interface ApplicationReviewService {
    ApplicationReviewInboxResponse getPending(
            @NotBlank String username, @Min(0) int page, @Min(1) @Max(100) int size);

    ApplicationReviewInboxResponse getEscalated(
            @NotBlank String username, @Min(0) int page, @Min(1) @Max(100) int size);

    ApplicationReviewResponse getReview(@NotBlank String username, @NotNull @Positive Long applicationId);

    ApplicationReviewResponse approve(
            @NotBlank String username, @NotNull @Positive Long applicationId,
            @NotNull @Positive Long stepId, @NotNull @Valid ApplicationApprovalRequest request);

    ApplicationReviewResponse reject(
            @NotBlank String username, @NotNull @Positive Long applicationId,
            @NotNull @Positive Long stepId, @NotNull @Valid ApplicationRejectionRequest request);
}

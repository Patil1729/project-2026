package com.party.registration.submission.service;

import com.party.registration.approval.dto.ApprovalStepResponse;
import com.party.registration.submission.dto.RegistrationSubmissionResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public interface RegistrationSubmissionService {
    RegistrationSubmissionResponse submit(
            @NotBlank String username, @NotNull @Positive Long applicationId);

    List<ApprovalStepResponse> getApprovalSteps(
            @NotBlank String username, @NotNull @Positive Long applicationId);
}

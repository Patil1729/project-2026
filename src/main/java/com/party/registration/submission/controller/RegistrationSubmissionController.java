package com.party.registration.submission.controller;

import com.party.registration.approval.dto.ApprovalStepResponse;
import com.party.registration.submission.dto.RegistrationSubmissionResponse;
import com.party.registration.common.exception.RegistrationException;
import com.party.registration.submission.service.RegistrationSubmissionService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/registration-applications")
public class RegistrationSubmissionController {
    private final RegistrationSubmissionService submissionService;
    private final Environment environment;

    @PostMapping("/{applicationId}/submit")
    public ResponseEntity<RegistrationSubmissionResponse> submit(
            Principal principal, @PathVariable("applicationId") @Positive Long applicationId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(submissionService.submit(username(principal), applicationId));
    }

    @GetMapping("/{applicationId}/approval-steps")
    public ResponseEntity<List<ApprovalStepResponse>> getApprovalSteps(
            Principal principal, @PathVariable("applicationId") @Positive Long applicationId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(submissionService.getApprovalSteps(username(principal), applicationId));
    }

    private String username(Principal principal) {
        if (principal == null && environment.acceptsProfiles(Profiles.of("local"))) {
            String username = environment.getProperty("app.registration.local-test-username");
            if (username != null && !username.isBlank()) {
                return username.strip();
            }
        }
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new RegistrationException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                    "An applicant identity is required to submit or track this application.");
        }
        return principal.getName();
    }
}

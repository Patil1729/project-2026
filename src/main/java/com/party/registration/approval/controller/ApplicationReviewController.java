package com.party.registration.approval.controller;

import com.party.registration.approval.dto.ApplicationApprovalRequest;
import com.party.registration.approval.dto.ApplicationRejectionRequest;
import com.party.registration.approval.dto.ApplicationReviewInboxResponse;
import com.party.registration.approval.dto.ApplicationReviewResponse;
import com.party.registration.common.exception.RegistrationException;
import com.party.registration.approval.service.ApplicationReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/application-reviews")
public class ApplicationReviewController {
    private final ApplicationReviewService reviewService;
    private final Environment environment;

    @GetMapping("/pending")
    public ResponseEntity<ApplicationReviewInboxResponse> getPending(
            Principal principal,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(reviewService.getPending(username(principal), page, size));
    }

    @GetMapping("/escalated")
    public ResponseEntity<ApplicationReviewInboxResponse> getEscalated(
            Principal principal,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(reviewService.getEscalated(username(principal), page, size));
    }

    @GetMapping("/applications/{applicationId}")
    public ResponseEntity<ApplicationReviewResponse> getReview(
            Principal principal, @PathVariable("applicationId") @Positive Long applicationId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(reviewService.getReview(username(principal), applicationId));
    }

    @PatchMapping(value = "/applications/{applicationId}/steps/{stepId}/approve", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApplicationReviewResponse> approve(
            Principal principal,
            @PathVariable("applicationId") @Positive Long applicationId,
            @PathVariable("stepId") @Positive Long stepId,
            @RequestBody @Valid ApplicationApprovalRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(reviewService.approve(username(principal), applicationId, stepId, request));
    }

    @PatchMapping(value = "/applications/{applicationId}/steps/{stepId}/reject", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApplicationReviewResponse> reject(
            Principal principal,
            @PathVariable("applicationId") @Positive Long applicationId,
            @PathVariable("stepId") @Positive Long stepId,
            @RequestBody @Valid ApplicationRejectionRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(reviewService.reject(username(principal), applicationId, stepId, request));
    }

    private String username(Principal principal) {
        if (principal == null && environment.acceptsProfiles(Profiles.of("local"))) {
            String username = environment.getProperty("app.registration.local-reviewer-username");
            if (username != null && !username.isBlank()) {
                return username.strip();
            }
        }
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new RegistrationException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                    "A reviewer identity is required to review this application.");
        }
        return principal.getName();
    }
}

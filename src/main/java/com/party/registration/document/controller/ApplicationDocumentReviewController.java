package com.party.registration.document.controller;

import com.party.registration.document.dto.ApplicationDocumentDownload;
import com.party.registration.document.dto.ApplicationDocumentRejectionRequest;
import com.party.registration.document.dto.ApplicationDocumentResponse;
import com.party.registration.document.dto.ApplicationDocumentVerificationRequest;
import com.party.registration.common.exception.RegistrationException;
import com.party.registration.document.service.ApplicationDocumentReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/document-reviews/applications/{applicationId}/documents")
public class ApplicationDocumentReviewController {
    private final ApplicationDocumentReviewService reviewService;
    private final Environment environment;

    @GetMapping
    public ResponseEntity<List<ApplicationDocumentResponse>> list(
            Principal principal, @PathVariable("applicationId") @Positive Long applicationId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(reviewService.list(username(principal), applicationId));
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<ApplicationDocumentResponse> get(
            Principal principal,
            @PathVariable("applicationId") @Positive Long applicationId,
            @PathVariable("documentId") @Positive Long documentId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(reviewService.get(username(principal), applicationId, documentId));
    }

    @GetMapping("/{documentId}/download")
    public ResponseEntity<Resource> download(
            Principal principal,
            @PathVariable("applicationId") @Positive Long applicationId,
            @PathVariable("documentId") @Positive Long documentId) {
        ApplicationDocumentDownload download = reviewService.download(username(principal), applicationId, documentId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.originalFilename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(new InputStreamResource(download.inputStream()));
    }

    @PatchMapping(value = "/{documentId}/verify", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApplicationDocumentResponse> verify(
            Principal principal,
            @PathVariable("applicationId") @Positive Long applicationId,
            @PathVariable("documentId") @Positive Long documentId,
            @Valid @RequestBody ApplicationDocumentVerificationRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(reviewService.verify(username(principal), applicationId, documentId, request));
    }

    @PatchMapping(value = "/{documentId}/reject", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApplicationDocumentResponse> reject(
            Principal principal,
            @PathVariable("applicationId") @Positive Long applicationId,
            @PathVariable("documentId") @Positive Long documentId,
            @Valid @RequestBody ApplicationDocumentRejectionRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(reviewService.reject(username(principal), applicationId, documentId, request));
    }

    private String username(Principal principal) {
        // Development identity only; appointment scope and self-review checks still run.
        if (principal == null && environment.acceptsProfiles(Profiles.of("local"))) {
            String localUsername = environment.getProperty("app.registration.local-reviewer-username");
            if (localUsername != null && !localUsername.isBlank()) {
                return localUsername.strip();
            }
        }
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new RegistrationException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                    "A reviewer identity is required to review documents.");
        }
        return principal.getName();
    }
}

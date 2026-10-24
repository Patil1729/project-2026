package com.party.registration.document.controller;

import com.party.registration.document.dto.ApplicationDocumentDownload;
import com.party.registration.document.dto.ApplicationDocumentResponse;
import com.party.registration.document.dto.ApplicationDocumentUploadRequest;
import com.party.registration.common.exception.RegistrationException;
import com.party.registration.document.service.ApplicationDocumentService;
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
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/registration-applications/{applicationId}/documents")
public class ApplicationDocumentController {
    private final ApplicationDocumentService documentService;
    private final Environment environment;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApplicationDocumentResponse> upload(
            Principal principal,
            @PathVariable("applicationId") @Positive Long applicationId,
            @Valid @ModelAttribute ApplicationDocumentUploadRequest request) {
        ApplicationDocumentResponse response = documentService.upload(username(principal), applicationId, request);
        return ResponseEntity.created(URI.create("/api/registration-applications/" + applicationId
                        + "/documents/" + response.id()))
                .cacheControl(CacheControl.noStore()).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ApplicationDocumentResponse>> list(
            Principal principal, @PathVariable("applicationId") @Positive Long applicationId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(documentService.list(username(principal), applicationId));
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<ApplicationDocumentResponse> get(
            Principal principal,
            @PathVariable("applicationId") @Positive Long applicationId,
            @PathVariable("documentId") @Positive Long documentId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(documentService.get(username(principal), applicationId, documentId));
    }

    @GetMapping("/{documentId}/download")
    public ResponseEntity<Resource> download(
            Principal principal,
            @PathVariable("applicationId") @Positive Long applicationId,
            @PathVariable("documentId") @Positive Long documentId) {
        ApplicationDocumentDownload download = documentService.download(
                username(principal), applicationId, documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.originalFilename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(new InputStreamResource(download.inputStream()));
    }

    private String username(Principal principal) {
        if (principal == null && environment.acceptsProfiles(Profiles.of("local"))) {
            String localUsername = environment.getProperty("app.registration.local-test-username");
            if (localUsername != null && !localUsername.isBlank()) {
                return localUsername.strip();
            }
        }
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new RegistrationException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                    "Sign in before accessing registration documents.");
        }
        return principal.getName();
    }
}

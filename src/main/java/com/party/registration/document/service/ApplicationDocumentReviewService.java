package com.party.registration.document.service;

import com.party.registration.document.dto.ApplicationDocumentDownload;
import com.party.registration.document.dto.ApplicationDocumentRejectionRequest;
import com.party.registration.document.dto.ApplicationDocumentResponse;
import com.party.registration.document.dto.ApplicationDocumentVerificationRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public interface ApplicationDocumentReviewService {
    List<ApplicationDocumentResponse> list(
            @NotBlank String username, @NotNull @Positive Long applicationId);

    ApplicationDocumentResponse get(
            @NotBlank String username, @NotNull @Positive Long applicationId,
            @NotNull @Positive Long documentId);

    ApplicationDocumentDownload download(
            @NotBlank String username, @NotNull @Positive Long applicationId,
            @NotNull @Positive Long documentId);

    ApplicationDocumentResponse verify(
            @NotBlank String username, @NotNull @Positive Long applicationId,
            @NotNull @Positive Long documentId,
            @NotNull @Valid ApplicationDocumentVerificationRequest request);

    ApplicationDocumentResponse reject(
            @NotBlank String username, @NotNull @Positive Long applicationId,
            @NotNull @Positive Long documentId,
            @NotNull @Valid ApplicationDocumentRejectionRequest request);
}

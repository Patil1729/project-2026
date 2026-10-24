package com.party.registration.document.service;

import com.party.registration.document.dto.ApplicationDocumentDownload;
import com.party.registration.document.dto.ApplicationDocumentResponse;
import com.party.registration.document.dto.ApplicationDocumentUploadRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public interface ApplicationDocumentService {
    ApplicationDocumentResponse upload(
            @NotBlank String username, @NotNull @Positive Long applicationId,
            @NotNull @Valid ApplicationDocumentUploadRequest request);

    List<ApplicationDocumentResponse> list(
            @NotBlank String username, @NotNull @Positive Long applicationId);

    ApplicationDocumentResponse get(
            @NotBlank String username, @NotNull @Positive Long applicationId,
            @NotNull @Positive Long documentId);

    ApplicationDocumentDownload download(
            @NotBlank String username, @NotNull @Positive Long applicationId,
            @NotNull @Positive Long documentId);
}

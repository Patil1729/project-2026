package com.party.registration.document.dto;

import com.party.registration.document.entity.DocumentStatus;
import com.party.registration.document.entity.DocumentType;

import java.time.Instant;

/** API metadata only; private storage keys and server paths are intentionally absent. */
public record ApplicationDocumentResponse(
        Long id,
        Long applicationId,
        DocumentType documentType,
        Integer documentVersion,
        String originalFilename,
        String contentType,
        Long sizeBytes,
        String sha256,
        DocumentStatus status,
        Long uploadedById,
        Instant uploadedAt,
        Long reviewedById,
        Instant reviewedAt,
        String reviewComment) {
}

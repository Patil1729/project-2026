package com.party.registration.document.dto;

import java.io.InputStream;

/** Internal service-to-controller payload, never returned as JSON. */
public record ApplicationDocumentDownload(
        String originalFilename,
        String contentType,
        long sizeBytes,
        InputStream inputStream) {
}

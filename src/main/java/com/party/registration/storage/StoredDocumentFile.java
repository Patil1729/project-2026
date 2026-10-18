package com.party.registration.storage;

public record StoredDocumentFile(
        String originalFilename,
        String storageKey,
        String contentType,
        long sizeBytes,
        String sha256) {
}

package com.party.registration.storage;

import java.io.IOException;
import java.io.InputStream;

/** Private object storage; callers own the input stream passed to store(). */
public interface DocumentStorage {
    StoredDocumentFile store(long applicationId, String originalFilename, InputStream input) throws IOException;

    /** The caller must close this stream, normally through Spring's resource response. */
    InputStream open(String storageKey) throws IOException;

    void delete(String storageKey) throws IOException;

    long maxFileBytes();
}

package com.party.registration.storage;

import java.io.IOException;

public class DocumentFileException extends IOException {
    public enum Reason {
        EMPTY_FILE, FILE_TOO_LARGE, UNSUPPORTED_FILE_TYPE, INVALID_FILENAME
    }

    private final Reason reason;

    public DocumentFileException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}

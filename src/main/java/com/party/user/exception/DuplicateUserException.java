package com.party.user.exception;

import com.party.exception.DuplicateResourceException;

public class DuplicateUserException extends DuplicateResourceException {

    public DuplicateUserException(String message) {
        super("DUPLICATE_USER", message);
    }
}

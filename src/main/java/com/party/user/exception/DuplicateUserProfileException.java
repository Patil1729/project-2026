package com.party.user.exception;

import com.party.exception.DuplicateResourceException;

public class DuplicateUserProfileException extends DuplicateResourceException {

    public DuplicateUserProfileException(String message) {
        super("DUPLICATE_USER_PROFILE", message);
    }
}

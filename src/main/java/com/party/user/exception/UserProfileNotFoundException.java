package com.party.user.exception;

import com.party.exception.ResourceNotFoundException;

public class UserProfileNotFoundException extends ResourceNotFoundException {

    public UserProfileNotFoundException(String message) {
        super("USER_PROFILE_NOT_FOUND", message);
    }
}

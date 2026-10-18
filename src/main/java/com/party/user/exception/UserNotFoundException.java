package com.party.user.exception;

import com.party.exception.ResourceNotFoundException;

public class UserNotFoundException extends ResourceNotFoundException {

    public UserNotFoundException(String message) {
        super("USER_NOT_FOUND", message);
    }
}

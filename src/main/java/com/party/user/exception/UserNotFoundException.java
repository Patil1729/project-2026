package com.party.user.exception;

import com.party.exception.ResourceNotFoundException;

public class UserNotFoundException extends ResourceNotFoundException {

    public UserNotFoundException(Long id) {
        super("USER_NOT_FOUND", "User with id " + id + " not found");
    }
}

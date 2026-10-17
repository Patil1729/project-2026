package com.party.user.exception;

import com.party.exception.ResourceInUseException;

public class UserInUseException extends ResourceInUseException {

    public UserInUseException(String message) {
        super("USER_IN_USE", message);
    }
}

package com.party.committee.appointment.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AppointmentException extends RuntimeException {
    private final HttpStatus status;
    private final String errorCode;

    public AppointmentException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }
}

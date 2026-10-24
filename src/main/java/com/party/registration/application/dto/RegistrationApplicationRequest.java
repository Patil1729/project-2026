package com.party.registration.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegistrationApplicationRequest {

    @NotNull
    @Positive
    private Long selectedShakhaId;

    @NotNull
    @Positive
    private Long requestedPositionId;
}

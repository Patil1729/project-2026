package com.party.committee.appointment.dto;

import jakarta.validation.constraints.*;

public record BootstrapPresidentRequest(@NotNull @Positive Long committeeId,
        @NotNull @Positive Long advocateUserId, @NotBlank @Size(max = 2000) String reason) { }

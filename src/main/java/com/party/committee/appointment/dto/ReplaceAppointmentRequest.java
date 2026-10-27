package com.party.committee.appointment.dto;

import jakarta.validation.constraints.*;

public record ReplaceAppointmentRequest(@NotNull @Positive Long advocateUserId,
        @NotBlank @Size(max = 2000) String reason) { }

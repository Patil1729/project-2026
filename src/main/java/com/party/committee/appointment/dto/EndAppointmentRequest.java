package com.party.committee.appointment.dto;

import jakarta.validation.constraints.*;

public record EndAppointmentRequest(@NotBlank @Size(max = 2000) String reason) { }

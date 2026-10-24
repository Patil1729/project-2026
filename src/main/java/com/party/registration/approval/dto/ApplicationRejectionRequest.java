package com.party.registration.approval.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApplicationRejectionRequest(@NotBlank @Size(max = 2000) String reason) {
}

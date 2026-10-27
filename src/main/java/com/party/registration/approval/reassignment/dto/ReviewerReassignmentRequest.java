package com.party.registration.approval.reassignment.dto;

import jakarta.validation.constraints.*;

public record ReviewerReassignmentRequest(@NotBlank @Size(max = 1500) String reason) { }

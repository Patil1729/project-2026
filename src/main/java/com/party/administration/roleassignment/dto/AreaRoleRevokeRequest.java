package com.party.administration.roleassignment.dto;

import jakarta.validation.constraints.*;

public record AreaRoleRevokeRequest(@NotBlank @Size(max = 2000) String reason) { }

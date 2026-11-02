package com.party.administration.roleassignment.dto;

import com.party.user.entity.RoleType;
import jakarta.validation.constraints.*;

public record AreaRoleGrantRequest(@NotNull @Positive Long userId, @NotNull RoleType role,
        @NotNull @Positive Long organizationUnitId, @NotBlank @Size(max = 2000) String reason) { }

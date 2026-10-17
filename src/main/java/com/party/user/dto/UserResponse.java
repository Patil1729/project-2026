package com.party.user.dto;


import com.party.user.entity.RoleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Builder
@AllArgsConstructor
public class UserResponse {

    private final Long id;
    private final String username;
    private final String fullName;
    private final String mobile;
    private final String email;
    private final boolean enabled;
    private final boolean passwordChangeRequired;
    private final Long organizationUnitId;
    private final String organizationUnitName;
    private final Set<RoleType> roles;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}

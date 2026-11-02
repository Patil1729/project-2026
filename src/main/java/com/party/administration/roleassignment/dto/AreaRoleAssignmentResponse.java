package com.party.administration.roleassignment.dto;

import com.party.organization.entity.OrganizationType;
import com.party.user.entity.RoleType;
import java.time.Instant;

public record AreaRoleAssignmentResponse(Long id, Long userId, String username, String fullName,
        RoleType role, Long organizationUnitId, String organizationUnitName, OrganizationType organizationType,
        boolean active, boolean effective, Instant assignedAt, String assignedBy, String reason,
        Instant revokedAt, String revokedBy, String revocationReason) { }

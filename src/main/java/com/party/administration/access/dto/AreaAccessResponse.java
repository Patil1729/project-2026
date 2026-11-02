package com.party.administration.access.dto;

import com.party.organization.entity.OrganizationType;
import com.party.user.entity.RoleType;
import java.util.List;

public record AreaAccessResponse(Long userId, boolean globalAdministrator, boolean areaAdministrator,
        boolean canManageAreas, boolean canManageUserAccounts, boolean canManageAppointments,
        boolean canAssignAreaRoles, List<Long> allowedOrganizationUnitIds, List<Scope> scopes) {
    public record Scope(Long assignmentId, RoleType role, Long organizationUnitId, String organizationUnitName, OrganizationType organizationType) { }
}

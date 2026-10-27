package com.party.registration.admin.common.dto;

import java.util.List;

/** An absent unit means global scope; a present unit always includes itself. */
public record OrganizationScope(Long organizationUnitId, boolean includeDescendants, List<Long> unitIds) {
    public OrganizationScope { unitIds = List.copyOf(unitIds); }
    public boolean isGlobal() { return organizationUnitId == null; }
}

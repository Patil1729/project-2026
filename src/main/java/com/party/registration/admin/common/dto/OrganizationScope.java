package com.party.registration.admin.common.dto;
import java.util.List;
/** global is an explicit authorization decision, not inferred from a missing filter. */
public record OrganizationScope(Long organizationUnitId, boolean includeDescendants, List<Long> unitIds, boolean global) {
    public OrganizationScope { unitIds = List.copyOf(unitIds); }
    public OrganizationScope(Long id, boolean descendants, List<Long> ids) { this(id, descendants, ids, id == null); }
    public boolean isGlobal() { return global; }
}

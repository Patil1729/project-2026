package com.party.administration.access.policy;

import com.party.administration.access.dto.AreaUnitNode;
import com.party.organization.entity.OrganizationType;
import com.party.user.entity.RoleType;
import java.util.*;

/** Pure policy: role flags and home addresses never create administration scopes. */
public final class AreaScopePolicy {
    private AreaScopePolicy() { }
    public static final Map<RoleType, OrganizationType> ROLE_TYPES = Map.ofEntries(
            Map.entry(RoleType.STATE_ADMIN, OrganizationType.STATE),
            Map.entry(RoleType.DIVISION_ADMIN, OrganizationType.DIVISION),
            Map.entry(RoleType.DISTRICT_ADMIN, OrganizationType.DISTRICT),
            Map.entry(RoleType.DIVISIONAL_DISTRICT_ADMIN, OrganizationType.DIVISIONAL_DISTRICT),
            Map.entry(RoleType.CONSTITUENCY_ADMIN, OrganizationType.ASSEMBLY_CONSTITUENCY),
            Map.entry(RoleType.TALUKA_ADMIN, OrganizationType.TALUKA),
            Map.entry(RoleType.MUNICIPAL_CORPORATION_ADMIN, OrganizationType.MUNICIPAL_CORPORATION),
            Map.entry(RoleType.MUNICIPAL_COUNCIL_ADMIN, OrganizationType.MUNICIPAL_COUNCIL),
            Map.entry(RoleType.NAGAR_PANCHAYAT_ADMIN, OrganizationType.NAGAR_PANCHAYAT));

    public record Grant(RoleType role, Long unitId) { }
    public static boolean validRoot(Grant grant, List<AreaUnitNode> nodes) {
        if (grant == null || grant.role() == null || grant.unitId() == null) return false;
        Map<Long, AreaUnitNode> byId = index(nodes);
        var unit = byId.get(grant.unitId());
        if (unit == null || ROLE_TYPES.get(grant.role()) != unit.type()) return false;
        Set<Long> seen = new HashSet<>();
        while (unit != null) {
            if (!Boolean.TRUE.equals(unit.active()) || unit.type() == null || !seen.add(unit.id())) return false;
            var parent = unit.parentId() == null ? null : byId.get(unit.parentId());
            if (unit.parentId() != null && parent == null) return false;
            if (!unit.type().canHaveParent(parent == null ? null : parent.type())) return false;
            unit = parent;
        }
        return true;
    }

    /** Include inactive descendants for history and reactivation; the grant root must remain active. */
    public static List<Long> allowedIds(List<Grant> grants, List<AreaUnitNode> nodes) {
        Set<Long> allowed = new TreeSet<>();
        for (Grant grant : grants) if (validRoot(grant, nodes)) allowed.addAll(descendants(grant.unitId(), nodes));
        return List.copyOf(allowed);
    }

    public static List<Long> descendants(Long root, List<AreaUnitNode> nodes) {
        Map<Long, AreaUnitNode> byId = index(nodes);
        if (root == null || !byId.containsKey(root)) return List.of();
        Map<Long, List<Long>> children = new HashMap<>();
        for (var node : nodes) if (node.parentId() != null) children.computeIfAbsent(node.parentId(), key -> new ArrayList<>()).add(node.id());
        Set<Long> found = new LinkedHashSet<>(); Deque<Long> queue = new ArrayDeque<>(); queue.add(root);
        while (!queue.isEmpty()) {
            Long id = queue.removeFirst();
            if (!found.add(id)) throw new IllegalArgumentException("A cycle exists in the organization hierarchy.");
            for (Long childId : children.getOrDefault(id, List.of())) {
                var parent = byId.get(id); var child = byId.get(childId);
                if (child.type() == null || !child.type().canHaveParent(parent.type()))
                    throw new IllegalArgumentException("An invalid parent-child link exists in the organization hierarchy.");
                queue.addLast(childId);
            }
        }
        return List.copyOf(found);
    }
    private static Map<Long, AreaUnitNode> index(List<AreaUnitNode> nodes) {
        Map<Long, AreaUnitNode> byId = new HashMap<>();
        for (var node : nodes) if (node.id() != null) byId.put(node.id(), node);
        return byId;
    }
}

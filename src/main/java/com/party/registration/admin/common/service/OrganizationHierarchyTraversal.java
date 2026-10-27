package com.party.registration.admin.common.service;

import com.party.registration.admin.common.dto.OrganizationLink;
import java.util.*;

/** Pure traversal shared by scoped queries; malformed cycles fail instead of hanging. */
public final class OrganizationHierarchyTraversal {
    private OrganizationHierarchyTraversal() { }

    public static List<Long> descendants(Long root, List<OrganizationLink> links) {
        Map<Long, List<Long>> children = new HashMap<>();
        for (OrganizationLink link : links) {
            if (link.parentId() != null) children.computeIfAbsent(link.parentId(), key -> new ArrayList<>()).add(link.id());
        }
        children.values().forEach(Collections::sort);
        Set<Long> visited = new LinkedHashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Long current = queue.removeFirst();
            if (!visited.add(current)) throw new IllegalArgumentException("A cycle exists in the organization hierarchy.");
            queue.addAll(children.getOrDefault(current, List.of()));
        }
        return List.copyOf(visited);
    }
}

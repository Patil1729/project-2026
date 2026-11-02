package com.party.portal.service;

import com.party.committee.entity.AdvocateAssignment;
import com.party.committee.entity.CommitteeType;
import com.party.organization.entity.OrganizationUnit;
import com.party.portal.dto.PublicOrganizationUnit;
import com.party.portal.dto.StateExecutiveSeat;
import com.party.portal.repository.PortalCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PortalCatalogService {
    private final PortalCatalogRepository repository;

    public List<PublicOrganizationUnit> organizations() {
        return repository.findActiveOrganizations().stream().filter(this::activeHierarchy)
                .map(unit -> new PublicOrganizationUnit(unit.getId(), unit.getName(), unit.getType(),
                        unit.getParent() == null ? null : unit.getParent().getId())).toList();
    }

    public List<StateExecutiveSeat> statePositions() {
        var assignments = repository.findCurrentStateAssignments(CommitteeType.STATE_COMMITTEE,
                LocalDate.now(ZoneId.of("Asia/Kolkata"))).stream()
                .collect(Collectors.groupingBy(a -> a.getCommitteePosition().getId()));
        return repository.findStatePositions(CommitteeType.STATE_COMMITTEE).stream()
                .filter(p -> activeHierarchy(p.getCommittee().getOrganizationUnit()))
                .filter(p -> p.isTemplateValid() && p.isResponsibilityValid())
                .map(position -> {
                    List<AdvocateAssignment> current = assignments.getOrDefault(position.getId(), List.of());
                    var assignment = current.size() == 1 ? current.get(0) : null;
                    boolean visible = assignment != null && assignment.getAdvocate().isEnabled();
                    String status = current.isEmpty() ? "VACANT" : visible ? "FILLED" : "UNAVAILABLE";
                    var division = position.getResponsibilityUnit();
                    return new StateExecutiveSeat(position.getId(), position.getCommittee().getName(),
                            position.getPositionType(), position.getPositionNumber(),
                            division == null ? null : division.getName(),
                            visible ? assignment.getId() : null,
                            visible ? assignment.getAdvocate().getFullName() : null, status);
                }).toList();
    }

    private boolean activeHierarchy(OrganizationUnit unit) {
        var visited = new HashSet<Long>();
        while (unit != null) {
            if (unit.getId() == null || !Boolean.TRUE.equals(unit.getActive()) || !visited.add(unit.getId()) || unit.getType() == null)
                return false;
            var parent = unit.getParent();
            if (!unit.getType().canHaveParent(parent == null ? null : parent.getType())) return false;
            unit = parent;
        }
        return true;
    }
}

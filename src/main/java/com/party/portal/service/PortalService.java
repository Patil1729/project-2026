package com.party.portal.service;

import com.party.committee.entity.CommitteeType;
import com.party.organization.entity.OrganizationUnit;
import com.party.portal.dto.PortalSummary;
import com.party.portal.dto.StateExecutiveMember;
import com.party.portal.repository.PortalRepository;
import com.party.user.entity.RoleType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class PortalService {
    private final PortalRepository repository;
    private final Long grievancesResolved;
    private final Long activeLegalClinics;

    public PortalService(PortalRepository repository,
            @Value("${app.portal.grievances-resolved:}") String grievancesResolved,
            @Value("${app.portal.active-legal-clinics:}") String activeLegalClinics) {
        this.repository = repository;
        this.grievancesResolved = verifiedCount(grievancesResolved, "app.portal.grievances-resolved");
        this.activeLegalClinics = verifiedCount(activeLegalClinics, "app.portal.active-legal-clinics");
    }

    public PortalSummary summary() {
        return new PortalSummary(repository.countRegisteredAdvocates(RoleType.ADVOCATE), grievancesResolved, activeLegalClinics);
    }

    public List<StateExecutiveMember> stateExecutive() {
        return repository.findStateExecutive(CommitteeType.STATE_COMMITTEE, LocalDate.now(ZoneId.of("Asia/Kolkata")))
                .stream().filter(a -> activeHierarchy(a.getCommitteePosition().getCommittee().getOrganizationUnit()))
                .map(a -> {
                    var position = a.getCommitteePosition();
                    var division = position.getResponsibilityUnit();
                    return new StateExecutiveMember(a.getId(), a.getAdvocate().getFullName(), position.getPositionType(),
                            position.getPositionNumber(), position.getCommittee().getName(), division == null ? null : division.getName());
                }).toList();
    }

    private boolean activeHierarchy(OrganizationUnit unit) {
        var visited = new HashSet<Long>();
        while (unit != null) {
            if (!Boolean.TRUE.equals(unit.getActive()) || !visited.add(unit.getId())) return false;
            unit = unit.getParent();
        }
        return true;
    }

    private Long verifiedCount(String value, String property) {
        if (value == null || value.isBlank()) return null;
        try {
            long count = Long.parseLong(value.strip());
            if (count >= 0 && count <= 9_007_199_254_740_991L) return count;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException(property + " must be blank or a non-negative safe integer");
    }
}

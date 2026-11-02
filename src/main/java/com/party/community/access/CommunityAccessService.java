package com.party.community.access;

import com.party.administration.access.service.AreaAccessService;
import com.party.committee.entity.*;
import com.party.organization.entity.*;
import com.party.organization.repository.OrganizationUnitRepository;
import com.party.registration.common.exception.RegistrationException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

/** Permissions come from current appointments and current area grants, never a title supplied by a client. */
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class CommunityAccessService {
    private final AreaAccessService areas;
    private final CommunityAppointmentRepository appointments;
    private final OrganizationUnitRepository organizations;
    private final Clock clock;

    public record Scope(Long actorId, boolean global, Set<Long> administrationUnits,
                        List<AdvocateAssignment> currentAppointments, List<OrganizationUnit> units) {
        public List<AdvocateAssignment> ownAppointments() {
            return currentAppointments.stream().filter(a -> Objects.equals(a.getAdvocate().getId(), actorId)).toList();
        }
        public boolean coordinator(OrganizationUnit unit) {
            if (global) return true;
            if (administrationUnits.contains(unit.getId())) return true;
            return ownAppointments().stream().anyMatch(a -> a.getCommitteePosition().getPositionType() == CommitteePositionType.PRESIDENT && covers(a, unit));
        }
        public Set<Long> coordinatorUnits() {
            var result = new HashSet<Long>();
            units.stream().filter(u -> u.getType() != OrganizationType.PARTY && validHierarchy(u) && coordinator(u)).forEach(u -> result.add(u.getId()));
            return result;
        }
        public boolean current(AdvocateAssignment assignment, OrganizationUnit origin) {
            return assignment != null && currentAppointments.stream().anyMatch(a -> Objects.equals(a.getId(), assignment.getId()) && covers(a, origin));
        }
    }
    public Scope current() {
        var area = areas.snapshot();
        var valid = appointments.current(LocalDate.ofInstant(clock.instant(), ZoneId.of("Asia/Kolkata"))).stream()
                .filter(a -> validHierarchy(a.getCommitteePosition().getCommittee().getOrganizationUnit()))
                .filter(a -> a.getCommitteePosition().isTemplateValid() && a.getCommitteePosition().isResponsibilityValid()).toList();
        return new Scope(areas.actorId(), area.global(), new HashSet<>(area.unitIds()), valid, organizations.findAll());
    }
    public record Appointment(Long id, Long organizationUnitId, String areaName, CommitteePositionType position) { }
    public record Capabilities(Long userId, boolean canUseInbox, boolean canManageGallery, Set<Long> galleryUnitIds,
                               List<Appointment> appointments) { }
    public Capabilities capabilities() {
        var scope = current(); var ids = scope.coordinatorUnits();
        return new Capabilities(scope.actorId(), scope.global() || !ids.isEmpty() || !scope.ownAppointments().isEmpty(),
                !ids.isEmpty(), ids, scope.ownAppointments().stream().map(a -> new Appointment(a.getId(),
                a.getCommitteePosition().getCommittee().getOrganizationUnit().getId(), a.getCommitteePosition().getCommittee().getOrganizationUnit().getName(),
                a.getCommitteePosition().getPositionType())).toList());
    }
    public static boolean covers(AdvocateAssignment assignment, OrganizationUnit origin) {
        if (!validHierarchy(origin)) return false;
        var chain = lineage(origin);
        var position = assignment.getCommitteePosition();
        return chain.stream().anyMatch(u -> Objects.equals(u.getId(), position.getCommittee().getOrganizationUnit().getId()))
                && (position.getResponsibilityUnit() == null || chain.stream().anyMatch(u -> Objects.equals(u.getId(), position.getResponsibilityUnit().getId())));
    }
    public static boolean validHierarchy(OrganizationUnit unit) {
        try { lineage(unit); return true; } catch (RegistrationException invalid) { return false; }
    }
    public static List<OrganizationUnit> lineage(OrganizationUnit unit) {
        var chain = new ArrayList<OrganizationUnit>(); var visited = new HashSet<Long>();
        if (unit == null) throw invalidArea();
        while (unit != null) {
            var parent = unit.getParent();
            if (unit.getId() == null || unit.getType() == null || !Boolean.TRUE.equals(unit.getActive()) || !visited.add(unit.getId())
                    || !unit.getType().canHaveParent(parent == null ? null : parent.getType())) throw invalidArea();
            chain.add(unit); unit = parent;
        }
        return chain;
    }
    public static RegistrationException invalidArea() { return new RegistrationException(HttpStatus.BAD_REQUEST, "INVALID_HELP_AREA", "Choose an active area with a valid parent hierarchy."); }
    public static RegistrationException denied() { return new RegistrationException(HttpStatus.FORBIDDEN, "COMMUNITY_ACCESS_DENIED", "Your current appointment or area assignment does not allow this action."); }
}

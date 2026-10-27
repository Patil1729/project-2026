package com.party.committee.appointment.service;

import com.party.common.dto.PageResponse;
import com.party.committee.appointment.audit.entity.*;
import com.party.committee.appointment.audit.repository.AppointmentAuditLogRepository;
import com.party.committee.appointment.dto.*;
import com.party.committee.appointment.exception.AppointmentException;
import com.party.committee.appointment.repository.AppointmentRepository;
import com.party.committee.entity.*;
import com.party.committee.repository.*;
import com.party.organization.entity.OrganizationUnit;
import com.party.organization.repository.OrganizationUnitRepository;
import com.party.registration.approval.policy.ApprovalPolicy;
import com.party.user.entity.*;
import com.party.user.repository.*;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AppointmentServiceImpl implements AppointmentService {
    private final AppointmentRepository appointments;
    private final AppointmentAuditLogRepository audits;
    private final UserRepository users;
    private final RoleRepository roles;
    private final CommitteeRepository committees;
    private final CommitteePositionRepository positions;
    private final OrganizationUnitRepository organizations;
    private final Clock clock;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponse bootstrapStatePresident(BootstrapPresidentRequest request) {
        String reason = reason(request.reason());
        AppUser advocate = lockUser(request.advocateUserId());
        requireEligible(advocate);
        Long positionId = appointments.findPresidentSeatId(request.committeeId(), CommitteePositionType.PRESIDENT)
                .orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "PRESIDENT_SEAT_NOT_FOUND", "Create the committee and its president seat first."));
        CommitteePosition position = lockSeat(positionId, request.committeeId(), true);
        if (position.getCommittee().getCommitteeType() != CommitteeType.STATE_COMMITTEE
                || position.getPositionType() != CommitteePositionType.PRESIDENT || position.getPositionNumber() != 1)
            throw failure(HttpStatus.BAD_REQUEST, "BOOTSTRAP_STATE_PRESIDENT_ONLY", "Direct bootstrap is limited to the State committee PRESIDENT seat.");
        if (positions.countActiveAssignmentsForPositionId(positionId) != 0)
            throw conflict("POSITION_OCCUPIED", "The State president seat already has an active appointment.");
        grantAdvocateMembership(advocate);
        AdvocateAssignment saved = appointments.saveAndFlush(AdvocateAssignment.builder()
                .committeePosition(position).advocate(advocate).appointedDate(today()).build());
        record(saved, null, AppointmentAuditAction.STATE_PRESIDENT_BOOTSTRAPPED, reason);
        return toResponse(saved);
    }

    @Override
    public AppointmentResponse getById(Long id) {
        return toResponse(appointments.findById(id).orElseThrow(this::notFound));
    }

    @Override
    public PageResponse<AppointmentResponse> search(AppointmentSearchRequest request) {
        validatePage(request.getPage(), request.getSize());
        Specification<AdvocateAssignment> filters = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (request.getCommitteeId() != null) predicates.add(cb.equal(root.get("committeePosition").get("committee").get("id"), request.getCommitteeId()));
            if (request.getAdvocateUserId() != null) predicates.add(cb.equal(root.get("advocate").get("id"), request.getAdvocateUserId()));
            if (request.getPositionType() != null) predicates.add(cb.equal(root.get("committeePosition").get("positionType"), request.getPositionType()));
            if (request.getActive() != null) predicates.add(cb.equal(root.get("active"), request.getActive()));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(appointments.findAll(filters, PageRequest.of(request.getPage(), request.getSize(),
                Sort.by(Sort.Direction.DESC, "appointedDate", "id"))).map(this::toResponse));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponse end(Long id, EndAppointmentRequest request) {
        String reason = reason(request.reason());
        AppointmentReference ref = reference(id);
        lockUser(ref.advocateUserId());
        lockSeat(ref.positionId(), ref.committeeId(), false);
        AdvocateAssignment assignment = lockAssignment(id, ref);
        if (!assignment.isActive()) return toResponse(assignment); // Idempotent; retain the original ending date/history.
        requireCurrent(assignment);
        assignment.endOn(today());
        appointments.saveAndFlush(assignment);
        record(assignment, null, AppointmentAuditAction.APPOINTMENT_ENDED, reason);
        return toResponse(assignment);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentReplacementResponse replace(Long id, ReplaceAppointmentRequest request) {
        String reason = reason(request.reason());
        AppointmentReference ref = reference(id);
        if (Objects.equals(ref.advocateUserId(), request.advocateUserId()))
            throw failure(HttpStatus.BAD_REQUEST, "SAME_ADVOCATE", "Choose a different advocate for the replacement.");
        // Stable order also serializes simultaneous replacements involving the same users.
        Map<Long, AppUser> lockedUsers = new HashMap<>();
        for (Long userId : new TreeSet<>(List.of(ref.advocateUserId(), request.advocateUserId())))
            lockedUsers.put(userId, lockUser(userId));
        AppUser replacementUser = lockedUsers.get(request.advocateUserId());
        requireEligible(replacementUser);
        CommitteePosition position = lockSeat(ref.positionId(), ref.committeeId(), true);
        AdvocateAssignment old = lockAssignment(id, ref);
        requireCurrent(old);
        grantAdvocateMembership(replacementUser);
        LocalDate effectiveDate = today();
        old.endOn(effectiveDate);
        // Release the generated MySQL unique active-seat key before inserting its successor.
        appointments.saveAndFlush(old);
        AdvocateAssignment replacement = appointments.saveAndFlush(AdvocateAssignment.builder()
                .advocate(replacementUser).committeePosition(position).appointedDate(effectiveDate).build());
        // The original approved application remains linked to its original, now ended appointment.
        record(old, replacement, AppointmentAuditAction.APPOINTMENT_REPLACED, reason);
        record(replacement, old, AppointmentAuditAction.APPOINTED_AS_REPLACEMENT, reason);
        return new AppointmentReplacementResponse(toResponse(old), toResponse(replacement));
    }

    @Override
    public PageResponse<AppointmentAuditResponse> history(Long id, int page, int size) {
        if (!appointments.existsById(id)) throw notFound();
        validatePage(page, size);
        return PageResponse.from(audits.findByAssignment_IdOrderByEventAtAscIdAsc(id, PageRequest.of(page, size))
                .map(event -> new AppointmentAuditResponse(event.getId(), id,
                        event.getRelatedAssignment() == null ? null : event.getRelatedAssignment().getId(),
                        event.getAction(), event.getEventAt(), event.getReason())));
    }

    private CommitteePosition lockSeat(Long positionId, Long committeeId, boolean requireActive) {
        Committee committee = committees.findByIdForUpdate(committeeId).orElseThrow(() -> conflict("INVALID_COMMITTEE", "Committee not found."));
        lockAncestry(committee.getOrganizationUnit().getId(), requireActive);
        positions.findResponsibilityUnitIdByPositionId(positionId).ifPresent(unitId -> lockAncestry(unitId, requireActive));
        CommitteePosition position = positions.findByIdForUpdate(positionId).orElseThrow(() -> conflict("INVALID_POSITION", "Position not found."));
        if (!Objects.equals(position.getCommittee().getId(), committeeId)) throw conflict("POSITION_CHANGED", "The position changed. Reload and retry.");
        if (requireActive && (!position.isActive() || !Boolean.TRUE.equals(committee.getActive())
                || committee.getCommitteeType() == null || !committee.isOrganizationTypeValid()
                || position.getPositionType() == null || position.getPositionNumber() == null
                || !position.isTemplateValid() || !position.isResponsibilityValid()))
            throw conflict("INVALID_POSITION", "The committee, organization ancestry and position must be active and valid.");
        return position;
    }

    private void lockAncestry(Long unitId, boolean requireActive) {
        Set<Long> visited = new HashSet<>();
        List<OrganizationUnit> ancestry = new ArrayList<>();
        Long next = unitId;
        while (next != null) {
            if (!visited.add(next)) throw conflict("INVALID_ORGANIZATION", "The organization hierarchy contains a cycle.");
            OrganizationUnit unit = organizations.findByIdForUpdate(next)
                    .orElseThrow(() -> conflict("INVALID_ORGANIZATION", "Organization unit not found."));
            ancestry.add(unit);
            next = unit.getParent() == null ? null : unit.getParent().getId();
        }
        if (requireActive) for (int index = 0; index < ancestry.size(); index++) {
            OrganizationUnit unit = ancestry.get(index);
            var parentType = index + 1 < ancestry.size() ? ancestry.get(index + 1).getType() : null;
            if (!Boolean.TRUE.equals(unit.getActive()) || unit.getType() == null || !unit.getType().canHaveParent(parentType))
                throw conflict("INVALID_ORGANIZATION", "The complete organization ancestry must be active and valid.");
        }
    }

    private AppUser lockUser(Long id) {
        return users.findByIdForUpdate(id).orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User " + id + " not found."));
    }

    private void requireEligible(AppUser user) {
        if (!user.isEnabled() || user.getProfile() == null)
            throw conflict("ADVOCATE_NOT_ELIGIBLE", "The advocate must be enabled and have a completed profile.");
    }

    private void requireCurrent(AdvocateAssignment assignment) {
        if (!assignment.isActive() || assignment.getEndDate() != null || assignment.getAppointedDate() == null
                || assignment.getAppointedDate().isAfter(today()))
            throw conflict("APPOINTMENT_NOT_CURRENT", "Only a current active appointment can be ended or replaced.");
    }

    private void grantAdvocateMembership(AppUser advocate) {
        Role role = roles.findByName(RoleType.ADVOCATE).orElseThrow(() -> conflict(
                "ADVOCATE_ROLE_MISSING", "Run the V4 ADVOCATE-role seed migration first."));
        if (advocate.getRoles().stream().noneMatch(existing -> existing.getName() == RoleType.ADVOCATE)) advocate.getRoles().add(role);
    }

    private AppointmentReference reference(Long id) {
        return appointments.findReference(id).orElseThrow(this::notFound);
    }

    private AdvocateAssignment lockAssignment(Long id, AppointmentReference ref) {
        AdvocateAssignment assignment = appointments.findForUpdate(id).orElseThrow(this::notFound);
        if (!Objects.equals(assignment.getAdvocate().getId(), ref.advocateUserId())
                || !Objects.equals(assignment.getCommitteePosition().getId(), ref.positionId()))
            throw conflict("APPOINTMENT_CHANGED", "The appointment changed. Reload and retry.");
        return assignment;
    }

    private void record(AdvocateAssignment assignment, AdvocateAssignment related, AppointmentAuditAction action, String reason) {
        audits.saveAndFlush(AppointmentAuditLog.builder().assignment(assignment).relatedAssignment(related)
                .action(action).eventAt(clock.instant()).reason(reason).build());
    }

    private AppointmentResponse toResponse(AdvocateAssignment assignment) {
        var position = assignment.getCommitteePosition();
        var committee = position.getCommittee();
        var advocate = assignment.getAdvocate();
        return new AppointmentResponse(assignment.getId(), advocate.getId(), advocate.getUsername(), advocate.getFullName(),
                committee.getId(), committee.getName(), committee.getOrganizationUnit().getId(), position.getId(),
                position.getPositionType(), position.getPositionNumber(), assignment.getAppointedDate(), assignment.getEndDate(),
                assignment.isActive(), assignment.getRegistrationApplication() == null ? null : assignment.getRegistrationApplication().getId());
    }

    private String reason(String value) {
        if (value == null || value.isBlank() || value.length() > 2000) throw failure(HttpStatus.BAD_REQUEST,
                "INVALID_APPOINTMENT_REASON", "Provide a nonblank reason of at most 2000 characters.");
        return value.strip();
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw failure(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "Use page >= 0 and size between 1 and 100.");
    }

    private LocalDate today() { return LocalDate.now(clock.withZone(ApprovalPolicy.BUSINESS_ZONE)); }
    private AppointmentException notFound() { return failure(HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND", "Advocate appointment not found."); }
    private AppointmentException conflict(String code, String message) { return failure(HttpStatus.CONFLICT, code, message); }
    private AppointmentException failure(HttpStatus status, String code, String message) { return new AppointmentException(status, code, message); }
}

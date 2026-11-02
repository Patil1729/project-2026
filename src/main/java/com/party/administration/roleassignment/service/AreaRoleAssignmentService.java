package com.party.administration.roleassignment.service;

import com.party.administration.access.policy.AreaScopePolicy;
import com.party.administration.access.repository.AreaAccessRepository;
import com.party.administration.access.service.AreaAccessService;
import com.party.administration.roleassignment.audit.entity.AreaRoleAuditLog;
import com.party.administration.roleassignment.audit.repository.AreaRoleAuditRepository;
import com.party.administration.roleassignment.dto.*;
import com.party.administration.roleassignment.entity.AreaRoleAssignment;
import com.party.administration.roleassignment.repository.AreaRoleAssignmentRepository;
import com.party.common.dto.PageResponse;
import com.party.organization.repository.OrganizationUnitRepository;
import com.party.registration.common.exception.RegistrationException;
import com.party.user.repository.RoleRepository;
import com.party.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.Objects;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class AreaRoleAssignmentService {
    private final AreaRoleAssignmentRepository assignments;
    private final AreaRoleAuditRepository audits;
    private final UserRepository users;
    private final RoleRepository roles;
    private final OrganizationUnitRepository organizations;
    private final AreaAccessRepository hierarchy;
    private final AreaAccessService access;

    public PageResponse<AreaRoleAssignmentResponse> list(Long userId, int page, int size) {
        access.requirePartyHead(); validatePage(page, size);
        return PageResponse.from(assignments.search(userId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))).map(this::response));
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AreaRoleAssignmentResponse grant(AreaRoleGrantRequest request) {
        access.requirePartyHead();
        if (request.role() == null || !AreaScopePolicy.ROLE_TYPES.containsKey(request.role())) throw failure(HttpStatus.BAD_REQUEST, "UNSUPPORTED_AREA_ROLE", "Select one of the nine supported area administrator roles.");
        // Match member-account writes: lock organisation before account. The account lock
        // then serializes grants/revocations and prevents lost role-set updates.
        var unit = organizations.findByIdForUpdate(request.organizationUnitId()).orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "ORGANIZATION_NOT_FOUND", "Organization not found."));
        var target = users.findByIdForUpdate(request.userId()).orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found."));
        if (!target.isEnabled()) throw failure(HttpStatus.CONFLICT, "ACCOUNT_DISABLED", "Enable the target account before assigning administration.");
        if (!AreaScopePolicy.validRoot(new AreaScopePolicy.Grant(request.role(), unit.getId()), hierarchy.hierarchy()))
            throw failure(HttpStatus.BAD_REQUEST, "INVALID_ROLE_SCOPE", "The role must match the unit type and its complete hierarchy must be active and valid.");
        var role = roles.findByName(request.role()).orElseThrow(() -> failure(HttpStatus.CONFLICT, "ROLE_NOT_SEEDED", "Run the V6 role migration before assigning area administration."));
        var assignment = assignments.findByUser_IdAndRole_NameAndOrganizationUnit_Id(target.getId(), request.role(), unit.getId()).orElseGet(AreaRoleAssignment::new);
        if (assignment.isActive()) throw failure(HttpStatus.CONFLICT, "AREA_ROLE_ALREADY_ASSIGNED", "This area role is already active for the user.");
        var actor = users.findById(access.actorId()).orElseThrow(() -> failure(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Administrator account not found."));
        assignment.setUser(target); assignment.setRole(role); assignment.setOrganizationUnit(unit);
        assignment.setActive(true); assignment.setAssignedBy(actor); assignment.setAssignedAt(Instant.now()); assignment.setReason(request.reason().strip());
        assignment.setRevokedBy(null); assignment.setRevokedAt(null); assignment.setRevocationReason(null);
        target.getRoles().add(role);
        assignments.saveAndFlush(assignment);
        audits.saveAndFlush(AreaRoleAuditLog.builder().assignment(assignment).actor(actor).action("GRANTED").eventAt(assignment.getAssignedAt()).reason(assignment.getReason()).build());
        return response(assignment);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AreaRoleAssignmentResponse revoke(Long id, AreaRoleRevokeRequest request) {
        access.requirePartyHead();
        // Lock the account before the assignment, matching grants and serializing role-set changes.
        var targetId = assignments.findUserId(id).orElseThrow(this::notFound);
        var target = users.findByIdForUpdate(targetId).orElseThrow(this::notFound);
        var assignment = assignments.findById(id).orElseThrow(this::notFound);
        if (!assignment.isActive()) return response(assignment);
        var actor = users.findById(access.actorId()).orElseThrow(this::notFound);
        assignment.setActive(false); assignment.setRevokedBy(actor); assignment.setRevokedAt(Instant.now()); assignment.setRevocationReason(request.reason().strip());
        assignments.saveAndFlush(assignment);
        if (!assignments.existsByUser_IdAndRole_NameAndActiveTrue(target.getId(), assignment.getRole().getName()))
            target.getRoles().removeIf(role -> role.getName() == assignment.getRole().getName());
        audits.saveAndFlush(AreaRoleAuditLog.builder().assignment(assignment).actor(actor).action("REVOKED").eventAt(assignment.getRevokedAt()).reason(assignment.getRevocationReason()).build());
        return response(assignment);
    }
    public PageResponse<AreaRoleAuditResponse> history(Long id, int page, int size) {
        access.requirePartyHead(); validatePage(page, size);
        if (!assignments.existsById(id)) throw notFound();
        return PageResponse.from(audits.findByAssignment_IdOrderByEventAtDescIdDesc(id, PageRequest.of(page, size))
                .map(a -> new AreaRoleAuditResponse(a.getId(), a.getAction(), a.getEventAt(), a.getActor().getUsername(), a.getReason())));
    }
    private AreaRoleAssignmentResponse response(AreaRoleAssignment assignment) {
        var user = assignment.getUser(); var unit = assignment.getOrganizationUnit();
        boolean effective = assignment.isActive() && user.isEnabled() && AreaScopePolicy.validRoot(new AreaScopePolicy.Grant(assignment.getRole().getName(), unit.getId()), hierarchy.hierarchy());
        return new AreaRoleAssignmentResponse(assignment.getId(), user.getId(), user.getUsername(), user.getFullName(), assignment.getRole().getName(), unit.getId(), unit.getName(), unit.getType(), assignment.isActive(), effective, assignment.getAssignedAt(), assignment.getAssignedBy().getUsername(), assignment.getReason(), assignment.getRevokedAt(), assignment.getRevokedBy() == null ? null : assignment.getRevokedBy().getUsername(), assignment.getRevocationReason());
    }
    private void validatePage(int page, int size) { if (page < 0 || size < 1 || size > 100) throw failure(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "Use page >= 0 and size between 1 and 100."); }
    private RegistrationException notFound() { return failure(HttpStatus.NOT_FOUND, "AREA_ROLE_NOT_FOUND", "Area role assignment not found."); }
    private RegistrationException failure(HttpStatus status, String code, String message) { return new RegistrationException(status, code, message); }
}

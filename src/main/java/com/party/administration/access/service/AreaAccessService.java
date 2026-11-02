package com.party.administration.access.service;

import com.party.administration.access.dto.*;
import com.party.administration.access.policy.AreaScopePolicy;
import com.party.administration.access.repository.AreaAccessRepository;
import com.party.administration.roleassignment.repository.AreaRoleAssignmentRepository;
import com.party.organization.dto.OrganizationUnitRequest;
import com.party.registration.common.exception.RegistrationException;
import com.party.security.services.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service("areaAccess") @RequiredArgsConstructor @Transactional(readOnly = true)
public class AreaAccessService {
    private final AreaAccessRepository repository;
    private final AreaRoleAssignmentRepository assignments;

    public record Snapshot(Long userId, boolean global, List<Long> unitIds, List<Long> roots,
            List<AreaAccessResponse.Scope> scopes, List<AreaUnitNode> hierarchy) {
        public boolean administrator() { return global || !roots.isEmpty(); }
    }
    public boolean isPartyHead() { return isPartyHead(authentication()); }
    public boolean isPartyHead(Authentication auth) {
        return auth != null && auth.isAuthenticated() && auth.getAuthorities().stream().anyMatch(a -> "PARTY_HEAD".equals(a.getAuthority()));
    }
    public Long actorId() {
        var auth = authentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetailsImpl user && user.isEnabled()) return user.getId();
        throw new RegistrationException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Sign in before accessing administration.");
    }
    public Snapshot snapshot() { return snapshot(authentication()); }
    public Snapshot snapshot(Authentication auth) {
        Long id = auth != null && auth.getPrincipal() instanceof UserDetailsImpl user && user.isEnabled() ? user.getId() : null;
        if (!isPartyHead(auth) && id == null) throw denied();
        var nodes = repository.hierarchy();
        if (isPartyHead(auth)) return new Snapshot(id, true, nodes.stream().map(AreaUnitNode::id).toList(), List.of(), List.of(), nodes);
        var effective = assignments.findByUser_IdAndActiveTrue(id).stream()
                .filter(a -> a.getUser().isEnabled())
                .filter(a -> AreaScopePolicy.validRoot(new AreaScopePolicy.Grant(a.getRole().getName(), a.getOrganizationUnit().getId()), nodes)).toList();
        var grants = effective.stream().map(a -> new AreaScopePolicy.Grant(a.getRole().getName(), a.getOrganizationUnit().getId())).toList();
        try {
            var scopes = effective.stream().map(a -> new AreaAccessResponse.Scope(a.getId(), a.getRole().getName(), a.getOrganizationUnit().getId(), a.getOrganizationUnit().getName(), a.getOrganizationUnit().getType())).toList();
            return new Snapshot(id, false, AreaScopePolicy.allowedIds(grants, nodes), grants.stream().map(AreaScopePolicy.Grant::unitId).distinct().toList(), scopes, nodes);
        } catch (IllegalArgumentException invalidHierarchy) { throw denied(); }
    }
    public AreaAccessResponse currentAccess() {
        var s = snapshot();
        return new AreaAccessResponse(s.userId(), s.global(), !s.scopes().isEmpty(), s.administrator(), s.global(), s.global(), s.global(), s.unitIds(), s.scopes());
    }
    public boolean hasAdminAccess() { return hasAdminAccess(authentication()); }
    public boolean hasAdminAccess(Authentication auth) {
        if (isPartyHead(auth)) return true;
        try { return snapshot(auth).administrator(); } catch (RegistrationException denied) { return false; }
    }
    public Snapshot requireAdministrator() {
        var scope = snapshot(); if (!scope.administrator()) throw denied(); return scope;
    }
    public void requirePartyHead() { if (!isPartyHead()) throw denied(); }
    public boolean canAccessUnit(Long id) {
        try { var s = requireAdministrator(); return s.global() || (id != null && s.unitIds().contains(id)); }
        catch (RegistrationException denied) { return false; }
    }
    public void requireUnit(Long id) { if (!canAccessUnit(id)) throw denied(); }
    public void requireCommittee(Long id) {
        if (isPartyHead()) return;
        requireUnit(repository.committeeUnit(id).orElseThrow(this::denied));
    }
    public void requirePosition(Long id) {
        if (isPartyHead()) return;
        requireUnit(repository.positionUnit(id).orElseThrow(this::denied));
    }
    public void requireCreateOrganization(OrganizationUnitRequest request) {
        if (!isPartyHead()) requireUnit(request.getParentId());
    }
    public void requireUpdateOrganization(Long id, OrganizationUnitRequest request) {
        if (isPartyHead()) return;
        var s = requireAdministrator(); if (!s.unitIds().contains(id)) throw denied();
        var existing = s.hierarchy().stream().filter(n -> Objects.equals(n.id(), id)).findFirst().orElseThrow(this::denied);
        if (s.roots().contains(id)) {
            if (existing.type() != request.getType() || !Objects.equals(existing.parentId(), request.getParentId())) throw denied();
        } else if (request.getParentId() == null || !s.unitIds().contains(request.getParentId())) throw denied();
    }
    public void requireDeactivateOrganization(Long id) {
        var s = requireAdministrator();
        if (!s.global() && (!s.unitIds().contains(id) || s.roots().contains(id))) throw denied();
    }
    public boolean canReadUser(Long userId) {
        if (isPartyHead()) return true;
        var auth = authentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetailsImpl user && Objects.equals(userId, user.getId())) return true;
        return repository.homeUnit(userId).map(this::canAccessUnit).orElse(false);
    }
    public void requireReadUser(Long userId) { if (!canReadUser(userId)) throw denied(); }
    private Authentication authentication() { return SecurityContextHolder.getContext().getAuthentication(); }
    private RegistrationException denied() {
        return new RegistrationException(HttpStatus.FORBIDDEN, "AREA_ACCESS_DENIED", "Your active administration assignments do not allow this action or organization area.");
    }
}

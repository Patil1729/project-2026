package com.party.registration.admin.common.service;

import com.party.registration.admin.common.dto.OrganizationLink;
import com.party.administration.access.service.AreaAccessService;
import com.party.administration.access.policy.AreaScopePolicy;
import com.party.registration.admin.common.dto.OrganizationScope;
import com.party.registration.admin.common.repository.OrganizationHierarchyRepository;
import com.party.registration.common.exception.RegistrationException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminOrganizationScopeService {
    private final OrganizationHierarchyRepository repository;
    private final AreaAccessService access;

    public OrganizationScope resolve(Long unitId, boolean includeDescendants) {
        var authorized = access.requireAdministrator();
        if (!authorized.global()) {
            if (unitId == null) return new OrganizationScope(null, includeDescendants,
                    includeDescendants ? authorized.unitIds() : authorized.roots(), false);
            access.requireUnit(unitId);
            return new OrganizationScope(unitId, includeDescendants,
                    includeDescendants ? AreaScopePolicy.descendants(unitId, authorized.hierarchy()).stream().filter(authorized.unitIds()::contains).toList() : List.of(unitId), false);
        }
        if (unitId == null) return new OrganizationScope(null, includeDescendants, List.of(), true);
        if (unitId <= 0) throw new RegistrationException(HttpStatus.BAD_REQUEST,
                "INVALID_ORGANIZATION_ID", "organizationUnitId must be positive.");
        if (!includeDescendants) {
            if (!repository.existsById(unitId)) throw notFound(unitId);
            return new OrganizationScope(unitId, false, List.of(unitId));
        }
        // One flat query, including inactive units so historical applications remain searchable.
        List<OrganizationLink> links = repository.findHierarchy();
        if (links.stream().noneMatch(link -> link.id().equals(unitId))) throw notFound(unitId);
        try {
            return new OrganizationScope(unitId, true, OrganizationHierarchyTraversal.descendants(unitId, links));
        } catch (IllegalArgumentException exception) {
            throw new RegistrationException(HttpStatus.CONFLICT, "INVALID_ORGANIZATION", exception.getMessage());
        }
    }

    private RegistrationException notFound(Long id) {
        return new RegistrationException(HttpStatus.NOT_FOUND, "ORGANIZATION_NOT_FOUND",
                "Organization unit " + id + " not found.");
    }
}

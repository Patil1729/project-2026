package com.party.organization.service;

import com.party.committee.repository.AdvocateAssignmentRepository;
import com.party.committee.repository.CommitteePositionRepository;
import com.party.committee.repository.CommitteeRepository;
import com.party.organization.dto.OrganizationUnitRequest;
import com.party.organization.dto.OrganizationUnitResponse;
import com.party.organization.entity.OrganizationType;
import com.party.organization.entity.OrganizationUnit;
import com.party.organization.exception.DuplicateOrganizationUnitException;
import com.party.organization.exception.InvalidOrganizationHierarchyException;
import com.party.organization.exception.OrganizationUnitHasChildrenException;
import com.party.organization.exception.OrganizationUnitNotFoundException;
import com.party.organization.exception.OrganizationUnitInUseException;
import com.party.registration.application.entity.RegistrationStatus;
import com.party.registration.application.repository.RegistrationApplicationRepository;
import com.party.organization.repository.OrganizationUnitRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
@RequiredArgsConstructor
public class OrganizationServiceImpl implements OrganizationService {

    private final ModelMapper modelMapper;
    private final OrganizationUnitRepository organizationUnitRepository;
    private final CommitteeRepository committeeRepository;
    private final CommitteePositionRepository committeePositionRepository;
    private final AdvocateAssignmentRepository advocateAssignmentRepository;
    private final RegistrationApplicationRepository registrationApplicationRepository;

    private static final List<RegistrationStatus> PENDING_STATUSES = List.of(
            RegistrationStatus.AWAITING_RECOMMENDATION,
            RegistrationStatus.UNDER_REVIEW);

    @Override
    public OrganizationUnitResponse create(OrganizationUnitRequest request) {

        // 1. duplicate code -> 409
        if (organizationUnitRepository.existsByCode(request.getCode())) {
            throw new DuplicateOrganizationUnitException(
                    "Organization with code " + request.getCode() + " already exists");
        }

        // 2. party must not have parent -> 400
        if (request.getType() == OrganizationType.PARTY && request.getParentId() != null) {
            throw new InvalidOrganizationHierarchyException("Party must not have a parent organization");
        }

        // 3. non-party must have parent -> 400
        if (request.getType() != OrganizationType.PARTY && request.getParentId() == null) {
            throw new InvalidOrganizationHierarchyException(
                    request.getType() + " must have a parent organization unit");
        }

        // 4. parent validation
        OrganizationUnit parent = null;
        if (request.getParentId() != null) {
            parent = organizationUnitRepository.findByIdForUpdate(request.getParentId())
                    .orElseThrow(() -> new OrganizationUnitNotFoundException(
                            "Parent organization with id " + request.getParentId() + " not found"));   // 404

            // check that parent is active or not
            if(!Boolean.TRUE.equals(parent.getActive())) {
                throw new InvalidOrganizationHierarchyException(
                        "Parent organization unit with id " + request.getParentId() + " is not active");
            }

            validateParentChild(parent.getType(), request.getType());                                  // 400
        }

        // 5. create entity
        OrganizationUnit organizationUnit = modelMapper.map(request, OrganizationUnit.class);
        organizationUnit.setParent(parent);

        // 6. save
        OrganizationUnit saved = organizationUnitRepository.saveAndFlush(organizationUnit);

        return mapToResponse(saved);
    }

    private void validateParentChild(
            OrganizationType parentType,
            OrganizationType childType) {

        if (childType == null) {
            throw new InvalidOrganizationHierarchyException(
                    "Organization type is required");
        }

        if (!childType.canHaveParent(parentType)) {
            throw new InvalidOrganizationHierarchyException(
                    "Invalid parent-child relationship. "
                            + childType
                            + " must have "
                            + childType.getRequiredParentType()
                            + " as parent, but found "
                            + parentType);
        }
    }


    @Override
    @Transactional(readOnly = true)
    public OrganizationUnitResponse getById(Long id) {
        OrganizationUnit unit = organizationUnitRepository.findById(id)
                .orElseThrow(() -> new OrganizationUnitNotFoundException(
                        "Organization unit with id " + id + " not found"));

        return mapToResponse(unit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrganizationUnitResponse> getChildren(Long parentId) {

        if (!organizationUnitRepository.existsById(parentId)) {
            throw new OrganizationUnitNotFoundException(
                    "Parent organization unit with id " + parentId + " not found");
        }

        return organizationUnitRepository.findByParentIdAndActiveTrue(parentId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrganizationUnitResponse> getRootUnits() {
        return organizationUnitRepository.findByParentIsNullAndActiveTrue()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrganizationUnitResponse> getAll() {
        return organizationUnitRepository.findAllByActiveTrue()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public OrganizationUnitResponse update(Long id, OrganizationUnitRequest request) {

        OrganizationUnit unit = organizationUnitRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new OrganizationUnitNotFoundException(
                        "Organization unit with id " + id + " not found"));                // 404

        if (organizationUnitRepository.existsByCodeAndIdNot(request.getCode(), id)) {
            throw new DuplicateOrganizationUnitException(
                    "Code already exists: " + request.getCode());                          // 409
        }

        OrganizationType type = request.getType();
        Long parentId = request.getParentId();

        // same structural rules as create -> 400
        if (type == OrganizationType.PARTY && parentId != null) {
            throw new InvalidOrganizationHierarchyException("PARTY must not have a parent");
        }
        if (type != OrganizationType.PARTY && parentId == null) {
            throw new InvalidOrganizationHierarchyException(type + " must have a parent");
        }

        Long previousParentId = unit.getParent() == null ? null : unit.getParent().getId();
        boolean structureChanged = type != unit.getType() || !Objects.equals(parentId, previousParentId);
        if (structureChanged) {
            if (organizationUnitRepository.existsByParentId(id)) {
                throw new OrganizationUnitHasChildrenException(
                        "Cannot change type or parent: active or inactive child units exist");
            }
            // Preserve committee templates and historical responsibility mappings.
            if (committeeRepository.existsByOrganizationUnitId(id)) {
                throw new OrganizationUnitInUseException(
                        "Cannot change type or parent: a committee references this organization. "
                                + "Name and code can still be updated.");
            }
            if (committeePositionRepository.existsByResponsibilityUnit_Id(id)) {
                throw new OrganizationUnitInUseException(
                        "Cannot change type or parent: committee positions reference this responsibility unit");
            }
        }

        OrganizationUnit parent = null;
        if (parentId != null) {
            if (id.equals(parentId)) {
                throw new InvalidOrganizationHierarchyException("Unit cannot be its own parent");   // 400
            }
            parent = organizationUnitRepository.findByIdForUpdate(parentId)
                    .orElseThrow(() -> new OrganizationUnitNotFoundException(
                            "Parent organization with id " + parentId + " not found"));

            // to check the parent is active or not
            if(!Boolean.TRUE.equals(parent.getActive())){
                throw new InvalidOrganizationHierarchyException(
                        "Parent organization unit with id " + parentId + " is not active");
            }

            validateParentChild(parent.getType(), type);   // parent first, child second -> 400
        }

        unit.setName(request.getName());
        unit.setCode(request.getCode());
        unit.setType(type);
        unit.setParent(parent);

        return mapToResponse(organizationUnitRepository.saveAndFlush(unit));
    }

    @Override
    public void delete(Long id) {
        OrganizationUnit unit = findForUpdate(id);
        if (!Boolean.TRUE.equals(unit.getActive())) {
            return;
        }
        if (organizationUnitRepository.existsByParentIdAndActiveTrue(id)) {
            throw new OrganizationUnitHasChildrenException(
                    "Cannot deactivate: active child units exist");
        }
        if (advocateAssignmentRepository.countActiveAssignmentsForOrganizationUnit(id) > 0) {
            throw new OrganizationUnitInUseException(
                    "Cannot deactivate: active advocate appointments depend on this organization. "
                            + "End those appointments first.");
        }
        if (registrationApplicationRepository.countPendingApplicationsForOrganizationUnit(
                id, PENDING_STATUSES) > 0) {
            throw new OrganizationUnitInUseException(
                    "Cannot deactivate: pending registration applications depend on this organization. "
                            + "Resolve those applications first.");
        }
        if (committeeRepository.existsByOrganizationUnit_IdAndActiveTrue(id)) {
            throw new OrganizationUnitInUseException(
                    "Cannot deactivate: an active committee exists. Deactivate the committee first.");
        }
        if (committeePositionRepository.existsByResponsibilityUnit_IdAndActiveTrue(id)) {
            throw new OrganizationUnitInUseException(
                    "Cannot deactivate: active committee positions use this responsibility unit. "
                            + "Deactivate those positions first.");
        }
        unit.setActive(false);
    }

    @Override
    public OrganizationUnitResponse activate(Long id) {
        OrganizationUnit unit = findForUpdate(id);
        OrganizationUnit current = unit;
        Set<Long> visited = new HashSet<>();
        while (current != null) {
            if (!visited.add(current.getId())) {
                throw new InvalidOrganizationHierarchyException("Organization hierarchy contains a cycle");
            }
            OrganizationUnit parent = current.getParent();
            if (parent != null) {
                parent = findForUpdate(parent.getId());
            }
            validateParentChild(parent == null ? null : parent.getType(), current.getType());
            if (parent != null && !Boolean.TRUE.equals(parent.getActive())) {
                throw new InvalidOrganizationHierarchyException(
                        "Cannot activate: parent organization with id " + parent.getId()
                                + " is inactive. Activate it first.");
            }
            current = parent;
        }
        unit.setActive(true);
        // Return the requested unit; current is null after the ancestor loop.
        return mapToResponse(organizationUnitRepository.saveAndFlush(unit));
    }

    private OrganizationUnit findForUpdate(Long id) {
        return organizationUnitRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new OrganizationUnitNotFoundException(
                        "Organization unit with id " + id + " not found"));
    }

    private OrganizationUnitResponse mapToResponse(OrganizationUnit unit) {
        OrganizationUnit parent = unit.getParent();
        return OrganizationUnitResponse.builder()
                .id(unit.getId())
                .name(unit.getName())
                .code(unit.getCode())
                .type(unit.getType())
                .parentId(parent != null ? parent.getId() : null)
                .parentName(parent != null ? parent.getName() : null)
                .active(unit.getActive())
                .createdAt(unit.getCreatedAt())
                .updatedAt(unit.getUpdatedAt())
                .build();
    }
}
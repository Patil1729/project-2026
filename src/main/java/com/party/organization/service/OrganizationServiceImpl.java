package com.party.organization.service;

import com.party.organization.dto.OrganizationUnitRequest;
import com.party.organization.dto.OrganizationUnitResponse;
import com.party.organization.entity.OrganizationType;
import com.party.organization.entity.OrganizationUnit;
import com.party.organization.exception.DuplicateOrganizationUnitException;
import com.party.organization.exception.InvalidOrganizationHierarchyException;
import com.party.organization.exception.OrganizationUnitHasChildrenException;
import com.party.organization.exception.OrganizationUnitNotFoundException;
import com.party.organization.repository.OrganizationUnitRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class OrganizationServiceImpl implements OrganizationService {

//    @Autowired
//    ModelMapper modelMapper;

    private final ModelMapper modelMapper;
    private final OrganizationUnitRepository organizationUnitRepository;

//    public OrganizationServiceImpl(OrganizationUnitRepository organizationUnitRepository) {
//        this.organizationUnitRepository = organizationUnitRepository;
//    }

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
            parent = organizationUnitRepository.findById(request.getParentId())
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
        OrganizationUnit saved = organizationUnitRepository.save(organizationUnit);

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

        OrganizationUnit unit = organizationUnitRepository.findById(id)
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

        // don't let a type change break existing children -> 409
//        if (type != unit.getType() && organizationUnitRepository.existsByParentIdAndActiveTrue(id)) {
//            throw new OrganizationUnitHasChildrenException(
//                    "Cannot change type of a unit that has child units");
//        }

        if (type != unit.getType()
                && organizationUnitRepository.existsByParentId(id)) {

            throw new OrganizationUnitHasChildrenException(
                    "Cannot change type: active or inactive child units exist");
        }

        OrganizationUnit parent = null;
        if (parentId != null) {
            if (id.equals(parentId)) {
                throw new InvalidOrganizationHierarchyException("Unit cannot be its own parent");   // 400
            }
            parent = organizationUnitRepository.findById(parentId)
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

        return mapToResponse(unit);   // dirty checking saves it at commit
    }

    @Override
    public void delete(Long id) {
        OrganizationUnit unit = organizationUnitRepository.findById(id)
                .orElseThrow(() -> new OrganizationUnitNotFoundException(
                        "Organization unit with id " + id + " not found"));                // 404

        if (organizationUnitRepository.existsByParentIdAndActiveTrue(id)) {
            throw new OrganizationUnitHasChildrenException(
                    "Cannot delete: active child units exist");                            // 409
        }
        unit.setActive(false);   // soft delete
    }



    @Transactional
    @Override
    public OrganizationUnitResponse activate(Long id) {
        OrganizationUnit unit = organizationUnitRepository.findById(id)
                .orElseThrow(() -> new OrganizationUnitNotFoundException(
                        "Organization unit with id " + id + " not found"));

        OrganizationUnit current = unit;

        while (current != null) {
            OrganizationUnit parent = current.getParent();

            OrganizationType parentType =
                    parent == null ? null : parent.getType();

            // 1. Validate parent presence and parent-child types.
            validateParentChild(parentType, current.getType());

            // 2. Validate that the parent is active.
            if (parent != null && !Boolean.TRUE.equals(parent.getActive())) {
                throw new InvalidOrganizationHierarchyException(
                        "Cannot activate: parent organization with id "
                                + parent.getId()
                                + " is inactive. Activate it first.");
            }

            // Continue checking the higher ancestors.
            current = parent;
        }

        // Activate only the requested organization.
        unit.setActive(true);

        return mapToResponse(current);
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
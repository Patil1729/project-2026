package com.party.organization.service;


import com.party.organization.dto.OrganizationUnitRequest;
import com.party.organization.dto.OrganizationUnitResponse;


import java.util.List;

public interface OrganizationService {


    OrganizationUnitResponse create(OrganizationUnitRequest request);

    OrganizationUnitResponse getById(Long id);

    List<OrganizationUnitResponse> getChildren(Long parentId);

    List<OrganizationUnitResponse> getRootUnits();

    List<OrganizationUnitResponse> getAll();

    OrganizationUnitResponse update(Long id, OrganizationUnitRequest request);

    void delete(Long id);

    OrganizationUnitResponse activate(Long id);
}
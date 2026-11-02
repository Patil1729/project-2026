package com.party.portal.dto;

import com.party.organization.entity.OrganizationType;

public record PublicOrganizationUnit(Long id, String name, OrganizationType type, Long parentId) { }

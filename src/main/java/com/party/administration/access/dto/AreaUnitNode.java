package com.party.administration.access.dto;

import com.party.organization.entity.OrganizationType;

public record AreaUnitNode(Long id, Long parentId, OrganizationType type, Boolean active, String name) { }

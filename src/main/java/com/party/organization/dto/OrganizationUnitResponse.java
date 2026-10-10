package com.party.organization.dto;

import com.party.organization.entity.OrganizationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrganizationUnitResponse {

        private Long id;
        private String name;
        private String code;
        private OrganizationType type;

        private Long parentId;
        private String parentName;

        private Boolean active;

        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

     }

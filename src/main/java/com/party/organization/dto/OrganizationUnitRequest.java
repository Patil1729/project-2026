package com.party.organization.dto;

import com.party.organization.entity.OrganizationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrganizationUnitRequest {

        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name cannot exceed 100 characters")
        private String name;

        @NotBlank(message = "Code is required")
        @Size(max = 50, message = "Code cannot exceed 50 characters")
        private String code;

        @NotNull(message = "Type is required")
        private OrganizationType type;

        private Long parentId;


    }

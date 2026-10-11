package com.party.committee.dto;

import com.party.committee.entity.CommitteeType;
import com.party.organization.entity.OrganizationUnit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommitteeRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name cannot exceed 255 characters")
    private String name;

    @NotBlank(message = "Code is required")
    @Size(max = 100, message = "Code cannot exceed 100 characters")
    private String code;

    @NotNull(message = "Type is required")
    private CommitteeType committeeType;

    @NotNull(message = "Organization Unit ID is required")
    @Positive(message = "Organization Unit ID must be greater than 0")
    private Long organizationUnitId;

}

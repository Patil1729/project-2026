package com.party.committee.dto;

import com.party.committee.entity.CommitteeType;
import com.party.organization.entity.OrganizationUnit;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommitteeResponse {

    private Long id;
    private String name;
    private String code;

    private CommitteeType committeeType;

    private Long organizationUnitId;
    private String organizationUnitName;

    private Boolean active;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}

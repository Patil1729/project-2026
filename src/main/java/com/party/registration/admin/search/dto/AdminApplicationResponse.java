package com.party.registration.admin.search.dto;

import com.party.committee.entity.CommitteePositionType;
import com.party.registration.application.entity.RegistrationStatus;
import java.time.Instant;
import java.time.LocalDateTime;

public record AdminApplicationResponse(Long id, String applicationNumber, Long applicantId,
        String username, String applicantName, String mobile, Long organizationUnitId,
        String organizationUnitName, Long committeeId, String committeeName, Long requestedPositionId,
        CommitteePositionType positionType, Integer positionNumber, RegistrationStatus status,
        LocalDateTime createdAt, Instant submittedAt, Instant decidedAt, String rejectionReason,
        Long assignmentId) { }

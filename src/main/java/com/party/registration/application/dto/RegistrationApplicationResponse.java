package com.party.registration.application.dto;

import com.party.committee.entity.CommitteePositionType;
import com.party.registration.application.entity.RegistrationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDateTime;

@Getter
@Builder
public class RegistrationApplicationResponse {
    private final Long id;
    private final String applicationNumber;
    private final Long applicantId;
    private final String applicantName;
    private final Long selectedShakhaId;
    private final String selectedShakhaName;
    private final Long committeeId;
    private final String committeeName;
    private final Long requestedPositionId;
    private final CommitteePositionType positionType;
    private final Integer positionNumber;
    private final RegistrationStatus status;
    private final LocalDateTime createdAt;
    private final Instant submittedAt;
    private final Instant decidedAt;
    private final String rejectionReason;
}

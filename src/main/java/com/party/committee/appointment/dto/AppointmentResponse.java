package com.party.committee.appointment.dto;

import com.party.committee.entity.CommitteePositionType;
import java.time.LocalDate;

public record AppointmentResponse(Long id, Long advocateUserId, String username, String advocateName,
        Long committeeId, String committeeName, Long organizationUnitId, Long committeePositionId,
        CommitteePositionType positionType, Integer positionNumber, LocalDate appointedDate,
        LocalDate endDate, boolean active, Long registrationApplicationId) { }

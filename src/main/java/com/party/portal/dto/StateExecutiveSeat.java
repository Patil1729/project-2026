package com.party.portal.dto;

import com.party.committee.entity.CommitteePositionType;

// Professional display fields only: no user IDs, usernames, mobile, email or profile data.
public record StateExecutiveSeat(Long positionId, String committeeName,
        CommitteePositionType positionType, Integer positionNumber,
        String responsibilityUnitName, Long appointmentId, String advocateName, String status) { }

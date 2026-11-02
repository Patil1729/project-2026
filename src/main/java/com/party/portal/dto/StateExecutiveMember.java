package com.party.portal.dto;

import com.party.committee.entity.CommitteePositionType;

// Deliberately excludes account IDs, usernames, contact details and profile data.
public record StateExecutiveMember(Long appointmentId, String advocateName,
        CommitteePositionType positionType, Integer positionNumber,
        String committeeName, String responsibilityUnitName) { }

package com.party.committee.dto;

import com.party.committee.entity.CommitteePositionType;

import java.time.LocalDateTime;

/** active is the seat flag; occupied is derived from its active appointment. */
public record CommitteePositionResponse(
        Long id,
        Long committeeId,
        String committeeName,
        CommitteePositionType positionType,
        Integer positionNumber,
        Long responsibilityUnitId,
        String responsibilityUnitName,
        boolean active,
        boolean occupied,
        boolean available,
        Long currentAdvocateId,
        String currentAdvocateName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
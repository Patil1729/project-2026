package com.party.registration.approval.dto;

import com.party.registration.approval.entity.ApprovalStatus;

import java.time.Instant;

public record ApprovalStepResponse(
        Long id,
        Integer sequenceNumber,
        Long reviewerCommitteeId,
        String reviewerCommitteeName,
        Long organizationUnitId,
        String organizationUnitName,
        Long assignedReviewerId,
        String assignedReviewerName,
        ApprovalStatus status,
        Instant assignedAt,
        Instant dueAt,
        Long decidedById,
        Instant decidedAt,
        String decisionComment,
        Long escalatedToId,
        Instant escalatedAt) {
}

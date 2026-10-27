package com.party.registration.approval.reassignment.dto;

import java.time.Instant;

public record ReviewerReassignmentResponse(Long applicationId, Long stepId, Long assignedReviewerId,
        Long escalatedToId, Instant assignedAt, Instant dueAt, boolean changed) { }

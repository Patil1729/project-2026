package com.party.registration.admin.dashboard.dto;

import com.party.registration.application.entity.RegistrationStatus;
import java.time.Instant;
import java.util.Map;

public record AdminDashboardResponse(Instant generatedAt, Long organizationUnitId, boolean includeDescendants,
        long totalApplications, Map<RegistrationStatus, Long> applicationsByStatus, long pendingApplications,
        long openReviewSteps, long overdueReviewSteps, long escalatedReviewSteps,
        long activeCommittees, long activePositions, long occupiedPositions, long vacantPositions) { }

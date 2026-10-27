package com.party.registration.audit.dto;

import com.party.registration.audit.entity.ApprovalAuditAction;
import java.time.Instant;

public record ApplicationAuditResponse(Long id, Long applicationId, Long approvalStepId,
        ApprovalAuditAction action, Long actorId, String actorUsername, Instant eventAt, String note) { }

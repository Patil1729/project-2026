package com.party.committee.appointment.dto;

import com.party.committee.appointment.audit.entity.AppointmentAuditAction;
import java.time.Instant;

public record AppointmentAuditResponse(Long id, Long assignmentId, Long relatedAssignmentId,
        AppointmentAuditAction action, Instant eventAt, String reason) { }

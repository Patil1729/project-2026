package com.party.registration.escalation.service;

import com.party.registration.escalation.dto.ApprovalEscalationCandidate;
import com.party.registration.escalation.dto.ApprovalEscalationOutcome;

import java.time.Instant;

/** Internal background-worker boundary; no HTTP mutation endpoint is exposed. */
public interface ApprovalEscalationService {
    ApprovalEscalationOutcome processCandidate(ApprovalEscalationCandidate candidate, Instant cutoff);
}

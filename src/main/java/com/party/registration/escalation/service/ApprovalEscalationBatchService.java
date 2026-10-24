package com.party.registration.escalation.service;

import com.party.registration.application.entity.RegistrationStatus;
import com.party.registration.escalation.config.ApprovalEscalationConfiguration;
import com.party.registration.escalation.config.ApprovalEscalationProperties;
import com.party.registration.escalation.dto.ApprovalEscalationBatchResult;
import com.party.registration.approval.entity.ApprovalStatus;
import com.party.registration.approval.repository.ApprovalStepRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

@Service
@Slf4j
public class ApprovalEscalationBatchService {
    private final ApprovalStepRepository stepRepository;
    private final ApprovalEscalationService escalationService;
    private final ApprovalEscalationProperties properties;
    private final Clock clock;
    private long cursor;

    public ApprovalEscalationBatchService(ApprovalStepRepository stepRepository,
            ApprovalEscalationService escalationService, ApprovalEscalationProperties properties,
            @Qualifier(ApprovalEscalationConfiguration.CLOCK_BEAN) Clock clock) {
        this.stepRepository = stepRepository;
        this.escalationService = escalationService;
        this.properties = properties;
        this.clock = clock;
    }

    // One bounded batch per tick. Keep the cursor between ticks so unresolved old
    // rows cannot permanently hide newer work. Parent locks handle other app instances.
    public synchronized ApprovalEscalationBatchResult processBatch() {
        var cutoff = clock.instant();
        var page = PageRequest.of(0, properties.getBatchSize());
        var statuses = List.of(ApprovalStatus.PENDING, ApprovalStatus.OVERDUE);
        var candidates = stepRepository.findEscalationCandidates(
                cutoff, cursor, RegistrationStatus.UNDER_REVIEW, statuses, page);
        if (candidates.isEmpty() && cursor > 0) {
            cursor = 0;
            candidates = stepRepository.findEscalationCandidates(
                    cutoff, cursor, RegistrationStatus.UNDER_REVIEW, statuses, page);
        }
        int overdue = 0, escalated = 0, unresolved = 0, skipped = 0, failed = 0;
        for (var candidate : candidates) {
            cursor = candidate.stepId();
            try {
                // Calls another Spring bean so REQUIRES_NEW applies to each item.
                var result = escalationService.processCandidate(candidate, cutoff);
                if (result.overdueMarked()) overdue++;
                if (result.escalated()) escalated++;
                if (result.missingReason() != null) unresolved++;
                if (result.skipped()) skipped++;
                if (result.missingRecorded()) {
                    log.warn("Escalation unavailable for application {} step {}: {}",
                            candidate.applicationId(), candidate.stepId(), result.missingReason());
                }
            } catch (RuntimeException exception) {
                failed++;
                log.error("Escalation failed for application {} step {}; will retry on a later scan",
                        candidate.applicationId(), candidate.stepId(), exception);
            }
        }
        return new ApprovalEscalationBatchResult(candidates.size(), overdue, escalated,
                unresolved, skipped, failed, cursor);
    }
}

package com.party.registration.escalation.service;

import com.party.committee.entity.AdvocateAssignment;
import com.party.committee.entity.Committee;
import com.party.committee.entity.CommitteePosition;
import com.party.committee.entity.CommitteePositionType;
import com.party.committee.repository.AdvocateAssignmentRepository;
import com.party.committee.repository.CommitteePositionRepository;
import com.party.committee.repository.CommitteeRepository;
import com.party.organization.entity.OrganizationUnit;
import com.party.organization.repository.OrganizationUnitRepository;
import com.party.registration.application.entity.RegistrationApplication;
import com.party.registration.application.entity.RegistrationStatus;
import com.party.registration.application.repository.RegistrationApplicationRepository;
import com.party.registration.escalation.dto.ApprovalEscalationCandidate;
import com.party.registration.escalation.dto.ApprovalEscalationOutcome;
import com.party.registration.approval.entity.ApprovalStep;
import com.party.registration.approval.policy.ApprovalChainPolicy;
import com.party.registration.approval.policy.ApprovalPolicy;
import com.party.registration.approval.repository.ApprovalStepRepository;
import com.party.registration.audit.entity.ApprovalAuditAction;
import com.party.registration.audit.entity.ApprovalAuditLog;
import com.party.registration.audit.repository.ApprovalAuditLogRepository;
import com.party.registration.common.exception.RegistrationException;
import com.party.user.entity.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class ApprovalEscalationServiceImpl implements ApprovalEscalationService {
    private final RegistrationApplicationRepository applicationRepository;
    private final ApprovalStepRepository stepRepository;
    private final CommitteeRepository committeeRepository;
    private final OrganizationUnitRepository organizationRepository;
    private final CommitteePositionRepository positionRepository;
    private final AdvocateAssignmentRepository assignmentRepository;
    private final ApprovalAuditLogRepository auditRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public ApprovalEscalationOutcome processCandidate(ApprovalEscalationCandidate candidate, Instant cutoff) {
        Objects.requireNonNull(candidate, "candidate is required");
        Objects.requireNonNull(cutoff, "cutoff is required");
        // This worker never locks a user later, so it can start at the shared parent.
        // Decision writers take applicant -> this parent -> committees -> units -> seat.
        RegistrationApplication application = applicationRepository.findByIdForApprovalUpdate(candidate.applicationId()).orElse(null);
        if (application == null || application.getStatus() != RegistrationStatus.UNDER_REVIEW) {
            return ApprovalEscalationOutcome.skip();
        }
        List<ApprovalStep> steps = stepRepository.findForDecisionByApplicationId(application.getId());
        ApprovalStep selected = steps.stream().filter(s -> Objects.equals(s.getId(), candidate.stepId())).findFirst().orElse(null);
        if (selected == null || selected.getStatus() == null || !selected.getStatus().canBeDecided()
                || !ApprovalPolicy.isOverdue(selected.getDueAt(), cutoff) || selected.getEscalatedAt() != null) {
            return ApprovalEscalationOutcome.skip();
        }

        Long positionId = application.getRequestedPosition().getId();
        Long targetCommitteeId = positionRepository.findCommitteeIdByPositionId(positionId).orElseThrow(this::invalidPosition);
        Set<Long> committeeIds = new TreeSet<>();
        committeeIds.add(targetCommitteeId);
        for (ApprovalStep step : steps) {
            if (step.getReviewerCommittee() == null || step.getReviewerCommittee().getId() == null) throw invalidChain();
            committeeIds.add(step.getReviewerCommittee().getId());
        }
        for (Long id : committeeIds) committeeRepository.findByIdForUpdate(id).orElseThrow(this::invalidChain);
        Map<Long, OrganizationUnit> locked = new HashMap<>();
        List<OrganizationUnit> ancestry = lockAncestry(application.getSelectedShakha().getId(), locked);
        Long responsibilityId = positionRepository.findResponsibilityUnitIdByPositionId(positionId).orElse(null);
        if (responsibilityId != null) lockAncestry(responsibilityId, locked);
        CommitteePosition position = positionRepository.findByIdForUpdate(positionId).orElseThrow(this::invalidPosition);
        if (position.getCommittee() == null || position.getPositionType() == null || position.getPositionNumber() == null
                || !Objects.equals(position.getCommittee().getId(), targetCommitteeId)
                || !Objects.equals(position.getCommittee().getOrganizationUnit().getId(), application.getSelectedShakha().getId())
                || !position.isActive() || !Boolean.TRUE.equals(position.getCommittee().getActive())
                || !position.getCommittee().isOrganizationTypeValid() || !position.isTemplateValid() || !position.isResponsibilityValid()) {
            throw invalidPosition();
        }
        ApprovalStep active = ApprovalChainPolicy.requireActiveStep(application, steps, ancestry);
        if (!Objects.equals(active.getId(), selected.getId())) return ApprovalEscalationOutcome.skip();
        boolean marked = active.markOverdue(cutoff);
        List<ApprovalAuditLog> events = new ArrayList<>();
        recordOnce(events, application, active, ApprovalAuditAction.STEP_OVERDUE, key(active, "overdue"), cutoff,
                "Required stage is overdue; original reviewer and deadline are retained");

        SeniorResolution resolution = resolveSenior(application, steps, active, cutoff);
        boolean escalated = false;
        boolean missingRecorded = false;
        if (resolution.reason() != null) {
            missingRecorded = recordOnce(events, application, active, ApprovalAuditAction.REVIEWER_MISSING,
                    key(active, "escalation-missing:" + resolution.reason()), cutoff,
                    "Escalation unavailable: " + resolution.reason() + "; stage remains required and retryable");
        } else {
            String escalationKey = key(active, "escalated");
            if (auditRepository.existsByEventKey(escalationKey)) {
                throw new RegistrationException(HttpStatus.CONFLICT, "ESCALATION_AUDIT_STATE_MISMATCH",
                        "Escalation audit exists but the step has no escalation timestamp; reconcile the data.");
            }
            escalated = active.escalateTo(resolution.senior(), cutoff);
            if (escalated) {
                events.add(event(application, active, ApprovalAuditAction.STEP_ESCALATED, escalationKey, cutoff,
                        "Escalated for senior visibility to reviewer " + resolution.senior().getId()
                                + "; the original assigned head must still decide"));
            }
        }
        if (marked || escalated || !events.isEmpty()) {
            stepRepository.saveAndFlush(active);
            if (!events.isEmpty()) auditRepository.saveAllAndFlush(events);
        }
        return new ApprovalEscalationOutcome(marked, escalated, resolution.reason(), missingRecorded, false);
    }

    private SeniorResolution resolveSenior(RegistrationApplication application, List<ApprovalStep> steps,
                                          ApprovalStep active, Instant cutoff) {
        int nextIndex = active.getSequenceNumber();
        if (nextIndex >= steps.size()) return new SeniorResolution(null, "NO_PARENT_STAGE");
        Committee committee = steps.get(nextIndex).getReviewerCommittee();
        List<AdvocateAssignment> heads = assignmentRepository.findCurrentPresidentsForCommittee(
                committee.getId(), CommitteePositionType.PRESIDENT, LocalDate.ofInstant(cutoff, ApprovalPolicy.BUSINESS_ZONE));
        if (heads.isEmpty()) return new SeniorResolution(null, "HEAD_MISSING");
        if (heads.size() != 1) return new SeniorResolution(null, "HEAD_AMBIGUOUS");
        AppUser senior = heads.get(0).getAdvocate();
        if (senior == null || !senior.isEnabled()) return new SeniorResolution(null, "HEAD_DISABLED");
        if (Objects.equals(senior.getId(), application.getApplicant().getId())) return new SeniorResolution(null, "APPLICANT_IS_HEAD");
        if (Objects.equals(senior.getId(), active.getAssignedReviewer().getId())) return new SeniorResolution(null, "ASSIGNED_REVIEWER_IS_HEAD");
        return new SeniorResolution(senior, null);
    }

    private List<OrganizationUnit> lockAncestry(Long id, Map<Long, OrganizationUnit> locked) {
        List<OrganizationUnit> units = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Long next = id;
        while (next != null) {
            if (!visited.add(next)) throw invalidOrganization();
            OrganizationUnit unit = locked.get(next);
            if (unit == null) {
                unit = organizationRepository.findByIdForUpdate(next).orElseThrow(this::invalidOrganization);
                locked.put(next, unit);
            }
            units.add(unit);
            next = unit.getParent() == null ? null : unit.getParent().getId();
        }
        ApprovalChainPolicy.validateAncestry(units);
        return units;
    }

    private boolean recordOnce(List<ApprovalAuditLog> events, RegistrationApplication application, ApprovalStep step,
                               ApprovalAuditAction action, String eventKey, Instant at, String note) {
        if (auditRepository.existsByEventKey(eventKey)) return false;
        events.add(event(application, step, action, eventKey, at, note));
        return true;
    }

    private ApprovalAuditLog event(RegistrationApplication application, ApprovalStep step, ApprovalAuditAction action,
                                   String eventKey, Instant at, String note) {
        // null actor identifies a system-generated event in the existing entity model.
        return ApprovalAuditLog.builder().application(application).approvalStep(step).actor(null)
                .action(action).eventKey(eventKey).eventAt(at).note(note).build();
    }

    private String key(ApprovalStep step, String suffix) { return "approval-step:" + step.getId() + ":" + suffix; }
    private RegistrationException invalidChain() { return new RegistrationException(HttpStatus.CONFLICT, "INVALID_APPROVAL_CHAIN", "The complete required chain must remain valid."); }
    private RegistrationException invalidPosition() { return new RegistrationException(HttpStatus.CONFLICT, "INVALID_POSITION", "The requested seat and scope must remain valid and active."); }
    private RegistrationException invalidOrganization() { return new RegistrationException(HttpStatus.CONFLICT, "INVALID_ORGANIZATION", "The organization ancestry must remain active and valid."); }
    private record SeniorResolution(AppUser senior, String reason) { }
}

package com.party.registration.approval.reassignment.service;

import com.party.committee.entity.*;
import com.party.committee.repository.*;
import com.party.organization.entity.OrganizationUnit;
import com.party.organization.repository.OrganizationUnitRepository;
import com.party.registration.application.entity.RegistrationStatus;
import com.party.registration.application.repository.RegistrationApplicationRepository;
import com.party.registration.approval.policy.*;
import com.party.registration.approval.reassignment.dto.*;
import com.party.registration.approval.repository.ApprovalStepRepository;
import com.party.registration.audit.entity.*;
import com.party.registration.audit.repository.ApprovalAuditLogRepository;
import com.party.registration.common.exception.RegistrationException;
import com.party.registration.common.repository.RegistrationApplicantRepository;
import com.party.user.entity.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReviewerReassignmentServiceImpl implements ReviewerReassignmentService {
    private final RegistrationApplicationRepository applications;
    private final RegistrationApplicantRepository users;
    private final ApprovalStepRepository steps;
    private final ApprovalAuditLogRepository audits;
    private final CommitteeRepository committees;
    private final CommitteePositionRepository positions;
    private final OrganizationUnitRepository organizations;
    private final AdvocateAssignmentRepository assignments;
    private final Clock clock;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReviewerReassignmentResponse reassign(Long id, ReviewerReassignmentRequest request) {
        if (request == null || request.reason() == null || request.reason().isBlank() || request.reason().length() > 1500)
            throw new RegistrationException(HttpStatus.BAD_REQUEST, "INVALID_REASSIGNMENT_REASON", "Provide a nonblank reason of at most 1500 characters.");
        Long applicantId = applications.findApplicantIdById(id).orElseThrow(this::notFound);
        AppUser applicant = users.findByIdForUpdate(applicantId).orElseThrow(this::notFound);
        var application = applications.findByIdForApprovalUpdate(id).orElseThrow(this::notFound);
        if (!Objects.equals(application.getApplicant().getId(), applicantId)) throw conflict("APPLICATION_CHANGED", "Reload the application and retry.");
        if (application.getStatus() != RegistrationStatus.UNDER_REVIEW)
            throw conflict("APPLICATION_NOT_UNDER_REVIEW", "Only applications under review can have a reviewer reassigned.");
        var chain = steps.findForDecisionByApplicationId(id);
        Long positionId = application.getRequestedPosition().getId();
        Long targetCommitteeId = positions.findCommitteeIdByPositionId(positionId)
                .orElseThrow(() -> conflict("INVALID_POSITION", "Requested position not found."));
        var committeeIds = new TreeSet<Long>();
        committeeIds.add(targetCommitteeId);
        chain.forEach(step -> committeeIds.add(step.getReviewerCommittee().getId()));
        for (Long committeeId : committeeIds) committees.findByIdForUpdate(committeeId)
                .orElseThrow(() -> conflict("INVALID_APPROVAL_CHAIN", "Reviewer committee not found."));
        Map<Long, OrganizationUnit> locked = new HashMap<>();
        var ancestry = lockAncestry(application.getSelectedShakha().getId(), locked);
        positions.findResponsibilityUnitIdByPositionId(positionId).ifPresent(unitId -> lockAncestry(unitId, locked));
        CommitteePosition position = positions.findByIdForUpdate(positionId)
                .orElseThrow(() -> conflict("INVALID_POSITION", "Requested position not found."));
        if (!position.isActive() || !Boolean.TRUE.equals(position.getCommittee().getActive())
                || !Objects.equals(position.getCommittee().getId(), targetCommitteeId)
                || !Objects.equals(position.getCommittee().getOrganizationUnit().getId(), application.getSelectedShakha().getId())
                || !position.getCommittee().isOrganizationTypeValid() || position.getPositionType() == null
                || position.getPositionNumber() == null || !position.isTemplateValid() || !position.isResponsibilityValid())
            throw conflict("INVALID_POSITION", "The requested position and its committee must remain active and valid.");
        var active = ApprovalChainPolicy.requireActiveStep(application, chain, ancestry);
        AppUser reviewer = currentHead(active.getReviewerCommittee(), applicant);
        AppUser senior = null;
        if (active.getEscalatedAt() != null) {
            if (active.getSequenceNumber() >= chain.size()) throw conflict("INVALID_APPROVAL_CHAIN", "An escalated stage requires a parent committee.");
            senior = currentHead(chain.get(active.getSequenceNumber()).getReviewerCommittee(), applicant);
            if (Objects.equals(reviewer.getId(), senior.getId())) throw conflict("REVIEWER_CONFLICT", "The required head and escalation senior must be different users.");
        }
        Long previousReviewer = active.getAssignedReviewer().getId();
        Long previousSenior = active.getEscalatedTo() == null ? null : active.getEscalatedTo().getId();
        boolean reviewerChanged = !Objects.equals(previousReviewer, reviewer.getId());
        boolean seniorChanged = senior != null && !Objects.equals(previousSenior, senior.getId());
        if (reviewerChanged) active.reassignTo(reviewer);
        if (seniorChanged) active.reassignEscalationTo(senior);
        if (reviewerChanged || seniorChanged) {
            steps.saveAndFlush(active);
            audits.saveAndFlush(ApprovalAuditLog.builder().application(application).approvalStep(active)
                    .action(ApprovalAuditAction.REVIEWER_REASSIGNED).eventKey(UUID.randomUUID().toString())
                    .eventAt(clock.instant()).note("Administrative reassignment; reviewer " + previousReviewer + " -> " + reviewer.getId()
                            + "; senior " + previousSenior + " -> " + (senior == null ? null : senior.getId())
                            + "; original deadlines retained. Reason: " + request.reason().strip()).build());
        }
        return new ReviewerReassignmentResponse(id, active.getId(), active.getAssignedReviewer().getId(),
                active.getEscalatedTo() == null ? null : active.getEscalatedTo().getId(),
                active.getAssignedAt(), active.getDueAt(), reviewerChanged || seniorChanged);
    }

    private AppUser currentHead(Committee committee, AppUser applicant) {
        var heads = assignments.findCurrentPresidentsForCommittee(committee.getId(), CommitteePositionType.PRESIDENT,
                LocalDate.now(clock.withZone(ApprovalPolicy.BUSINESS_ZONE)));
        if (heads.size() != 1) throw conflict("REVIEWER_MISSING_OR_AMBIGUOUS", "Exactly one enabled current president is required for committee " + committee.getId());
        AppUser head = heads.get(0).getAdvocate();
        if (!head.isEnabled() || Objects.equals(head.getId(), applicant.getId()))
            throw conflict("REVIEWER_CONFLICT", "The committee president must be enabled and different from the applicant.");
        return head;
    }

    private List<OrganizationUnit> lockAncestry(Long unitId, Map<Long, OrganizationUnit> locked) {
        List<OrganizationUnit> result = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Long next = unitId;
        while (next != null) {
            if (!visited.add(next)) throw conflict("INVALID_ORGANIZATION", "A cycle exists in the organization hierarchy.");
            OrganizationUnit unit = locked.get(next);
            if (unit == null) {
                unit = organizations.findByIdForUpdate(next).orElseThrow(() -> conflict("INVALID_ORGANIZATION", "Organization unit not found."));
                locked.put(next, unit);
            }
            result.add(unit);
            next = unit.getParent() == null ? null : unit.getParent().getId();
        }
        ApprovalChainPolicy.validateAncestry(result);
        return result;
    }

    private RegistrationException notFound() { return new RegistrationException(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "Registration application not found."); }
    private RegistrationException conflict(String code, String message) { return new RegistrationException(HttpStatus.CONFLICT, code, message); }
}

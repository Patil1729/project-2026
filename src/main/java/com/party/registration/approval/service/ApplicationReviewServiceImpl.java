package com.party.registration.approval.service;

import com.party.committee.entity.AdvocateAssignment;
import com.party.committee.entity.Committee;
import com.party.committee.entity.CommitteePosition;
import com.party.committee.entity.CommitteePositionType;
import com.party.committee.repository.AdvocateAssignmentRepository;
import com.party.committee.repository.CommitteePositionRepository;
import com.party.committee.repository.CommitteeRepository;
import com.party.organization.entity.OrganizationUnit;
import com.party.organization.repository.OrganizationUnitRepository;
import com.party.registration.approval.policy.ApprovalPolicy;
import com.party.registration.approval.policy.ApprovalChainPolicy;
import com.party.registration.approval.dto.ApplicationApprovalRequest;
import com.party.registration.approval.dto.ApplicationRejectionRequest;
import com.party.registration.approval.dto.ApplicationReviewInboxItem;
import com.party.registration.approval.dto.ApplicationReviewInboxResponse;
import com.party.registration.approval.dto.ApplicationReviewResponse;
import com.party.registration.approval.dto.ApprovalStepResponse;
import com.party.registration.application.dto.RegistrationApplicationResponse;
import com.party.registration.audit.entity.ApprovalAuditAction;
import com.party.registration.audit.entity.ApprovalAuditLog;
import com.party.registration.approval.entity.ApprovalStatus;
import com.party.registration.approval.entity.ApprovalStep;
import com.party.registration.application.entity.RegistrationApplication;
import com.party.registration.application.entity.RegistrationStatus;
import com.party.registration.common.exception.RegistrationException;
import com.party.registration.audit.repository.ApprovalAuditLogRepository;
import com.party.registration.approval.repository.ApprovalStepRepository;
import com.party.registration.common.repository.RegistrationApplicantRepository;
import com.party.registration.application.repository.RegistrationApplicationRepository;
import com.party.user.entity.AppUser;
import com.party.user.entity.Role;
import com.party.user.entity.RoleType;
import com.party.user.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

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
import java.util.UUID;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationReviewServiceImpl implements ApplicationReviewService {
    private static final List<ApprovalStatus> OPEN_STEPS = List.of(ApprovalStatus.PENDING, ApprovalStatus.OVERDUE);

    private final RegistrationApplicationRepository applicationRepository;
    private final RegistrationApplicantRepository userRepository;
    private final ApprovalStepRepository stepRepository;
    private final ApprovalAuditLogRepository auditRepository;
    private final AdvocateAssignmentRepository assignmentRepository;
    private final CommitteeRepository committeeRepository;
    private final CommitteePositionRepository positionRepository;
    private final OrganizationUnitRepository organizationRepository;
    private final RoleRepository roleRepository;

    @Override
    public ApplicationReviewInboxResponse getPending(String username, int page, int size) {
        AppUser reviewer = enabledReviewer(username);
        if (page < 0 || size < 1 || size > 100) {
            throw failure(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "Use page >= 0 and size between 1 and 100.");
        }
        var slice = stepRepository.findPendingInbox(reviewer.getId(), RegistrationStatus.UNDER_REVIEW,
                OPEN_STEPS, CommitteePositionType.PRESIDENT, today(), PageRequest.of(page, size));
        Instant now = Instant.now();
        return new ApplicationReviewInboxResponse(slice.getContent().stream()
                .map(step -> new ApplicationReviewInboxItem(applicationResponse(step.getApplication()),
                        stepResponse(step), ApprovalPolicy.isOverdue(step.getDueAt(), now)))
                .toList(), page, size, slice.hasNext());
    }

    @Override
    public ApplicationReviewInboxResponse getEscalated(String username, int page, int size) {
        AppUser reviewer = enabledReviewer(username);
        if (page < 0 || size < 1 || size > 100) {
            throw failure(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "Use page >= 0 and size between 1 and 100.");
        }
        var slice = stepRepository.findEscalatedInbox(reviewer.getId(), RegistrationStatus.UNDER_REVIEW,
                OPEN_STEPS, CommitteePositionType.PRESIDENT, today(), PageRequest.of(page, size));
        Instant now = Instant.now();
        return new ApplicationReviewInboxResponse(slice.getContent().stream()
                .map(step -> new ApplicationReviewInboxItem(applicationResponse(step.getApplication()),
                        stepResponse(step), ApprovalPolicy.isOverdue(step.getDueAt(), now)))
                .toList(), page, size, slice.hasNext());
    }

    @Override
    public ApplicationReviewResponse getReview(String username, Long applicationId) {
        AppUser reviewer = enabledReviewer(username);
        RegistrationApplication application = applicationRepository.findById(applicationId).orElseThrow(this::notFound);
        List<ApprovalStep> steps = stepRepository.findAllByApplication_IdOrderBySequenceNumberAsc(applicationId);
        List<ApprovalStep> assigned = steps.stream().filter(step -> sameUser(step.getAssignedReviewer(), reviewer)).toList();
        boolean escalationRecipient = steps.stream().anyMatch(step -> isOpenEscalation(application, step, reviewer));
        if (assigned.isEmpty() && !escalationRecipient) throw notFound();
        requireActiveAncestry(application.getSelectedShakha());
        RegistrationException noLongerEligible = null;
        for (ApprovalStep step : assigned) {
            try {
                requireAssignedHead(step, reviewer, application.getApplicant());
                return response(application, steps);
            } catch (RegistrationException exception) {
                if (!"REVIEWER_NO_LONGER_ELIGIBLE".equals(exception.getErrorCode())) throw exception;
                noLongerEligible = exception;
            }
        }
        if (escalationRecipient) {
            requireSeniorReadAccess(application, steps, reviewer);
            return response(application, steps);
        }
        throw noLongerEligible;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ApplicationReviewResponse approve(String username, Long applicationId, Long stepId, ApplicationApprovalRequest request) {
        if (request == null) {
            throw failure(HttpStatus.BAD_REQUEST, "INVALID_REVIEW_REQUEST", "Send a JSON object, for example {}.");
        }
        String comment = comment(request.comment());
        DecisionContext context = lockDecision(username, applicationId, stepId);
        RegistrationApplication application = context.application();
        List<ApprovalStep> steps = context.steps();
        ApprovalStep active = context.active();
        int nextIndex = active.getSequenceNumber(); // Sequence is one-based; the next list index has the same number.
        Instant now = Instant.now();
        List<ApprovalAuditLog> events = new ArrayList<>();

        if (nextIndex < steps.size()) {
            ApprovalStep next = steps.get(nextIndex);
            // Resolve before changing the active step: a missing next head leaves the current decision open.
            AppUser nextReviewer = eligibleHead(next.getReviewerCommittee(), application.getApplicant());
            active.approve(context.reviewer(), now, comment);
            next.activate(nextReviewer, now);
            events.add(audit(application, active, context.reviewer(), ApprovalAuditAction.STEP_APPROVED, now,
                    "Required stage " + active.getSequenceNumber() + " approved"));
            events.add(audit(application, next, context.reviewer(), ApprovalAuditAction.STEP_ACTIVATED, now,
                    "Next stage assigned to reviewer " + nextReviewer.getId() + "; due " + next.getDueAt()));
        } else {
            AppUser applicant = context.applicant();
            if (!applicant.isEnabled() || applicant.getProfile() == null) {
                throw conflict("APPLICANT_NOT_ELIGIBLE", "The applicant must still be enabled and have a profile.");
            }
            if (positionRepository.countActiveAssignmentsForPositionId(application.getRequestedPosition().getId()) > 0) {
                throw conflict("POSITION_OCCUPIED", "The requested position already has an active appointment.");
            }
            if (assignmentRepository.existsByRegistrationApplication_Id(applicationId)) {
                throw conflict("APPOINTMENT_EXISTS", "An appointment already exists for this application.");
            }
            Role advocateRole = roleRepository.findByName(RoleType.ADVOCATE).orElseThrow(() ->
                    conflict("ADVOCATE_ROLE_MISSING", "Run the V4 ADVOCATE-role seed migration before final approval."));

            active.approve(context.reviewer(), now, comment);
            // Existing entity guard checks the entire managed collection, not just this last decision.
            application.approve(now);
            AdvocateAssignment appointment = AdvocateAssignment.builder()
                    .advocate(applicant).committeePosition(application.getRequestedPosition())
                    .registrationApplication(application).appointedDate(today()).build();
            application.setAssignment(appointment);
            if (applicant.getRoles().stream().noneMatch(role -> role.getName() == RoleType.ADVOCATE)) {
                applicant.getRoles().add(advocateRole);
            }
            try {
                // Flush here to surface the existing MySQL unique active-seat/application guards.
                assignmentRepository.saveAndFlush(appointment);
            } catch (DataIntegrityViolationException exception) {
                throw conflict("APPOINTMENT_CONFLICT", "The appointment could not be saved. Reload the seat and retry.");
            }
            events.add(audit(application, active, context.reviewer(), ApprovalAuditAction.STEP_APPROVED, now,
                    "Final required stage approved"));
            events.add(audit(application, null, context.reviewer(), ApprovalAuditAction.APPLICATION_APPROVED, now,
                    "All required heads approved; appointment " + appointment.getId() + " created; ADVOCATE membership retained/granted"));
        }

        // All step/application/role changes are managed by this transaction; no cascade is needed.
        applicationRepository.saveAndFlush(application);
        auditRepository.saveAllAndFlush(events);
        return response(application, steps);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ApplicationReviewResponse reject(String username, Long applicationId, Long stepId, ApplicationRejectionRequest request) {
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw failure(HttpStatus.BAD_REQUEST, "REJECTION_REASON_REQUIRED", "Provide a nonblank rejection reason.");
        }
        String reason = comment(request.reason());
        DecisionContext context = lockDecision(username, applicationId, stepId);
        Instant now = Instant.now();
        context.active().reject(context.reviewer(), now, reason);
        context.application().reject(now, reason);
        applicationRepository.saveAndFlush(context.application());
        auditRepository.saveAllAndFlush(List.of(
                audit(context.application(), context.active(), context.reviewer(), ApprovalAuditAction.STEP_REJECTED, now,
                        "Required stage " + context.active().getSequenceNumber() + " rejected"),
                audit(context.application(), null, context.reviewer(), ApprovalAuditAction.APPLICATION_REJECTED, now,
                        "Application rejected; later stages remain unactivated")
        ));
        return response(context.application(), context.steps());
    }

    private DecisionContext lockDecision(String username, Long applicationId, Long stepId) {
        AppUser reviewer = enabledReviewer(username);
        Long applicantId = applicationRepository.findApplicantIdForAssignedStep(applicationId, stepId, reviewer.getId())
                .orElseThrow(this::notFound);
        // Follow the existing creation order: applicant -> application -> committees -> units -> seat.
        // The scalar lookup above does not hydrate the applicant or application before these locks.
        AppUser applicant = userRepository.findByIdForUpdate(applicantId).orElseThrow(this::notFound);
        RegistrationApplication application = applicationRepository.findByIdForApprovalUpdate(applicationId).orElseThrow(this::notFound);
        if (!Objects.equals(application.getApplicant().getId(), applicantId)) {
            throw conflict("APPLICATION_CHANGED", "The application changed. Reload and retry.");
        }
        if (application.getStatus() != RegistrationStatus.UNDER_REVIEW) {
            throw conflict("APPLICATION_NOT_UNDER_REVIEW", "Only an application under review can receive a decision.");
        }
        if (sameUser(reviewer, applicant)) {
            throw failure(HttpStatus.FORBIDDEN, "SELF_REVIEW_NOT_ALLOWED", "Applicants cannot decide their own applications.");
        }
        List<ApprovalStep> steps = stepRepository.findForDecisionByApplicationId(applicationId);
        if (steps.isEmpty()) {
            throw invalidChain();
        }
        Long positionId = application.getRequestedPosition().getId();
        Long targetCommitteeId = positionRepository.findCommitteeIdByPositionId(positionId)
                .orElseThrow(() -> conflict("INVALID_POSITION", "Requested position not found."));
        Set<Long> committeeIds = new TreeSet<>();
        committeeIds.add(targetCommitteeId);
        for (ApprovalStep step : steps) {
            if (step.getReviewerCommittee() == null || step.getReviewerCommittee().getId() == null) {
                throw invalidChain();
            }
            committeeIds.add(step.getReviewerCommittee().getId());
        }
        for (Long id : committeeIds) {
            committeeRepository.findByIdForUpdate(id).orElseThrow(this::invalidChain);
        }
        Map<Long, OrganizationUnit> lockedUnits = new HashMap<>();
        List<OrganizationUnit> ancestry = lockActiveAncestry(application.getSelectedShakha().getId(), lockedUnits);
        Long responsibilityId = positionRepository.findResponsibilityUnitIdByPositionId(positionId).orElse(null);
        if (responsibilityId != null) {
            lockActiveAncestry(responsibilityId, lockedUnits);
        }
        CommitteePosition position = positionRepository.findByIdForUpdate(positionId)
                .orElseThrow(() -> conflict("INVALID_POSITION", "Requested position not found."));
        if (!Objects.equals(position.getCommittee().getId(), targetCommitteeId)
                || position.getPositionType() == null || position.getPositionNumber() == null
                || !Objects.equals(position.getCommittee().getOrganizationUnit().getId(), application.getSelectedShakha().getId())
                || !position.isActive() || !Boolean.TRUE.equals(position.getCommittee().getActive())
                || !position.getCommittee().isOrganizationTypeValid()
                || !position.isTemplateValid() || !position.isResponsibilityValid()) {
            throw conflict("INVALID_POSITION", "The requested seat and its scope must remain valid and active.");
        }
        ApprovalStep active = requireValidChain(application, steps, ancestry);
        if (!Objects.equals(active.getId(), stepId)) {
            throw conflict("STEP_NOT_ACTIVE", "Only the current required stage can receive a decision.");
        }
        requireAssignedHead(active, reviewer, applicant);
        return new DecisionContext(application, applicant, reviewer, steps, active);
    }

    private ApprovalStep requireValidChain(RegistrationApplication application, List<ApprovalStep> steps,
                                          List<OrganizationUnit> ancestry) {
        return ApprovalChainPolicy.requireActiveStep(application, steps, ancestry);
    }

    private List<OrganizationUnit> lockActiveAncestry(Long id, Map<Long, OrganizationUnit> locked) {
        List<OrganizationUnit> ancestry = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Long next = id;
        while (next != null) {
            if (!visited.add(next)) throw invalidOrganization();
            OrganizationUnit unit = locked.get(next);
            if (unit == null) {
                unit = organizationRepository.findByIdForUpdate(next).orElseThrow(this::invalidOrganization);
                locked.put(next, unit);
            }
            ancestry.add(unit);
            next = unit.getParent() == null ? null : unit.getParent().getId();
        }
        validateAncestry(ancestry);
        return ancestry;
    }

    private void requireActiveAncestry(OrganizationUnit selected) {
        ApprovalChainPolicy.activeAncestry(selected);
    }

    private void validateAncestry(List<OrganizationUnit> ancestry) {
        ApprovalChainPolicy.validateAncestry(ancestry);
    }

    private void requireAssignedHead(ApprovalStep step, AppUser reviewer, AppUser applicant) {
        if (sameUser(reviewer, applicant)) {
            throw failure(HttpStatus.FORBIDDEN, "SELF_REVIEW_NOT_ALLOWED", "Applicants cannot decide their own applications.");
        }
        if (!sameUser(step.getAssignedReviewer(), reviewer)) throw notFound();
        Committee committee = step.getReviewerCommittee();
        if (committee == null || !Boolean.TRUE.equals(committee.getActive()) || committee.getCommitteeType() == null
                || committee.getOrganizationUnit() == null || !committee.isOrganizationTypeValid()) {
            throw invalidChain();
        }
        requireActiveAncestry(committee.getOrganizationUnit());
        List<AdvocateAssignment> heads = currentHeads(committee);
        if (heads.size() > 1) {
            throw conflict("AMBIGUOUS_REVIEWER", "More than one current president exists for committee " + committee.getId());
        }
        if (heads.size() != 1 || !sameUser(heads.get(0).getAdvocate(), reviewer)) {
            throw failure(HttpStatus.FORBIDDEN, "REVIEWER_NO_LONGER_ELIGIBLE",
                    "Only the assigned, current enabled president of this exact committee can review the application.");
        }
    }

    private boolean isOpenEscalation(RegistrationApplication application, ApprovalStep step, AppUser reviewer) {
        return application.getStatus() == RegistrationStatus.UNDER_REVIEW && step.getStatus() != null
                && step.getStatus().canBeDecided() && step.getEscalatedAt() != null
                && sameUser(step.getEscalatedTo(), reviewer);
    }

    private void requireSeniorReadAccess(RegistrationApplication application, List<ApprovalStep> steps, AppUser reviewer) {
        ApprovalStep active = ApprovalChainPolicy.requireActiveStep(application, steps,
                ApprovalChainPolicy.activeAncestry(application.getSelectedShakha()));
        if (!isOpenEscalation(application, active, reviewer) || sameUser(active.getAssignedReviewer(), reviewer)
                || active.getSequenceNumber() >= steps.size()) throw notFound();
        try {
            AppUser currentSenior = eligibleHead(steps.get(active.getSequenceNumber()).getReviewerCommittee(), application.getApplicant());
            if (sameUser(currentSenior, reviewer)) return;
        } catch (RegistrationException exception) {
            // A former/ambiguous senior appointment grants no read authority.
        }
        throw failure(HttpStatus.FORBIDDEN, "REVIEWER_NO_LONGER_ELIGIBLE", "The escalation recipient must still head the next required committee.");
    }

    private AppUser eligibleHead(Committee committee, AppUser applicant) {
        List<AdvocateAssignment> heads = currentHeads(committee);
        if (heads.isEmpty()) {
            throw conflict("REVIEWER_MISSING", "Appoint an enabled current PRESIDENT for committee " + committee.getId() + " before approving.");
        }
        if (heads.size() != 1) {
            throw conflict("AMBIGUOUS_REVIEWER", "More than one current president exists for committee " + committee.getId());
        }
        AppUser head = heads.get(0).getAdvocate();
        if (head == null || !head.isEnabled()) throw conflict("REVIEWER_MISSING", "The next president must be an enabled account.");
        if (sameUser(head, applicant)) throw conflict("REVIEWER_CONFLICT", "The applicant cannot review a later stage of their own application.");
        return head;
    }

    private List<AdvocateAssignment> currentHeads(Committee committee) {
        return assignmentRepository.findCurrentPresidentsForCommittee(committee.getId(), CommitteePositionType.PRESIDENT, today());
    }

    private AppUser enabledReviewer(String username) {
        AppUser reviewer = userRepository.findByUsername(username).orElseThrow(() ->
                failure(HttpStatus.UNAUTHORIZED, "REVIEWER_NOT_AUTHENTICATED", "The username has no reviewer account."));
        if (!reviewer.isEnabled()) throw failure(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "Your account is disabled.");
        return reviewer;
    }

    private String comment(String value) {
        if (value != null && value.length() > 2000) {
            throw failure(HttpStatus.BAD_REQUEST, "INVALID_REVIEW_REQUEST", "A comment or reason cannot exceed 2000 characters.");
        }
        return value == null || value.isBlank() ? null : value.strip();
    }

    private ApplicationReviewResponse response(RegistrationApplication application, List<ApprovalStep> steps) {
        AdvocateAssignment appointment = application.getAssignment();
        return new ApplicationReviewResponse(applicationResponse(application), appointment == null ? null : appointment.getId(),
                steps.stream().map(this::stepResponse).toList());
    }

    private RegistrationApplicationResponse applicationResponse(RegistrationApplication application) {
        CommitteePosition position = application.getRequestedPosition();
        Committee committee = position.getCommittee();
        return RegistrationApplicationResponse.builder().id(application.getId()).applicationNumber(application.getApplicationNumber())
                .applicantId(application.getApplicant().getId()).applicantName(application.getApplicant().getFullName())
                .selectedShakhaId(application.getSelectedShakha().getId()).selectedShakhaName(application.getSelectedShakha().getName())
                .committeeId(committee.getId()).committeeName(committee.getName()).requestedPositionId(position.getId())
                .positionType(position.getPositionType()).positionNumber(position.getPositionNumber())
                .status(application.getStatus()).createdAt(application.getCreatedAt()).submittedAt(application.getSubmittedAt())
                .decidedAt(application.getDecidedAt()).rejectionReason(application.getRejectionReason()).build();
    }

    private ApprovalStepResponse stepResponse(ApprovalStep step) {
        Committee committee = step.getReviewerCommittee();
        AppUser assigned = step.getAssignedReviewer();
        AppUser decided = step.getDecidedBy();
        AppUser senior = step.getEscalatedTo();
        return new ApprovalStepResponse(step.getId(), step.getSequenceNumber(), committee.getId(), committee.getName(),
                committee.getOrganizationUnit().getId(), committee.getOrganizationUnit().getName(),
                assigned == null ? null : assigned.getId(), assigned == null ? null : assigned.getFullName(),
                step.getStatus(), step.getAssignedAt(), step.getDueAt(), decided == null ? null : decided.getId(),
                step.getDecidedAt(), step.getDecisionComment(), senior == null ? null : senior.getId(), step.getEscalatedAt());
    }

    private ApprovalAuditLog audit(RegistrationApplication application, ApprovalStep step, AppUser actor,
                                   ApprovalAuditAction action, Instant at, String note) {
        return ApprovalAuditLog.builder().application(application).approvalStep(step).actor(actor).action(action)
                .eventKey(UUID.randomUUID().toString()).eventAt(at).note(note).build();
    }

    private static boolean sameUser(AppUser first, AppUser second) {
        return first != null && second != null && first.getId() != null && Objects.equals(first.getId(), second.getId());
    }

    private LocalDate today() { return LocalDate.now(ApprovalPolicy.BUSINESS_ZONE); }
    private RegistrationException notFound() { return failure(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "Assigned application review not found."); }
    private RegistrationException invalidChain() { return conflict("INVALID_APPROVAL_CHAIN", "The complete required chain, order, and timelines must be valid."); }
    private RegistrationException invalidOrganization() { return conflict("INVALID_ORGANIZATION", "The complete organization ancestry must remain active and valid."); }
    private RegistrationException conflict(String code, String message) { return failure(HttpStatus.CONFLICT, code, message); }
    private RegistrationException failure(HttpStatus status, String code, String message) { return new RegistrationException(status, code, message); }

    private record DecisionContext(RegistrationApplication application, AppUser applicant, AppUser reviewer,
                                   List<ApprovalStep> steps, ApprovalStep active) { }
}

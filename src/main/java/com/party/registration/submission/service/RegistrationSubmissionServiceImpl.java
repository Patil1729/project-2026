package com.party.registration.submission.service;

import com.party.committee.entity.AdvocateAssignment;
import com.party.committee.entity.Committee;
import com.party.committee.entity.CommitteePosition;
import com.party.committee.entity.CommitteePositionType;
import com.party.committee.repository.AdvocateAssignmentRepository;
import com.party.committee.repository.CommitteePositionRepository;
import com.party.committee.repository.CommitteeRepository;
import com.party.organization.entity.OrganizationType;
import com.party.organization.entity.OrganizationUnit;
import com.party.registration.approval.policy.ApprovalPolicy;
import com.party.registration.submission.config.RegistrationSubmissionProperties;
import com.party.registration.approval.dto.ApprovalStepResponse;
import com.party.registration.submission.dto.RegistrationSubmissionResponse;
import com.party.registration.document.entity.ApplicationDocument;
import com.party.registration.audit.entity.ApprovalAuditAction;
import com.party.registration.audit.entity.ApprovalAuditLog;
import com.party.registration.approval.entity.ApprovalStep;
import com.party.registration.document.entity.DocumentStatus;
import com.party.registration.document.entity.DocumentType;
import com.party.registration.application.entity.RegistrationApplication;
import com.party.registration.application.entity.RegistrationStatus;
import com.party.registration.common.exception.RegistrationException;
import com.party.registration.document.repository.ApplicationDocumentRepository;
import com.party.registration.audit.repository.ApprovalAuditLogRepository;
import com.party.registration.approval.repository.ApprovalStepRepository;
import com.party.registration.common.repository.RegistrationApplicantRepository;
import com.party.registration.application.repository.RegistrationApplicationRepository;
import com.party.user.entity.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegistrationSubmissionServiceImpl implements RegistrationSubmissionService {
    private final RegistrationApplicationRepository applicationRepository;
    private final RegistrationApplicantRepository userRepository;
    private final ApplicationDocumentRepository documentRepository;
    private final CommitteeRepository committeeRepository;
    private final CommitteePositionRepository positionRepository;
    private final AdvocateAssignmentRepository assignmentRepository;
    private final ApprovalStepRepository approvalStepRepository;
    private final ApprovalAuditLogRepository auditLogRepository;
    private final RegistrationSubmissionProperties properties;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RegistrationSubmissionResponse submit(String username, Long applicationId) {
        AppUser applicant = findEnabledApplicant(username);
        // Upload, document review and submission all serialize on this parent row.
        RegistrationApplication application = applicationRepository
                .findOwnedByIdForUpdate(applicationId, applicant.getId())
                .orElseThrow(this::applicationNotFound);
        if (application.getStatus() != RegistrationStatus.AWAITING_RECOMMENDATION) {
            throw conflict("APPLICATION_ALREADY_SUBMITTED",
                    "Only an application awaiting recommendation can be submitted.");
        }
        if (applicant.getProfile() == null) {
            throw conflict("PROFILE_REQUIRED", "Complete your profile before submitting the application.");
        }
        if (approvalStepRepository.existsByApplication_Id(applicationId)) {
            throw conflict("APPROVAL_CHAIN_EXISTS", "Approval steps already exist for this application.");
        }

        List<OrganizationUnit> ancestry = activeAncestry(application.getSelectedShakha());
        requireAvailableTarget(application);
        List<DocumentType> requiredTypes = List.copyOf(EnumSet.copyOf(properties.getRequiredDocumentTypes()));
        List<ApplicationDocument> requiredDocuments = requireVerifiedDocuments(applicationId, requiredTypes);
        List<Committee> committees = buildCommitteeChain(application, ancestry);
        AppUser firstReviewer = resolveFirstReviewer(committees.get(0), applicant);

        Instant now = Instant.now();
        List<ApprovalStep> steps = new ArrayList<>();
        for (int index = 0; index < committees.size(); index++) {
            steps.add(ApprovalStep.builder().application(application)
                    .sequenceNumber(index + 1).reviewerCommittee(committees.get(index)).build());
        }
        steps.get(0).activate(firstReviewer, now);
        // Initialize/synchronize the inverse collection before inserting new children.
        application.getApprovalSteps().addAll(steps);
        application.submitForReview(now);
        // Save the owning child side explicitly: the application has no cascade for steps.
        List<ApprovalStep> savedSteps = approvalStepRepository.saveAll(steps);
        applicationRepository.saveAndFlush(application);
        auditLogRepository.saveAllAndFlush(List.of(
                audit(application, null, applicant, ApprovalAuditAction.APPLICATION_SUBMITTED, now,
                        "Submitted with verified documents " + requiredDocuments.stream()
                                .map(d -> d.getDocumentType() + "#" + d.getId() + "v" + d.getDocumentVersion())
                                .toList() + "; approval committees " + committees.stream().map(Committee::getId).toList()),
                audit(application, savedSteps.get(0), applicant, ApprovalAuditAction.STEP_ACTIVATED, now,
                        "First stage assigned to reviewer " + firstReviewer.getId()
                                + "; due " + savedSteps.get(0).getDueAt())
        ));
        return new RegistrationSubmissionResponse(application.getId(), application.getApplicationNumber(),
                application.getStatus(), application.getSubmittedAt(), requiredTypes,
                savedSteps.stream().map(this::toResponse).toList());
    }

    @Override
    public List<ApprovalStepResponse> getApprovalSteps(String username, Long applicationId) {
        AppUser applicant = findEnabledApplicant(username);
        applicationRepository.findByIdAndApplicant_Id(applicationId, applicant.getId())
                .orElseThrow(this::applicationNotFound);
        return approvalStepRepository.findAllByApplication_IdOrderBySequenceNumberAsc(applicationId)
                .stream().map(this::toResponse).toList();
    }

    private void requireAvailableTarget(RegistrationApplication application) {
        CommitteePosition position = application.getRequestedPosition();
        Committee committee = position == null ? null : position.getCommittee();
        if (position == null || position.getId() == null || position.getPositionType() == null
                || committee == null || committee.getCommitteeType() == null
                || committee.getOrganizationUnit() == null
                || !Objects.equals(committee.getOrganizationUnit().getId(), application.getSelectedShakha().getId())
                || !committee.isOrganizationTypeValid() || !position.isTemplateValid()
                || !position.isResponsibilityValid()) {
            throw conflict("INVALID_POSITION", "The requested position and its organization scope must be valid.");
        }
        if (!position.isActive() || !Boolean.TRUE.equals(committee.getActive())) {
            throw conflict("INACTIVE_POSITION", "The committee and requested position must be active.");
        }
        if (position.getResponsibilityUnit() != null) {
            activeAncestry(position.getResponsibilityUnit());
        }
        if (positionRepository.countActiveAssignmentsForPositionId(position.getId()) > 0) {
            throw conflict("POSITION_OCCUPIED", "The requested position already has an active appointment.");
        }
    }

    private List<ApplicationDocument> requireVerifiedDocuments(Long applicationId, List<DocumentType> requiredTypes) {
        Map<DocumentType, ApplicationDocument> latest = new EnumMap<>(DocumentType.class);
        for (ApplicationDocument document : documentRepository.findLatestVersionsForApplication(applicationId)) {
            latest.put(document.getDocumentType(), document);
        }
        List<ApplicationDocument> required = new ArrayList<>();
        for (DocumentType type : requiredTypes) {
            ApplicationDocument document = latest.get(type);
            if (document == null) {
                throw conflict("REQUIRED_DOCUMENT_MISSING", "Upload the required document: " + type);
            }
            if (document.getStatus() != DocumentStatus.VERIFIED) {
                throw conflict("REQUIRED_DOCUMENT_NOT_VERIFIED",
                        "The latest version of " + type + " must be VERIFIED before submission.");
            }
            required.add(document);
        }
        return required;
    }

    private List<Committee> buildCommitteeChain(RegistrationApplication application, List<OrganizationUnit> ancestry) {
        List<Committee> committees = new ArrayList<>();
        // A PRESIDENT request goes to its parent head; the requested seat itself is vacant.
        int start = application.getRequestedPosition().getPositionType() == CommitteePositionType.PRESIDENT ? 1 : 0;
        for (int index = start; index < ancestry.size(); index++) {
            OrganizationUnit unit = ancestry.get(index);
            if (unit.getType() == OrganizationType.PARTY) {
                continue; // PARTY has no committee type in this model.
            }
            Committee committee = committeeRepository.findByOrganizationUnitId(unit.getId()).orElseThrow(() ->
                    conflict("APPROVAL_COMMITTEE_MISSING", "Create the approval committee for organization unit " + unit.getId()));
            if (!Boolean.TRUE.equals(committee.getActive()) || committee.getCommitteeType() == null
                    || committee.getCommitteeType().getOrganizationType() != unit.getType()) {
                throw conflict("INVALID_APPROVAL_COMMITTEE", "Approval committee " + committee.getId() + " must be active and match its unit.");
            }
            committees.add(committee);
        }
        if (committees.isEmpty()) {
            throw conflict("APPROVAL_CHAIN_UNAVAILABLE",
                    "No parent approval committee exists. State PRESIDENT appointments require the separate bootstrap/admin flow.");
        }
        return committees;
    }

    private AppUser resolveFirstReviewer(Committee committee, AppUser applicant) {
        List<AdvocateAssignment> appointments = assignmentRepository.findCurrentPresidentsForCommittee(
                committee.getId(), CommitteePositionType.PRESIDENT, LocalDate.now(ApprovalPolicy.BUSINESS_ZONE));
        if (appointments.isEmpty()) {
            throw conflict("REVIEWER_MISSING", "Appoint an enabled current PRESIDENT for committee " + committee.getId() + " before submitting.");
        }
        if (appointments.size() != 1) {
            throw conflict("AMBIGUOUS_REVIEWER", "Committee " + committee.getId() + " has more than one eligible president appointment.");
        }
        AppUser reviewer = appointments.get(0).getAdvocate();
        if (reviewer == null || !reviewer.isEnabled()) {
            throw conflict("REVIEWER_MISSING", "The first committee head must be an enabled account.");
        }
        if (Objects.equals(reviewer.getId(), applicant.getId())) {
            throw conflict("REVIEWER_CONFLICT", "The applicant cannot be the head deciding their own application.");
        }
        return reviewer;
    }

    private List<OrganizationUnit> activeAncestry(OrganizationUnit selected) {
        List<OrganizationUnit> units = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        OrganizationUnit current = selected;
        while (current != null) {
            if (current.getId() == null || !visited.add(current.getId())
                    || current.getType() == null || !Boolean.TRUE.equals(current.getActive())) {
                throw conflict("INVALID_ORGANIZATION", "The entire organization ancestry must be active and valid.");
            }
            OrganizationUnit parent = current.getParent();
            if (!current.getType().canHaveParent(parent == null ? null : parent.getType())) {
                throw conflict("INVALID_ORGANIZATION", "The organization parent types are invalid.");
            }
            units.add(current);
            current = parent;
        }
        if (units.isEmpty()) {
            throw conflict("INVALID_ORGANIZATION", "The selected organization unit is missing.");
        }
        return units;
    }

    private AppUser findEnabledApplicant(String username) {
        AppUser user = userRepository.findByUsername(username).orElseThrow(() ->
                new RegistrationException(HttpStatus.UNAUTHORIZED, "APPLICANT_NOT_AUTHENTICATED", "The username has no application account."));
        if (!user.isEnabled()) {
            throw new RegistrationException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "Your account is disabled.");
        }
        return user;
    }

    private ApprovalStepResponse toResponse(ApprovalStep step) {
        Committee committee = step.getReviewerCommittee();
        OrganizationUnit unit = committee.getOrganizationUnit();
        AppUser reviewer = step.getAssignedReviewer();
        AppUser decidedBy = step.getDecidedBy();
        AppUser escalatedTo = step.getEscalatedTo();
        return new ApprovalStepResponse(step.getId(), step.getSequenceNumber(), committee.getId(), committee.getName(),
                unit.getId(), unit.getName(), reviewer == null ? null : reviewer.getId(),
                reviewer == null ? null : reviewer.getFullName(), step.getStatus(), step.getAssignedAt(), step.getDueAt(),
                decidedBy == null ? null : decidedBy.getId(), step.getDecidedAt(), step.getDecisionComment(),
                escalatedTo == null ? null : escalatedTo.getId(), step.getEscalatedAt());
    }

    private ApprovalAuditLog audit(RegistrationApplication application, ApprovalStep step, AppUser actor,
                                    ApprovalAuditAction action, Instant at, String note) {
        return ApprovalAuditLog.builder().application(application).approvalStep(step).actor(actor)
                .action(action).eventKey(UUID.randomUUID().toString()).eventAt(at).note(note).build();
    }

    private RegistrationException applicationNotFound() {
        return new RegistrationException(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "Registration application not found.");
    }

    private RegistrationException conflict(String code, String message) {
        return new RegistrationException(HttpStatus.CONFLICT, code, message);
    }
}

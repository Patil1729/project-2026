package com.party.registration.approval.service;

import com.party.committee.entity.AdvocateAssignment;
import com.party.committee.entity.Committee;
import com.party.committee.entity.CommitteePosition;
import com.party.committee.entity.CommitteePositionType;
import com.party.committee.entity.CommitteeType;
import com.party.committee.repository.AdvocateAssignmentRepository;
import com.party.committee.repository.CommitteePositionRepository;
import com.party.committee.repository.CommitteeRepository;
import com.party.organization.entity.OrganizationType;
import com.party.organization.entity.OrganizationUnit;
import com.party.organization.repository.OrganizationUnitRepository;
import com.party.registration.approval.policy.ApprovalPolicy;
import com.party.registration.approval.dto.ApplicationApprovalRequest;
import com.party.registration.approval.dto.ApplicationRejectionRequest;
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
import com.party.user.entity.UserProfile;
import com.party.user.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationReviewServiceImplTest {
    @Mock RegistrationApplicationRepository applications;
    @Mock RegistrationApplicantRepository users;
    @Mock ApprovalStepRepository steps;
    @Mock ApprovalAuditLogRepository audits;
    @Mock AdvocateAssignmentRepository assignments;
    @Mock CommitteeRepository committees;
    @Mock CommitteePositionRepository positions;
    @Mock OrganizationUnitRepository organizations;
    @Mock RoleRepository roles;

    private ApplicationReviewServiceImpl service;
    private AppUser applicant;
    private AppUser firstHead;
    private AppUser secondHead;
    private OrganizationUnit party;
    private OrganizationUnit state;
    private OrganizationUnit division;
    private Committee stateCommittee;
    private Committee divisionCommittee;
    private CommitteePosition position;
    private RegistrationApplication application;
    private List<ApprovalStep> chain;
    private ApprovalStep first;
    private ApprovalStep second;
    private Role advocate;
    private Instant assignedAt;

    @BeforeEach
    void setUp() {
        service = new ApplicationReviewServiceImpl(applications, users, steps, audits,
                assignments, committees, positions, organizations, roles);
        applicant = AppUser.builder().id(1L).username("applicant").fullName("Shubham Patil").enabled(true)
                .profile(UserProfile.builder().id(1L).build()).build();
        applicant.getRoles().add(Role.builder().id(1L).name(RoleType.APPLICANT).build());
        firstHead = AppUser.builder().id(2L).username("division.head").fullName("Division Head").enabled(true).build();
        secondHead = AppUser.builder().id(3L).username("state.head").fullName("State Head").enabled(true).build();
        party = OrganizationUnit.builder().id(1L).type(OrganizationType.PARTY).build();
        state = OrganizationUnit.builder().id(2L).name("Maharashtra").type(OrganizationType.STATE).parent(party).build();
        division = OrganizationUnit.builder().id(3L).name("Kokan").type(OrganizationType.DIVISION).parent(state).build();
        stateCommittee = Committee.builder().id(11L).name("State Committee").committeeType(CommitteeType.STATE_COMMITTEE)
                .organizationUnit(state).build();
        divisionCommittee = Committee.builder().id(12L).name("Division Committee").committeeType(CommitteeType.DIVISIONAL_COMMITTEE)
                .organizationUnit(division).build();
        position = CommitteePosition.builder().id(20L).committee(divisionCommittee)
                .positionType(CommitteePositionType.MEMBER).positionNumber(1).build();
        application = RegistrationApplication.builder().id(10L).applicationNumber("REG-REVIEW-TEST")
                .applicant(applicant).selectedShakha(division).requestedPosition(position).build();
        assignedAt = Instant.now().minusSeconds(14 * 86400);
        application.submitForReview(assignedAt.minusSeconds(60));
        first = ApprovalStep.builder().id(100L).application(application).sequenceNumber(1).reviewerCommittee(divisionCommittee).build();
        second = ApprovalStep.builder().id(101L).application(application).sequenceNumber(2).reviewerCommittee(stateCommittee).build();
        first.activate(firstHead, assignedAt);
        chain = new ArrayList<>(List.of(first, second));
        application.getApprovalSteps().addAll(chain);
        advocate = Role.builder().id(9L).name(RoleType.ADVOCATE).build();
    }

    @Test
    void intermediateApprovalActivatesOnlyNextStageAndGrantsNothing() {
        ready(firstHead, first);
        var result = service.approve(firstHead.getUsername(), 10L, 100L, new ApplicationApprovalRequest("  Checked  "));
        assertEquals(ApprovalStatus.APPROVED, first.getStatus());
        assertEquals("Checked", first.getDecisionComment());
        assertEquals(ApprovalStatus.PENDING, second.getStatus());
        assertSame(secondHead, second.getAssignedReviewer());
        assertEquals(ApprovalPolicy.deadlineFor(second.getAssignedAt()), second.getDueAt());
        assertEquals(RegistrationStatus.UNDER_REVIEW, result.application().getStatus());
        assertNull(result.assignmentId());
        assertEquals(Set.of(RoleType.APPLICANT), roleNames());
        verify(assignments, never()).saveAndFlush(any());
        verifyNoInteractions(roles);
        assertEvents(ApprovalAuditAction.STEP_APPROVED, ApprovalAuditAction.STEP_ACTIVATED);
        var order = inOrder(users, applications, committees, organizations, positions);
        order.verify(users).findByIdForUpdate(1L);
        order.verify(applications).findByIdForApprovalUpdate(10L);
        order.verify(committees).findByIdForUpdate(11L);
        order.verify(committees).findByIdForUpdate(12L);
        order.verify(organizations).findByIdForUpdate(3L);
        order.verify(positions).findByIdForUpdate(20L);
    }

    @Test
    void missingNextPresidentLeavesCurrentStageOpen() {
        ready(firstHead, first);
        when(assignments.findCurrentPresidentsForCommittee(eq(11L), any(), any())).thenReturn(List.of());
        failure(HttpStatus.CONFLICT, "REVIEWER_MISSING", this::approveFirst);
        assertUnchangedOpenChain();
    }

    @Test
    void ambiguousNextPresidentDoesNotSkipTheStage() {
        ready(firstHead, first);
        when(assignments.findCurrentPresidentsForCommittee(eq(11L), any(), any()))
                .thenReturn(List.of(appointment(secondHead), appointment(firstHead)));
        failure(HttpStatus.CONFLICT, "AMBIGUOUS_REVIEWER", this::approveFirst);
        assertUnchangedOpenChain();
    }

    @Test
    void applicantCannotBecomeTheirOwnLaterReviewer() {
        ready(firstHead, first);
        when(assignments.findCurrentPresidentsForCommittee(eq(11L), any(), any())).thenReturn(List.of(appointment(applicant)));
        failure(HttpStatus.CONFLICT, "REVIEWER_CONFLICT", this::approveFirst);
        assertUnchangedOpenChain();
    }

    @Test
    void disabledNextHeadIsNotActivated() {
        ready(firstHead, first);
        secondHead.setEnabled(false);
        failure(HttpStatus.CONFLICT, "REVIEWER_MISSING", this::approveFirst);
        assertUnchangedOpenChain();
    }

    @Test
    void formerPresidentCannotDecideAnAssignedStage() {
        ready(firstHead, first);
        when(assignments.findCurrentPresidentsForCommittee(eq(12L), any(), any())).thenReturn(List.of(appointment(secondHead)));
        failure(HttpStatus.FORBIDDEN, "REVIEWER_NO_LONGER_ELIGIBLE", this::approveFirst);
        assertUnchangedOpenChain();
    }

    @Test
    void endedPresidentCannotDecideAnAssignedStage() {
        ready(firstHead, first);
        when(assignments.findCurrentPresidentsForCommittee(eq(12L), any(), any())).thenReturn(List.of());
        failure(HttpStatus.FORBIDDEN, "REVIEWER_NO_LONGER_ELIGIBLE", this::approveFirst);
        assertUnchangedOpenChain();
    }

    @Test
    void ambiguousCurrentPresidentBlocksDecision() {
        ready(firstHead, first);
        when(assignments.findCurrentPresidentsForCommittee(eq(12L), any(), any()))
                .thenReturn(List.of(appointment(firstHead), appointment(secondHead)));
        failure(HttpStatus.CONFLICT, "AMBIGUOUS_REVIEWER", this::approveFirst);
        assertUnchangedOpenChain();
    }

    @Test
    void unassignedReviewerCannotLockOrMutateTheApplication() {
        when(users.findByUsername(secondHead.getUsername())).thenReturn(Optional.of(secondHead));
        failure(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", () -> service.approve(secondHead.getUsername(), 10L, 100L,
                new ApplicationApprovalRequest(null)));
        verify(users, never()).findByIdForUpdate(anyLong());
        verify(applications, never()).findByIdForApprovalUpdate(anyLong());
        verifyNoInteractions(steps, audits, roles);
    }

    @Test
    void escalationRecipientCannotReplaceTheRequiredHead() {
        assignedAt = Instant.now().minusSeconds(14 * 86400);
        first = ApprovalStep.builder().id(100L).application(application).sequenceNumber(1).reviewerCommittee(divisionCommittee).build();
        first.activate(firstHead, assignedAt);
        first.escalateTo(secondHead, Instant.now());
        when(users.findByUsername(secondHead.getUsername())).thenReturn(Optional.of(secondHead));
        failure(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", () -> service.approve(secondHead.getUsername(), 10L, 100L,
                new ApplicationApprovalRequest(null)));
        assertEquals(ApprovalStatus.OVERDUE, first.getStatus());
        assertNull(first.getDecidedAt());
    }

    @Test
    void unknownReviewerIsUnauthorized() {
        failure(HttpStatus.UNAUTHORIZED, "REVIEWER_NOT_AUTHENTICATED", this::approveFirst);
        verifyNoInteractions(applications, steps, assignments, audits);
    }

    @Test
    void disabledReviewerIsForbidden() {
        firstHead.setEnabled(false);
        when(users.findByUsername(firstHead.getUsername())).thenReturn(Optional.of(firstHead));
        failure(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", this::approveFirst);
        verifyNoInteractions(applications, steps, assignments, audits);
    }

    @Test
    void corruptedSelfAssignmentCannotBeUsedToApprove() {
        firstHead = applicant;
        ready(firstHead, first);
        failure(HttpStatus.FORBIDDEN, "SELF_REVIEW_NOT_ALLOWED", this::approveFirst);
        verifyNoInteractions(audits);
    }

    @Test
    void missingRequiredAncestorStageBlocksApproval() {
        chain.remove(second);
        ready(firstHead, first);
        failure(HttpStatus.CONFLICT, "INVALID_APPROVAL_CHAIN", this::approveFirst);
        assertEquals(ApprovalStatus.PENDING, first.getStatus());
        verifyNoInteractions(audits);
    }

    @Test
    void twoSimultaneouslyOpenStagesAreRejected() {
        second.activate(secondHead, assignedAt);
        ready(firstHead, first);
        failure(HttpStatus.CONFLICT, "INVALID_APPROVAL_CHAIN", this::approveFirst);
        verifyNoInteractions(audits);
    }

    @Test
    void tamperedSequenceIsRejected() {
        first.setSequenceNumber(2);
        ready(firstHead, first);
        failure(HttpStatus.CONFLICT, "INVALID_APPROVAL_CHAIN", this::approveFirst);
        verifyNoInteractions(audits);
    }

    @Test
    void inactiveAncestryBlocksApproval() {
        state.setActive(false);
        ready(firstHead, first);
        failure(HttpStatus.CONFLICT, "INVALID_ORGANIZATION", this::approveFirst);
        assertUnchangedOpenChain();
    }

    @Test
    void inactiveTargetSeatBlocksApproval() {
        position.setActive(false);
        ready(firstHead, first);
        failure(HttpStatus.CONFLICT, "INVALID_POSITION", this::approveFirst);
        assertUnchangedOpenChain();
    }

    @Test
    void alreadyApprovedEarlierStageCannotBeRepeated() {
        prepareLast();
        ready(firstHead, first);
        failure(HttpStatus.CONFLICT, "STEP_NOT_ACTIVE", this::approveFirst);
        assertEquals(ApprovalStatus.PENDING, second.getStatus());
        verifyNoInteractions(audits);
    }

    @Test
    void requiredHeadCanStillDecideAnOverdueStage() {
        first = ApprovalStep.builder().id(100L).application(application).sequenceNumber(1).reviewerCommittee(divisionCommittee).build();
        first.activate(firstHead, Instant.now().minusSeconds(14 * 86400));
        first.markOverdue(Instant.now());
        chain.set(0, first);
        application.getApprovalSteps().set(0, first);
        ready(firstHead, first);
        approveFirst();
        assertEquals(ApprovalStatus.APPROVED, first.getStatus());
        assertEquals(ApprovalStatus.PENDING, second.getStatus());
    }

    @Test
    void rejectionStopsApplicationWithoutActivatingLaterStagesOrGrantingRoles() {
        ready(firstHead, first);
        var response = service.reject(firstHead.getUsername(), 10L, 100L, new ApplicationRejectionRequest("  Details do not match  "));
        assertEquals(RegistrationStatus.REJECTED, response.application().getStatus());
        assertEquals(ApprovalStatus.REJECTED, first.getStatus());
        assertEquals("Details do not match", first.getDecisionComment());
        assertEquals("Details do not match", application.getRejectionReason());
        assertEquals(ApprovalStatus.WAITING, second.getStatus());
        assertNull(second.getAssignedAt());
        assertEquals(Set.of(RoleType.APPLICANT), roleNames());
        verify(assignments, never()).saveAndFlush(any());
        verifyNoInteractions(roles);
        assertEvents(ApprovalAuditAction.STEP_REJECTED, ApprovalAuditAction.APPLICATION_REJECTED);
    }

    @Test
    void blankRejectionReasonIsRejectedBeforeDatabaseAccess() {
        failure(HttpStatus.BAD_REQUEST, "REJECTION_REASON_REQUIRED", () -> service.reject(firstHead.getUsername(), 10L, 100L,
                new ApplicationRejectionRequest("  ")));
        verifyNoInteractions(users, applications, audits);
    }

    @Test
    void oversizedDecisionTextIsRejectedBeforeDatabaseAccess() {
        failure(HttpStatus.BAD_REQUEST, "INVALID_REVIEW_REQUEST", () -> service.approve(firstHead.getUsername(), 10L, 100L,
                new ApplicationApprovalRequest("x".repeat(2001))));
        verifyNoInteractions(users, applications, audits);
    }

    @Test
    void terminalApplicationCannotReceiveAnotherDecision() {
        application.reject(Instant.now(), "Already rejected");
        ready(firstHead, first);
        failure(HttpStatus.CONFLICT, "APPLICATION_NOT_UNDER_REVIEW", this::approveFirst);
        verifyNoInteractions(audits);
    }

    @Test
    void finalApprovalCreatesOneLinkedAppointmentAndAddsOnlyAdvocate() {
        prepareLast();
        ready(secondHead, second);
        var response = approveLast();
        assertEquals(RegistrationStatus.APPROVED, response.application().getStatus());
        assertEquals(ApprovalStatus.APPROVED, second.getStatus());
        assertEquals(500L, response.assignmentId());
        assertEquals(Set.of(RoleType.APPLICANT, RoleType.ADVOCATE), roleNames());
        assertTrue(application.isRequiredApprovalsValid());
        ArgumentCaptor<AdvocateAssignment> saved = ArgumentCaptor.forClass(AdvocateAssignment.class);
        verify(assignments, times(1)).saveAndFlush(saved.capture());
        assertSame(application, saved.getValue().getRegistrationApplication());
        assertSame(applicant, saved.getValue().getAdvocate());
        assertSame(position, saved.getValue().getCommitteePosition());
        assertTrue(saved.getValue().isSourceApplicationValid());
        assertEvents(ApprovalAuditAction.STEP_APPROVED, ApprovalAuditAction.APPLICATION_APPROVED);
    }

    @Test
    void existingAdvocateMembershipIsPreservedWithoutDuplication() {
        prepareLast();
        applicant.getRoles().add(advocate);
        ready(secondHead, second);
        approveLast();
        assertEquals(2, applicant.getRoles().size());
        assertEquals(Set.of(RoleType.APPLICANT, RoleType.ADVOCATE), roleNames());
    }

    @Test
    void newlyOccupiedSeatBlocksFinalApprovalBeforeMutation() {
        prepareLast();
        ready(secondHead, second);
        when(positions.countActiveAssignmentsForPositionId(20L)).thenReturn(1L);
        failure(HttpStatus.CONFLICT, "POSITION_OCCUPIED", this::approveLast);
        assertPendingFinalWithoutAppointment();
    }

    @Test
    void missingAdvocateRoleBlocksFinalApprovalBeforeMutation() {
        prepareLast();
        ready(secondHead, second);
        when(roles.findByName(RoleType.ADVOCATE)).thenReturn(Optional.empty());
        failure(HttpStatus.CONFLICT, "ADVOCATE_ROLE_MISSING", this::approveLast);
        assertPendingFinalWithoutAppointment();
    }

    @Test
    void existingApplicationAppointmentCannotBeDuplicated() {
        prepareLast();
        ready(secondHead, second);
        when(assignments.existsByRegistrationApplication_Id(10L)).thenReturn(true);
        failure(HttpStatus.CONFLICT, "APPOINTMENT_EXISTS", this::approveLast);
        assertPendingFinalWithoutAppointment();
    }

    @Test
    void disabledApplicantCannotReceiveFinalAppointment() {
        prepareLast();
        applicant.setEnabled(false);
        ready(secondHead, second);
        failure(HttpStatus.CONFLICT, "APPLICANT_NOT_ELIGIBLE", this::approveLast);
        assertPendingFinalWithoutAppointment();
    }

    @Test
    void missingProfilePreventsFinalAppointment() {
        prepareLast();
        applicant.setProfile(null);
        ready(secondHead, second);
        failure(HttpStatus.CONFLICT, "APPLICANT_NOT_ELIGIBLE", this::approveLast);
        assertPendingFinalWithoutAppointment();
    }

    @Test
    void uniqueSeatConstraintIsReportedAsConflictAndNoAuditIsWritten() {
        prepareLast();
        ready(secondHead, second);
        when(assignments.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique seat"));
        failure(HttpStatus.CONFLICT, "APPOINTMENT_CONFLICT", this::approveLast);
        verifyNoInteractions(audits);
        // Mocks do not roll back mutated objects. Real rollback must be checked with MySQL.
    }

    @Test
    void auditInsertFailureIsPropagatedToTheTransaction() {
        ready(firstHead, first);
        when(audits.saveAllAndFlush(anyList())).thenThrow(new DataIntegrityViolationException("audit failure"));
        assertThrows(DataIntegrityViolationException.class, this::approveFirst);
        // Transaction rollback is an integration test, not proved by this mock.
    }

    @Test
    void presidentRequestUsesParentCommitteeAndCanFinishThere() {
        position.setPositionType(CommitteePositionType.PRESIDENT);
        chain.clear();
        second = ApprovalStep.builder().id(101L).application(application).sequenceNumber(1).reviewerCommittee(stateCommittee).build();
        second.activate(secondHead, assignedAt);
        chain.add(second);
        application.getApprovalSteps().clear();
        application.getApprovalSteps().add(second);
        ready(secondHead, second);
        assertEquals(RegistrationStatus.APPROVED, approveLast().application().getStatus());
        assertEquals(1, application.getApprovalSteps().size());
    }

    @Test
    void assignedCurrentHeadCanReadTheReview() {
        when(users.findByUsername(firstHead.getUsername())).thenReturn(Optional.of(firstHead));
        when(applications.findById(10L)).thenReturn(Optional.of(application));
        when(steps.findAllByApplication_IdOrderBySequenceNumberAsc(10L)).thenReturn(chain);
        when(assignments.findCurrentPresidentsForCommittee(eq(12L), any(), any())).thenReturn(List.of(appointment(firstHead)));
        assertEquals(2, service.getReview(firstHead.getUsername(), 10L).approvalSteps().size());
        verify(applications, never()).findByIdForApprovalUpdate(anyLong());
    }

    @Test
    void unassignedHeadCannotReadTheApplicationDetails() {
        when(users.findByUsername(secondHead.getUsername())).thenReturn(Optional.of(secondHead));
        when(applications.findById(10L)).thenReturn(Optional.of(application));
        when(steps.findAllByApplication_IdOrderBySequenceNumberAsc(10L)).thenReturn(chain);
        failure(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", () -> service.getReview(secondHead.getUsername(), 10L));
        verifyNoInteractions(assignments);
    }

    @Test
    void aValidLaterAssignmentRemainsReadableAfterAnEarlierPresidencyEnds() {
        first.approve(firstHead, assignedAt.plusSeconds(10), "Approved division stage");
        second.activate(firstHead, assignedAt.plusSeconds(10));
        when(users.findByUsername(firstHead.getUsername())).thenReturn(Optional.of(firstHead));
        when(applications.findById(10L)).thenReturn(Optional.of(application));
        when(steps.findAllByApplication_IdOrderBySequenceNumberAsc(10L)).thenReturn(chain);
        when(assignments.findCurrentPresidentsForCommittee(eq(12L), any(), any())).thenReturn(List.of());
        when(assignments.findCurrentPresidentsForCommittee(eq(11L), any(), any())).thenReturn(List.of(appointment(firstHead)));
        assertEquals(2, service.getReview(firstHead.getUsername(), 10L).approvalSteps().size());
    }

    @Test
    void inboxPreservesPagingAndReportsElapsedDeadlineWithoutChangingStatus() {
        when(users.findByUsername(firstHead.getUsername())).thenReturn(Optional.of(firstHead));
        when(steps.findPendingInbox(eq(2L), eq(RegistrationStatus.UNDER_REVIEW), anyList(),
                eq(CommitteePositionType.PRESIDENT), any(), eq(PageRequest.of(0, 1))))
                .thenReturn(new SliceImpl<>(List.of(first), PageRequest.of(0, 1), true));
        var result = service.getPending(firstHead.getUsername(), 0, 1);
        assertTrue(result.hasNext());
        assertEquals(1, result.items().size());
        assertEquals(100L, result.items().get(0).step().id());
        assertTrue(result.items().get(0).overdue());
        assertEquals(ApprovalStatus.PENDING, first.getStatus());
        verifyNoInteractions(audits);
    }

    @Test
    void invalidPaginationIsRejected() {
        when(users.findByUsername(firstHead.getUsername())).thenReturn(Optional.of(firstHead));
        failure(HttpStatus.BAD_REQUEST, "INVALID_PAGE", () -> service.getPending(firstHead.getUsername(), -1, 20));
        failure(HttpStatus.BAD_REQUEST, "INVALID_PAGE", () -> service.getPending(firstHead.getUsername(), 0, 101));
        verifyNoInteractions(steps);
    }

    @Test
    void seniorCanReadTheirEscalatedInboxWithoutBecomingAssignedHead() {
        first.escalateTo(secondHead, Instant.now());
        when(users.findByUsername(secondHead.getUsername())).thenReturn(Optional.of(secondHead));
        when(steps.findEscalatedInbox(eq(3L), eq(RegistrationStatus.UNDER_REVIEW), anyList(),
                eq(CommitteePositionType.PRESIDENT), any(), eq(PageRequest.of(0, 20))))
                .thenReturn(new SliceImpl<>(List.of(first), PageRequest.of(0, 20), false));
        var result = service.getEscalated(secondHead.getUsername(), 0, 20);
        assertEquals(1, result.items().size());
        assertEquals(2L, result.items().get(0).step().assignedReviewerId());
        assertEquals(3L, result.items().get(0).step().escalatedToId());
        verifyNoInteractions(audits);
    }

    @Test
    void currentEscalationSeniorCanReadFullReviewButCannotReplaceOriginalHead() {
        first.escalateTo(secondHead, Instant.now());
        when(users.findByUsername(secondHead.getUsername())).thenReturn(Optional.of(secondHead));
        when(applications.findById(10L)).thenReturn(Optional.of(application));
        when(steps.findAllByApplication_IdOrderBySequenceNumberAsc(10L)).thenReturn(chain);
        when(assignments.findCurrentPresidentsForCommittee(eq(11L), any(), any())).thenReturn(List.of(appointment(secondHead)));
        assertEquals(2, service.getReview(secondHead.getUsername(), 10L).approvalSteps().size());
        failure(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", () -> service.approve(secondHead.getUsername(), 10L, 100L,
                new ApplicationApprovalRequest(null)));
        assertNull(first.getDecidedAt());
    }

    @Test
    void formerSeniorCannotReadUsingOldEscalation() {
        first.escalateTo(secondHead, Instant.now());
        when(users.findByUsername(secondHead.getUsername())).thenReturn(Optional.of(secondHead));
        when(applications.findById(10L)).thenReturn(Optional.of(application));
        when(steps.findAllByApplication_IdOrderBySequenceNumberAsc(10L)).thenReturn(chain);
        when(assignments.findCurrentPresidentsForCommittee(eq(11L), any(), any())).thenReturn(List.of(appointment(firstHead)));
        failure(HttpStatus.FORBIDDEN, "REVIEWER_NO_LONGER_ELIGIBLE", () -> service.getReview(secondHead.getUsername(), 10L));
    }

    @Test
    void terminalApplicationIsNotReadableThroughEscalationAlone() {
        first.escalateTo(secondHead, Instant.now());
        first.reject(firstHead, Instant.now(), "Rejected");application.reject(Instant.now(), "Rejected");
        when(users.findByUsername(secondHead.getUsername())).thenReturn(Optional.of(secondHead));
        when(applications.findById(10L)).thenReturn(Optional.of(application));
        when(steps.findAllByApplication_IdOrderBySequenceNumberAsc(10L)).thenReturn(chain);
        failure(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", () -> service.getReview(secondHead.getUsername(), 10L));
    }

    @Test
    void escalationInboxEnforcesPaginationBounds() {
        when(users.findByUsername(secondHead.getUsername())).thenReturn(Optional.of(secondHead));
        failure(HttpStatus.BAD_REQUEST, "INVALID_PAGE", () -> service.getEscalated(secondHead.getUsername(), 0, 101));
        verifyNoInteractions(steps);
    }

    private void ready(AppUser actor, ApprovalStep requested) {
        // Shared fixtures are lenient because guard tests intentionally return before later queries.
        lenient().when(users.findByUsername(actor.getUsername())).thenReturn(Optional.of(actor));
        lenient().when(applications.findApplicantIdForAssignedStep(10L, requested.getId(), actor.getId())).thenReturn(Optional.of(applicant.getId()));
        lenient().when(users.findByIdForUpdate(applicant.getId())).thenReturn(Optional.of(applicant));
        lenient().when(applications.findByIdForApprovalUpdate(10L)).thenReturn(Optional.of(application));
        lenient().when(steps.findForDecisionByApplicationId(10L)).thenReturn(chain);
        lenient().when(positions.findCommitteeIdByPositionId(20L)).thenReturn(Optional.of(12L));
        lenient().when(committees.findByIdForUpdate(11L)).thenReturn(Optional.of(stateCommittee));
        lenient().when(committees.findByIdForUpdate(12L)).thenReturn(Optional.of(divisionCommittee));
        for (OrganizationUnit unit : List.of(party, state, division)) {
            lenient().when(organizations.findByIdForUpdate(unit.getId())).thenReturn(Optional.of(unit));
        }
        lenient().when(positions.findByIdForUpdate(20L)).thenReturn(Optional.of(position));
        lenient().when(assignments.findCurrentPresidentsForCommittee(eq(12L), any(), any())).thenReturn(List.of(appointment(firstHead)));
        lenient().when(assignments.findCurrentPresidentsForCommittee(eq(11L), any(), any())).thenReturn(List.of(appointment(secondHead)));
        lenient().when(roles.findByName(RoleType.ADVOCATE)).thenReturn(Optional.of(advocate));
        lenient().when(assignments.saveAndFlush(any())).thenAnswer(call -> {
            AdvocateAssignment value = call.getArgument(0); value.setId(500L); return value;
        });
    }

    private void prepareLast() {
        first.approve(firstHead, assignedAt.plusSeconds(10), "Division approved");
        second.activate(secondHead, assignedAt.plusSeconds(10));
    }

    private Object approveFirst() { return service.approve(firstHead.getUsername(), 10L, 100L, new ApplicationApprovalRequest(null)); }
    private com.party.registration.approval.dto.ApplicationReviewResponse approveLast() {
        return service.approve(secondHead.getUsername(), 10L, 101L, new ApplicationApprovalRequest("Approved"));
    }
    private AdvocateAssignment appointment(AppUser user) { return AdvocateAssignment.builder().advocate(user).build(); }
    private Set<RoleType> roleNames() { return applicant.getRoles().stream().map(Role::getName).collect(java.util.stream.Collectors.toSet()); }

    private void assertUnchangedOpenChain() {
        assertEquals(ApprovalStatus.PENDING, first.getStatus());
        assertEquals(ApprovalStatus.WAITING, second.getStatus());
        assertEquals(RegistrationStatus.UNDER_REVIEW, application.getStatus());
        verify(applications, never()).saveAndFlush(any());
        verifyNoInteractions(audits);
    }

    private void assertPendingFinalWithoutAppointment() {
        assertEquals(ApprovalStatus.PENDING, second.getStatus());
        assertEquals(RegistrationStatus.UNDER_REVIEW, application.getStatus());
        assertEquals(Set.of(RoleType.APPLICANT), roleNames());
        verify(assignments, never()).saveAndFlush(any());
        verifyNoInteractions(audits);
    }

    private void assertEvents(ApprovalAuditAction... expected) {
        @SuppressWarnings("unchecked") ArgumentCaptor<List<ApprovalAuditLog>> capture = ArgumentCaptor.forClass(List.class);
        verify(audits).saveAllAndFlush(capture.capture());
        assertEquals(List.of(expected), capture.getValue().stream().map(ApprovalAuditLog::getAction).toList());
        assertTrue(capture.getValue().stream().allMatch(e -> e.getActor() != null && e.getEventKey() != null));
    }

    private void failure(HttpStatus status, String code, org.junit.jupiter.api.function.Executable action) {
        RegistrationException exception = assertThrows(RegistrationException.class, action);
        assertEquals(status, exception.getStatus());
        assertEquals(code, exception.getErrorCode());
    }
}

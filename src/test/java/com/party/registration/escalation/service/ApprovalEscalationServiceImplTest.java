package com.party.registration.escalation.service;

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
import com.party.registration.application.entity.RegistrationApplication;
import com.party.registration.application.entity.RegistrationStatus;
import com.party.registration.application.repository.RegistrationApplicationRepository;
import com.party.registration.escalation.dto.ApprovalEscalationCandidate;
import com.party.registration.approval.entity.ApprovalStatus;
import com.party.registration.approval.entity.ApprovalStep;
import com.party.registration.approval.repository.ApprovalStepRepository;
import com.party.registration.audit.entity.ApprovalAuditAction;
import com.party.registration.audit.entity.ApprovalAuditLog;
import com.party.registration.audit.repository.ApprovalAuditLogRepository;
import com.party.registration.common.exception.RegistrationException;
import com.party.user.entity.AppUser;
import com.party.user.entity.Role;
import com.party.user.entity.RoleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApprovalEscalationServiceImplTest {
    @Mock RegistrationApplicationRepository applications;
    @Mock ApprovalStepRepository steps;
    @Mock CommitteeRepository committees;
    @Mock OrganizationUnitRepository organizations;
    @Mock CommitteePositionRepository positions;
    @Mock AdvocateAssignmentRepository assignments;
    @Mock ApprovalAuditLogRepository audits;
    private ApprovalEscalationServiceImpl service;
    private RegistrationApplication application;
    private AppUser applicant, head, senior;
    private OrganizationUnit party, state, division;
    private Committee stateCommittee, divisionCommittee;
    private CommitteePosition position;
    private ApprovalStep first, second;
    private List<ApprovalStep> chain;
    private final Set<String> keys = new HashSet<>();
    private final List<ApprovalAuditLog> savedEvents = new ArrayList<>();
    private final Instant assigned = Instant.parse("2026-10-12T04:30:00Z");
    private final Instant cutoff = Instant.parse("2026-10-20T09:00:00Z");

    @BeforeEach
    void setUp() {
        service = new ApprovalEscalationServiceImpl(applications, steps, committees, organizations, positions, assignments, audits);
        applicant = AppUser.builder().id(1L).username("applicant").enabled(true).build();
        applicant.getRoles().add(Role.builder().id(1L).name(RoleType.APPLICANT).build());
        head = AppUser.builder().id(2L).username("division.head").enabled(true).build();
        senior = AppUser.builder().id(3L).username("state.head").enabled(true).build();
        party = OrganizationUnit.builder().id(1L).type(OrganizationType.PARTY).build();
        state = OrganizationUnit.builder().id(2L).type(OrganizationType.STATE).parent(party).build();
        division = OrganizationUnit.builder().id(3L).type(OrganizationType.DIVISION).parent(state).build();
        stateCommittee = Committee.builder().id(11L).committeeType(CommitteeType.STATE_COMMITTEE).organizationUnit(state).build();
        divisionCommittee = Committee.builder().id(12L).committeeType(CommitteeType.DIVISIONAL_COMMITTEE).organizationUnit(division).build();
        position = CommitteePosition.builder().id(20L).committee(divisionCommittee)
                .positionType(CommitteePositionType.MEMBER).positionNumber(1).build();
        application = RegistrationApplication.builder().id(10L).applicationNumber("REG-ESCALATION")
                .applicant(applicant).selectedShakha(division).requestedPosition(position).build();
        application.submitForReview(assigned.minusSeconds(60));
        first = ApprovalStep.builder().id(100L).application(application).sequenceNumber(1).reviewerCommittee(divisionCommittee).build();
        second = ApprovalStep.builder().id(101L).application(application).sequenceNumber(2).reviewerCommittee(stateCommittee).build();
        first.activate(head, assigned);
        chain = new ArrayList<>(List.of(first, second));
        application.getApprovalSteps().addAll(chain);
    }

    @Test
    void overdueStageEscalatesWithoutChangingAssignmentDeadlineApplicationOrNextStage() {
        ready();
        Instant due = first.getDueAt();
        var result = process();
        assertTrue(result.overdueMarked());
        assertTrue(result.escalated());
        assertEquals(ApprovalStatus.OVERDUE, first.getStatus());
        assertSame(head, first.getAssignedReviewer());
        assertSame(senior, first.getEscalatedTo());
        assertEquals(assigned, first.getAssignedAt());
        assertEquals(due, first.getDueAt());
        assertEquals(cutoff, first.getEscalatedAt());
        assertNull(first.getDecidedAt());
        assertEquals(ApprovalStatus.WAITING, second.getStatus());
        assertNull(second.getAssignedReviewer());
        assertEquals(RegistrationStatus.UNDER_REVIEW, application.getStatus());
        assertNull(application.getAssignment());
        assertEquals(List.of(RoleType.APPLICANT), applicant.getRoles().stream().map(Role::getName).toList());
        assertEquals(List.of(ApprovalAuditAction.STEP_OVERDUE, ApprovalAuditAction.STEP_ESCALATED), actions());
        assertTrue(savedEvents.stream().allMatch(e -> e.getActor() == null));
        verify(assignments, never()).saveAndFlush(any());
        var order = inOrder(applications, committees, organizations, positions);
        order.verify(applications).findByIdForApprovalUpdate(10L);
        order.verify(committees).findByIdForUpdate(11L);
        order.verify(committees).findByIdForUpdate(12L);
        order.verify(organizations).findByIdForUpdate(3L);
        order.verify(positions).findByIdForUpdate(20L);
    }

    @Test
    void alreadyProcessedCandidateDoesNotDuplicateAudits() {
        ready();process();
        assertTrue(process().skipped());
        assertEquals(2, savedEvents.size());
        verify(audits, times(1)).saveAllAndFlush(anyList());
    }

    @Test
    void missingSeniorMarksOverdueAndRecordsReasonOnce() {
        ready();missingHead();
        var firstRun = process();
        var retry = process();
        assertTrue(firstRun.missingRecorded());
        assertEquals("HEAD_MISSING", firstRun.missingReason());
        assertFalse(retry.missingRecorded());
        assertEquals(ApprovalStatus.OVERDUE, first.getStatus());
        assertNull(first.getEscalatedAt());
        assertEquals(List.of(ApprovalAuditAction.STEP_OVERDUE, ApprovalAuditAction.REVIEWER_MISSING), actions());
        verify(audits, times(1)).saveAllAndFlush(anyList());
    }

    @Test
    void missingSeniorIsRetriedAndCanLaterEscalate() {
        ready();missingHead();process();
        when(assignments.findCurrentPresidentsForCommittee(eq(11L), any(), any())).thenReturn(List.of(appointment(senior)));
        var result = process();
        assertFalse(result.overdueMarked());
        assertTrue(result.escalated());
        assertSame(senior, first.getEscalatedTo());
        assertEquals(List.of(ApprovalAuditAction.STEP_OVERDUE, ApprovalAuditAction.REVIEWER_MISSING,
                ApprovalAuditAction.STEP_ESCALATED), actions());
    }

    @Test
    void changedMissingReasonGetsItsOwnSingleAudit() {
        ready();missingHead();process();
        when(assignments.findCurrentPresidentsForCommittee(eq(11L), any(), any()))
                .thenReturn(List.of(appointment(head), appointment(senior)));
        assertEquals("HEAD_AMBIGUOUS", process().missingReason());
        assertFalse(process().missingRecorded());
        assertEquals(3, savedEvents.size());
    }

    @Test
    void ambiguousSeniorDoesNotChooseAnArbitraryAccount() {
        ready();
        when(assignments.findCurrentPresidentsForCommittee(eq(11L), any(), any()))
                .thenReturn(List.of(appointment(head), appointment(senior)));
        assertEquals("HEAD_AMBIGUOUS", process().missingReason());
        assertNull(first.getEscalatedTo());
    }

    @Test
    void applicantCannotReceiveTheirOwnEscalation() {
        ready();
        when(assignments.findCurrentPresidentsForCommittee(eq(11L), any(), any())).thenReturn(List.of(appointment(applicant)));
        assertEquals("APPLICANT_IS_HEAD", process().missingReason());
        assertNull(first.getEscalatedTo());
    }

    @Test
    void samePersonHeadingNextCommitteeIsNotEscalatedToThemselves() {
        ready();
        when(assignments.findCurrentPresidentsForCommittee(eq(11L), any(), any())).thenReturn(List.of(appointment(head)));
        assertEquals("ASSIGNED_REVIEWER_IS_HEAD", process().missingReason());
        assertNull(first.getEscalatedTo());
    }

    @Test
    void disabledSeniorIsNotSelected() {
        ready();senior.setEnabled(false);
        assertEquals("HEAD_DISABLED", process().missingReason());
        assertNull(first.getEscalatedTo());
    }

    @Test
    void highestStateStageRemainsOverdueWithoutInventingPartyCommittee() {
        first.approve(head, assigned.plusSeconds(1), null);
        second.activate(senior, assigned.plusSeconds(1));
        ready();
        var result = service.processCandidate(new ApprovalEscalationCandidate(101L, 10L), cutoff);
        assertEquals("NO_PARENT_STAGE", result.missingReason());
        assertEquals(ApprovalStatus.OVERDUE, second.getStatus());
        assertNull(second.getEscalatedTo());
        assertEquals(RegistrationStatus.UNDER_REVIEW, application.getStatus());
        verifyNoInteractions(assignments);
    }

    @Test
    void futureDeadlineIsSkipped() {
        ready();
        assertTrue(service.processCandidate(new ApprovalEscalationCandidate(100L, 10L), first.getDueAt().minusNanos(1)).skipped());
        assertEquals(ApprovalStatus.PENDING, first.getStatus());
        verifyNoInteractions(audits, assignments);
    }

    @Test
    void exactDeadlineIsOverdueAndUsesBusinessDateForHeadLookup() {
        ready();
        assertTrue(service.processCandidate(new ApprovalEscalationCandidate(100L, 10L), first.getDueAt()).escalated());
        verify(assignments).findCurrentPresidentsForCommittee(eq(11L), eq(CommitteePositionType.PRESIDENT), eq(LocalDate.of(2026, 10, 15)));
    }

    @Test
    void alreadyOverdueRowCanReceiveItsFirstEscalation() {
        first.markOverdue(cutoff);ready();
        var result = process();
        assertFalse(result.overdueMarked());
        assertTrue(result.escalated());
        assertEquals(2, savedEvents.size());
    }

    @Test
    void terminalApplicationIsSkippedUnderParentLock() {
        first.reject(head, cutoff.minusSeconds(1), "Rejected");
        application.reject(cutoff.minusSeconds(1), "Rejected");ready();
        assertTrue(process().skipped());
        verifyNoInteractions(steps, audits, assignments);
    }

    @Test
    void deletedApplicationIsSkipped() {
        assertTrue(process().skipped());
        verifyNoInteractions(steps, audits);
    }

    @Test
    void alreadyDecidedStageFromStaleCandidateIsSkipped() {
        first.approve(head, cutoff.minusSeconds(1), null);
        second.activate(senior, cutoff.minusSeconds(1));ready();
        assertTrue(process().skipped());
        verifyNoInteractions(audits, committees);
    }

    @Test
    void candidateStepMustBelongToLockedApplication() {
        ready();
        assertTrue(service.processCandidate(new ApprovalEscalationCandidate(999L, 10L), cutoff).skipped());
        verifyNoInteractions(audits, committees);
    }

    @Test
    void missingRequiredStageFailsBeforeMarkingOverdue() {
        chain.remove(second);ready();
        assertEquals("INVALID_APPROVAL_CHAIN", assertThrows(RegistrationException.class, this::process).getErrorCode());
        assertEquals(ApprovalStatus.PENDING, first.getStatus());
        verifyNoInteractions(audits);
    }

    @Test
    void inactiveAncestorFailsBeforeMarkingOverdue() {
        state.setActive(false);ready();
        assertEquals("INVALID_ORGANIZATION", assertThrows(RegistrationException.class, this::process).getErrorCode());
        assertEquals(ApprovalStatus.PENDING, first.getStatus());
    }

    @Test
    void invalidSeatFailsBeforeMarkingOverdue() {
        position.setActive(false);ready();
        assertEquals("INVALID_POSITION", assertThrows(RegistrationException.class, this::process).getErrorCode());
        assertEquals(ApprovalStatus.PENDING, first.getStatus());
    }

    @Test
    void auditInsertFailurePropagatesForTransactionRollback() {
        ready();
        when(audits.saveAllAndFlush(anyList())).thenThrow(new DataIntegrityViolationException("audit insert"));
        assertThrows(DataIntegrityViolationException.class, this::process);
        // Real rollback needs MySQL; mocks do not restore mutated Java objects.
    }

    @Test
    void inconsistentExistingEscalationAuditRequiresReconciliation() {
        ready();keys.add("approval-step:100:escalated");
        assertEquals("ESCALATION_AUDIT_STATE_MISMATCH", assertThrows(RegistrationException.class, this::process).getErrorCode());
        verify(steps, never()).saveAndFlush(any());
        verify(audits, never()).saveAllAndFlush(anyList());
    }

    private com.party.registration.escalation.dto.ApprovalEscalationOutcome process() {
        return service.processCandidate(new ApprovalEscalationCandidate(100L, 10L), cutoff);
    }
    private AdvocateAssignment appointment(AppUser user) { return AdvocateAssignment.builder().advocate(user).build(); }
    private void missingHead() { when(assignments.findCurrentPresidentsForCommittee(eq(11L), any(), any())).thenReturn(List.of()); }
    private List<ApprovalAuditAction> actions() { return savedEvents.stream().map(ApprovalAuditLog::getAction).toList(); }
    private void ready() {
        lenient().when(applications.findByIdForApprovalUpdate(10L)).thenReturn(Optional.of(application));
        lenient().when(steps.findForDecisionByApplicationId(10L)).thenReturn(chain);
        lenient().when(positions.findCommitteeIdByPositionId(20L)).thenReturn(Optional.of(12L));
        lenient().when(positions.findByIdForUpdate(20L)).thenReturn(Optional.of(position));
        lenient().when(committees.findByIdForUpdate(11L)).thenReturn(Optional.of(stateCommittee));
        lenient().when(committees.findByIdForUpdate(12L)).thenReturn(Optional.of(divisionCommittee));
        for (var unit : List.of(party, state, division)) lenient().when(organizations.findByIdForUpdate(unit.getId())).thenReturn(Optional.of(unit));
        lenient().when(assignments.findCurrentPresidentsForCommittee(eq(11L), any(), any())).thenReturn(List.of(appointment(senior)));
        lenient().when(audits.existsByEventKey(anyString())).thenAnswer(call -> keys.contains(call.getArgument(0)));
        lenient().when(audits.saveAllAndFlush(anyList())).thenAnswer(call -> {
            List<ApprovalAuditLog> events = call.getArgument(0);
            for (var event : events) { assertTrue(keys.add(event.getEventKey())); savedEvents.add(event); }
            return events;
        });
    }
}

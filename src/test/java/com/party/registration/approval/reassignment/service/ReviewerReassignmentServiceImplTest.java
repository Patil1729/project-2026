package com.party.registration.approval.reassignment.service;

import com.party.committee.entity.*;
import com.party.committee.repository.*;
import com.party.organization.entity.*;
import com.party.organization.repository.OrganizationUnitRepository;
import com.party.registration.application.entity.RegistrationApplication;
import com.party.registration.application.repository.RegistrationApplicationRepository;
import com.party.registration.approval.entity.*;
import com.party.registration.approval.reassignment.dto.ReviewerReassignmentRequest;
import com.party.registration.approval.repository.ApprovalStepRepository;
import com.party.registration.audit.repository.ApprovalAuditLogRepository;
import com.party.registration.common.exception.RegistrationException;
import com.party.registration.common.repository.RegistrationApplicantRepository;
import com.party.user.entity.AppUser;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReviewerReassignmentServiceImplTest {
    final RegistrationApplicationRepository applications = mock(RegistrationApplicationRepository.class);
    final RegistrationApplicantRepository users = mock(RegistrationApplicantRepository.class);
    final ApprovalStepRepository steps = mock(ApprovalStepRepository.class);
    final ApprovalAuditLogRepository audits = mock(ApprovalAuditLogRepository.class);
    final CommitteeRepository committees = mock(CommitteeRepository.class);
    final CommitteePositionRepository positions = mock(CommitteePositionRepository.class);
    final OrganizationUnitRepository organizations = mock(OrganizationUnitRepository.class);
    final AdvocateAssignmentRepository assignments = mock(AdvocateAssignmentRepository.class);
    final Instant now = Instant.parse("2026-10-24T09:00:00Z");
    final ReviewerReassignmentServiceImpl service = new ReviewerReassignmentServiceImpl(applications, users, steps,
            audits, committees, positions, organizations, assignments, Clock.fixed(now, ZoneOffset.UTC));
    AppUser applicant, newHead, newSenior;
    ApprovalStep active;
    RegistrationApplication application;

    @BeforeEach void setup() {
        applicant = user(10L);
        newHead = user(21L);
        newSenior = user(31L);
        var party = OrganizationUnit.builder().id(1L).type(OrganizationType.PARTY).build();
        var state = OrganizationUnit.builder().id(2L).type(OrganizationType.STATE).parent(party).build();
        var division = OrganizationUnit.builder().id(3L).type(OrganizationType.DIVISION).parent(state).build();
        var stateCommittee = Committee.builder().id(11L).committeeType(CommitteeType.STATE_COMMITTEE).organizationUnit(state).build();
        var divisionCommittee = Committee.builder().id(12L).committeeType(CommitteeType.DIVISIONAL_COMMITTEE).organizationUnit(division).build();
        var position = CommitteePosition.builder().id(20L).committee(divisionCommittee)
                .positionType(CommitteePositionType.VICE_PRESIDENT).positionNumber(1).build();
        application = RegistrationApplication.builder().id(99L).applicant(applicant).selectedShakha(division).requestedPosition(position).build();
        application.submitForReview(now.minus(Duration.ofDays(10)));
        active = ApprovalStep.builder().id(100L).application(application).reviewerCommittee(divisionCommittee).sequenceNumber(1).build();
        active.activate(user(20L), now.minus(Duration.ofDays(10)));
        active.escalateTo(user(30L), now.minusSeconds(60));
        var waiting = ApprovalStep.builder().id(101L).application(application).reviewerCommittee(stateCommittee).sequenceNumber(2).build();
        when(applications.findApplicantIdById(99L)).thenReturn(Optional.of(10L));
        when(users.findByIdForUpdate(10L)).thenReturn(Optional.of(applicant));
        when(applications.findByIdForApprovalUpdate(99L)).thenReturn(Optional.of(application));
        when(steps.findForDecisionByApplicationId(99L)).thenReturn(List.of(active, waiting));
        when(positions.findCommitteeIdByPositionId(20L)).thenReturn(Optional.of(12L));
        when(committees.findByIdForUpdate(11L)).thenReturn(Optional.of(stateCommittee));
        when(committees.findByIdForUpdate(12L)).thenReturn(Optional.of(divisionCommittee));
        when(organizations.findByIdForUpdate(1L)).thenReturn(Optional.of(party));
        when(organizations.findByIdForUpdate(2L)).thenReturn(Optional.of(state));
        when(organizations.findByIdForUpdate(3L)).thenReturn(Optional.of(division));
        when(positions.findByIdForUpdate(20L)).thenReturn(Optional.of(position));
        when(assignments.findCurrentPresidentsForCommittee(eq(12L), any(), any())).thenReturn(List.of(appointment(newHead)));
        when(assignments.findCurrentPresidentsForCommittee(eq(11L), any(), any())).thenReturn(List.of(appointment(newSenior)));
    }

    @Test void replacingHeadsPreservesDeadlinesAndEscalationHistory() {
        Instant due = active.getDueAt(), assigned = active.getAssignedAt(), escalated = active.getEscalatedAt();
        var response = service.reassign(99L, new ReviewerReassignmentRequest("President appointments changed"));
        assertTrue(response.changed());
        assertEquals(21L, response.assignedReviewerId());
        assertEquals(31L, response.escalatedToId());
        assertEquals(due, active.getDueAt());
        assertEquals(assigned, active.getAssignedAt());
        assertEquals(escalated, active.getEscalatedAt());
        assertEquals(ApprovalStatus.OVERDUE, active.getStatus());
        assertTrue(active.isTimelineValid());
        verify(audits).saveAndFlush(argThat(event -> event.getNote().contains("President appointments changed")));
    }

    @Test void unchangedHeadsDoNotCreateDuplicateEvents() {
        service.reassign(99L, new ReviewerReassignmentRequest("President appointments changed"));
        clearInvocations(audits);
        assertFalse(service.reassign(99L, new ReviewerReassignmentRequest("Check again")).changed());
        verifyNoInteractions(audits);
    }

    @Test void applicantCannotBeAssignedTheirOwnReview() {
        when(assignments.findCurrentPresidentsForCommittee(eq(12L), any(), any())).thenReturn(List.of(appointment(applicant)));
        assertEquals("REVIEWER_CONFLICT", assertThrows(RegistrationException.class,
                () -> service.reassign(99L, new ReviewerReassignmentRequest("Head changed"))).getErrorCode());
        assertEquals(20L, active.getAssignedReviewer().getId());
        verifyNoInteractions(audits);
    }

    @Test void missingPresidentDoesNotRemoveTheExistingAssignment() {
        when(assignments.findCurrentPresidentsForCommittee(eq(12L), any(), any())).thenReturn(List.of());
        assertThrows(RegistrationException.class, () -> service.reassign(99L, new ReviewerReassignmentRequest("Head changed")));
        assertEquals(20L, active.getAssignedReviewer().getId());
        verifyNoInteractions(audits);
    }

    private AppUser user(Long id) { return AppUser.builder().id(id).enabled(true).build(); }
    private AdvocateAssignment appointment(AppUser user) { return AdvocateAssignment.builder().advocate(user).build(); }
}

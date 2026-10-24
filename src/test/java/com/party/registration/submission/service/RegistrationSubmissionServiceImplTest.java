package com.party.registration.submission.service;

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
import com.party.registration.approval.policy.ApprovalPolicy;
import com.party.registration.submission.config.RegistrationSubmissionProperties;
import com.party.registration.document.entity.ApplicationDocument;
import com.party.registration.audit.entity.ApprovalAuditAction;
import com.party.registration.audit.entity.ApprovalAuditLog;
import com.party.registration.approval.entity.ApprovalStatus;
import com.party.registration.approval.entity.ApprovalStep;
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
import com.party.user.entity.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationSubmissionServiceImplTest {
    @Mock RegistrationApplicationRepository applications;
    @Mock RegistrationApplicantRepository users;
    @Mock ApplicationDocumentRepository documents;
    @Mock CommitteeRepository committees;
    @Mock CommitteePositionRepository positions;
    @Mock AdvocateAssignmentRepository assignments;
    @Mock ApprovalStepRepository steps;
    @Mock ApprovalAuditLogRepository audits;
    private RegistrationSubmissionProperties properties;
    private RegistrationSubmissionServiceImpl service;
    private AppUser applicant;
    private AppUser head;
    private OrganizationUnit state;
    private OrganizationUnit division;
    private Committee stateCommittee;
    private Committee divisionCommittee;
    private CommitteePosition position;
    private RegistrationApplication application;
    private ApplicationDocument recommendation;

    @BeforeEach
    void setUp() {
        properties = new RegistrationSubmissionProperties();
        service = new RegistrationSubmissionServiceImpl(applications, users, documents, committees,
                positions, assignments, steps, audits, properties);
        applicant = AppUser.builder().id(1L).username("shubham.patil").enabled(true)
                .profile(UserProfile.builder().id(1L).build()).build();
        head = AppUser.builder().id(2L).username("head").fullName("Committee Head").enabled(true).build();
        OrganizationUnit party = OrganizationUnit.builder().id(1L).type(OrganizationType.PARTY).build();
        state = OrganizationUnit.builder().id(2L).name("Maharashtra").type(OrganizationType.STATE).parent(party).build();
        division = OrganizationUnit.builder().id(3L).name("Kokan").type(OrganizationType.DIVISION).parent(state).build();
        stateCommittee = Committee.builder().id(11L).name("State").committeeType(CommitteeType.STATE_COMMITTEE).organizationUnit(state).build();
        divisionCommittee = Committee.builder().id(12L).name("Division").committeeType(CommitteeType.DIVISIONAL_COMMITTEE).organizationUnit(division).build();
        position = CommitteePosition.builder().id(20L).committee(divisionCommittee)
                .positionType(CommitteePositionType.MEMBER).positionNumber(1).build();
        application = RegistrationApplication.builder().id(10L).applicationNumber("REG-EXAMPLE")
                .applicant(applicant).selectedShakha(division).requestedPosition(position).build();
        recommendation = uploaded(100L, DocumentType.RECOMMENDATION_LETTER, 1);
        recommendation.verify(head, Instant.now().minusSeconds(30), "Signed letter checked");
    }

    @Test
    void submissionCreatesOrderedStagesActivatesOnlyFirstAndRecordsAudits() {
        ready();
        when(steps.saveAll(anyList())).thenAnswer(call -> {
            List<ApprovalStep> values = call.getArgument(0);
            for (int index = 0; index < values.size(); index++) values.get(index).setId(200L + index);
            return values;
        });

        var response = service.submit("shubham.patil", 10L);

        assertEquals(RegistrationStatus.UNDER_REVIEW, response.status());
        assertNotNull(response.submittedAt());
        assertEquals(List.of(DocumentType.RECOMMENDATION_LETTER), response.requiredDocumentTypes());
        assertEquals(2, response.approvalSteps().size());
        var first = response.approvalSteps().get(0);
        var second = response.approvalSteps().get(1);
        assertEquals(12L, first.reviewerCommitteeId());
        assertEquals(11L, second.reviewerCommitteeId());
        assertEquals(ApprovalStatus.PENDING, first.status());
        assertEquals(2L, first.assignedReviewerId());
        assertEquals(ApprovalPolicy.deadlineFor(first.assignedAt()), first.dueAt());
        assertEquals(ApprovalStatus.WAITING, second.status());
        assertNull(second.assignedReviewerId());
        assertNull(second.dueAt());
        assertEquals(2, application.getApprovalSteps().size());
        verify(applications).findOwnedByIdForUpdate(10L, 1L);
        ArgumentCaptor<List<ApprovalAuditLog>> events = ArgumentCaptor.forClass(List.class);
        verify(audits).saveAllAndFlush(events.capture());
        assertEquals(ApprovalAuditAction.APPLICATION_SUBMITTED, events.getValue().get(0).getAction());
        assertEquals(ApprovalAuditAction.STEP_ACTIVATED, events.getValue().get(1).getAction());
        assertSame(application.getApprovalSteps().get(0), events.getValue().get(1).getApprovalStep());
        assertTrue(applicant.getRoles().isEmpty());
        verify(assignments, never()).save(any());
    }

    @Test
    void applicantCannotSubmitAnotherUsersApplication() {
        when(users.findByUsername("shubham.patil")).thenReturn(Optional.of(applicant));
        failure(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", () -> service.submit("shubham.patil", 99L));
        verifyNoInteractions(documents, committees, assignments, steps, audits);
    }

    @Test
    void unknownAccountCannotSubmit() {
        failure(HttpStatus.UNAUTHORIZED, "APPLICANT_NOT_AUTHENTICATED", () -> service.submit("unknown", 10L));
        verifyNoInteractions(applications, documents, steps);
    }

    @Test
    void disabledAccountCannotSubmit() {
        applicant.setEnabled(false);
        when(users.findByUsername("shubham.patil")).thenReturn(Optional.of(applicant));
        failure(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", () -> service.submit("shubham.patil", 10L));
        verifyNoInteractions(applications, documents, steps);
    }

    @Test
    void repeatedSubmissionDoesNotCreateAnotherChain() {
        owner();
        application.submitForReview(Instant.now());
        failure(HttpStatus.CONFLICT, "APPLICATION_ALREADY_SUBMITTED", () -> service.submit("shubham.patil", 10L));
        verifyNoInteractions(steps, audits, documents);
    }

    @Test
    void missingProfileStopsSubmission() {
        owner();
        applicant.setProfile(null);
        failure(HttpStatus.CONFLICT, "PROFILE_REQUIRED", () -> service.submit("shubham.patil", 10L));
        verifyNoInteractions(steps, documents, audits);
    }

    @Test
    void preexistingStepsAreNotDuplicated() {
        owner();
        when(steps.existsByApplication_Id(10L)).thenReturn(true);
        failure(HttpStatus.CONFLICT, "APPROVAL_CHAIN_EXISTS", () -> service.submit("shubham.patil", 10L));
        verify(steps, never()).saveAll(any());
        verifyNoInteractions(documents, audits);
    }

    @Test
    void missingRecommendationKeepsTheApplicationUnsubmitted() {
        owner();
        failure(HttpStatus.CONFLICT, "REQUIRED_DOCUMENT_MISSING", () -> service.submit("shubham.patil", 10L));
        assertEquals(RegistrationStatus.AWAITING_RECOMMENDATION, application.getStatus());
        verify(steps, never()).saveAll(any());
        verifyNoInteractions(audits);
    }

    @Test
    void aNewerUnverifiedRecommendationBlocksSubmission() {
        owner();
        when(documents.findLatestVersionsForApplication(10L))
                .thenReturn(List.of(uploaded(101L, DocumentType.RECOMMENDATION_LETTER, 2)));
        failure(HttpStatus.CONFLICT, "REQUIRED_DOCUMENT_NOT_VERIFIED", () -> service.submit("shubham.patil", 10L));
        assertEquals(RegistrationStatus.AWAITING_RECOMMENDATION, application.getStatus());
        verifyNoInteractions(committees, assignments, audits);
    }

    @Test
    void rejectedRecommendationBlocksSubmission() {
        owner();
        ApplicationDocument rejected = uploaded(101L, DocumentType.RECOMMENDATION_LETTER, 2);
        rejected.reject(head, Instant.now(), "Signature missing");
        when(documents.findLatestVersionsForApplication(10L)).thenReturn(List.of(rejected));
        failure(HttpStatus.CONFLICT, "REQUIRED_DOCUMENT_NOT_VERIFIED", () -> service.submit("shubham.patil", 10L));
        verifyNoInteractions(committees, audits);
    }

    @Test
    void additionalConfiguredTypesAreRequiredToo() {
        owner();
        verifiedDocuments();
        properties.setRequiredDocumentTypes(EnumSet.of(DocumentType.IDENTITY_PROOF, DocumentType.RECOMMENDATION_LETTER));
        failure(HttpStatus.CONFLICT, "REQUIRED_DOCUMENT_MISSING", () -> service.submit("shubham.patil", 10L));
        verifyNoInteractions(committees, audits);
    }

    @Test
    void occupiedRequestedSeatStopsSubmission() {
        owner();
        when(positions.countActiveAssignmentsForPositionId(20L)).thenReturn(1L);
        failure(HttpStatus.CONFLICT, "POSITION_OCCUPIED", () -> service.submit("shubham.patil", 10L));
        verifyNoInteractions(documents, audits);
    }

    @Test
    void inactiveRequestedPositionStopsSubmission() {
        owner();
        position.setActive(false);
        failure(HttpStatus.CONFLICT, "INACTIVE_POSITION", () -> service.submit("shubham.patil", 10L));
        verifyNoInteractions(documents, audits);
    }

    @Test
    void invalidPositionScopeStopsSubmission() {
        owner();
        application.setSelectedShakha(state);
        failure(HttpStatus.CONFLICT, "INVALID_POSITION", () -> service.submit("shubham.patil", 10L));
        verifyNoInteractions(documents, audits);
    }

    @Test
    void inactiveAncestorStopsSubmission() {
        owner();
        state.setActive(false);
        failure(HttpStatus.CONFLICT, "INVALID_ORGANIZATION", () -> service.submit("shubham.patil", 10L));
        verifyNoInteractions(documents, audits);
    }

    @Test
    void missingAncestorCommitteeIsNotSilentlySkipped() {
        owner();
        verifiedDocuments();
        when(committees.findByOrganizationUnitId(3L)).thenReturn(Optional.of(divisionCommittee));
        failure(HttpStatus.CONFLICT, "APPROVAL_COMMITTEE_MISSING", () -> service.submit("shubham.patil", 10L));
        verifyNoInteractions(assignments, audits);
    }

    @Test
    void inactiveAncestorCommitteeStopsSubmission() {
        owner();
        verifiedDocuments();
        chain();
        stateCommittee.setActive(false);
        failure(HttpStatus.CONFLICT, "INVALID_APPROVAL_COMMITTEE", () -> service.submit("shubham.patil", 10L));
        verifyNoInteractions(assignments, audits);
    }

    @Test
    void missingFirstHeadDoesNotStrandAnApplicationUnderReview() {
        owner();
        verifiedDocuments();
        chain();
        failure(HttpStatus.CONFLICT, "REVIEWER_MISSING", () -> service.submit("shubham.patil", 10L));
        assertEquals(RegistrationStatus.AWAITING_RECOMMENDATION, application.getStatus());
        assertTrue(application.getApprovalSteps().isEmpty());
        verify(steps, never()).saveAll(any());
        verifyNoInteractions(audits);
    }

    @Test
    void ambiguousFirstHeadStopsSubmission() {
        owner();
        verifiedDocuments();
        chain();
        when(assignments.findCurrentPresidentsForCommittee(eq(12L), eq(CommitteePositionType.PRESIDENT), any(LocalDate.class)))
                .thenReturn(List.of(appointment(head), appointment(head)));
        failure(HttpStatus.CONFLICT, "AMBIGUOUS_REVIEWER", () -> service.submit("shubham.patil", 10L));
        verifyNoInteractions(audits);
    }

    @Test
    void applicantCannotBeTheFirstApprovingHead() {
        owner();
        verifiedDocuments();
        chain();
        when(assignments.findCurrentPresidentsForCommittee(eq(12L), eq(CommitteePositionType.PRESIDENT), any(LocalDate.class)))
                .thenReturn(List.of(appointment(applicant)));
        failure(HttpStatus.CONFLICT, "REVIEWER_CONFLICT", () -> service.submit("shubham.patil", 10L));
        verifyNoInteractions(audits);
    }

    @Test
    void presidentApplicationStartsAtTheParentCommittee() {
        owner();
        verifiedDocuments();
        position.setPositionType(CommitteePositionType.PRESIDENT);
        when(committees.findByOrganizationUnitId(2L)).thenReturn(Optional.of(stateCommittee));
        headFor(11L);
        when(steps.saveAll(anyList())).thenAnswer(call -> call.getArgument(0));
        var response = service.submit("shubham.patil", 10L);
        assertEquals(1, response.approvalSteps().size());
        assertEquals(11L, response.approvalSteps().get(0).reviewerCommitteeId());
        verify(committees, never()).findByOrganizationUnitId(3L);
    }

    @Test
    void statePresidentUsesTheSeparateBootstrapFlow() {
        owner();
        verifiedDocuments();
        application.setSelectedShakha(state);
        position.setCommittee(stateCommittee);
        position.setPositionType(CommitteePositionType.PRESIDENT);
        failure(HttpStatus.CONFLICT, "APPROVAL_CHAIN_UNAVAILABLE", () -> service.submit("shubham.patil", 10L));
        verifyNoInteractions(committees, assignments, audits);
    }

    @Test
    void ownerCanReadStagesBeforeSubmissionAsAnEmptyList() {
        when(users.findByUsername("shubham.patil")).thenReturn(Optional.of(applicant));
        when(applications.findByIdAndApplicant_Id(10L, 1L)).thenReturn(Optional.of(application));
        assertTrue(service.getApprovalSteps("shubham.patil", 10L).isEmpty());
        verify(applications, never()).findOwnedByIdForUpdate(anyLong(), anyLong());
    }

    @Test
    void anotherUserCannotReadApprovalStages() {
        when(users.findByUsername("shubham.patil")).thenReturn(Optional.of(applicant));
        failure(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", () -> service.getApprovalSteps("shubham.patil", 99L));
        verifyNoInteractions(steps);
    }

    @Test
    void auditFailurePropagatesForTheTransactionToRollBack() {
        ready();
        when(steps.saveAll(anyList())).thenAnswer(call -> call.getArgument(0));
        when(audits.saveAllAndFlush(anyList())).thenThrow(new DataIntegrityViolationException("audit error"));
        assertThrows(DataIntegrityViolationException.class, () -> service.submit("shubham.patil", 10L));
        // A real database test is required to prove rollback, locking, and race behavior.
    }

    private void owner() {
        when(users.findByUsername("shubham.patil")).thenReturn(Optional.of(applicant));
        when(applications.findOwnedByIdForUpdate(10L, 1L)).thenReturn(Optional.of(application));
    }

    private void verifiedDocuments() {
        when(documents.findLatestVersionsForApplication(10L)).thenReturn(List.of(recommendation));
    }

    private void chain() {
        when(committees.findByOrganizationUnitId(3L)).thenReturn(Optional.of(divisionCommittee));
        when(committees.findByOrganizationUnitId(2L)).thenReturn(Optional.of(stateCommittee));
    }

    private void headFor(Long committeeId) {
        when(assignments.findCurrentPresidentsForCommittee(eq(committeeId), eq(CommitteePositionType.PRESIDENT), any(LocalDate.class)))
                .thenReturn(List.of(appointment(head)));
    }

    private void ready() {
        owner();
        verifiedDocuments();
        chain();
        headFor(12L);
    }

    private AdvocateAssignment appointment(AppUser reviewer) {
        return AdvocateAssignment.builder().advocate(reviewer).appointedDate(LocalDate.now().minusDays(1)).build();
    }

    private ApplicationDocument uploaded(Long id, DocumentType type, int version) {
        return ApplicationDocument.builder().id(id).application(application).documentType(type)
                .documentVersion(version).uploadedBy(applicant).uploadedAt(Instant.now().minusSeconds(3600)).build();
    }

    private void failure(HttpStatus status, String code, org.junit.jupiter.api.function.Executable action) {
        RegistrationException exception = assertThrows(RegistrationException.class, action);
        assertEquals(status, exception.getStatus());
        assertEquals(code, exception.getErrorCode());
    }
}

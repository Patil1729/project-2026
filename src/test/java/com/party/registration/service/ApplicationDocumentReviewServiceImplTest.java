package com.party.registration.service;

import com.party.committee.entity.CommitteePositionType;
import com.party.committee.repository.AdvocateAssignmentRepository;
import com.party.organization.entity.OrganizationType;
import com.party.organization.entity.OrganizationUnit;
import com.party.registration.dto.ApplicationDocumentRejectionRequest;
import com.party.registration.dto.ApplicationDocumentVerificationRequest;
import com.party.registration.entity.ApplicationDocument;
import com.party.registration.entity.ApprovalAuditAction;
import com.party.registration.entity.ApprovalAuditLog;
import com.party.registration.entity.DocumentStatus;
import com.party.registration.entity.DocumentType;
import com.party.registration.entity.RegistrationApplication;
import com.party.registration.entity.RegistrationStatus;
import com.party.registration.exception.RegistrationException;
import com.party.registration.repository.ApplicationDocumentRepository;
import com.party.registration.repository.ApprovalAuditLogRepository;
import com.party.registration.repository.RegistrationApplicantRepository;
import com.party.registration.repository.RegistrationApplicationRepository;
import com.party.registration.storage.DocumentStorage;
import com.party.user.entity.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationDocumentReviewServiceImplTest {
    @Mock ApplicationDocumentRepository documents;
    @Mock RegistrationApplicationRepository applications;
    @Mock RegistrationApplicantRepository users;
    @Mock AdvocateAssignmentRepository assignments;
    @Mock ApprovalAuditLogRepository audits;
    @Mock DocumentStorage storage;

    private ApplicationDocumentReviewServiceImpl service;
    private AppUser applicant;
    private AppUser reviewer;
    private OrganizationUnit state;
    private OrganizationUnit division;
    private RegistrationApplication application;
    private ApplicationDocument document;

    @BeforeEach
    void setUp() {
        service = new ApplicationDocumentReviewServiceImpl(documents, applications, users, assignments, audits, storage);
        applicant = AppUser.builder().id(1L).username("shubham.patil").enabled(true).build();
        reviewer = AppUser.builder().id(2L).username("document.reviewer").enabled(true).build();
        OrganizationUnit party = OrganizationUnit.builder().id(1L).type(OrganizationType.PARTY).build();
        state = OrganizationUnit.builder().id(2L).type(OrganizationType.STATE).parent(party).build();
        division = OrganizationUnit.builder().id(3L).type(OrganizationType.DIVISION).parent(state).build();
        application = RegistrationApplication.builder().id(10L).applicant(applicant).selectedShakha(division).build();
        document = ApplicationDocument.builder().id(100L).application(application)
                .documentType(DocumentType.IDENTITY_PROOF).documentVersion(2)
                .originalFilename("proof.pdf").storageKey("10/proof.pdf")
                .contentType("application/pdf").sizeBytes(23L).sha256("a".repeat(64))
                .uploadedBy(applicant).uploadedAt(Instant.parse("2026-10-18T10:00:00Z")).build();
    }

    @Test
    void verificationRecordsReviewerCommentAndAuditWithoutSubmittingApplication() {
        allowWrite();
        latestDocument();
        var request = new ApplicationDocumentVerificationRequest();
        request.setComment("  Details checked  ");

        var response = service.verify("document.reviewer", 10L, 100L, request);

        assertEquals(DocumentStatus.VERIFIED, response.status());
        assertEquals(2L, response.reviewedById());
        assertEquals("Details checked", response.reviewComment());
        assertNotNull(response.reviewedAt());
        assertEquals(RegistrationStatus.AWAITING_RECOMMENDATION, application.getStatus());
        assertNull(application.getSubmittedAt());
        ArgumentCaptor<ApprovalAuditLog> event = ArgumentCaptor.forClass(ApprovalAuditLog.class);
        verify(audits).saveAndFlush(event.capture());
        assertEquals(ApprovalAuditAction.DOCUMENT_VERIFIED, event.getValue().getAction());
        assertSame(reviewer, event.getValue().getActor());
        assertSame(application, event.getValue().getApplication());
        assertEquals(response.reviewedAt(), event.getValue().getEventAt());
        assertTrue(event.getValue().getNote().contains("100"));
        verify(documents).saveAndFlush(document);
        verifyNoInteractions(storage);
    }

    @Test
    void rejectionRecordsReasonWithoutRejectingTheApplication() {
        allowWrite();
        latestDocument();
        var response = service.reject("document.reviewer", 10L, 100L, rejection("  Upload a clearer scan  "));

        assertEquals(DocumentStatus.REJECTED, response.status());
        assertEquals("Upload a clearer scan", response.reviewComment());
        assertEquals(RegistrationStatus.AWAITING_RECOMMENDATION, application.getStatus());
        verify(audits).saveAndFlush(argThat(event -> event.getAction() == ApprovalAuditAction.DOCUMENT_REJECTED));
    }

    @Test
    void anEmptyVerificationCommentIsOptional() {
        allowWrite();
        latestDocument();
        var request = new ApplicationDocumentVerificationRequest();
        request.setComment("   ");
        assertNull(service.verify("document.reviewer", 10L, 100L, request).reviewComment());
    }

    @Test
    void allowsDocumentReviewDuringTheFutureUnderReviewPhase() {
        application.submitForReview(Instant.parse("2026-10-18T11:00:00Z"));
        allowWrite();
        latestDocument();
        service.verify("document.reviewer", 10L, 100L, new ApplicationDocumentVerificationRequest());
        assertEquals(RegistrationStatus.UNDER_REVIEW, application.getStatus());
    }

    @Test
    void aSupersededUploadedVersionCannotBeReviewed() {
        allowWrite();
        when(documents.findByIdAndApplication_Id(100L, 10L)).thenReturn(Optional.of(document));
        when(documents.findMaxDocumentVersion(10L, DocumentType.IDENTITY_PROOF)).thenReturn(3);

        assertFailure(HttpStatus.CONFLICT, "DOCUMENT_VERSION_SUPERSEDED",
                () -> service.verify("document.reviewer", 10L, 100L, new ApplicationDocumentVerificationRequest()));
        assertEquals(DocumentStatus.UPLOADED, document.getStatus());
        verify(documents, never()).saveAndFlush(any());
        verifyNoInteractions(audits);
    }

    @Test
    void aDecisionCannotBeOverwrittenByAnotherDecision() {
        document.verify(reviewer, Instant.parse("2026-10-18T11:00:00Z"), "Original decision");
        allowWrite();
        when(documents.findByIdAndApplication_Id(100L, 10L)).thenReturn(Optional.of(document));

        assertFailure(HttpStatus.CONFLICT, "DOCUMENT_ALREADY_REVIEWED",
                () -> service.reject("document.reviewer", 10L, 100L, rejection("Changed my mind")));
        assertEquals(DocumentStatus.VERIFIED, document.getStatus());
        assertEquals("Original decision", document.getReviewComment());
        verify(documents, never()).saveAndFlush(any());
        verifyNoInteractions(audits);
    }

    @Test
    void terminalApplicationsAreClosedForNewDocumentDecisions() {
        application.submitForReview(Instant.parse("2026-10-18T11:00:00Z"));
        application.reject(Instant.parse("2026-10-18T12:00:00Z"), "Application rejected");
        allowWrite();

        assertFailure(HttpStatus.CONFLICT, "APPLICATION_REVIEW_CLOSED",
                () -> service.reject("document.reviewer", 10L, 100L, rejection("Unreadable")));
        verifyNoInteractions(documents, audits, storage);
    }

    @Test
    void unknownReviewerIsUnauthorized() {
        assertFailure(HttpStatus.UNAUTHORIZED, "REVIEWER_NOT_AUTHENTICATED",
                () -> service.list("unknown", 10L));
        verifyNoInteractions(applications, assignments, documents, storage);
    }

    @Test
    void disabledReviewerCannotReadOrDecide() {
        reviewer.setEnabled(false);
        when(users.findByUsername("document.reviewer")).thenReturn(Optional.of(reviewer));
        assertFailure(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED",
                () -> service.list("document.reviewer", 10L));
        verifyNoInteractions(applications, assignments, documents, storage);
    }

    @Test
    void anApplicantCannotUseReviewerEndpointsEvenIfTheyHoldAnAppointment() {
        when(users.findByUsername("shubham.patil")).thenReturn(Optional.of(applicant));
        when(applications.findByIdForDocumentReviewUpdate(10L)).thenReturn(Optional.of(application));

        assertFailure(HttpStatus.FORBIDDEN, "DOCUMENT_SELF_REVIEW_FORBIDDEN",
                () -> service.verify("shubham.patil", 10L, 100L, new ApplicationDocumentVerificationRequest()));
        verifyNoInteractions(assignments, documents, audits, storage);
    }

    @Test
    void aHomeUnitWithoutAnAppointmentDoesNotGrantAccess() {
        reviewer.setOrganizationUnit(division);
        when(users.findByUsername("document.reviewer")).thenReturn(Optional.of(reviewer));
        when(applications.findById(10L)).thenReturn(Optional.of(application));

        assertFailure(HttpStatus.FORBIDDEN, "DOCUMENT_REVIEW_FORBIDDEN",
                () -> service.list("document.reviewer", 10L));
        verify(assignments).countCurrentPresidentsInUnits(eq(2L), eq(Set.of(1L, 2L, 3L)),
                eq(CommitteePositionType.PRESIDENT), any(LocalDate.class));
        verifyNoInteractions(documents, storage);
    }

    @Test
    void anInactiveAncestorStopsReviewBeforeTheAppointmentLookup() {
        state.setActive(false);
        when(users.findByUsername("document.reviewer")).thenReturn(Optional.of(reviewer));
        when(applications.findById(10L)).thenReturn(Optional.of(application));
        assertFailure(HttpStatus.CONFLICT, "INVALID_ORGANIZATION", () -> service.list("document.reviewer", 10L));
        verifyNoInteractions(assignments, documents);
    }

    @Test
    void invalidParentTypesAreRejected() {
        division.setParent(state.getParent());
        when(users.findByUsername("document.reviewer")).thenReturn(Optional.of(reviewer));
        when(applications.findById(10L)).thenReturn(Optional.of(application));
        assertFailure(HttpStatus.CONFLICT, "INVALID_ORGANIZATION", () -> service.list("document.reviewer", 10L));
        verifyNoInteractions(assignments, documents);
    }

    @Test
    void missingApplicationReturns404() {
        when(users.findByUsername("document.reviewer")).thenReturn(Optional.of(reviewer));
        assertFailure(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", () -> service.list("document.reviewer", 99L));
        verifyNoInteractions(assignments, documents, storage);
    }

    @Test
    void aDocumentFromAnotherApplicationCannotBeReviewed() {
        allowWrite();
        assertFailure(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND",
                () -> service.verify("document.reviewer", 10L, 999L, new ApplicationDocumentVerificationRequest()));
        verifyNoInteractions(audits, storage);
    }

    @Test
    void reviewerListingIncludesVersionHistoryWithoutChangingDocuments() {
        allowRead();
        when(documents.findAllByApplication_IdOrderByIdDesc(10L)).thenReturn(List.of(document));
        var response = service.list("document.reviewer", 10L);
        assertEquals(1, response.size());
        assertEquals(2, response.get(0).documentVersion());
        verify(documents, never()).saveAndFlush(any());
        verifyNoInteractions(audits, storage);
    }

    @Test
    void reviewerCanReadTheRecordedDecision() {
        allowRead();
        document.verify(reviewer, Instant.parse("2026-10-18T11:00:00Z"), "Checked");
        when(documents.findByIdAndApplication_Id(100L, 10L)).thenReturn(Optional.of(document));
        assertEquals("Checked", service.get("document.reviewer", 10L, 100L).reviewComment());
        verifyNoInteractions(storage, audits);
    }

    @Test
    void downloadChecksScopeBeforeOpeningThePrivateFile() throws Exception {
        allowRead();
        when(documents.findByIdAndApplication_Id(100L, 10L)).thenReturn(Optional.of(document));
        byte[] bytes = "%PDF-1.4".getBytes(StandardCharsets.UTF_8);
        when(storage.open("10/proof.pdf")).thenReturn(new ByteArrayInputStream(bytes));
        var response = service.download("document.reviewer", 10L, 100L);
        try (var input = response.inputStream()) {
            assertArrayEquals(bytes, input.readAllBytes());
        }
        var order = inOrder(assignments, storage);
        order.verify(assignments).countCurrentPresidentsInUnits(eq(2L), anySet(),
                eq(CommitteePositionType.PRESIDENT), any(LocalDate.class));
        order.verify(storage).open("10/proof.pdf");
    }

    @Test
    void missingStoredContentReturns404() throws Exception {
        allowRead();
        when(documents.findByIdAndApplication_Id(100L, 10L)).thenReturn(Optional.of(document));
        when(storage.open("10/proof.pdf")).thenThrow(new NoSuchFileException("proof.pdf"));
        assertFailure(HttpStatus.NOT_FOUND, "DOCUMENT_CONTENT_NOT_FOUND",
                () -> service.download("document.reviewer", 10L, 100L));
    }

    @Test
    void auditFailureIsPropagatedToTheTransactionInsteadOfReturningSuccess() {
        allowWrite();
        latestDocument();
        when(audits.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("audit failure"));
        assertThrows(DataIntegrityViolationException.class,
                () -> service.verify("document.reviewer", 10L, 100L, new ApplicationDocumentVerificationRequest()));
        // Real rollback and concurrent requests require the database integration check.
    }

    private void allowRead() {
        when(users.findByUsername("document.reviewer")).thenReturn(Optional.of(reviewer));
        when(applications.findById(10L)).thenReturn(Optional.of(application));
        allowAppointment();
    }

    private void allowWrite() {
        when(users.findByUsername("document.reviewer")).thenReturn(Optional.of(reviewer));
        when(applications.findByIdForDocumentReviewUpdate(10L)).thenReturn(Optional.of(application));
        allowAppointment();
    }

    private void allowAppointment() {
        when(assignments.countCurrentPresidentsInUnits(eq(2L), eq(Set.of(1L, 2L, 3L)),
                eq(CommitteePositionType.PRESIDENT), any(LocalDate.class))).thenReturn(1L);
    }

    private void latestDocument() {
        when(documents.findByIdAndApplication_Id(100L, 10L)).thenReturn(Optional.of(document));
        when(documents.findMaxDocumentVersion(10L, DocumentType.IDENTITY_PROOF)).thenReturn(2);
    }

    private ApplicationDocumentRejectionRequest rejection(String reason) {
        var request = new ApplicationDocumentRejectionRequest();
        request.setReason(reason);
        return request;
    }

    private void assertFailure(HttpStatus status, String code, org.junit.jupiter.api.function.Executable action) {
        RegistrationException failure = assertThrows(RegistrationException.class, action);
        assertEquals(status, failure.getStatus());
        assertEquals(code, failure.getErrorCode());
    }
}

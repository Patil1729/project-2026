package com.party.registration.service;

import com.party.registration.dto.ApplicationDocumentUploadRequest;
import com.party.registration.entity.ApplicationDocument;
import com.party.registration.entity.ApprovalAuditAction;
import com.party.registration.entity.ApprovalAuditLog;
import com.party.registration.entity.DocumentStatus;
import com.party.registration.entity.DocumentType;
import com.party.registration.entity.RegistrationApplication;
import com.party.registration.exception.RegistrationException;
import com.party.registration.repository.ApplicationDocumentRepository;
import com.party.registration.repository.ApprovalAuditLogRepository;
import com.party.registration.repository.RegistrationApplicantRepository;
import com.party.registration.repository.RegistrationApplicationRepository;
import com.party.registration.storage.DocumentFileException;
import com.party.registration.storage.DocumentStorage;
import com.party.registration.storage.StoredDocumentFile;
import com.party.user.entity.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationDocumentServiceImplTest {
    @Mock ApplicationDocumentRepository documents;
    @Mock RegistrationApplicationRepository applications;
    @Mock RegistrationApplicantRepository applicants;
    @Mock ApprovalAuditLogRepository audits;
    @Mock DocumentStorage storage;
    private ApplicationDocumentServiceImpl service;
    private AppUser applicant;
    private RegistrationApplication application;
    private ApplicationDocumentUploadRequest request;
    private StoredDocumentFile stored;

    @BeforeEach
    void setUp() {
        service = new ApplicationDocumentServiceImpl(documents, applications, applicants, audits, storage);
        applicant = AppUser.builder().id(1L).username("shubham.patil").enabled(true).build();
        application = RegistrationApplication.builder().id(1L).applicant(applicant).build();
        request = new ApplicationDocumentUploadRequest();
        request.setDocumentType(DocumentType.IDENTITY_PROOF);
        request.setFile(new MockMultipartFile("file", "proof.pdf", "application/pdf",
                "%PDF-1.4\nfixture\n%%EOF".getBytes(StandardCharsets.UTF_8)));
        stored = new StoredDocumentFile("proof.pdf", "1/39edf283-84f1-4bad-a561-91bdcc3e2836.pdf",
                "application/pdf", request.getFile().getSize(), "a".repeat(64));
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void createsNextVersionAndAuditAndKeepsContentOnCommit() throws Exception {
        prepareUpload();
        when(documents.findMaxDocumentVersion(1L, DocumentType.IDENTITY_PROOF)).thenReturn(1);
        when(documents.saveAndFlush(any(ApplicationDocument.class))).thenAnswer(invocation -> {
            ApplicationDocument document = invocation.getArgument(0);
            document.setId(100L);
            return document;
        });

        var response = service.upload("shubham.patil", 1L, request);

        assertEquals(2, response.documentVersion());
        assertEquals(DocumentStatus.UPLOADED, response.status());
        assertNull(response.reviewedById());
        ArgumentCaptor<ApprovalAuditLog> event = ArgumentCaptor.forClass(ApprovalAuditLog.class);
        verify(audits).saveAndFlush(event.capture());
        assertEquals(ApprovalAuditAction.DOCUMENT_UPLOADED, event.getValue().getAction());
        assertSame(applicant, event.getValue().getActor());
        complete(TransactionSynchronization.STATUS_COMMITTED);
        verify(storage, never()).delete(anyString());
    }

    @Test
    void rollbackRemovesContentWhenMetadataInsertFails() throws Exception {
        prepareUpload();
        when(documents.saveAndFlush(any(ApplicationDocument.class)))
                .thenThrow(new DataIntegrityViolationException("insert failed"));

        assertThrows(DataIntegrityViolationException.class, () -> service.upload("shubham.patil", 1L, request));
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(storage).delete(stored.storageKey());
        verifyNoInteractions(audits);
    }

    @Test
    void unknownTransactionOutcomeDoesNotDeletePossiblyCommittedContent() throws Exception {
        prepareUpload();
        when(documents.saveAndFlush(any(ApplicationDocument.class))).thenAnswer(invocation -> {
            ApplicationDocument document = invocation.getArgument(0);
            document.setId(100L);
            return document;
        });

        service.upload("shubham.patil", 1L, request);
        complete(TransactionSynchronization.STATUS_UNKNOWN);

        verify(storage, never()).delete(anyString());
    }

    @Test
    void refusesUploadToAnotherApplicantsApplicationBeforeWritingFile() {
        when(applicants.findByUsername("shubham.patil")).thenReturn(Optional.of(applicant));
        when(applications.findOwnedByIdForUpdate(99L, 1L)).thenReturn(Optional.empty());

        assertFailure("APPLICATION_NOT_FOUND", () -> service.upload("shubham.patil", 99L, request));
        verifyNoInteractions(storage, documents, audits);
    }

    @Test
    void freezesUploadsAfterSubmission() {
        application.submitForReview(Instant.now());
        prepareOwnedUpload();

        assertFailure("APPLICATION_NOT_EDITABLE", () -> service.upload("shubham.patil", 1L, request));
        verifyNoInteractions(storage, documents, audits);
    }

    @Test
    void ownershipIsCheckedBeforeListingOrReadingStorage() {
        when(applicants.findByUsername("shubham.patil")).thenReturn(Optional.of(applicant));
        when(applications.findByIdAndApplicant_Id(99L, 1L)).thenReturn(Optional.empty());

        assertFailure("APPLICATION_NOT_FOUND", () -> service.list("shubham.patil", 99L));
        assertFailure("APPLICATION_NOT_FOUND", () -> service.download("shubham.patil", 99L, 100L));
        verifyNoInteractions(storage, documents);
    }

    @Test
    void documentIdMustBelongToRequestedApplication() {
        when(applicants.findByUsername("shubham.patil")).thenReturn(Optional.of(applicant));
        when(applications.findByIdAndApplicant_Id(1L, 1L)).thenReturn(Optional.of(application));
        when(documents.findByIdAndApplication_Id(999L, 1L)).thenReturn(Optional.empty());

        assertFailure("DOCUMENT_NOT_FOUND", () -> service.download("shubham.patil", 1L, 999L));
        verifyNoInteractions(storage);
    }

    @Test
    void disabledAccountCannotAccessDocuments() {
        applicant.setEnabled(false);
        when(applicants.findByUsername("shubham.patil")).thenReturn(Optional.of(applicant));

        RegistrationException exception = assertThrows(RegistrationException.class,
                () -> service.list("shubham.patil", 1L));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        verifyNoInteractions(applications, documents, storage);
    }

    @Test
    void unsupportedFileBecomes415WithoutMetadataOrAuditInsert() throws Exception {
        prepareOwnedUpload();
        when(storage.maxFileBytes()).thenReturn(5242880L);
        when(storage.store(eq(1L), eq("proof.pdf"), any())).thenThrow(new DocumentFileException(
                DocumentFileException.Reason.UNSUPPORTED_FILE_TYPE, "Unsupported file"));

        RegistrationException exception = assertThrows(RegistrationException.class,
                () -> service.upload("shubham.patil", 1L, request));
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, exception.getStatus());
        verify(documents, never()).saveAndFlush(any());
        verifyNoInteractions(audits);
    }

    private void prepareOwnedUpload() {
        when(applicants.findByUsername("shubham.patil")).thenReturn(Optional.of(applicant));
        when(applications.findOwnedByIdForUpdate(1L, 1L)).thenReturn(Optional.of(application));
    }

    private void prepareUpload() throws Exception {
        prepareOwnedUpload();
        when(storage.maxFileBytes()).thenReturn(5242880L);
        when(storage.store(eq(1L), eq("proof.pdf"), any())).thenReturn(stored);
    }

    private void complete(int status) {
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(status);
        }
        TransactionSynchronizationManager.clearSynchronization();
    }

    private void assertFailure(String code, org.junit.jupiter.api.function.Executable action) {
        RegistrationException exception = assertThrows(RegistrationException.class, action);
        assertEquals(code, exception.getErrorCode());
    }
}

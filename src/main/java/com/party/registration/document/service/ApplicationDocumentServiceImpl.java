package com.party.registration.document.service;

import com.party.registration.document.dto.ApplicationDocumentDownload;
import com.party.registration.document.dto.ApplicationDocumentResponse;
import com.party.registration.document.dto.ApplicationDocumentUploadRequest;
import com.party.registration.document.entity.ApplicationDocument;
import com.party.registration.audit.entity.ApprovalAuditAction;
import com.party.registration.audit.entity.ApprovalAuditLog;
import com.party.registration.application.entity.RegistrationApplication;
import com.party.registration.application.entity.RegistrationStatus;
import com.party.registration.common.exception.RegistrationException;
import com.party.registration.document.repository.ApplicationDocumentRepository;
import com.party.registration.audit.repository.ApprovalAuditLogRepository;
import com.party.registration.common.repository.RegistrationApplicantRepository;
import com.party.registration.application.repository.RegistrationApplicationRepository;
import com.party.registration.storage.DocumentFileException;
import com.party.registration.storage.DocumentStorage;
import com.party.registration.storage.StoredDocumentFile;
import com.party.user.entity.AppUser;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationDocumentServiceImpl implements ApplicationDocumentService {
    private static final Logger log = LoggerFactory.getLogger(ApplicationDocumentServiceImpl.class);

    private final ApplicationDocumentRepository documentRepository;
    private final RegistrationApplicationRepository applicationRepository;
    private final RegistrationApplicantRepository applicantRepository;
    private final ApprovalAuditLogRepository auditLogRepository;
    private final DocumentStorage documentStorage;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ApplicationDocumentResponse upload(
            String username, Long applicationId, ApplicationDocumentUploadRequest request) {
        AppUser applicant = findEnabledApplicant(username);
        RegistrationApplication application = applicationRepository
                .findOwnedByIdForUpdate(applicationId, applicant.getId())
                .orElseThrow(this::applicationNotFound);
        if (application.getStatus() != RegistrationStatus.AWAITING_RECOMMENDATION) {
            throw failure(HttpStatus.CONFLICT, "APPLICATION_NOT_EDITABLE",
                    "Documents can be uploaded only before the application is submitted for review.");
        }
        if (request == null || request.getDocumentType() == null || request.getFile() == null) {
            throw failure(HttpStatus.BAD_REQUEST, "DOCUMENT_FIELDS_REQUIRED",
                    "Document type and file are required.");
        }
        MultipartFile file = request.getFile();
        if (file.isEmpty()) {
            throw failure(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "Select a nonempty file.");
        }
        if (file.getSize() > documentStorage.maxFileBytes()) {
            throw failure(HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE",
                    "The file exceeds the configured maximum size.");
        }

        // The parent row lock serializes version allocation for this application.
        int lastVersion = documentRepository.findMaxDocumentVersion(applicationId, request.getDocumentType());
        if (lastVersion == Integer.MAX_VALUE) {
            throw failure(HttpStatus.CONFLICT, "DOCUMENT_VERSION_LIMIT", "Document version limit reached.");
        }
        StoredDocumentFile stored = storeAndTrackRollback(applicationId, file);
        Instant now = Instant.now();
        ApplicationDocument document = documentRepository.saveAndFlush(ApplicationDocument.builder()
                .application(application)
                .documentType(request.getDocumentType())
                .documentVersion(lastVersion + 1)
                .originalFilename(stored.originalFilename())
                .storageKey(stored.storageKey())
                .contentType(stored.contentType())
                .sizeBytes(stored.sizeBytes())
                .sha256(stored.sha256())
                .uploadedBy(applicant)
                .uploadedAt(now)
                .build());

        auditLogRepository.saveAndFlush(ApprovalAuditLog.builder()
                .application(application).actor(applicant)
                .action(ApprovalAuditAction.DOCUMENT_UPLOADED)
                .eventKey(UUID.randomUUID().toString()).eventAt(now)
                .note("Document " + document.getId() + ", type " + document.getDocumentType()
                        + ", version " + document.getDocumentVersion() + " uploaded")
                .build());
        return toResponse(document);
    }

    @Override
    public List<ApplicationDocumentResponse> list(String username, Long applicationId) {
        AppUser applicant = findEnabledApplicant(username);
        requireOwnedApplication(applicationId, applicant.getId());
        return documentRepository.findAllByApplication_IdOrderByIdDesc(applicationId).stream()
                .map(this::toResponse).toList();
    }

    @Override
    public ApplicationDocumentResponse get(String username, Long applicationId, Long documentId) {
        AppUser applicant = findEnabledApplicant(username);
        requireOwnedApplication(applicationId, applicant.getId());
        return toResponse(findDocument(applicationId, documentId));
    }

    @Override
    public ApplicationDocumentDownload download(String username, Long applicationId, Long documentId) {
        AppUser applicant = findEnabledApplicant(username);
        requireOwnedApplication(applicationId, applicant.getId());
        ApplicationDocument document = findDocument(applicationId, documentId);
        try {
            InputStream input = documentStorage.open(document.getStorageKey());
            return new ApplicationDocumentDownload(document.getOriginalFilename(), document.getContentType(),
                    document.getSizeBytes(), input);
        } catch (NoSuchFileException exception) {
            throw failure(HttpStatus.NOT_FOUND, "DOCUMENT_CONTENT_NOT_FOUND",
                    "The stored document content is unavailable.");
        } catch (IOException exception) {
            log.error("Could not open content for document {}", documentId, exception);
            throw failure(HttpStatus.INTERNAL_SERVER_ERROR, "DOCUMENT_STORAGE_FAILURE",
                    "The document could not be read. Please try again later.");
        }
    }

    private StoredDocumentFile storeAndTrackRollback(Long applicationId, MultipartFile file) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Document upload must run through the transactional service bean");
        }
        try (InputStream input = file.getInputStream()) {
            StoredDocumentFile stored = documentStorage.store(applicationId, file.getOriginalFilename(), input);
            try {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK) {
                            cleanup(stored.storageKey());
                        } else if (status == STATUS_UNKNOWN) {
                            // Do not delete content if the DB may actually have committed.
                            log.warn("Unknown upload transaction outcome for application {}; check stored content",
                                    applicationId);
                        }
                    }
                });
            } catch (RuntimeException exception) {
                cleanup(stored.storageKey());
                throw exception;
            }
            return stored;
        } catch (DocumentFileException exception) {
            HttpStatus status = switch (exception.getReason()) {
                case FILE_TOO_LARGE -> HttpStatus.CONTENT_TOO_LARGE;
                case UNSUPPORTED_FILE_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
                default -> HttpStatus.BAD_REQUEST;
            };
            throw failure(status, exception.getReason().name(), exception.getMessage());
        } catch (IOException exception) {
            log.error("Could not store a document for application {}", applicationId, exception);
            throw failure(HttpStatus.INTERNAL_SERVER_ERROR, "DOCUMENT_STORAGE_FAILURE",
                    "The document could not be stored. Please try again later.");
        }
    }

    private void cleanup(String storageKey) {
        try {
            documentStorage.delete(storageKey);
        } catch (IOException | RuntimeException exception) {
            // Rollback has already happened; keep the original transaction failure.
            log.error("Could not remove document content after upload rollback", exception);
        }
    }

    private AppUser findEnabledApplicant(String username) {
        AppUser applicant = applicantRepository.findByUsername(username).orElseThrow(() ->
                failure(HttpStatus.UNAUTHORIZED, "APPLICANT_NOT_AUTHENTICATED",
                        "The current username has no application account."));
        if (!applicant.isEnabled()) {
            throw failure(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "Your account is disabled.");
        }
        return applicant;
    }

    private void requireOwnedApplication(Long applicationId, Long applicantId) {
        applicationRepository.findByIdAndApplicant_Id(applicationId, applicantId)
                .orElseThrow(this::applicationNotFound);
    }

    private ApplicationDocument findDocument(Long applicationId, Long documentId) {
        return documentRepository.findByIdAndApplication_Id(documentId, applicationId).orElseThrow(() ->
                failure(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "Document not found in this application."));
    }

    private ApplicationDocumentResponse toResponse(ApplicationDocument document) {
        AppUser reviewer = document.getReviewedBy();
        return new ApplicationDocumentResponse(document.getId(), document.getApplication().getId(),
                document.getDocumentType(), document.getDocumentVersion(), document.getOriginalFilename(),
                document.getContentType(), document.getSizeBytes(), document.getSha256(), document.getStatus(),
                document.getUploadedBy().getId(), document.getUploadedAt(),
                reviewer == null ? null : reviewer.getId(), document.getReviewedAt(), document.getReviewComment());
    }

    private RegistrationException applicationNotFound() {
        return failure(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "Registration application not found.");
    }

    private RegistrationException failure(HttpStatus status, String code, String message) {
        return new RegistrationException(status, code, message);
    }
}

package com.party.registration.service;

import com.party.committee.entity.CommitteePositionType;
import com.party.committee.repository.AdvocateAssignmentRepository;
import com.party.organization.entity.OrganizationUnit;
import com.party.registration.dto.ApplicationDocumentDownload;
import com.party.registration.dto.ApplicationDocumentRejectionRequest;
import com.party.registration.dto.ApplicationDocumentResponse;
import com.party.registration.dto.ApplicationDocumentVerificationRequest;
import com.party.registration.entity.ApplicationDocument;
import com.party.registration.entity.ApprovalAuditAction;
import com.party.registration.entity.ApprovalAuditLog;
import com.party.registration.entity.DocumentStatus;
import com.party.registration.entity.RegistrationApplication;
import com.party.registration.exception.RegistrationException;
import com.party.registration.repository.ApplicationDocumentRepository;
import com.party.registration.repository.ApprovalAuditLogRepository;
import com.party.registration.repository.RegistrationApplicantRepository;
import com.party.registration.repository.RegistrationApplicationRepository;
import com.party.registration.storage.DocumentStorage;
import com.party.user.entity.AppUser;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationDocumentReviewServiceImpl implements ApplicationDocumentReviewService {
    private static final Logger log = LoggerFactory.getLogger(ApplicationDocumentReviewServiceImpl.class);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private final ApplicationDocumentRepository documentRepository;
    private final RegistrationApplicationRepository applicationRepository;
    private final RegistrationApplicantRepository userRepository;
    private final AdvocateAssignmentRepository assignmentRepository;
    private final ApprovalAuditLogRepository auditLogRepository;
    private final DocumentStorage documentStorage;

    @Override
    public List<ApplicationDocumentResponse> list(String username, Long applicationId) {
        AppUser reviewer = findEnabledReviewer(username);
        requireReviewAccess(reviewer, findApplication(applicationId, false));
        return documentRepository.findAllByApplication_IdOrderByIdDesc(applicationId).stream()
                .map(this::toResponse).toList();
    }

    @Override
    public ApplicationDocumentResponse get(String username, Long applicationId, Long documentId) {
        AppUser reviewer = findEnabledReviewer(username);
        requireReviewAccess(reviewer, findApplication(applicationId, false));
        return toResponse(findDocument(applicationId, documentId));
    }

    @Override
    public ApplicationDocumentDownload download(String username, Long applicationId, Long documentId) {
        AppUser reviewer = findEnabledReviewer(username);
        requireReviewAccess(reviewer, findApplication(applicationId, false));
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

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ApplicationDocumentResponse verify(String username, Long applicationId, Long documentId,
                                              ApplicationDocumentVerificationRequest request) {
        AppUser reviewer = findEnabledReviewer(username);
        ApplicationDocument document = requireReviewableDocument(reviewer, applicationId, documentId);
        Instant now = Instant.now();
        document.verify(reviewer, now, normalize(request.getComment()));
        return saveDecision(document, reviewer, now, ApprovalAuditAction.DOCUMENT_VERIFIED);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ApplicationDocumentResponse reject(String username, Long applicationId, Long documentId,
                                              ApplicationDocumentRejectionRequest request) {
        AppUser reviewer = findEnabledReviewer(username);
        ApplicationDocument document = requireReviewableDocument(reviewer, applicationId, documentId);
        Instant now = Instant.now();
        document.reject(reviewer, now, request.getReason().strip());
        return saveDecision(document, reviewer, now, ApprovalAuditAction.DOCUMENT_REJECTED);
    }

    private ApplicationDocument requireReviewableDocument(AppUser reviewer, Long applicationId, Long documentId) {
        // The parent lock serializes decisions against both other decisions and uploads.
        RegistrationApplication application = findApplication(applicationId, true);
        requireReviewAccess(reviewer, application);
        if (application.getStatus().isTerminal()) {
            throw failure(HttpStatus.CONFLICT, "APPLICATION_REVIEW_CLOSED",
                    "Documents cannot be reviewed after the application is approved or rejected.");
        }
        ApplicationDocument document = findDocument(applicationId, documentId);
        if (document.getStatus() != DocumentStatus.UPLOADED) {
            throw failure(HttpStatus.CONFLICT, "DOCUMENT_ALREADY_REVIEWED",
                    "This document version already has a decision. Upload a new version to request another review.");
        }
        int latestVersion = documentRepository.findMaxDocumentVersion(applicationId, document.getDocumentType());
        if (!Objects.equals(document.getDocumentVersion(), latestVersion)) {
            throw failure(HttpStatus.CONFLICT, "DOCUMENT_VERSION_SUPERSEDED",
                    "A newer version of this document exists. Review the latest version.");
        }
        return document;
    }

    private ApplicationDocumentResponse saveDecision(ApplicationDocument document, AppUser reviewer,
                                                     Instant at, ApprovalAuditAction action) {
        documentRepository.saveAndFlush(document);
        // Document decision and audit event commit together. Full comments stay on the document.
        auditLogRepository.saveAndFlush(ApprovalAuditLog.builder()
                .application(document.getApplication()).actor(reviewer).action(action)
                .eventKey(UUID.randomUUID().toString()).eventAt(at)
                .note("Document " + document.getId() + ", type " + document.getDocumentType()
                        + ", version " + document.getDocumentVersion() + ": " + action)
                .build());
        return toResponse(document);
    }

    private AppUser findEnabledReviewer(String username) {
        AppUser reviewer = userRepository.findByUsername(username).orElseThrow(() ->
                failure(HttpStatus.UNAUTHORIZED, "REVIEWER_NOT_AUTHENTICATED",
                        "The current username has no application account."));
        if (!reviewer.isEnabled()) {
            throw failure(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "Your account is disabled.");
        }
        return reviewer;
    }

    private RegistrationApplication findApplication(Long applicationId, boolean lockForUpdate) {
        return (lockForUpdate
                ? applicationRepository.findByIdForDocumentReviewUpdate(applicationId)
                : applicationRepository.findById(applicationId))
                .orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND",
                        "Registration application not found."));
    }

    private void requireReviewAccess(AppUser reviewer, RegistrationApplication application) {
        if (Objects.equals(reviewer.getId(), application.getApplicant().getId())) {
            throw failure(HttpStatus.FORBIDDEN, "DOCUMENT_SELF_REVIEW_FORBIDDEN",
                    "Applicants cannot review their own documents. Use the applicant endpoints to view them.");
        }
        Set<Long> scope = activeAncestry(application.getSelectedShakha());
        if (assignmentRepository.countCurrentPresidentsInUnits(reviewer.getId(), scope,
                CommitteePositionType.PRESIDENT, LocalDate.now(BUSINESS_ZONE)) == 0) {
            throw failure(HttpStatus.FORBIDDEN, "DOCUMENT_REVIEW_FORBIDDEN",
                    "Document review requires a current president appointment in this branch or a parent committee.");
        }
    }

    private Set<Long> activeAncestry(OrganizationUnit selectedShakha) {
        Set<Long> ids = new LinkedHashSet<>();
        OrganizationUnit current = selectedShakha;
        while (current != null) {
            if (current.getId() == null || !ids.add(current.getId())
                    || !Boolean.TRUE.equals(current.getActive()) || current.getType() == null) {
                throw invalidHierarchy();
            }
            OrganizationUnit parent = current.getParent();
            if (!current.getType().canHaveParent(parent == null ? null : parent.getType())) {
                throw invalidHierarchy();
            }
            current = parent;
        }
        if (ids.isEmpty()) {
            throw invalidHierarchy();
        }
        return ids;
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

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private RegistrationException invalidHierarchy() {
        return failure(HttpStatus.CONFLICT, "INVALID_ORGANIZATION",
                "The application's organization hierarchy must be active and valid.");
    }

    private RegistrationException failure(HttpStatus status, String code, String message) {
        return new RegistrationException(status, code, message);
    }
}

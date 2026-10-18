package com.party.registration.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.party.common.entity.AuditableEntity;
import com.party.user.entity.AppUser;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "application_documents",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_document_storage_key", columnNames = "storage_key"),
                @UniqueConstraint(name = "uk_document_version",
                        columnNames = {"application_id", "document_type", "document_version"})
        },
        indexes = @Index(name = "idx_document_application", columnList = "application_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationDocument extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_document_application"))
    private RegistrationApplication application;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 50)
    private DocumentType documentType;

    @NotNull
    @Min(1)
    @Column(name = "document_version", nullable = false)
    private Integer documentVersion;

    @NotBlank
    @Size(max = 255)
    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @JsonIgnore
    @NotBlank
    @Size(max = 512)
    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @NotBlank
    @Size(max = 100)
    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @NotNull
    @Positive
    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Pattern(regexp = "[a-fA-F0-9]{64}", message = "Use a SHA-256 hex digest")
    @Column(name = "sha256", length = 64)
    private String sha256;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_document_uploader"))
    private AppUser uploadedBy;

    @NotNull
    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    @Setter(AccessLevel.NONE)
    private DocumentStatus status = DocumentStatus.UPLOADED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id",
            foreignKey = @ForeignKey(name = "fk_document_reviewer"))
    @Setter(AccessLevel.NONE)
    private AppUser reviewedBy;

    @Column(name = "reviewed_at")
    @Setter(AccessLevel.NONE)
    private Instant reviewedAt;

    @Size(max = 2000)
    @Column(name = "review_comment", length = 2000)
    @Setter(AccessLevel.NONE)
    private String reviewComment;

    public void verify(AppUser reviewer, Instant at, String comment) {
        recordReview(DocumentStatus.VERIFIED, reviewer, at, comment);
    }

    public void reject(AppUser reviewer, Instant at, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("document rejection reason is required");
        }
        recordReview(DocumentStatus.REJECTED, reviewer, at, reason);
    }

    @JsonIgnore
    @AssertTrue(message = "Document review state, reviewer, and timestamps must agree")
    public boolean isReviewStateValid() {
        if (status == null) {
            return true;
        }
        if (status == DocumentStatus.UPLOADED) {
            return reviewedBy == null && reviewedAt == null && reviewComment == null;
        }
        return reviewedBy != null && reviewedAt != null && uploadedAt != null
                && !reviewedAt.isBefore(uploadedAt)
                && (status != DocumentStatus.REJECTED
                || (reviewComment != null && !reviewComment.isBlank()));
    }

    private void recordReview(DocumentStatus result, AppUser reviewer, Instant at, String comment) {
        if (status != DocumentStatus.UPLOADED) {
            throw new IllegalStateException("This document version already has a review decision");
        }
        Objects.requireNonNull(reviewer, "reviewer is required");
        Objects.requireNonNull(at, "review time is required");
        if (uploadedAt == null || at.isBefore(uploadedAt)) {
            throw new IllegalArgumentException("review cannot precede upload");
        }
        reviewedBy = reviewer;
        reviewedAt = at;
        reviewComment = comment;
        status = result;
    }
}

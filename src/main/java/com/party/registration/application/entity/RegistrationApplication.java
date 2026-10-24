package com.party.registration.application.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.party.committee.entity.AdvocateAssignment;
import com.party.committee.entity.CommitteePosition;
import com.party.common.entity.AuditableEntity;
import com.party.organization.entity.OrganizationUnit;
import com.party.registration.approval.entity.ApprovalStatus;
import com.party.registration.approval.entity.ApprovalStep;
import com.party.registration.audit.entity.ApprovalAuditLog;
import com.party.registration.document.entity.ApplicationDocument;
import com.party.user.entity.AppUser;
import jakarta.persistence.*;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "registration_applications",
        uniqueConstraints = @UniqueConstraint(name = "uk_registration_number",
                columnNames = "application_number"),
        indexes = {
                @Index(name = "idx_registration_applicant", columnList = "applicant_id, status"),
                @Index(name = "idx_registration_position", columnList = "requested_position_id, status")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistrationApplication extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 50)
    @Column(name = "application_number", nullable = false, length = 50)
    private String applicationNumber;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applicant_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_registration_applicant"))
    private AppUser applicant;

    // Assumption: shakha means an existing local organization unit with a committee.
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "selected_shakha_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_registration_shakha"))
    private OrganizationUnit selectedShakha;

    // The exact seat requested, including its position number.
    // A request is not yet an AdvocateAssignment and grants no elevated permission.
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_position_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_registration_position"))
    private CommitteePosition requestedPosition;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @Builder.Default
    @Setter(AccessLevel.NONE)
    private RegistrationStatus status = RegistrationStatus.AWAITING_RECOMMENDATION;

    @Column(name = "submitted_at")
    @Setter(AccessLevel.NONE)
    private Instant submittedAt;

    @Column(name = "decided_at")
    @Setter(AccessLevel.NONE)
    private Instant decidedAt;

    @Size(max = 2000)
    @Column(name = "rejection_reason", length = 2000)
    @Setter(AccessLevel.NONE)
    private String rejectionReason;

    @JsonIgnore
    @OneToMany(mappedBy = "application", fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    @Builder.Default
    private List<ApplicationDocument> documents = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "application", fetch = FetchType.LAZY)
    @OrderBy("sequenceNumber ASC")
    @Builder.Default
    private List<ApprovalStep> approvalSteps = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "application", fetch = FetchType.LAZY)
    @OrderBy("eventAt ASC, id ASC")
    @Builder.Default
    private List<ApprovalAuditLog> auditLogs = new ArrayList<>();

    @JsonIgnore
    @OneToOne(mappedBy = "registrationApplication", fetch = FetchType.LAZY)
    private AdvocateAssignment assignment;

    /** Call only after the service validates the recommendation and review chain. */
    public void submitForReview(Instant at) {
        requireStatus(RegistrationStatus.AWAITING_RECOMMENDATION);
        submittedAt = Objects.requireNonNull(at, "submission time is required");
        status = RegistrationStatus.UNDER_REVIEW;
    }

    /** The service also creates the assignment and grants scoped permissions in this transaction. */
    public void approve(Instant at) {
        requireStatus(RegistrationStatus.UNDER_REVIEW);
        requireDecisionTime(at);
        if (approvalSteps.isEmpty()
                || approvalSteps.stream().anyMatch(step -> step.getStatus() != ApprovalStatus.APPROVED)) {
            throw new IllegalStateException("Every required head must approve before final acceptance");
        }
        decidedAt = at;
        status = RegistrationStatus.APPROVED;
    }

    public void reject(Instant at, String reason) {
        requireStatus(RegistrationStatus.UNDER_REVIEW);
        requireDecisionTime(at);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("rejection reason is required");
        }
        decidedAt = at;
        rejectionReason = reason;
        status = RegistrationStatus.REJECTED;
    }

    @JsonIgnore
    @AssertTrue(message = "Application status and timestamps must agree")
    public boolean isApplicationStateValid() {
        if (status == null) {
            return true;
        }
        if (status == RegistrationStatus.AWAITING_RECOMMENDATION) {
            return submittedAt == null && decidedAt == null && rejectionReason == null;
        }
        if (submittedAt == null) {
            return false;
        }
        if (status == RegistrationStatus.UNDER_REVIEW) {
            return decidedAt == null && rejectionReason == null;
        }
        return decidedAt != null && !decidedAt.isBefore(submittedAt)
                && (status == RegistrationStatus.REJECTED
                ? rejectionReason != null && !rejectionReason.isBlank()
                : rejectionReason == null);
    }

    @JsonIgnore
    @AssertTrue(message = "Requested position must belong to the selected Shakha committee")
    public boolean isSelectedShakhaValid() {
        if (selectedShakha == null || requestedPosition == null) {
            return true;
        }
        if (requestedPosition.getCommittee() == null) {
            return false;
        }
        OrganizationUnit seatUnit = requestedPosition.getCommittee().getOrganizationUnit();
        return seatUnit != null && (seatUnit == selectedShakha
                || (seatUnit.getId() != null && Objects.equals(seatUnit.getId(), selectedShakha.getId())));
    }

    @JsonIgnore
    @AssertTrue(message = "An approved application requires all its head approvals")
    public boolean isRequiredApprovalsValid() {
        return status != RegistrationStatus.APPROVED || (!approvalSteps.isEmpty()
                && approvalSteps.stream().allMatch(step -> step.getStatus() == ApprovalStatus.APPROVED));
    }

    private void requireStatus(RegistrationStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Expected " + expected + " but application is " + status);
        }
    }

    private void requireDecisionTime(Instant at) {
        Objects.requireNonNull(at, "decision time is required");
        if (submittedAt == null || at.isBefore(submittedAt)) {
            throw new IllegalArgumentException("decision cannot precede submission");
        }
    }
}

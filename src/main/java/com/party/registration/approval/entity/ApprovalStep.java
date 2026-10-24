package com.party.registration.approval.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.party.committee.entity.Committee;
import com.party.common.entity.AuditableEntity;
import com.party.registration.application.entity.RegistrationApplication;
import com.party.registration.approval.policy.ApprovalPolicy;
import com.party.user.entity.AppUser;
import jakarta.persistence.*;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "approval_steps",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_approval_application_sequence",
                        columnNames = {"application_id", "sequence_number"}),
                @UniqueConstraint(name = "uk_approval_application_committee",
                        columnNames = {"application_id", "reviewer_committee_id"})
        },
        indexes = {
                @Index(name = "idx_approval_reviewer_status", columnList = "assigned_reviewer_id, status"),
                @Index(name = "idx_approval_deadline", columnList = "status, due_at"),
                @Index(name = "idx_approval_escalated_status", columnList = "escalated_to_id, status")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalStep extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_approval_application"))
    private RegistrationApplication application;

    @NotNull
    @Min(1)
    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_committee_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_approval_committee"))
    private Committee reviewerCommittee;

    // Nullable only while WAITING. Resolve the active committee PRESIDENT in the service.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_reviewer_id",
            foreignKey = @ForeignKey(name = "fk_approval_assigned_reviewer"))
    @Setter(AccessLevel.NONE)
    private AppUser assignedReviewer;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    @Setter(AccessLevel.NONE)
    private ApprovalStatus status = ApprovalStatus.WAITING;

    @Column(name = "assigned_at")
    @Setter(AccessLevel.NONE)
    private Instant assignedAt;

    @Column(name = "due_at")
    @Setter(AccessLevel.NONE)
    private Instant dueAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_id",
            foreignKey = @ForeignKey(name = "fk_approval_decided_by"))
    @Setter(AccessLevel.NONE)
    private AppUser decidedBy;

    @Column(name = "decided_at")
    @Setter(AccessLevel.NONE)
    private Instant decidedAt;

    @Size(max = 2000)
    @Column(name = "decision_comment", length = 2000)
    @Setter(AccessLevel.NONE)
    private String decisionComment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "escalated_to_id",
            foreignKey = @ForeignKey(name = "fk_approval_escalated_to"))
    @Setter(AccessLevel.NONE)
    private AppUser escalatedTo;

    @Column(name = "escalated_at")
    @Setter(AccessLevel.NONE)
    private Instant escalatedAt;

    /** Activate only the next required stage. Authorization and ordering belong to the service. */
    public void activate(AppUser reviewer, Instant at) {
        if (status != ApprovalStatus.WAITING) {
            throw new IllegalStateException("Only a waiting step can be activated");
        }
        requireEligibleIdentity(reviewer);
        Objects.requireNonNull(at, "assignment time is required");
        assignedReviewer = reviewer;
        assignedAt = at;
        dueAt = ApprovalPolicy.deadlineFor(at);
        status = ApprovalStatus.PENDING;
    }

    /** Administrative reassignment preserves the original deadline and escalation history. */
    public void reassignTo(AppUser reviewer) {
        if (!status.canBeDecided()) {
            throw new IllegalStateException("Only an open step can be reassigned");
        }
        requireEligibleIdentity(reviewer);
        assignedReviewer = reviewer;
    }

    /** Idempotent: repeated scheduler runs do not repeatedly change the state. */
    public boolean markOverdue(Instant now) {
        if (status != ApprovalStatus.PENDING || !ApprovalPolicy.isOverdue(dueAt, now)) {
            return false;
        }
        status = ApprovalStatus.OVERDUE;
        return true;
    }

    /**
     * Escalation gives a senior visibility; it does not replace the required head's decision.
     * The original assignedAt and dueAt are preserved.
     */
    public boolean escalateTo(AppUser senior, Instant now) {
        Objects.requireNonNull(senior, "escalation recipient is required");
        Objects.requireNonNull(now, "escalation time is required");
        if ((status != ApprovalStatus.PENDING && status != ApprovalStatus.OVERDUE)
                || escalatedAt != null || !ApprovalPolicy.isOverdue(dueAt, now)) {
            return false;
        }
        requireEligibleIdentity(senior);
        if (sameUser(senior, assignedReviewer)) {
            throw new IllegalArgumentException("Escalation must go to a different authorized senior");
        }
        status = ApprovalStatus.OVERDUE;
        escalatedTo = senior;
        escalatedAt = now;
        return true;
    }

    public void approve(AppUser actor, Instant at, String comment) {
        recordDecision(ApprovalStatus.APPROVED, actor, at, comment);
    }

    public void reject(AppUser actor, Instant at, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("rejection reason is required");
        }
        recordDecision(ApprovalStatus.REJECTED, actor, at, reason);
    }

    @JsonIgnore
    @AssertTrue(message = "Approval status, assignment, deadline, decision, and escalation must agree")
    public boolean isTimelineValid() {
        if (status == null) {
            return true;
        }
        if (status == ApprovalStatus.WAITING) {
            return assignedReviewer == null && assignedAt == null && dueAt == null
                    && decidedBy == null && decidedAt == null && decisionComment == null
                    && escalatedTo == null && escalatedAt == null;
        }
        if (assignedReviewer == null || assignedAt == null || dueAt == null
                || !dueAt.equals(ApprovalPolicy.deadlineFor(assignedAt))) {
            return false;
        }
        if (application != null && (sameUser(assignedReviewer, application.getApplicant())
                || sameUser(escalatedTo, application.getApplicant()))) {
            return false;
        }
        boolean escalationValid = (escalatedTo == null && escalatedAt == null)
                || (escalatedTo != null && escalatedAt != null
                && !escalatedAt.isBefore(dueAt));
        if (!escalationValid || (status == ApprovalStatus.PENDING && escalatedAt != null)) {
            return false;
        }
        if (status.canBeDecided()) {
            return decidedBy == null && decidedAt == null && decisionComment == null;
        }
        return decidedBy != null && sameUser(decidedBy, assignedReviewer) && decidedAt != null
                && !decidedAt.isBefore(assignedAt)
                && (status != ApprovalStatus.REJECTED
                || (decisionComment != null && !decisionComment.isBlank()));
    }

    private void recordDecision(ApprovalStatus result, AppUser actor, Instant at, String comment) {
        if (!status.canBeDecided()) {
            throw new IllegalStateException("Only pending or overdue steps can receive a decision");
        }
        Objects.requireNonNull(actor, "decision actor is required");
        Objects.requireNonNull(at, "decision time is required");
        if (!sameUser(actor, assignedReviewer)) {
            throw new IllegalArgumentException("The assigned committee head must make this decision");
        }
        if (assignedAt == null || at.isBefore(assignedAt)) {
            throw new IllegalArgumentException("decision cannot precede assignment");
        }
        decidedBy = actor;
        decidedAt = at;
        decisionComment = comment;
        status = result;
    }

    private void requireEligibleIdentity(AppUser user) {
        Objects.requireNonNull(user, "reviewer is required");
        if (application != null && sameUser(user, application.getApplicant())) {
            throw new IllegalArgumentException("Applicants cannot review or receive escalation of their own application");
        }
    }

    private static boolean sameUser(AppUser first, AppUser second) {
        return first != null && second != null && (first == second
                || (first.getId() != null && Objects.equals(first.getId(), second.getId())));
    }
}

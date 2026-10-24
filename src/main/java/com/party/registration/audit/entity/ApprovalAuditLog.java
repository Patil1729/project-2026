package com.party.registration.audit.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.party.common.entity.AuditableEntity;
import com.party.registration.application.entity.RegistrationApplication;
import com.party.registration.approval.entity.ApprovalStep;
import com.party.user.entity.AppUser;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/** Append-only through the application service; scheduler events have a null actor. */
@Entity
@Immutable
@Table(name = "approval_audit_logs",
        uniqueConstraints = @UniqueConstraint(name = "uk_approval_audit_event_key", columnNames = "event_key"),
        indexes = @Index(name = "idx_audit_application_time", columnList = "application_id, event_at"))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalAuditLog extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_audit_application"))
    private RegistrationApplication application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approval_step_id",
            foreignKey = @ForeignKey(name = "fk_audit_step"))
    private ApprovalStep approvalStep;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id",
            foreignKey = @ForeignKey(name = "fk_audit_actor"))
    private AppUser actor;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ApprovalAuditAction action;

    // UUID for ordinary events; deterministic key for one-time step escalation events.
    @NotBlank
    @Size(max = 150)
    @Column(name = "event_key", nullable = false, length = 150)
    private String eventKey;

    @NotNull
    @Column(name = "event_at", nullable = false)
    private Instant eventAt;

    @Size(max = 2000)
    @Column(length = 2000)
    private String note;
}

package com.party.committee.appointment.audit.entity;

import com.party.common.entity.AuditableEntity;
import com.party.committee.entity.AdvocateAssignment;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.Immutable;
import java.time.Instant;

/** Separate history because a bootstrap appointment has no registration application. */
@Entity
@Immutable
@Table(name = "appointment_audit_logs", indexes = @Index(name = "idx_appointment_audit_time", columnList = "assignment_id, event_at"))
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentAuditLog extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @NotNull @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false, foreignKey = @ForeignKey(name = "fk_appointment_audit_assignment"))
    private AdvocateAssignment assignment;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_assignment_id", foreignKey = @ForeignKey(name = "fk_appointment_audit_related"))
    private AdvocateAssignment relatedAssignment;
    @NotNull @Enumerated(EnumType.STRING) @Column(nullable = false, length = 50)
    private AppointmentAuditAction action;
    @NotNull @Column(name = "event_at", nullable = false) private Instant eventAt;
    @NotBlank @Size(max = 2000) @Column(nullable = false, length = 2000) private String reason;
}

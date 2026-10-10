package com.party.committee.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.party.common.entity.AuditableEntity;
import com.party.registration.entity.RegistrationApplication;
import com.party.registration.entity.RegistrationStatus;
import com.party.user.entity.AppUser;
import jakarta.persistence.*;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "advocate_assignments",
        uniqueConstraints = @UniqueConstraint(name = "uk_assignment_application",
                columnNames = "registration_application_id"),
        indexes = {
                @Index(name = "idx_assignment_position_active", columnList = "committee_position_id, active"),
                @Index(name = "idx_assignment_user_active", columnList = "user_id, active")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdvocateAssignment extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "committee_position_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_assignment_position"))
    private CommitteePosition committeePosition;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_assignment_advocate"))
    private AppUser advocate;

    // Null for authorized bootstrap/admin appointments.
    // A successful registration produces at most one appointment.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registration_application_id", unique = true,
            foreignKey = @ForeignKey(name = "fk_assignment_registration"))
    private RegistrationApplication registrationApplication;

    @NotNull
    @Column(name = "appointed_date", nullable = false)
    private LocalDate appointedDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    // The accompanying MySQL guard enforces one active appointment per seat.
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @JsonIgnore
    @AssertTrue(message = "endDate must be on or after appointedDate")
    public boolean isDateRangeValid() {
        return appointedDate == null || endDate == null || !endDate.isBefore(appointedDate);
    }

    @JsonIgnore
    @AssertTrue(message = "Active appointments have no endDate; ended appointments require one")
    public boolean isAppointmentStatusValid() {
        return active ? endDate == null : endDate != null;
    }

    @JsonIgnore
    @AssertTrue(message = "A registration appointment must match its approved application's applicant and seat")
    public boolean isSourceApplicationValid() {
        if (registrationApplication == null) {
            return true;
        }
        AppUser applicant = registrationApplication.getApplicant();
        CommitteePosition requested = registrationApplication.getRequestedPosition();
        boolean matchingUser = applicant != null && advocate != null && (applicant == advocate
                || (applicant.getId() != null && Objects.equals(applicant.getId(), advocate.getId())));
        boolean matchingSeat = requested != null && committeePosition != null && (requested == committeePosition
                || (requested.getId() != null && Objects.equals(requested.getId(), committeePosition.getId())));
        return registrationApplication.getStatus() == RegistrationStatus.APPROVED
                && matchingUser && matchingSeat;
    }

    public void endOn(LocalDate date) {
        Objects.requireNonNull(date, "end date is required");
        if (appointedDate == null || date.isBefore(appointedDate)) {
            throw new IllegalArgumentException("end date must be on or after appointed date");
        }
        endDate = date;
        active = false;
    }
}

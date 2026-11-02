package com.party.help.entity;
import com.party.common.entity.AuditableEntity;
import com.party.organization.entity.OrganizationUnit;
import com.party.committee.entity.AdvocateAssignment;
import com.party.user.entity.AppUser;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name="help_requests",uniqueConstraints={@UniqueConstraint(name="uk_help_reference",columnNames="reference"),@UniqueConstraint(name="uk_help_submission",columnNames={"requester_id","submission_key"})}, indexes={@Index(name="idx_help_requester",columnList="requester_id,status"),@Index(name="idx_help_assignee",columnList="assigned_appointment_id,status"),@Index(name="idx_help_area_status",columnList="organization_unit_id,status"),@Index(name="idx_help_activity",columnList="urgent,last_activity_at")})
@Getter @Setter @NoArgsConstructor
public class HelpRequest extends AuditableEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=40) private String reference;
    @Column(name="submission_key",nullable=false,length=36) private String submissionKey;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="requester_id",nullable=false) private AppUser requester;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="organization_unit_id",nullable=false) private OrganizationUnit organizationUnit;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="routing_unit_id",nullable=false) private OrganizationUnit routingUnit;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="assigned_appointment_id") private AdvocateAssignment assignedAppointment;
    @Column(name="contact_name",nullable=false,length=150) private String contactName;
    @Column(name="contact_mobile",nullable=false,length=16) private String contactMobile;
    @Column(nullable=false,length=200) private String locality;
    @Column(nullable=false,length=150) private String subject;
    @Column(nullable=false,length=4000) private String description;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private HelpCategory category;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private HelpStatus status;
    @Column(nullable=false) private boolean urgent;
    @Column(name="consent_at",nullable=false) private Instant consentAt;
    @Column(name="last_activity_at",nullable=false) private Instant lastActivityAt;
    @Column(name="resolution_note",length=2000) private String resolutionNote;
    @Column(name="resolved_at") private Instant resolvedAt;
}

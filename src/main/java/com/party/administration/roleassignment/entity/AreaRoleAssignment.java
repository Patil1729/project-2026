package com.party.administration.roleassignment.entity;

import com.party.common.entity.AuditableEntity;
import com.party.organization.entity.OrganizationUnit;
import com.party.user.entity.AppUser;
import com.party.user.entity.Role;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "area_role_assignments", uniqueConstraints = @UniqueConstraint(name = "uk_area_role_scope", columnNames = {"user_id", "role_id", "organization_unit_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AreaRoleAssignment extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private AppUser user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "role_id", nullable = false) private Role role;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "organization_unit_id", nullable = false) private OrganizationUnit organizationUnit;
    @Column(nullable = false) private boolean active;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "assigned_by_id", nullable = false) private AppUser assignedBy;
    @Column(name = "assigned_at", nullable = false) private Instant assignedAt;
    @Column(nullable = false, length = 2000) private String reason;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "revoked_by_id") private AppUser revokedBy;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "revocation_reason", length = 2000) private String revocationReason;
}

package com.party.administration.roleassignment.audit.entity;

import com.party.administration.roleassignment.entity.AreaRoleAssignment;
import com.party.common.entity.AuditableEntity;
import com.party.user.entity.AppUser;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "area_role_audit_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AreaRoleAuditLog extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "assignment_id", nullable = false) private AreaRoleAssignment assignment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "actor_id", nullable = false) private AppUser actor;
    @Column(nullable = false, length = 30) private String action;
    @Column(name = "event_at", nullable = false) private Instant eventAt;
    @Column(nullable = false, length = 2000) private String reason;
}

-- Role definitions are permission names. Only a scoped assignment below grants area administration.
INSERT INTO roles (created_at, updated_at, version, name)
SELECT UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0, seed.name
FROM (
 SELECT 'STATE_ADMIN' AS name UNION ALL SELECT 'DIVISION_ADMIN'
 UNION ALL SELECT 'DISTRICT_ADMIN' UNION ALL SELECT 'DIVISIONAL_DISTRICT_ADMIN'
 UNION ALL SELECT 'CONSTITUENCY_ADMIN' UNION ALL SELECT 'TALUKA_ADMIN'
 UNION ALL SELECT 'MUNICIPAL_CORPORATION_ADMIN' UNION ALL SELECT 'MUNICIPAL_COUNCIL_ADMIN'
 UNION ALL SELECT 'NAGAR_PANCHAYAT_ADMIN'
) seed WHERE NOT EXISTS (SELECT 1 FROM roles r WHERE r.name = seed.name);

CREATE TABLE area_role_assignments (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL, version BIGINT NOT NULL,
 user_id BIGINT NOT NULL, role_id BIGINT NOT NULL, organization_unit_id BIGINT NOT NULL,
 active BIT(1) NOT NULL, assigned_by_id BIGINT NOT NULL, assigned_at DATETIME(6) NOT NULL,
 reason VARCHAR(2000) NOT NULL, revoked_by_id BIGINT NULL, revoked_at DATETIME(6) NULL,
 revocation_reason VARCHAR(2000) NULL,
 CONSTRAINT uk_area_role_scope UNIQUE (user_id, role_id, organization_unit_id),
 CONSTRAINT fk_area_role_user FOREIGN KEY (user_id) REFERENCES users(id),
 CONSTRAINT fk_area_role_role FOREIGN KEY (role_id) REFERENCES roles(id),
 CONSTRAINT fk_area_role_unit FOREIGN KEY (organization_unit_id) REFERENCES organization_units(id),
 CONSTRAINT fk_area_role_assigned_by FOREIGN KEY (assigned_by_id) REFERENCES users(id),
 CONSTRAINT fk_area_role_revoked_by FOREIGN KEY (revoked_by_id) REFERENCES users(id),
 INDEX idx_area_role_user_active (user_id, active)
);

CREATE TABLE area_role_audit_logs (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL, version BIGINT NOT NULL,
 assignment_id BIGINT NOT NULL, actor_id BIGINT NOT NULL,
 action VARCHAR(30) NOT NULL, event_at DATETIME(6) NOT NULL, reason VARCHAR(2000) NOT NULL,
 CONSTRAINT fk_area_role_audit_assignment FOREIGN KEY (assignment_id) REFERENCES area_role_assignments(id),
 CONSTRAINT fk_area_role_audit_actor FOREIGN KEY (actor_id) REFERENCES users(id),
 INDEX idx_area_role_audit_assignment (assignment_id, event_at, id)
);
-- Existing unscoped *_ADMIN flags are deliberately not converted to grants.
-- PARTY_HEAD must choose and audit an explicit organisation scope for each administrator.

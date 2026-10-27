-- Preserve V1-V4: only this new migration is applied to an existing database.
CREATE TABLE appointment_audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    related_assignment_id BIGINT NULL,
    action VARCHAR(50) NOT NULL,
    event_at DATETIME(6) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_appointment_audit_time (assignment_id, event_at),
    CONSTRAINT fk_appointment_audit_assignment FOREIGN KEY (assignment_id) REFERENCES advocate_assignments (id),
    CONSTRAINT fk_appointment_audit_related FOREIGN KEY (related_assignment_id) REFERENCES advocate_assignments (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Record the existing snapshot without inventing earlier actions or operator identities.
INSERT INTO appointment_audit_logs
    (created_at, updated_at, version, assignment_id, related_assignment_id, action, event_at, reason)
SELECT UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0, id, NULL, 'EXISTING_APPOINTMENT_IMPORTED', UTC_TIMESTAMP(6),
       CONCAT('Existing appointment captured by V5; original appointed date: ', appointed_date,
              '. Earlier appointment actions were not recorded here.')
FROM advocate_assignments;

CREATE INDEX idx_registration_admin_status_created ON registration_applications (status, created_at, id);
CREATE INDEX idx_registration_admin_unit_created ON registration_applications (selected_shakha_id, created_at, id);
CREATE INDEX idx_approval_admin_committee_status ON approval_steps (reviewer_committee_id, status);

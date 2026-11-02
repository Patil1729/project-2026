-- Additive migration. Keep V1-V7 unchanged; no existing records are deleted.
CREATE TABLE gallery_entries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL, version BIGINT NOT NULL,
    organization_unit_id BIGINT NOT NULL, created_by BIGINT NOT NULL, updated_by BIGINT NOT NULL,
    title VARCHAR(150) NOT NULL, description VARCHAR(2000) NOT NULL, event_date DATE NOT NULL,
    published BIT NOT NULL DEFAULT b'0', deleted BIT NOT NULL DEFAULT b'0',
    storage_key VARCHAR(500) NOT NULL, size_bytes BIGINT NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_gallery_public (published, deleted, event_date), INDEX idx_gallery_area (organization_unit_id),
    CONSTRAINT fk_gallery_unit FOREIGN KEY (organization_unit_id) REFERENCES organization_units(id),
    CONSTRAINT fk_gallery_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_gallery_updated_by FOREIGN KEY (updated_by) REFERENCES users(id),
    CONSTRAINT chk_gallery_size CHECK (size_bytes > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE help_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL, version BIGINT NOT NULL,
    reference VARCHAR(40) NOT NULL, submission_key VARCHAR(36) NOT NULL,
    requester_id BIGINT NOT NULL, organization_unit_id BIGINT NOT NULL, routing_unit_id BIGINT NOT NULL,
    assigned_appointment_id BIGINT NULL,
    contact_name VARCHAR(150) NOT NULL, contact_mobile VARCHAR(16) NOT NULL, locality VARCHAR(200) NOT NULL,
    subject VARCHAR(150) NOT NULL, description VARCHAR(4000) NOT NULL,
    category VARCHAR(40) NOT NULL, status VARCHAR(30) NOT NULL, urgent BIT NOT NULL DEFAULT b'0',
    consent_at DATETIME(6) NOT NULL, last_activity_at DATETIME(6) NOT NULL,
    resolution_note VARCHAR(2000) NULL, resolved_at DATETIME(6) NULL,
    PRIMARY KEY (id), UNIQUE KEY uk_help_reference (reference), UNIQUE KEY uk_help_submission (requester_id,submission_key),
    INDEX idx_help_requester (requester_id,status), INDEX idx_help_assignee (assigned_appointment_id,status),
    INDEX idx_help_area_status (organization_unit_id,status), INDEX idx_help_activity (urgent,last_activity_at),
    CONSTRAINT fk_help_requester FOREIGN KEY (requester_id) REFERENCES users(id),
    CONSTRAINT fk_help_origin FOREIGN KEY (organization_unit_id) REFERENCES organization_units(id),
    CONSTRAINT fk_help_routing_unit FOREIGN KEY (routing_unit_id) REFERENCES organization_units(id),
    CONSTRAINT fk_help_appointment FOREIGN KEY (assigned_appointment_id) REFERENCES advocate_assignments(id),
    CONSTRAINT chk_help_status CHECK (status IN ('UNASSIGNED','ASSIGNED','IN_PROGRESS','RESOLVED','CLOSED')),
    CONSTRAINT chk_help_category CHECK (category IN ('POLICE_FIR','ARREST_DETENTION','POLITICAL_HARASSMENT','WOMEN_SAFETY','PROPERTY_DISPUTE','OTHER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE help_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL, version BIGINT NOT NULL,
    help_request_id BIGINT NOT NULL, actor_id BIGINT NOT NULL,
    action VARCHAR(40) NOT NULL, note VARCHAR(2000) NOT NULL,
    from_advocate VARCHAR(150) NULL, to_advocate VARCHAR(150) NULL,
    PRIMARY KEY (id), INDEX idx_help_event_request (help_request_id,id),
    CONSTRAINT fk_help_event_request FOREIGN KEY (help_request_id) REFERENCES help_requests(id),
    CONSTRAINT fk_help_event_actor FOREIGN KEY (actor_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE help_attachments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL, version BIGINT NOT NULL,
    help_request_id BIGINT NOT NULL, uploaded_by BIGINT NOT NULL,
    original_filename VARCHAR(255) NOT NULL, storage_key VARCHAR(500) NOT NULL,
    content_type VARCHAR(100) NOT NULL, size_bytes BIGINT NOT NULL,
    PRIMARY KEY (id), INDEX idx_help_attachment_request (help_request_id),
    CONSTRAINT fk_help_attachment_request FOREIGN KEY (help_request_id) REFERENCES help_requests(id),
    CONSTRAINT fk_help_attachment_uploader FOREIGN KEY (uploaded_by) REFERENCES users(id),
    CONSTRAINT chk_help_attachment_size CHECK (size_bytes > 0 AND size_bytes <= 5242880)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

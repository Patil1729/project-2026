-- Initial schema aligned with the supplied party_2026 MySQL 8.0.36 export.
-- Creates structure only: no rows, DROP statements, USE, or constraint-disabling commands.
-- Empty databases: Flyway executes this script, then V2.
-- Existing verified party_2026 database: baseline at version 1 so this script is skipped.
-- AUTO_INCREMENT row counters from the export are intentionally omitted.

CREATE TABLE `addresses` (
                             `id` bigint NOT NULL AUTO_INCREMENT,
                             `created_at` datetime(6) NOT NULL,
                             `updated_at` datetime(6) NOT NULL,
                             `version` bigint NOT NULL,
                             `area` varchar(150) DEFAULT NULL,
                             `building_name` varchar(150) DEFAULT NULL,
                             `city` varchar(150) DEFAULT NULL,
                             `country` varchar(100) NOT NULL,
                             `district` varchar(150) NOT NULL,
                             `house_or_flat_number` varchar(50) DEFAULT NULL,
                             `landmark` varchar(200) DEFAULT NULL,
                             `pin_code` varchar(6) NOT NULL,
                             `state` varchar(100) NOT NULL,
                             `street` varchar(200) DEFAULT NULL,
                             `taluka` varchar(150) NOT NULL,
                             `village` varchar(150) DEFAULT NULL,
                             PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `advocate_assignments` (
                                        `id` bigint NOT NULL AUTO_INCREMENT,
                                        `created_at` datetime(6) NOT NULL,
                                        `updated_at` datetime(6) NOT NULL,
                                        `version` bigint NOT NULL,
                                        `active` bit(1) NOT NULL,
                                        `appointed_date` date NOT NULL,
                                        `end_date` date DEFAULT NULL,
                                        `user_id` bigint NOT NULL,
                                        `committee_position_id` bigint NOT NULL,
                                        `registration_application_id` bigint DEFAULT NULL,
                                        PRIMARY KEY (`id`),
                                        UNIQUE KEY `uk_assignment_application` (`registration_application_id`),
                                        KEY `idx_assignment_position_active` (`committee_position_id`,`active`),
                                        KEY `idx_assignment_user_active` (`user_id`,`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `application_documents` (
                                         `id` bigint NOT NULL AUTO_INCREMENT,
                                         `created_at` datetime(6) NOT NULL,
                                         `updated_at` datetime(6) NOT NULL,
                                         `version` bigint NOT NULL,
                                         `content_type` varchar(100) NOT NULL,
                                         `document_type` enum('AADHAAR_PROOF','ADDRESS_PROOF','EDUCATION_CERTIFICATE','IDENTITY_PROOF','OTHER','RECOMMENDATION_LETTER','VOTER_ID') NOT NULL,
                                         `document_version` int NOT NULL,
                                         `original_filename` varchar(255) NOT NULL,
                                         `review_comment` varchar(2000) DEFAULT NULL,
                                         `reviewed_at` datetime(6) DEFAULT NULL,
                                         `sha256` varchar(64) DEFAULT NULL,
                                         `size_bytes` bigint NOT NULL,
                                         `status` enum('REJECTED','UPLOADED','VERIFIED') NOT NULL,
                                         `storage_key` varchar(512) NOT NULL,
                                         `uploaded_at` datetime(6) NOT NULL,
                                         `application_id` bigint NOT NULL,
                                         `reviewed_by_id` bigint DEFAULT NULL,
                                         `uploaded_by_id` bigint NOT NULL,
                                         PRIMARY KEY (`id`),
                                         UNIQUE KEY `uk_document_storage_key` (`storage_key`),
                                         UNIQUE KEY `uk_document_version` (`application_id`,`document_type`,`document_version`),
                                         KEY `idx_document_application` (`application_id`),
                                         KEY `fk_document_reviewer` (`reviewed_by_id`),
                                         KEY `fk_document_uploader` (`uploaded_by_id`),
                                         CONSTRAINT `application_documents_chk_1` CHECK ((`document_version` >= 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `approval_audit_logs` (
                                       `id` bigint NOT NULL AUTO_INCREMENT,
                                       `created_at` datetime(6) NOT NULL,
                                       `updated_at` datetime(6) NOT NULL,
                                       `version` bigint NOT NULL,
                                       `action` enum('APPLICATION_APPROVED','APPLICATION_REJECTED','APPLICATION_SUBMITTED','DOCUMENT_REJECTED','DOCUMENT_UPLOADED','DOCUMENT_VERIFIED','REGISTRATION_CREATED','REVIEWER_MISSING','REVIEWER_REASSIGNED','STEP_ACTIVATED','STEP_APPROVED','STEP_ESCALATED','STEP_OVERDUE','STEP_REJECTED') NOT NULL,
                                       `event_at` datetime(6) NOT NULL,
                                       `event_key` varchar(150) NOT NULL,
                                       `note` varchar(2000) DEFAULT NULL,
                                       `actor_id` bigint DEFAULT NULL,
                                       `application_id` bigint NOT NULL,
                                       `approval_step_id` bigint DEFAULT NULL,
                                       PRIMARY KEY (`id`),
                                       UNIQUE KEY `uk_approval_audit_event_key` (`event_key`),
                                       KEY `idx_audit_application_time` (`application_id`,`event_at`),
                                       KEY `fk_audit_actor` (`actor_id`),
                                       KEY `fk_audit_step` (`approval_step_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `approval_steps` (
                                  `id` bigint NOT NULL AUTO_INCREMENT,
                                  `created_at` datetime(6) NOT NULL,
                                  `updated_at` datetime(6) NOT NULL,
                                  `version` bigint NOT NULL,
                                  `assigned_at` datetime(6) DEFAULT NULL,
                                  `decided_at` datetime(6) DEFAULT NULL,
                                  `decision_comment` varchar(2000) DEFAULT NULL,
                                  `due_at` datetime(6) DEFAULT NULL,
                                  `escalated_at` datetime(6) DEFAULT NULL,
                                  `sequence_number` int NOT NULL,
                                  `status` enum('APPROVED','OVERDUE','PENDING','REJECTED','WAITING') NOT NULL,
                                  `application_id` bigint NOT NULL,
                                  `assigned_reviewer_id` bigint DEFAULT NULL,
                                  `decided_by_id` bigint DEFAULT NULL,
                                  `escalated_to_id` bigint DEFAULT NULL,
                                  `reviewer_committee_id` bigint NOT NULL,
                                  PRIMARY KEY (`id`),
                                  UNIQUE KEY `uk_approval_application_sequence` (`application_id`,`sequence_number`),
                                  UNIQUE KEY `uk_approval_application_committee` (`application_id`,`reviewer_committee_id`),
                                  KEY `idx_approval_reviewer_status` (`assigned_reviewer_id`,`status`),
                                  KEY `idx_approval_deadline` (`status`,`due_at`),
                                  KEY `idx_approval_escalated_status` (`escalated_to_id`,`status`),
                                  KEY `fk_approval_decided_by` (`decided_by_id`),
                                  KEY `fk_approval_committee` (`reviewer_committee_id`),
                                  CONSTRAINT `approval_steps_chk_1` CHECK ((`sequence_number` >= 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `assembly_constituencies` (
                                           `id` bigint NOT NULL AUTO_INCREMENT,
                                           `created_at` datetime(6) NOT NULL,
                                           `updated_at` datetime(6) NOT NULL,
                                           `version` bigint NOT NULL,
                                           `active` bit(1) NOT NULL,
                                           `constituency_number` int NOT NULL,
                                           `name` varchar(255) NOT NULL,
                                           `state_code` varchar(10) NOT NULL,
                                           PRIMARY KEY (`id`),
                                           UNIQUE KEY `uk_assembly_state_number` (`state_code`,`constituency_number`),
                                           CONSTRAINT `assembly_constituencies_chk_1` CHECK ((`constituency_number` >= 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `committee_positions` (
                                       `id` bigint NOT NULL AUTO_INCREMENT,
                                       `created_at` datetime(6) NOT NULL,
                                       `updated_at` datetime(6) NOT NULL,
                                       `version` bigint NOT NULL,
                                       `active` bit(1) NOT NULL,
                                       `position_number` int NOT NULL,
                                       `position_type` enum('DEPUTY_TREASURER','JOINT_SECRETARY','MEMBER','PRESIDENT','SECRETARY','TREASURER','VICE_PRESIDENT','WORKING_PRESIDENT') NOT NULL,
                                       `committee_id` bigint NOT NULL,
                                       `responsibility_unit_id` bigint DEFAULT NULL,
                                       PRIMARY KEY (`id`),
                                       UNIQUE KEY `uk_committee_position_number` (`committee_id`,`position_type`,`position_number`),
                                       UNIQUE KEY `uk_committee_position_scope` (`committee_id`,`position_type`,`responsibility_unit_id`),
                                       KEY `fk_position_responsibility_unit` (`responsibility_unit_id`),
                                       CONSTRAINT `committee_positions_chk_1` CHECK ((`position_number` >= 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `committees` (
                              `id` bigint NOT NULL AUTO_INCREMENT,
                              `created_at` datetime(6) NOT NULL,
                              `updated_at` datetime(6) NOT NULL,
                              `version` bigint NOT NULL,
                              `active` bit(1) NOT NULL,
                              `code` varchar(100) NOT NULL,
                              `committee_type` enum('ASSEMBLY_COMMITTEE','DISTRICT_COMMITTEE','DIVISIONAL_COMMITTEE','DIVISIONAL_DISTRICT_COMMITTEE','MUNICIPAL_CORPORATION_COMMITTEE','MUNICIPAL_COUNCIL_COMMITTEE','NAGAR_PANCHAYAT_COMMITTEE','STATE_COMMITTEE','TALUKA_COMMITTEE') NOT NULL,
                              `name` varchar(255) NOT NULL,
                              `organization_unit_id` bigint NOT NULL,
                              PRIMARY KEY (`id`),
                              UNIQUE KEY `uk_committee_code` (`code`),
                              UNIQUE KEY `uk_committee_organization_unit` (`organization_unit_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `organization_units` (
                                      `id` bigint NOT NULL AUTO_INCREMENT,
                                      `created_at` datetime(6) NOT NULL,
                                      `updated_at` datetime(6) NOT NULL,
                                      `version` bigint NOT NULL,
                                      `active` bit(1) NOT NULL,
                                      `code` varchar(100) NOT NULL,
                                      `name` varchar(255) NOT NULL,
                                      `type` enum('ASSEMBLY_CONSTITUENCY','DISTRICT','DIVISION','DIVISIONAL_DISTRICT','MUNICIPAL_CORPORATION','MUNICIPAL_COUNCIL','NAGAR_PANCHAYAT','PARTY','STATE','TALUKA') NOT NULL,
                                      `parent_id` bigint DEFAULT NULL,
                                      PRIMARY KEY (`id`),
                                      UNIQUE KEY `uk_organization_unit_code` (`code`),
                                      KEY `idx_organization_unit_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `registration_applications` (
                                             `id` bigint NOT NULL AUTO_INCREMENT,
                                             `created_at` datetime(6) NOT NULL,
                                             `updated_at` datetime(6) NOT NULL,
                                             `version` bigint NOT NULL,
                                             `application_number` varchar(50) NOT NULL,
                                             `decided_at` datetime(6) DEFAULT NULL,
                                             `rejection_reason` varchar(2000) DEFAULT NULL,
                                             `status` enum('APPROVED','AWAITING_RECOMMENDATION','REJECTED','UNDER_REVIEW') NOT NULL,
                                             `submitted_at` datetime(6) DEFAULT NULL,
                                             `applicant_id` bigint NOT NULL,
                                             `requested_position_id` bigint NOT NULL,
                                             `selected_shakha_id` bigint NOT NULL,
                                             PRIMARY KEY (`id`),
                                             UNIQUE KEY `uk_registration_number` (`application_number`),
                                             KEY `idx_registration_applicant` (`applicant_id`,`status`),
                                             KEY `idx_registration_position` (`requested_position_id`,`status`),
                                             KEY `fk_registration_shakha` (`selected_shakha_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `roles` (
                         `id` bigint NOT NULL AUTO_INCREMENT,
                         `created_at` datetime(6) NOT NULL,
                         `updated_at` datetime(6) NOT NULL,
                         `version` bigint NOT NULL,
                         `name` enum('ADVOCATE','APPLICANT','CONSTITUENCY_ADMIN','DISTRICT_ADMIN','DIVISIONAL_DISTRICT_ADMIN','DIVISION_ADMIN','FIELD_WORKER','MUNICIPAL_CORPORATION_ADMIN','MUNICIPAL_COUNCIL_ADMIN','NAGAR_PANCHAYAT_ADMIN','PARTY_HEAD','STATE_ADMIN','TALUKA_ADMIN','WARD_ADMIN') NOT NULL,
                         PRIMARY KEY (`id`),
                         UNIQUE KEY `uk_role_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `user_profiles` (
                                 `id` bigint NOT NULL AUTO_INCREMENT,
                                 `created_at` datetime(6) NOT NULL,
                                 `updated_at` datetime(6) NOT NULL,
                                 `version` bigint NOT NULL,
                                 `aadhaar_encrypted` varchar(1024) DEFAULT NULL,
                                 `aadhaar_last_four` varchar(4) DEFAULT NULL,
                                 `date_of_birth` date NOT NULL,
                                 `education` varchar(255) NOT NULL,
                                 `voter_id_number` varchar(30) NOT NULL,
                                 `voter_list_part_number` int NOT NULL,
                                 `address_id` bigint NOT NULL,
                                 `assembly_constituency_id` bigint NOT NULL,
                                 `user_id` bigint NOT NULL,
                                 PRIMARY KEY (`id`),
                                 UNIQUE KEY `uk_profile_user` (`user_id`),
                                 UNIQUE KEY `uk_profile_address` (`address_id`),
                                 KEY `idx_profile_assembly` (`assembly_constituency_id`),
                                 CONSTRAINT `user_profiles_chk_1` CHECK ((`voter_list_part_number` >= 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `user_roles` (
                              `user_id` bigint NOT NULL,
                              `role_id` bigint NOT NULL,
                              UNIQUE KEY `uk_user_role` (`user_id`,`role_id`),
                              KEY `fk_user_roles_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `users` (
                         `id` bigint NOT NULL AUTO_INCREMENT,
                         `created_at` datetime(6) NOT NULL,
                         `updated_at` datetime(6) NOT NULL,
                         `version` bigint NOT NULL,
                         `email` varchar(255) DEFAULT NULL,
                         `enabled` bit(1) NOT NULL,
                         `full_name` varchar(150) NOT NULL,
                         `mobile` varchar(16) NOT NULL,
                         `password` varchar(255) NOT NULL,
                         `password_change_required` bit(1) NOT NULL,
                         `username` varchar(150) NOT NULL,
                         `organization_unit_id` bigint DEFAULT NULL,
                         PRIMARY KEY (`id`),
                         UNIQUE KEY `uk_user_username` (`username`),
                         UNIQUE KEY `uk_user_mobile` (`mobile`),
                         KEY `idx_user_home_unit` (`organization_unit_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Add foreign keys after all referenced tables exist.
ALTER TABLE `advocate_assignments` ADD CONSTRAINT `fk_assignment_advocate` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);
ALTER TABLE `advocate_assignments` ADD CONSTRAINT `fk_assignment_position` FOREIGN KEY (`committee_position_id`) REFERENCES `committee_positions` (`id`);
ALTER TABLE `advocate_assignments` ADD CONSTRAINT `fk_assignment_registration` FOREIGN KEY (`registration_application_id`) REFERENCES `registration_applications` (`id`);
ALTER TABLE `application_documents` ADD CONSTRAINT `fk_document_application` FOREIGN KEY (`application_id`) REFERENCES `registration_applications` (`id`);
ALTER TABLE `application_documents` ADD CONSTRAINT `fk_document_reviewer` FOREIGN KEY (`reviewed_by_id`) REFERENCES `users` (`id`);
ALTER TABLE `application_documents` ADD CONSTRAINT `fk_document_uploader` FOREIGN KEY (`uploaded_by_id`) REFERENCES `users` (`id`);
ALTER TABLE `approval_audit_logs` ADD CONSTRAINT `fk_audit_actor` FOREIGN KEY (`actor_id`) REFERENCES `users` (`id`);
ALTER TABLE `approval_audit_logs` ADD CONSTRAINT `fk_audit_application` FOREIGN KEY (`application_id`) REFERENCES `registration_applications` (`id`);
ALTER TABLE `approval_audit_logs` ADD CONSTRAINT `fk_audit_step` FOREIGN KEY (`approval_step_id`) REFERENCES `approval_steps` (`id`);
ALTER TABLE `approval_steps` ADD CONSTRAINT `fk_approval_application` FOREIGN KEY (`application_id`) REFERENCES `registration_applications` (`id`);
ALTER TABLE `approval_steps` ADD CONSTRAINT `fk_approval_assigned_reviewer` FOREIGN KEY (`assigned_reviewer_id`) REFERENCES `users` (`id`);
ALTER TABLE `approval_steps` ADD CONSTRAINT `fk_approval_committee` FOREIGN KEY (`reviewer_committee_id`) REFERENCES `committees` (`id`);
ALTER TABLE `approval_steps` ADD CONSTRAINT `fk_approval_decided_by` FOREIGN KEY (`decided_by_id`) REFERENCES `users` (`id`);
ALTER TABLE `approval_steps` ADD CONSTRAINT `fk_approval_escalated_to` FOREIGN KEY (`escalated_to_id`) REFERENCES `users` (`id`);
ALTER TABLE `committee_positions` ADD CONSTRAINT `fk_position_committee` FOREIGN KEY (`committee_id`) REFERENCES `committees` (`id`);
ALTER TABLE `committee_positions` ADD CONSTRAINT `fk_position_responsibility_unit` FOREIGN KEY (`responsibility_unit_id`) REFERENCES `organization_units` (`id`);
ALTER TABLE `committees` ADD CONSTRAINT `fk_committee_organization_unit` FOREIGN KEY (`organization_unit_id`) REFERENCES `organization_units` (`id`);
ALTER TABLE `organization_units` ADD CONSTRAINT `fk_organization_unit_parent` FOREIGN KEY (`parent_id`) REFERENCES `organization_units` (`id`);
ALTER TABLE `registration_applications` ADD CONSTRAINT `fk_registration_applicant` FOREIGN KEY (`applicant_id`) REFERENCES `users` (`id`);
ALTER TABLE `registration_applications` ADD CONSTRAINT `fk_registration_position` FOREIGN KEY (`requested_position_id`) REFERENCES `committee_positions` (`id`);
ALTER TABLE `registration_applications` ADD CONSTRAINT `fk_registration_shakha` FOREIGN KEY (`selected_shakha_id`) REFERENCES `organization_units` (`id`);
ALTER TABLE `user_profiles` ADD CONSTRAINT `fk_profile_address` FOREIGN KEY (`address_id`) REFERENCES `addresses` (`id`);
ALTER TABLE `user_profiles` ADD CONSTRAINT `fk_profile_assembly` FOREIGN KEY (`assembly_constituency_id`) REFERENCES `assembly_constituencies` (`id`);
ALTER TABLE `user_profiles` ADD CONSTRAINT `fk_profile_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);
ALTER TABLE `user_roles` ADD CONSTRAINT `fk_user_roles_role` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`);
ALTER TABLE `user_roles` ADD CONSTRAINT `fk_user_roles_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);
ALTER TABLE `users` ADD CONSTRAINT `fk_user_home_unit` FOREIGN KEY (`organization_unit_id`) REFERENCES `organization_units` (`id`);

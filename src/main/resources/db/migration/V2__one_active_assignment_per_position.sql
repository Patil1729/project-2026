-- NULL for historical assignments permits multiple ended appointments per seat.
-- Existing duplicate active appointments make this migration fail; resolve them first.
-- This extra column is database-managed and deliberately absent from the JPA entity.
ALTER TABLE advocate_assignments
    ADD COLUMN active_position_id BIGINT
        GENERATED ALWAYS AS (CASE WHEN active = 1 THEN committee_position_id ELSE NULL END) STORED,
    ADD CONSTRAINT uk_assignment_active_position UNIQUE (active_position_id);

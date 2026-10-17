
INSERT INTO roles (created_at, updated_at, version, name)
SELECT UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0, 'APPLICANT'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'APPLICANT');

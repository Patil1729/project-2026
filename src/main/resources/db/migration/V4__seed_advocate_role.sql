-- Membership is granted only by final application approval, not by this seed.
INSERT INTO roles (created_at, updated_at, version, name)
SELECT UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0, 'ADVOCATE'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ADVOCATE');

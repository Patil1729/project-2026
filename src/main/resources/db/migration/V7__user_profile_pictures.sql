-- Pictures are stored privately; users contain metadata only.
ALTER TABLE users
 ADD COLUMN profile_picture_key VARCHAR(500) NULL,
 ADD COLUMN profile_picture_sha256 VARCHAR(64) NULL,
 ADD COLUMN profile_picture_size_bytes BIGINT NULL,
 ADD COLUMN profile_picture_width INT NULL,
 ADD COLUMN profile_picture_height INT NULL,
 ADD COLUMN profile_picture_uploaded_at DATETIME(6) NULL;

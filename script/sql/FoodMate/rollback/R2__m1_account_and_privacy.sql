-- Run only after backup and confirmation that no M1-1 records depend on V2.
DROP TABLE IF EXISTS account_deletion_jobs;
DROP TABLE IF EXISTS data_export_jobs;
DROP TABLE IF EXISTS password_reset_tokens;

DROP INDEX IF EXISTS idx_user_auth_sessions_user_active;
ALTER TABLE user_avatar_assets DROP COLUMN IF EXISTS original_filename;
ALTER TABLE user_avatar_assets DROP COLUMN IF EXISTS content_sha256;
ALTER TABLE user_avatar_assets ALTER COLUMN url SET NOT NULL;

ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_role;
ALTER TABLE users ADD CONSTRAINT chk_users_role
    CHECK (role IN ('user', 'admin', 'operator'));

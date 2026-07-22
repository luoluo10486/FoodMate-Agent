-- FoodMate M1-1 account, authorization and personal-data structures.
-- Manual execution only. Flyway remains disabled by policy.

ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_role;
ALTER TABLE users ADD CONSTRAINT chk_users_role
    CHECK (role IN ('user', 'admin', 'operator', 'superadmin'));

ALTER TABLE user_avatar_assets
    ALTER COLUMN url DROP NOT NULL;
ALTER TABLE user_avatar_assets
    ADD COLUMN IF NOT EXISTS original_filename VARCHAR(255),
    ADD COLUMN IF NOT EXISTS content_sha256 CHAR(64);

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    password_reset_token_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (user_id),
    token_hash CHAR(64) NOT NULL,
    purpose VARCHAR(32) NOT NULL DEFAULT 'password_reset',
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    CONSTRAINT chk_password_reset_tokens_purpose CHECK (purpose = 'password_reset')
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_password_reset_tokens_hash
    ON password_reset_tokens (token_hash);
CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_user_expires
    ON password_reset_tokens (user_id, expires_at)
    WHERE used_at IS NULL AND is_deleted = FALSE;

CREATE TABLE IF NOT EXISTS data_export_jobs (
    export_job_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (user_id),
    status VARCHAR(32) NOT NULL DEFAULT 'queued',
    object_key VARCHAR(512),
    expires_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    download_consumed_at TIMESTAMPTZ,
    failure_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    CONSTRAINT chk_data_export_jobs_status
        CHECK (status IN ('queued', 'running', 'completed', 'failed', 'expired'))
);
CREATE INDEX IF NOT EXISTS idx_data_export_jobs_user_created
    ON data_export_jobs (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_data_export_jobs_cleanup
    ON data_export_jobs (status, expires_at)
    WHERE is_deleted = FALSE;

CREATE TABLE IF NOT EXISTS account_deletion_jobs (
    deletion_job_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (user_id),
    status VARCHAR(32) NOT NULL DEFAULT 'queued',
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    failure_code VARCHAR(64),
    retry_count INT NOT NULL DEFAULT 0,
    deleted_object_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    CONSTRAINT chk_account_deletion_jobs_status
        CHECK (status IN ('queued', 'running', 'completed', 'failed'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_account_deletion_jobs_user
    ON account_deletion_jobs (user_id) WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_account_deletion_jobs_status
    ON account_deletion_jobs (status, updated_at);

CREATE INDEX IF NOT EXISTS idx_user_auth_sessions_user_active
    ON user_auth_sessions (user_id, last_seen_at DESC)
    WHERE revoked_at IS NULL AND is_deleted = FALSE;

COMMENT ON TABLE password_reset_tokens IS '密码重置一次性令牌，仅保存哈希。';
COMMENT ON TABLE data_export_jobs IS '个人数据异步导出任务，不保存 ZIP 二进制或明文下载令牌。';
COMMENT ON TABLE account_deletion_jobs IS '账号注销后的不可撤销异步物理清理任务。';

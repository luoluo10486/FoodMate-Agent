CREATE TABLE user_auth_sessions (
    auth_session_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (user_id),
    session_token_hash VARCHAR(255) NOT NULL,
    csrf_token_hash VARCHAR(255) NOT NULL,
    device_id VARCHAR(128),
    user_agent VARCHAR(512),
    ip_address VARCHAR(64),
    expires_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT
);

CREATE UNIQUE INDEX uk_user_auth_sessions_token_hash ON user_auth_sessions (session_token_hash);
CREATE INDEX idx_user_auth_sessions_user_expires ON user_auth_sessions (user_id, expires_at) WHERE revoked_at IS NULL AND is_deleted = FALSE;

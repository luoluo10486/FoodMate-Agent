-- M1-2 query indexes. This migration is intentionally manual while Flyway is disabled.
CREATE INDEX IF NOT EXISTS idx_sessions_m12_user_deleted_status_last_message
    ON sessions (user_id, is_deleted, status, last_message_at DESC);

CREATE INDEX IF NOT EXISTS idx_messages_m12_session_deleted_sequence
    ON messages (session_id, is_deleted, sequence_no);

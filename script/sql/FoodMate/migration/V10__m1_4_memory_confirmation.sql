-- M1-4 长期记忆确认状态：作用范围 scope 与用户确认状态分开保存。
ALTER TABLE user_memories
    ADD COLUMN IF NOT EXISTS confirmation_status VARCHAR(32) NOT NULL DEFAULT 'confirmed';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_user_memories_confirmation_status') THEN
        ALTER TABLE user_memories ADD CONSTRAINT chk_user_memories_confirmation_status
            CHECK (confirmation_status IN ('confirmed', 'conflict', 'rejected'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_user_memories_context
    ON user_memories(user_id, updated_at DESC)
    WHERE is_deleted = FALSE AND confirmation_status = 'confirmed';

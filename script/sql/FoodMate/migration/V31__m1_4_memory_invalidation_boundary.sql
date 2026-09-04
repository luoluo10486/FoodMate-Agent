-- M1-4 memory governance: retain source message IDs and durable suppression markers.
-- This migration is manual and does not remove or rewrite existing memory data.

ALTER TABLE user_memories
    ADD COLUMN IF NOT EXISTS source_message_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS suppressed_source_message_ids JSONB NOT NULL DEFAULT '[]'::jsonb;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_user_memories_source_message_ids_arrays'
    ) THEN
        ALTER TABLE user_memories
            ADD CONSTRAINT chk_user_memories_source_message_ids_arrays CHECK (
                jsonb_typeof(source_message_ids) = 'array'
                AND jsonb_typeof(suppressed_source_message_ids) = 'array'
            );
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_user_memories_source_message_ids
    ON user_memories USING GIN (source_message_ids);

CREATE INDEX IF NOT EXISTS idx_user_memories_suppressed_source_ids
    ON user_memories USING GIN (suppressed_source_message_ids)
    WHERE is_deleted = TRUE OR suppressed_source_message_ids <> '[]'::jsonb;

COMMENT ON COLUMN user_memories.source_message_ids IS
    'Message IDs that support this memory candidate; stored as opaque string values.';
COMMENT ON COLUMN user_memories.suppressed_source_message_ids IS
    'Message IDs that must not regenerate a deleted or corrected memory.';

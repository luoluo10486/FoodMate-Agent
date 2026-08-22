-- M2-3 管理写操作契约：乐观并发版本和恢复状态门禁。
-- Manual execution only. Existing rows keep revision 1; no business data is removed.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS revision BIGINT NOT NULL DEFAULT 1;
ALTER TABLE tool_registries
    ADD COLUMN IF NOT EXISTS revision BIGINT NOT NULL DEFAULT 1;
ALTER TABLE knowledge_documents
    ADD COLUMN IF NOT EXISTS revision BIGINT NOT NULL DEFAULT 1;
ALTER TABLE messages
    ADD COLUMN IF NOT EXISTS revision BIGINT NOT NULL DEFAULT 1;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_users_revision'
          AND conrelid = 'users'::regclass
    ) THEN
        ALTER TABLE users ADD CONSTRAINT chk_users_revision CHECK (revision >= 1);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_tool_registries_revision'
          AND conrelid = 'tool_registries'::regclass
    ) THEN
        ALTER TABLE tool_registries ADD CONSTRAINT chk_tool_registries_revision CHECK (revision >= 1);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_knowledge_documents_revision'
          AND conrelid = 'knowledge_documents'::regclass
    ) THEN
        ALTER TABLE knowledge_documents ADD CONSTRAINT chk_knowledge_documents_revision CHECK (revision >= 1);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_messages_revision'
          AND conrelid = 'messages'::regclass
    ) THEN
        ALTER TABLE messages ADD CONSTRAINT chk_messages_revision CHECK (revision >= 1);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_users_revision
    ON users (user_id, revision) WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_tool_registries_revision
    ON tool_registries (name, revision) WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_knowledge_documents_revision
    ON knowledge_documents (document_id, revision);
CREATE INDEX IF NOT EXISTS idx_messages_revision
    ON messages (message_id, revision);

COMMENT ON COLUMN users.revision IS '管理状态和会话撤销使用的乐观并发版本，从 1 开始。';
COMMENT ON COLUMN tool_registries.revision IS '工具启停使用的乐观并发版本，从 1 开始。';
COMMENT ON COLUMN knowledge_documents.revision IS '管理恢复使用的乐观并发版本，从 1 开始。';
COMMENT ON COLUMN messages.revision IS '管理恢复使用的乐观并发版本，从 1 开始。';

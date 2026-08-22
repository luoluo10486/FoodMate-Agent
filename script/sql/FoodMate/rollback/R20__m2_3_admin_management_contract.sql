-- V20 回滚前置说明：仅允许在停止管理写流量、确认没有依赖 revision 的代码后执行。
-- V13/V15 已拥有 food_logs/meal_plans.revision，本回滚不会删除这些既有列。

DROP INDEX IF EXISTS idx_messages_revision;
DROP INDEX IF EXISTS idx_knowledge_documents_revision;
DROP INDEX IF EXISTS idx_tool_registries_revision;
DROP INDEX IF EXISTS idx_users_revision;

ALTER TABLE messages DROP CONSTRAINT IF EXISTS chk_messages_revision;
ALTER TABLE knowledge_documents DROP CONSTRAINT IF EXISTS chk_knowledge_documents_revision;
ALTER TABLE tool_registries DROP CONSTRAINT IF EXISTS chk_tool_registries_revision;
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_revision;

ALTER TABLE messages DROP COLUMN IF EXISTS revision;
ALTER TABLE knowledge_documents DROP COLUMN IF EXISTS revision;
ALTER TABLE tool_registries DROP COLUMN IF EXISTS revision;
ALTER TABLE users DROP COLUMN IF EXISTS revision;

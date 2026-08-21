-- Rollback V17 only after M2-1 delivery code is retired.
DROP TABLE IF EXISTS knowledge_index_result_inbox;
DROP TABLE IF EXISTS knowledge_visibility_outbox;
ALTER TABLE knowledge_index_outbox DROP COLUMN IF EXISTS updated_at, DROP COLUMN IF EXISTS last_error,
  DROP COLUMN IF EXISTS lease_until, DROP COLUMN IF EXISTS owner_token;
ALTER TABLE knowledge_import_items DROP COLUMN IF EXISTS chunk_count;

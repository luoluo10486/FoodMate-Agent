-- Rollback V16 M2-1. Execute only after all M2-1 application code and indexed vectors are retired.
DROP TABLE IF EXISTS knowledge_import_sse_outbox;
DROP TABLE IF EXISTS knowledge_index_outbox;
DROP TABLE IF EXISTS knowledge_import_items;
DROP TABLE IF EXISTS knowledge_import_jobs;
DROP INDEX IF EXISTS uk_knowledge_chunks_embedding_id;
DROP INDEX IF EXISTS idx_knowledge_documents_public_search;
DROP INDEX IF EXISTS uk_knowledge_documents_current_source_version;
ALTER TABLE knowledge_documents DROP CONSTRAINT IF EXISTS chk_knowledge_documents_visibility;
ALTER TABLE knowledge_documents DROP COLUMN IF EXISTS indexed_at, DROP COLUMN IF EXISTS current_version,
    DROP COLUMN IF EXISTS visibility, DROP COLUMN IF EXISTS license_notice, DROP COLUMN IF EXISTS source_version,
    DROP COLUMN IF EXISTS source_name;
ALTER TABLE knowledge_chunks DROP COLUMN IF EXISTS acl_metadata, DROP COLUMN IF EXISTS document_version;

-- V16 M2-1 validation. Read-only.
SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'
  AND table_name IN ('knowledge_import_jobs', 'knowledge_import_items', 'knowledge_index_outbox', 'knowledge_import_sse_outbox') ORDER BY table_name;
SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'knowledge_documents'
  AND column_name IN ('source_name', 'source_version', 'license_notice', 'visibility', 'current_version', 'indexed_at') ORDER BY column_name;
SELECT COUNT(*) AS invalid_public_documents FROM knowledge_documents
  WHERE visibility = 'published' AND (status <> 'indexed' OR is_deleted = TRUE OR current_version = FALSE);

-- V16 M2-1 validation. Read-only.
SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'
  AND table_name IN ('knowledge_import_jobs', 'knowledge_import_items', 'knowledge_index_outbox', 'knowledge_import_sse_outbox') ORDER BY table_name;
SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'knowledge_documents'
  AND column_name IN ('source_name', 'source_version', 'license_notice', 'visibility', 'current_version', 'indexed_at') ORDER BY column_name;
SELECT column_name, character_maximum_length FROM information_schema.columns
  WHERE table_schema = 'public' AND table_name IN ('knowledge_documents', 'knowledge_chunks')
    AND column_name = 'version';
SELECT COUNT(*) AS invalid_public_documents FROM knowledge_documents
  WHERE visibility = 'published' AND (status <> 'indexed' OR is_deleted = TRUE OR current_version = FALSE);
SELECT COUNT(*) AS invalid_import_jobs
  FROM knowledge_import_jobs
  WHERE status NOT IN ('queued','uploading','uploaded','indexing','completed','partial_failed','failed','cancelled')
     OR requested_mode NOT IN ('stub','local')
     OR length(btrim(idempotency_key)) = 0;
SELECT COUNT(*) AS invalid_import_items
  FROM knowledge_import_items
  WHERE attempt_count NOT BETWEEN 0 AND 3
     OR upload_status NOT IN ('queued','uploading','uploaded','upload_failed')
     OR index_status NOT IN ('pending','parsing','parsed','indexing','indexed','index_failed');
SELECT indexname FROM pg_indexes WHERE schemaname='public'
  AND indexname IN ('uk_knowledge_documents_current_source_version','uk_knowledge_chunks_embedding_id');

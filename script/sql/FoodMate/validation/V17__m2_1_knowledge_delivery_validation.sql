-- V17 read-only validation.
SELECT table_name FROM information_schema.tables WHERE table_schema='public'
  AND table_name IN ('knowledge_visibility_outbox','knowledge_index_result_inbox') ORDER BY table_name;
SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name='knowledge_index_result_inbox'
  AND column_name = 'attempt_count';
SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name='knowledge_index_outbox'
  AND column_name IN ('owner_token','lease_until','last_error','updated_at') ORDER BY column_name;
SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name='knowledge_import_items'
  AND column_name = 'chunk_count';
SELECT COUNT(*) AS invalid_result_attempts
  FROM knowledge_index_result_inbox
  WHERE attempt_count NOT BETWEEN 1 AND 3;
SELECT indexname FROM pg_indexes WHERE schemaname='public'
  AND indexname = 'uk_knowledge_documents_current_source_document_version';
SELECT conname FROM pg_constraint
  WHERE conname IN ('chk_knowledge_index_outbox_topic','chk_knowledge_visibility_outbox_topic');

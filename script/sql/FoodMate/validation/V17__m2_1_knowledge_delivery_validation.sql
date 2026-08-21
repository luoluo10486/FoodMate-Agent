-- V17 read-only validation.
SELECT table_name FROM information_schema.tables WHERE table_schema='public'
  AND table_name IN ('knowledge_visibility_outbox','knowledge_index_result_inbox') ORDER BY table_name;
SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name='knowledge_index_result_inbox'
  AND column_name = 'attempt_count';
SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name='knowledge_index_outbox'
  AND column_name IN ('owner_token','lease_until','last_error','updated_at') ORDER BY column_name;
SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name='knowledge_import_items'
  AND column_name = 'chunk_count';

SELECT column_name
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'runtime_message_dlq'
  AND column_name = 'raw_payload_text';

SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name = 'runtime_dlq_replay_outbox';

SELECT indexname
FROM pg_indexes
WHERE schemaname = 'public'
  AND tablename = 'runtime_dlq_replay_outbox'
  AND indexname IN ('uk_runtime_dlq_replay_active_dlq', 'idx_runtime_dlq_replay_pending');

SELECT COUNT(*) AS invalid_published_replays
FROM runtime_dlq_replay_outbox
WHERE status = 'published'
  AND (broker_message_id IS NULL OR published_at IS NULL);

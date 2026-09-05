-- V31 validation: source metadata remains array-shaped and no existing row is deleted.

SELECT
    COUNT(*) AS memory_rows,
    COUNT(*) FILTER (WHERE source_message_ids IS NULL) AS null_source_rows,
    COUNT(*) FILTER (WHERE suppressed_source_message_ids IS NULL) AS null_suppressed_rows,
    COUNT(*) FILTER (WHERE jsonb_typeof(source_message_ids) <> 'array') AS invalid_source_rows,
    COUNT(*) FILTER (WHERE jsonb_typeof(suppressed_source_message_ids) <> 'array') AS invalid_suppressed_rows
FROM user_memories;

SELECT indexname
FROM pg_indexes
WHERE tablename = 'user_memories'
  AND indexname IN (
      'idx_user_memories_source_message_ids',
      'idx_user_memories_suppressed_source_ids'
  )
ORDER BY indexname;

SELECT conname
FROM pg_constraint
WHERE conname = 'chk_user_memories_source_message_ids_arrays';

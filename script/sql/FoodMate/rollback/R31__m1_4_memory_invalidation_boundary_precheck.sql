-- V31 rollback precheck only. Do not drop columns while any memory row carries source metadata.

SELECT
    COUNT(*) FILTER (WHERE source_message_ids <> '[]'::jsonb) AS rows_with_source_ids,
    COUNT(*) FILTER (WHERE suppressed_source_message_ids <> '[]'::jsonb)
        AS rows_with_suppressed_source_ids
FROM user_memories;

SELECT
    'manual review required before removing V31 columns; no destructive rollback is executed' AS notice;

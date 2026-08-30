SELECT table_name, column_name, data_type, character_maximum_length
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'knowledge_import_items'
  AND column_name = 'provider_trace_id';

SELECT COUNT(*) AS invalid_provider_trace_ids
FROM knowledge_import_items
WHERE provider_trace_id IS NOT NULL
  AND (length(provider_trace_id) = 0 OR length(provider_trace_id) > 256);

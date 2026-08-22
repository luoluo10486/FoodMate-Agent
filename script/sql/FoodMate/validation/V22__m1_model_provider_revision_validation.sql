-- V22 provider revision validation. Read-only checks after manual execution.

SELECT column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'model_providers'
  AND column_name = 'revision';

SELECT indexname
FROM pg_indexes
WHERE schemaname = 'public'
  AND indexname = 'idx_model_providers_revision';

SELECT COUNT(*) AS invalid_revision_rows
FROM model_providers
WHERE revision < 1;

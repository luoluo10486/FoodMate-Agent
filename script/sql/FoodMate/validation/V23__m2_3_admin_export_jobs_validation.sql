-- V23 M2-3 export job validation. Read-only; does not create or modify objects.

SELECT table_name, column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'admin_export_jobs'
ORDER BY ordinal_position;

SELECT indexname
FROM pg_indexes
WHERE schemaname = 'public'
  AND indexname IN (
      'idx_admin_export_jobs_operator_created',
      'idx_admin_export_jobs_queue'
  )
ORDER BY indexname;

SELECT COUNT(*) AS invalid_status_rows
FROM admin_export_jobs
WHERE status NOT IN ('queued', 'running', 'completed', 'failed', 'expired');

SELECT COUNT(*) AS unsafe_completed_rows
FROM admin_export_jobs
WHERE status = 'completed'
  AND (object_key IS NULL OR expires_at IS NULL);

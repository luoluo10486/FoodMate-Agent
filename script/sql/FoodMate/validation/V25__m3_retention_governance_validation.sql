SELECT COUNT(*) AS policy_count
FROM data_retention_policies
WHERE resource_type IN ('knowledge_document', 'admin_export_job')
  AND status = 'active';

SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN ('data_legal_holds', 'data_purge_requests', 'data_purge_tasks')
ORDER BY table_name;

SELECT indexname
FROM pg_indexes
WHERE schemaname = 'public'
  AND indexname IN ('uk_data_legal_hold_active_resource', 'uk_data_purge_active_resource', 'idx_data_purge_tasks_pending')
ORDER BY indexname;

SELECT COUNT(*) AS active_hold_blocks
FROM data_legal_holds
WHERE status = 'active';

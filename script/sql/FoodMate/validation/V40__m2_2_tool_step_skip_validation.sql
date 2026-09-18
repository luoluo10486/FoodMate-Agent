-- V40 工具步骤跳过迁移的只读结构与事实校验。

SELECT CASE
           WHEN EXISTS (
               SELECT 1
                 FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name = 'agent_run_tool_skips'
           ) THEN 'applied'
           ELSE 'not_applied'
       END AS migration_status;

SELECT column_name
  FROM information_schema.columns
 WHERE table_schema = 'public'
   AND table_name = 'tool_schema_versions'
   AND column_name = 'skippable';

SELECT column_name
  FROM information_schema.columns
 WHERE table_schema = 'public'
   AND table_name = 'runtime_tool_proposal_inbox'
   AND column_name IN ('execution_started_at', 'skip_requested_at')
 ORDER BY column_name;

SELECT COUNT(*) AS invalid_skip_status_rows
  FROM agent_run_tool_skips
 WHERE status NOT IN ('requested', 'dispatched', 'applied', 'rejected', 'failed');

SELECT COUNT(*) AS skippable_schema_rows
  FROM tool_schema_versions
 WHERE skippable = TRUE
   AND is_deleted = FALSE;

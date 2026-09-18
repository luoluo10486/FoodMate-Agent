-- V40 只读回滚前置检查，不执行删除或结构回滚。

SELECT 'rollback_precheck' AS check_name,
       COUNT(*) AS skip_command_rows,
       COUNT(*) FILTER (WHERE status IN ('requested', 'dispatched')) AS pending_skip_command_rows
  FROM agent_run_tool_skips;

SELECT 'proposal_skip_state' AS check_name,
       COUNT(*) FILTER (WHERE status = 'skip_requested') AS pending_proposals,
       COUNT(*) FILTER (WHERE skip_requested_at IS NOT NULL) AS proposals_with_skip_timestamp
  FROM runtime_tool_proposal_inbox;

SELECT 'skippable_schema_rows' AS check_name,
       COUNT(*) AS rows_with_skip_policy
  FROM tool_schema_versions
 WHERE skippable = TRUE
   AND is_deleted = FALSE;

-- FoodMate V1 人工执行后校验。只读，不创建或修改对象。
SELECT current_database() AS database_name, current_user AS database_user, version();

SELECT COUNT(*) AS user_tables
FROM information_schema.tables
WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
  AND table_name NOT IN ('flyway_schema_history', 'runtime_runs', 'runtime_dispatches', 'runtime_cancels', 'runtime_event_inbox');

SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
  AND table_name NOT IN ('flyway_schema_history', 'runtime_runs', 'runtime_dispatches', 'runtime_cancels', 'runtime_event_inbox')
ORDER BY table_name;

SELECT COUNT(*) AS missing_soft_delete_columns
FROM (
  SELECT table_name
  FROM information_schema.tables
  WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
    AND table_name NOT IN ('flyway_schema_history', 'runtime_runs', 'runtime_dispatches', 'runtime_cancels', 'runtime_event_inbox')
  EXCEPT
  SELECT table_name
  FROM information_schema.columns
  WHERE table_schema = 'public' AND column_name = 'is_deleted'
) missing;

SELECT COUNT(*) AS unexpected_flyway_history_rows
FROM information_schema.tables
WHERE table_schema = 'public' AND table_name = 'flyway_schema_history';

SELECT indexname
FROM pg_indexes
WHERE schemaname = 'public'
  AND indexname IN ('idx_sessions_user_last_message_at', 'idx_messages_session_sequence',
                    'idx_agent_runs_session_created_at', 'idx_tool_calls_run_created_at',
                    'idx_food_logs_user_meal_time', 'idx_knowledge_documents_tenant_status')
ORDER BY indexname;

SELECT table_name, column_name
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name IN ('runtime_runs', 'runtime_dispatches', 'runtime_cancels', 'runtime_event_inbox')
ORDER BY table_name, ordinal_position;

-- V18 read-only validation. All seven names must have an active current v1 schema.
SELECT t.name,t.status,t.current_version,s.version,s.timeout_ms,s.retryable,s.idempotent
FROM tool_registries t
JOIN tool_schema_versions s ON s.tool_id=t.tool_id AND s.version=t.current_version AND s.is_deleted=FALSE
WHERE t.name IN ('calculator','time_parser','knowledge_search','database_query','food_log_writer','plan_validator','meal_plan.save_plan')
  AND t.is_deleted=FALSE
ORDER BY t.name;

SELECT COUNT(*) AS active_seed_tools
FROM tool_registries
WHERE name IN ('calculator','time_parser','knowledge_search','database_query','food_log_writer','plan_validator','meal_plan.save_plan')
  AND status='active' AND current_version='v1' AND is_deleted=FALSE;

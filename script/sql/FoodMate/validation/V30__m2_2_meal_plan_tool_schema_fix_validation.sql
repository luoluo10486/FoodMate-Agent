-- V30 read-only validation for the meal_plan.save_plan Proposal contract.
SELECT t.name,
       t.status,
       t.current_version,
       s.version,
       s.input_schema::text AS input_schema,
       s.permissions::text AS permissions
FROM tool_registries t
JOIN tool_schema_versions s
  ON s.tool_id = t.tool_id
 AND s.version = t.current_version
 AND s.is_deleted = FALSE
WHERE t.tool_id = 720007
  AND t.name = 'meal_plan.save_plan'
  AND t.is_deleted = FALSE;

SELECT COUNT(*) AS invalid_meal_plan_schema
FROM tool_schema_versions
WHERE tool_id = 720007
  AND version = 'v2'
  AND NOT (
      input_schema->'required' @> '["plan"]'::jsonb
      AND NOT (input_schema->'required' @> '["idempotencyKey"]'::jsonb)
      AND input_schema->'additionalProperties' = 'false'::jsonb
  );

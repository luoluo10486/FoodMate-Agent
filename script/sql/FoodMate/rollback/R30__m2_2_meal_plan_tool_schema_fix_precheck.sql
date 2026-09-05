-- V30 rollback precheck: report references before any manual rollback review.
SELECT COUNT(*) AS active_v2_registry_rows
FROM tool_registries
WHERE tool_id = 720007
  AND name = 'meal_plan.save_plan'
  AND current_version = 'v2'
  AND is_deleted = FALSE;

SELECT COUNT(*) AS v2_schema_rows
FROM tool_schema_versions
WHERE tool_id = 720007
  AND version = 'v2'
  AND is_deleted = FALSE;

-- Rollback requires retiring all callers of v2 and restoring the registry pointer:
-- Review the registry pointer and v2 schema row manually before any approved reversal.

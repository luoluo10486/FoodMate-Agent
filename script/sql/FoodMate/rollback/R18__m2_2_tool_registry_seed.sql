-- Rollback only the rows created by the initial seed and only after all M2-2
-- callers are retired. Never delete a registry row that an administrator reused.
BEGIN;
DELETE FROM tool_schema_versions
WHERE tool_schema_version_id BETWEEN 721001 AND 721007
  AND tool_id BETWEEN 720001 AND 720007
  AND created_by=0;
DELETE FROM tool_registries
WHERE tool_id BETWEEN 720001 AND 720007
  AND created_by=0
  AND is_deleted=FALSE;
COMMIT;

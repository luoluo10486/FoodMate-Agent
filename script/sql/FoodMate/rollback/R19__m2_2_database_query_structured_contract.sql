-- Rollback V19 only after the structured database_query callers are retired.
-- This restores the V18 current version and removes only the V19-owned schema row.
BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM tool_schema_versions
         WHERE tool_schema_version_id = 721008
           AND tool_id = 720004
           AND version = 'v2'
           AND created_by = 0
           AND is_deleted = FALSE
    ) THEN
        RAISE EXCEPTION 'R19 requires the V19 database_query v2 schema row';
    END IF;
END $$;

UPDATE tool_registries
   SET current_version = 'v1',
       updated_at = CURRENT_TIMESTAMP,
       updated_by = 0
 WHERE tool_id = 720004
   AND current_version = 'v2'
   AND is_deleted = FALSE;

DELETE FROM tool_schema_versions
 WHERE tool_schema_version_id = 721008
   AND tool_id = 720004
   AND version = 'v2'
   AND created_by = 0;

COMMIT;

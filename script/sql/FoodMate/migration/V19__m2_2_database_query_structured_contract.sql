-- M2-2: publish the structured database_query contract without rewriting V18.
-- The wire protocol remains v1; v2 is the registry schema version.
BEGIN;

DO $$
DECLARE
    database_query_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO database_query_count
      FROM tool_registries
     WHERE tool_id = 720004
       AND name = 'database_query'
       AND current_version = 'v1'
       AND is_deleted = FALSE;
    IF database_query_count <> 1 THEN
        RAISE EXCEPTION 'V19 requires exactly one active database_query registry at v1';
    END IF;
END $$;

INSERT INTO tool_schema_versions
    (tool_schema_version_id,tool_id,version,input_schema,output_schema,permissions,timeout_ms,retryable,idempotent,published_at,created_by,updated_by)
VALUES
    (721008,
     720004,
     'v2',
     '{"type":"object","properties":{"intent":{"type":"string"},"time_range":{"type":"object"},"metrics":{"type":"array"},"dimensions":{"type":"array"},"filters":{"type":"object"},"candidate_sql":{"type":"string","maxLength":8192},"planner_mode":{"type":"string","enum":["stub","local"]},"planner_version":{"type":"string","maxLength":64}},"required":["intent","metrics","dimensions","filters","candidate_sql","planner_mode","planner_version"],"additionalProperties":false}'::jsonb,
     '{"type":"object","properties":{"rows":{"type":"array"},"sql_audit_id":{"type":"string"}},"required":["rows"]}'::jsonb,
     '{"roles":["user","operator","admin","superadmin"],"approval":"none","scope":"user"}'::jsonb,
     5000,
     FALSE,
     TRUE,
     '2026-08-22T00:00:00Z',
     0,
     0);

UPDATE tool_registries
   SET current_version = 'v2',
       updated_at = CURRENT_TIMESTAMP,
       updated_by = 0
 WHERE tool_id = 720004
   AND name = 'database_query'
   AND current_version = 'v1'
   AND is_deleted = FALSE;

COMMIT;

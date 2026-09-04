-- M2-2: align meal_plan.save_plan registry schema with the v1 Proposal wire contract.
-- The idempotency key is a Proposal payload field, not business input.
-- Manual execution only; this migration preserves the existing v1 schema row.
BEGIN;

DO $$
DECLARE
    meal_plan_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO meal_plan_count
      FROM tool_registries
     WHERE tool_id = 720007
       AND name = 'meal_plan.save_plan'
       AND current_version = 'v1'
       AND is_deleted = FALSE;
    IF meal_plan_count <> 1 THEN
        RAISE EXCEPTION 'V30 requires exactly one active meal_plan.save_plan registry at v1';
    END IF;
END $$;

INSERT INTO tool_schema_versions
    (tool_schema_version_id,tool_id,version,input_schema,output_schema,permissions,timeout_ms,retryable,idempotent,published_at,created_by,updated_by)
VALUES
    (721009,
     720007,
     'v2',
     '{"type":"object","properties":{"plan":{"type":"object"}},"required":["plan"],"additionalProperties":false}'::jsonb,
     '{"type":"object","properties":{"status":{"type":"string"},"resourceId":{"type":"string"}},"required":["status"]}'::jsonb,
     '{"roles":["user","operator","admin","superadmin"],"approval":"required","scope":"user"}'::jsonb,
     10000,
     FALSE,
     TRUE,
     '2026-09-05T00:00:00Z',
     0,
     0)
ON CONFLICT DO NOTHING;

UPDATE tool_registries
   SET current_version = 'v2',
       updated_at = CURRENT_TIMESTAMP,
       updated_by = 0
 WHERE tool_id = 720007
   AND name = 'meal_plan.save_plan'
   AND current_version = 'v1'
   AND is_deleted = FALSE;

COMMIT;

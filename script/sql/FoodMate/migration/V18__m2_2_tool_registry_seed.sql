-- M2-2: immutable initial registry seed for the seven application tools.
-- Manual execution only. Existing rows are never overwritten by this seed.
-- Runtime code must not create or mutate registry metadata.
BEGIN;

INSERT INTO tool_registries
    (tool_id,name,display_name,description,category,risk_level,availability_scope,status,current_version,created_by,updated_by)
VALUES
    (720001,'calculator','Calculator','Evaluate a bounded arithmetic expression.','utility','low','user','active','v1',0,0),
    (720002,'time_parser','Time parser','Resolve a natural-language time range into bounded instants.','utility','low','user','active','v1',0,0),
    (720003,'knowledge_search','Knowledge search','Search the public published knowledge scope.','retrieval','low','public','active','v1',0,0),
    (720004,'database_query','Database query','Run an authorized read-only query for the current user.','analysis','medium','user','active','v1',0,0),
    (720005,'food_log_writer','Food log writer','Create, update, delete, or restore the current user''s food log.','write','high','user','active','v1',0,0),
    (720006,'plan_validator','Plan validator','Validate a meal plan against nutrition and business constraints.','planning','medium','user','active','v1',0,0),
    (720007,'meal_plan.save_plan','Save meal plan','Persist a validated meal plan for the current user.','write','high','user','active','v1',0,0)
ON CONFLICT DO NOTHING;

INSERT INTO tool_schema_versions
    (tool_schema_version_id,tool_id,version,input_schema,output_schema,permissions,timeout_ms,retryable,idempotent,published_at,created_by,updated_by)
VALUES
    (721001,720001,'v1','{"type":"object","properties":{"expression":{"type":"string","maxLength":256}},"required":["expression"],"additionalProperties":false}','{"type":"object","properties":{"result":{"type":"number"}},"required":["result"]}','{"roles":["user","operator","admin","superadmin"],"approval":"none","scope":"user"}',1000,TRUE,TRUE,'2026-01-01T00:00:00Z',0,0),
    (721002,720002,'v1','{"type":"object","properties":{"question":{"type":"string","maxLength":512},"timezone":{"type":"string","maxLength":64}},"required":["question"],"additionalProperties":false}','{"type":"object","properties":{"from":{"type":"string"},"to":{"type":"string"}},"required":["from","to"]}','{"roles":["user","operator","admin","superadmin"],"approval":"none","scope":"user"}',1000,TRUE,TRUE,'2026-01-01T00:00:00Z',0,0),
    (721003,720003,'v1','{"type":"object","properties":{"query":{"type":"string","minLength":1,"maxLength":512},"limit":{"type":"integer","minimum":1,"maximum":12}},"required":["query"],"additionalProperties":false}','{"type":"object","properties":{"citations":{"type":"array"}},"required":["citations"]}','{"roles":["user","operator","admin","superadmin"],"approval":"none","scope":"public"}',3000,TRUE,TRUE,'2026-01-01T00:00:00Z',0,0),
    (721004,720004,'v1','{"type":"object","properties":{"intent":{"type":"string"},"from":{"type":"string"},"to":{"type":"string"},"metrics":{"type":"array"},"dimensions":{"type":"array"},"filters":{"type":"object"}},"required":["intent"],"additionalProperties":false}','{"type":"object","properties":{"rows":{"type":"array"}},"required":["rows"]}','{"roles":["user","operator","admin","superadmin"],"approval":"none","scope":"user"}',5000,FALSE,TRUE,'2026-01-01T00:00:00Z',0,0),
    (721005,720005,'v1','{"type":"object","properties":{"operation":{"type":"string","enum":["create","update","delete","restore"]},"parameters":{"type":"object"}},"required":["operation","parameters"],"additionalProperties":false}','{"type":"object","properties":{"status":{"type":"string"},"resourceId":{"type":"string"}},"required":["status"]}','{"roles":["user","operator","admin","superadmin"],"approval":"required","scope":"user"}',10000,FALSE,TRUE,'2026-01-01T00:00:00Z',0,0),
    (721006,720006,'v1','{"type":"object","properties":{"plan":{"type":"object"}},"required":["plan"],"additionalProperties":false}','{"type":"object","properties":{"status":{"type":"string"},"issues":{"type":"array"}},"required":["status","issues"]}','{"roles":["user","operator","admin","superadmin"],"approval":"none","scope":"user"}',5000,TRUE,TRUE,'2026-01-01T00:00:00Z',0,0),
    (721007,720007,'v1','{"type":"object","properties":{"plan":{"type":"object"},"idempotencyKey":{"type":"string","maxLength":128}},"required":["plan","idempotencyKey"],"additionalProperties":false}','{"type":"object","properties":{"status":{"type":"string"},"resourceId":{"type":"string"}},"required":["status"]}','{"roles":["user","operator","admin","superadmin"],"approval":"required","scope":"user"}',10000,FALSE,TRUE,'2026-01-01T00:00:00Z',0,0)
ON CONFLICT DO NOTHING;

COMMIT;

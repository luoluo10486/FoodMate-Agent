-- V21 rollback precondition: stop model governance writes and confirm no active run depends on
-- the new snapshot columns. This rollback removes only M1 governance objects and columns.

DROP INDEX IF EXISTS idx_model_usage_logs_governance;
DROP INDEX IF EXISTS idx_model_route_rules_active_priority;
DROP INDEX IF EXISTS uk_model_budget_policy_version;
DROP INDEX IF EXISTS idx_model_budget_active_scene;
DROP INDEX IF EXISTS uk_model_price_version;
DROP INDEX IF EXISTS idx_model_price_active_effective;
DROP INDEX IF EXISTS uk_model_catalog_provider_name;
DROP INDEX IF EXISTS idx_model_catalog_type_status;
DROP INDEX IF EXISTS uk_model_providers_code;

DROP TABLE IF EXISTS model_budget_policies;
DROP TABLE IF EXISTS model_price_versions;
DROP TABLE IF EXISTS model_catalog;
DROP TABLE IF EXISTS model_providers;

ALTER TABLE model_usage_logs
    DROP COLUMN IF EXISTS budget_policy_version,
    DROP COLUMN IF EXISTS price_version,
    DROP COLUMN IF EXISTS route_version;

ALTER TABLE model_route_rules
    DROP COLUMN IF EXISTS revision,
    DROP COLUMN IF EXISTS budget_policy_version,
    DROP COLUMN IF EXISTS price_version,
    DROP COLUMN IF EXISTS route_version,
    DROP COLUMN IF EXISTS priority,
    DROP COLUMN IF EXISTS fallback_model_name,
    DROP COLUMN IF EXISTS model_name;

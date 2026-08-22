-- V21 M1 model governance validation. Read-only checks after manual execution.

SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN (
      'model_providers', 'model_catalog', 'model_price_versions', 'model_budget_policies'
  )
ORDER BY table_name;

SELECT table_name, column_name
FROM information_schema.columns
WHERE table_schema = 'public'
  AND (
      (table_name = 'model_route_rules' AND column_name IN (
          'model_name', 'fallback_model_name', 'priority', 'route_version',
          'price_version', 'budget_policy_version', 'revision'
      ))
      OR (table_name = 'model_usage_logs' AND column_name IN (
          'route_version', 'price_version', 'budget_policy_version'
      ))
  )
ORDER BY table_name, column_name;

SELECT indexname
FROM pg_indexes
WHERE schemaname = 'public'
  AND indexname IN (
      'uk_model_providers_code', 'uk_model_catalog_provider_name',
      'uk_model_price_version', 'uk_model_budget_policy_version',
      'idx_model_route_rules_active_priority', 'idx_model_usage_logs_governance'
  )
ORDER BY indexname;

SELECT 'model_catalog' AS table_name, COUNT(*) AS invalid_rows
FROM model_catalog
WHERE revision < 1 OR timeout_ms <= 0
UNION ALL
SELECT 'model_budget_policies', COUNT(*)
FROM model_budget_policies
WHERE revision < 1 OR max_total_tokens <= 0 OR max_cost_cny < 0 OR max_model_calls <= 0
UNION ALL
SELECT 'model_price_versions', COUNT(*)
FROM model_price_versions
WHERE input_price_per_million < 0 OR output_price_per_million < 0;

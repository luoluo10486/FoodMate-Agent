-- V13 M1-5 执行后校验。只读，不创建或修改对象。

SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN (
      'food_logs', 'food_log_items', 'nutrition_foods',
      'nutrition_unit_conversions', 'approval_requests'
  )
ORDER BY table_name;

SELECT table_name, column_name, data_type, numeric_precision, numeric_scale
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name IN ('food_logs', 'food_log_items', 'nutrition_foods', 'nutrition_unit_conversions', 'approval_requests')
ORDER BY table_name, ordinal_position;

SELECT table_name, constraint_name, constraint_type
FROM information_schema.table_constraints
WHERE table_schema = 'public'
  AND table_name IN ('food_logs', 'food_log_items', 'nutrition_foods', 'nutrition_unit_conversions', 'approval_requests')
ORDER BY table_name, constraint_name;

SELECT indexname
FROM pg_indexes
WHERE schemaname = 'public'
  AND indexname IN (
      'uk_food_logs_user_idempotency', 'idx_food_logs_user_revision',
      'uk_food_log_items_order', 'idx_food_log_items_food_log',
      'uk_nutrition_foods_standard_name', 'idx_nutrition_foods_lookup',
      'uk_nutrition_unit_conversions_rule', 'idx_nutrition_unit_conversions_lookup',
      'uk_approval_requests_user_idempotency', 'idx_approval_requests_user_status',
      'idx_approval_requests_resource'
  )
ORDER BY indexname;

SELECT column_name
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'food_logs'
  AND column_name IN ('items_json', 'nutrition_json');

SELECT COUNT(*) AS food_logs_with_legacy_json
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'food_logs'
  AND column_name IN ('items_json', 'nutrition_json');

SELECT COUNT(*) AS invalid_matched_food_items
FROM food_log_items
WHERE nutrition_status = 'matched'
  AND (
      nutrition_food_id IS NULL
      OR normalized_amount IS NULL
      OR normalized_unit NOT IN ('g', 'ml')
      OR calories_kcal IS NULL
      OR protein_g IS NULL
      OR fat_g IS NULL
      OR carbs_g IS NULL
  );

SELECT COUNT(*) AS unmatched_items_with_nutrition_snapshot
FROM food_log_items
WHERE nutrition_status <> 'matched'
  AND (calories_kcal IS NOT NULL OR protein_g IS NOT NULL OR fat_g IS NOT NULL OR carbs_g IS NOT NULL);

SELECT COUNT(*) AS invalid_revision_rows
FROM food_logs
WHERE revision < 1;


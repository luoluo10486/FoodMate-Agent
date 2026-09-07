SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN ('composite_dishes', 'composite_dish_items')
ORDER BY table_name;

SELECT column_name
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'food_logs'
  AND column_name IN ('composite_dish_id', 'composite_dish_revision', 'composite_dish_servings', 'composite_dish_snapshot_json')
ORDER BY column_name;

SELECT indexname
FROM pg_indexes
WHERE schemaname = 'public'
  AND indexname IN (
      'idx_composite_dishes_user_active',
      'idx_composite_dish_items_dish',
      'uk_composite_dishes_user_idempotency',
      'idx_food_logs_composite_dish'
  )
ORDER BY indexname;

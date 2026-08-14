-- V15 M1-5 执行后校验。只读，不创建或修改对象。

SELECT column_name, data_type, character_maximum_length
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'meal_plans'
  AND column_name IN ('idempotency_key', 'revision')
ORDER BY column_name;

SELECT indexname
FROM pg_indexes
WHERE schemaname = 'public'
  AND indexname IN ('uk_meal_plans_user_idempotency', 'idx_meal_plans_user_revision')
ORDER BY indexname;

SELECT COUNT(*) AS invalid_meal_plan_revisions
FROM meal_plans
WHERE revision < 1;

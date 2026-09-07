-- M2-6：只读校验，不修改数据。
SELECT column_name
  FROM information_schema.columns
 WHERE table_name = 'food_logs'
   AND column_name = 'meal_plan_meal_id';

SELECT table_name
  FROM information_schema.tables
 WHERE table_name IN ('meal_plan_meals', 'shopping_list_items')
 ORDER BY table_name;

SELECT indexname
  FROM pg_indexes
 WHERE indexname IN (
       'uk_meal_plan_meals_slot',
       'idx_food_logs_meal_plan_meal',
       'uk_shopping_list_items_key',
       'idx_shopping_list_items_user_plan')
 ORDER BY indexname;

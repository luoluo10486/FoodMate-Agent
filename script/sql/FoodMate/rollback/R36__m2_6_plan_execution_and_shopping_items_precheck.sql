-- M2-6 回滚前置检查：先确认新关联没有业务数据，再由人工评估执行回滚。
SELECT COUNT(*) AS linked_food_logs FROM food_logs WHERE meal_plan_meal_id IS NOT NULL;
SELECT COUNT(*) AS meal_plan_meals FROM meal_plan_meals;
SELECT COUNT(*) AS shopping_list_items FROM shopping_list_items;

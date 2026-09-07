-- V38 回滚前置检查，只读。
-- 复合菜聚合明细依赖该约束；在确认不存在此类记录前不得恢复旧约束。

SELECT COUNT(*) AS composite_snapshot_rows
FROM food_log_items
WHERE nutrition_source ~ '^composite_dish:[0-9]+$';

SELECT COUNT(*) AS composite_snapshot_rows_without_atomic_food_id
FROM food_log_items
WHERE nutrition_source ~ '^composite_dish:[0-9]+$'
  AND nutrition_food_id IS NULL;

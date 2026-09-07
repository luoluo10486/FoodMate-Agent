-- V38 复合菜聚合营养快照约束校验，只读。

SELECT conname
FROM pg_constraint
WHERE conrelid = 'food_log_items'::regclass
  AND conname = 'chk_food_log_items_matched_snapshot';

SELECT COUNT(*) AS invalid_matched_snapshot_rows
FROM food_log_items
WHERE nutrition_status = 'matched'
  AND NOT (
      normalized_amount IS NOT NULL
      AND calories_kcal IS NOT NULL
      AND protein_g IS NOT NULL
      AND fat_g IS NOT NULL
      AND carbs_g IS NOT NULL
      AND (
          (nutrition_food_id IS NOT NULL AND normalized_unit IN ('g', 'ml'))
          OR (
              nutrition_food_id IS NULL
              AND normalized_unit = 'serving'
              AND nutrition_source ~ '^composite_dish:[0-9]+$'
              AND nutrition_version ~ '^revision:[0-9]+$'
          )
      )
  );

SELECT COUNT(*) AS composite_snapshot_rows,
       COUNT(*) FILTER (
           WHERE nutrition_status = 'matched'
             AND nutrition_food_id IS NULL
             AND normalized_unit = 'serving'
             AND nutrition_source ~ '^composite_dish:[0-9]+$'
             AND nutrition_version ~ '^revision:[0-9]+$'
       ) AS valid_composite_snapshot_rows
FROM food_log_items
WHERE nutrition_source ~ '^composite_dish:[0-9]+$';

SELECT COUNT(*) AS composite_dish_count FROM composite_dishes;
SELECT COUNT(*) AS composite_dish_item_count FROM composite_dish_items;
SELECT COUNT(*) AS food_logs_with_composite_snapshot
FROM food_logs
WHERE composite_dish_id IS NOT NULL;

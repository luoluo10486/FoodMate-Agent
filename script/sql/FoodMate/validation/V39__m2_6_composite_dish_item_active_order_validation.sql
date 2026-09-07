-- V39 复合菜活动组成明细顺序约束校验，只读。

SELECT indexname
FROM pg_indexes
WHERE schemaname = 'public'
  AND indexname = 'uk_composite_dish_items_order';

SELECT COUNT(*) AS duplicate_active_item_orders
FROM (
    SELECT composite_dish_id, item_order
    FROM composite_dish_items
    WHERE is_deleted = FALSE
    GROUP BY composite_dish_id, item_order
    HAVING COUNT(*) > 1
) duplicates;

SELECT COUNT(*) AS soft_deleted_reusable_item_orders
FROM composite_dish_items
WHERE is_deleted = TRUE;

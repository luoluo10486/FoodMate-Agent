-- V39 回滚前置检查，只读。
-- 只有不存在软删除复合菜明细，且所有活动顺序唯一时，才可人工评估恢复全量唯一约束。

SELECT COUNT(*) AS soft_deleted_composite_items
FROM composite_dish_items
WHERE is_deleted = TRUE;

SELECT COUNT(*) AS duplicate_active_item_orders
FROM (
    SELECT composite_dish_id, item_order
    FROM composite_dish_items
    WHERE is_deleted = FALSE
    GROUP BY composite_dish_id, item_order
    HAVING COUNT(*) > 1
) duplicates;

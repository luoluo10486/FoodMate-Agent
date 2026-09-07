-- M2-6 复合菜组成明细支持版本更新。
-- 更新流程保留旧明细软删除，再写入相同 item_order 的新明细；唯一性只应约束活动明细。

BEGIN;

ALTER TABLE composite_dish_items
    DROP CONSTRAINT IF EXISTS uk_composite_dish_items_order;

DROP INDEX IF EXISTS uk_composite_dish_items_order;

CREATE UNIQUE INDEX uk_composite_dish_items_order
    ON composite_dish_items (composite_dish_id, item_order)
    WHERE is_deleted = FALSE;

COMMIT;

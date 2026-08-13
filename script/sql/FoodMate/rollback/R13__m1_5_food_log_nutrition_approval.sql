-- 回滚 V13 M1-5。
-- 仅允许在没有依赖新结构的业务数据时执行；会丢弃 V13 新表和字段。

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM food_log_items)
        OR EXISTS (SELECT 1 FROM nutrition_unit_conversions)
        OR EXISTS (SELECT 1 FROM nutrition_foods)
        OR EXISTS (SELECT 1 FROM approval_requests) THEN
        RAISE EXCEPTION
            'R13 requires empty V13 tables; export or discard dependent data before rollback';
    END IF;
END $$;

ALTER TABLE food_log_items
    DROP CONSTRAINT IF EXISTS fk_food_log_items_conversion,
    DROP CONSTRAINT IF EXISTS fk_food_log_items_nutrition_food;

DROP TABLE IF EXISTS approval_requests;
DROP TABLE IF EXISTS nutrition_unit_conversions;
DROP TABLE IF EXISTS nutrition_foods;
DROP TABLE IF EXISTS food_log_items;

DROP INDEX IF EXISTS idx_food_logs_user_revision;
DROP INDEX IF EXISTS uk_food_logs_user_idempotency;

ALTER TABLE food_logs
    DROP CONSTRAINT IF EXISTS chk_food_logs_meal_type,
    DROP CONSTRAINT IF EXISTS chk_food_logs_revision;

ALTER TABLE food_logs
    DROP COLUMN IF EXISTS agent_run_id,
    DROP COLUMN IF EXISTS idempotency_key,
    DROP COLUMN IF EXISTS revision;

ALTER TABLE food_logs
    ADD COLUMN IF NOT EXISTS items_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS nutrition_json JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE food_logs
    ALTER COLUMN meal_type DROP NOT NULL;

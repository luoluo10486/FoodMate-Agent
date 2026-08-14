-- 回滚 V15 M1-5。
-- 仅允许在确认没有依赖这些字段的计划写入代码时执行。

DROP INDEX IF EXISTS idx_meal_plans_user_revision;
DROP INDEX IF EXISTS uk_meal_plans_user_idempotency;

ALTER TABLE meal_plans
    DROP CONSTRAINT IF EXISTS chk_meal_plans_revision,
    DROP COLUMN IF EXISTS idempotency_key,
    DROP COLUMN IF EXISTS revision;

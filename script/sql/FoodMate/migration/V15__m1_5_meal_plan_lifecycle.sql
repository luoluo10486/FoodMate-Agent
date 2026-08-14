-- M1-5：补齐餐食计划的版本控制、幂等和生命周期字段。
-- Manual execution only. Flyway remains disabled by policy.

ALTER TABLE meal_plans
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128),
    ADD COLUMN IF NOT EXISTS revision BIGINT NOT NULL DEFAULT 1;

ALTER TABLE meal_plans
    DROP CONSTRAINT IF EXISTS chk_meal_plans_revision;

ALTER TABLE meal_plans
    ADD CONSTRAINT chk_meal_plans_revision CHECK (revision >= 1);

CREATE UNIQUE INDEX IF NOT EXISTS uk_meal_plans_user_idempotency
    ON meal_plans (user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL
      AND is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_meal_plans_user_revision
    ON meal_plans (user_id, meal_plan_id, revision)
    WHERE is_deleted = FALSE;

COMMENT ON COLUMN meal_plans.idempotency_key IS '计划创建写入的幂等键。';
COMMENT ON COLUMN meal_plans.revision IS '计划编辑、状态变更、删除和恢复使用的乐观并发版本，从 1 开始。';

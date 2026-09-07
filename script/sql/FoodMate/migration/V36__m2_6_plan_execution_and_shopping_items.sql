-- M2-6：为餐食计划补充稳定餐次、实际饮食记录关联和可持久化购物项。
-- Manual execution only; Flyway remains disabled by policy.
BEGIN;

ALTER TABLE food_logs
    ADD COLUMN IF NOT EXISTS meal_plan_meal_id BIGINT;

CREATE TABLE IF NOT EXISTS meal_plan_meals (
    meal_plan_meal_id BIGINT PRIMARY KEY,
    meal_plan_id BIGINT NOT NULL REFERENCES meal_plans (meal_plan_id),
    user_id BIGINT NOT NULL REFERENCES users (user_id),
    day_index INT NOT NULL,
    meal_type VARCHAR(32) NOT NULL,
    meal_name VARCHAR(255),
    meal_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    plan_revision BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    CONSTRAINT chk_meal_plan_meals_day_index CHECK (day_index >= 0),
    CONSTRAINT chk_meal_plan_meals_type CHECK (meal_type IN ('breakfast', 'lunch', 'dinner', 'snack'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_meal_plan_meals_slot
    ON meal_plan_meals (meal_plan_id, day_index, meal_type)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_meal_plan_meals_user_plan
    ON meal_plan_meals (user_id, meal_plan_id, day_index, meal_type)
    WHERE is_deleted = FALSE;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conname = 'fk_food_logs_meal_plan_meal'
    ) THEN
        ALTER TABLE food_logs
            ADD CONSTRAINT fk_food_logs_meal_plan_meal
            FOREIGN KEY (meal_plan_meal_id) REFERENCES meal_plan_meals (meal_plan_meal_id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_food_logs_meal_plan_meal
    ON food_logs (user_id, meal_plan_meal_id, meal_time DESC)
    WHERE is_deleted = FALSE AND meal_plan_meal_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS shopping_list_items (
    shopping_list_item_id BIGINT PRIMARY KEY,
    shopping_list_id BIGINT NOT NULL REFERENCES shopping_lists (shopping_list_id),
    meal_plan_id BIGINT NOT NULL REFERENCES meal_plans (meal_plan_id),
    user_id BIGINT NOT NULL REFERENCES users (user_id),
    item_key VARCHAR(512) NOT NULL,
    item_name VARCHAR(255) NOT NULL,
    amount NUMERIC(14, 3),
    unit VARCHAR(32),
    purchased BOOLEAN NOT NULL DEFAULT FALSE,
    purchased_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    CONSTRAINT chk_shopping_list_items_amount CHECK (amount IS NULL OR amount >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_shopping_list_items_key
    ON shopping_list_items (shopping_list_id, item_key)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_shopping_list_items_user_plan
    ON shopping_list_items (user_id, meal_plan_id, shopping_list_id, is_deleted);

COMMENT ON TABLE meal_plan_meals IS '餐食计划的稳定餐次身份；实际饮食记录通过该身份判断执行情况。';
COMMENT ON COLUMN meal_plan_meals.day_index IS '从 0 开始的计划日序号，计划编辑时保持同一餐次的稳定身份。';
COMMENT ON TABLE shopping_list_items IS '购物清单的稳定条目和购买状态；同一条目数量变化会重置购买确认。';
COMMENT ON COLUMN shopping_list_items.item_key IS '规范化后的食材名称和单位组合，用于计划更新时匹配原条目。';

COMMIT;

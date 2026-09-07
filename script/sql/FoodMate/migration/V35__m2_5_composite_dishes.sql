-- M2-5 用户复合菜基础业务闭环。
-- 复合菜独立于营养原子目录；饮食记录只保存选用时的修订和营养快照。

BEGIN;

CREATE TABLE IF NOT EXISTS composite_dishes (
    composite_dish_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (user_id),
    dish_name VARCHAR(128) NOT NULL,
    total_servings NUMERIC(10, 3) NOT NULL,
    calories_kcal_per_serving NUMERIC(12, 4) NOT NULL,
    protein_g_per_serving NUMERIC(12, 4) NOT NULL,
    fat_g_per_serving NUMERIC(12, 4) NOT NULL,
    carbs_g_per_serving NUMERIC(12, 4) NOT NULL,
    nutrition_source VARCHAR(255) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 1,
    idempotency_key VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    CONSTRAINT chk_composite_dishes_servings CHECK (total_servings > 0),
    CONSTRAINT chk_composite_dishes_revision CHECK (revision >= 1)
);

CREATE TABLE IF NOT EXISTS composite_dish_items (
    composite_dish_item_id BIGINT PRIMARY KEY,
    composite_dish_id BIGINT NOT NULL REFERENCES composite_dishes (composite_dish_id),
    item_order INT NOT NULL,
    nutrition_food_id BIGINT NOT NULL REFERENCES nutrition_foods (nutrition_food_id),
    raw_name VARCHAR(255) NOT NULL,
    amount NUMERIC(12, 3) NOT NULL,
    unit VARCHAR(32) NOT NULL,
    normalized_amount NUMERIC(12, 3) NOT NULL,
    normalized_unit VARCHAR(16) NOT NULL,
    conversion_id BIGINT,
    calories_kcal NUMERIC(12, 4) NOT NULL,
    protein_g NUMERIC(12, 4) NOT NULL,
    fat_g NUMERIC(12, 4) NOT NULL,
    carbs_g NUMERIC(12, 4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    CONSTRAINT chk_composite_dish_items_amount CHECK (amount > 0 AND normalized_amount > 0),
    CONSTRAINT uk_composite_dish_items_order UNIQUE (composite_dish_id, item_order)
);

ALTER TABLE food_logs
    ADD COLUMN IF NOT EXISTS composite_dish_id BIGINT REFERENCES composite_dishes (composite_dish_id),
    ADD COLUMN IF NOT EXISTS composite_dish_revision BIGINT,
    ADD COLUMN IF NOT EXISTS composite_dish_servings NUMERIC(10, 3),
    ADD COLUMN IF NOT EXISTS composite_dish_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb;

CREATE INDEX IF NOT EXISTS idx_composite_dishes_user_active
    ON composite_dishes (user_id, is_deleted, updated_at DESC, composite_dish_id DESC);
CREATE INDEX IF NOT EXISTS idx_composite_dish_items_dish
    ON composite_dish_items (composite_dish_id, is_deleted, item_order);
CREATE UNIQUE INDEX IF NOT EXISTS uk_composite_dishes_user_idempotency
    ON composite_dishes (user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_food_logs_composite_dish
    ON food_logs (user_id, composite_dish_id, is_deleted);

COMMENT ON TABLE composite_dishes IS '用户自有复合菜；不属于公共营养原子目录。';
COMMENT ON TABLE composite_dish_items IS '复合菜组成及创建时的营养计算快照。';
COMMENT ON COLUMN food_logs.composite_dish_snapshot_json IS '记录写入时的安全营养快照，不随复合菜后续修改而变化。';

COMMIT;

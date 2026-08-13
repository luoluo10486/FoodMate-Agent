-- M1-5：饮食记录、营养目录、单位换算和写操作确认。
-- Manual execution only. Flyway remains disabled by policy.
-- Preconditions: food_logs has no historical rows, or the project owner has
-- explicitly reviewed the JSON-to-detail migration plan before execution.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM food_logs) THEN
        RAISE EXCEPTION
            'V13 requires an empty food_logs table; review historical JSON data before execution';
    END IF;
END $$;

ALTER TABLE food_logs
    ADD COLUMN IF NOT EXISTS agent_run_id BIGINT REFERENCES agent_runs (agent_run_id),
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128),
    ADD COLUMN IF NOT EXISTS revision BIGINT NOT NULL DEFAULT 1;

ALTER TABLE food_logs
    DROP COLUMN IF EXISTS items_json,
    DROP COLUMN IF EXISTS nutrition_json;

ALTER TABLE food_logs
    ALTER COLUMN meal_type SET NOT NULL;

ALTER TABLE food_logs
    DROP CONSTRAINT IF EXISTS chk_food_logs_meal_type;

ALTER TABLE food_logs
    ADD CONSTRAINT chk_food_logs_meal_type
        CHECK (meal_type IN ('breakfast', 'lunch', 'dinner', 'snack'));

ALTER TABLE food_logs
    DROP CONSTRAINT IF EXISTS chk_food_logs_revision;

ALTER TABLE food_logs
    ADD CONSTRAINT chk_food_logs_revision CHECK (revision >= 1);

CREATE UNIQUE INDEX IF NOT EXISTS uk_food_logs_user_idempotency
    ON food_logs (user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_food_logs_user_revision
    ON food_logs (user_id, food_log_id, revision)
    WHERE is_deleted = FALSE;

CREATE TABLE food_log_items (
    food_log_item_id BIGINT PRIMARY KEY,
    food_log_id BIGINT NOT NULL REFERENCES food_logs (food_log_id),
    item_order INT NOT NULL,
    raw_name VARCHAR(255) NOT NULL,
    nutrition_food_id BIGINT,
    amount NUMERIC(12, 3) NOT NULL,
    unit VARCHAR(32) NOT NULL,
    normalized_amount NUMERIC(12, 3),
    normalized_unit VARCHAR(32),
    conversion_id BIGINT,
    calories_kcal NUMERIC(12, 4),
    protein_g NUMERIC(12, 4),
    fat_g NUMERIC(12, 4),
    carbs_g NUMERIC(12, 4),
    nutrition_status VARCHAR(32) NOT NULL DEFAULT 'pending',
    nutrition_source VARCHAR(64),
    nutrition_version VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    CONSTRAINT chk_food_log_items_order CHECK (item_order >= 0),
    CONSTRAINT chk_food_log_items_amount CHECK (amount > 0),
    CONSTRAINT chk_food_log_items_normalized_amount
        CHECK (normalized_amount IS NULL OR normalized_amount > 0),
    CONSTRAINT chk_food_log_items_status
        CHECK (nutrition_status IN ('matched', 'pending', 'invalid')),
    CONSTRAINT chk_food_log_items_matched_snapshot CHECK (
        nutrition_status <> 'matched'
        OR (
            nutrition_food_id IS NOT NULL
            AND normalized_amount IS NOT NULL
            AND normalized_unit IN ('g', 'ml')
            AND calories_kcal IS NOT NULL
            AND protein_g IS NOT NULL
            AND fat_g IS NOT NULL
            AND carbs_g IS NOT NULL
        )
    ),
    CONSTRAINT chk_food_log_items_unmatched_snapshot CHECK (
        nutrition_status = 'matched'
        OR (
            calories_kcal IS NULL
            AND protein_g IS NULL
            AND fat_g IS NULL
            AND carbs_g IS NULL
        )
    )
);

CREATE UNIQUE INDEX uk_food_log_items_order
    ON food_log_items (food_log_id, item_order)
    WHERE is_deleted = FALSE;

CREATE INDEX idx_food_log_items_food_log
    ON food_log_items (food_log_id, item_order, is_deleted);

CREATE TABLE nutrition_foods (
    nutrition_food_id BIGINT PRIMARY KEY,
    standard_name VARCHAR(255) NOT NULL,
    chinese_name VARCHAR(255) NOT NULL,
    aliases_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    category VARCHAR(64),
    basis_unit VARCHAR(8) NOT NULL,
    calories_kcal_per_100 NUMERIC(12, 4) NOT NULL,
    protein_g_per_100 NUMERIC(12, 4) NOT NULL,
    fat_g_per_100 NUMERIC(12, 4) NOT NULL,
    carbs_g_per_100 NUMERIC(12, 4) NOT NULL,
    source_name VARCHAR(128) NOT NULL,
    source_version VARCHAR(64) NOT NULL,
    review_status VARCHAR(32) NOT NULL DEFAULT 'pending',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    CONSTRAINT chk_nutrition_foods_basis_unit CHECK (basis_unit IN ('g', 'ml')),
    CONSTRAINT chk_nutrition_foods_values CHECK (
        calories_kcal_per_100 >= 0
        AND protein_g_per_100 >= 0
        AND fat_g_per_100 >= 0
        AND carbs_g_per_100 >= 0
    ),
    CONSTRAINT chk_nutrition_foods_review_status
        CHECK (review_status IN ('pending', 'approved', 'retired'))
);

CREATE UNIQUE INDEX uk_nutrition_foods_standard_name
    ON nutrition_foods (standard_name)
    WHERE is_deleted = FALSE;

CREATE INDEX idx_nutrition_foods_lookup
    ON nutrition_foods (chinese_name, review_status, is_deleted);

ALTER TABLE food_log_items
    ADD CONSTRAINT fk_food_log_items_nutrition_food
        FOREIGN KEY (nutrition_food_id) REFERENCES nutrition_foods (nutrition_food_id);

CREATE TABLE nutrition_unit_conversions (
    conversion_id BIGINT PRIMARY KEY,
    nutrition_food_id BIGINT NOT NULL REFERENCES nutrition_foods (nutrition_food_id),
    source_unit VARCHAR(32) NOT NULL,
    target_unit VARCHAR(8) NOT NULL,
    multiplier NUMERIC(12, 4) NOT NULL,
    source_name VARCHAR(128) NOT NULL,
    source_version VARCHAR(64) NOT NULL,
    review_status VARCHAR(32) NOT NULL DEFAULT 'pending',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    CONSTRAINT chk_nutrition_unit_conversions_target_unit CHECK (target_unit IN ('g', 'ml')),
    CONSTRAINT chk_nutrition_unit_conversions_multiplier CHECK (multiplier > 0),
    CONSTRAINT chk_nutrition_unit_conversions_review_status
        CHECK (review_status IN ('pending', 'approved', 'retired'))
);

CREATE UNIQUE INDEX uk_nutrition_unit_conversions_rule
    ON nutrition_unit_conversions (nutrition_food_id, source_unit, target_unit)
    WHERE is_deleted = FALSE;

CREATE INDEX idx_nutrition_unit_conversions_lookup
    ON nutrition_unit_conversions (nutrition_food_id, source_unit, review_status, is_deleted);

ALTER TABLE food_log_items
    ADD CONSTRAINT fk_food_log_items_conversion
        FOREIGN KEY (conversion_id) REFERENCES nutrition_unit_conversions (conversion_id);

CREATE TABLE approval_requests (
    approval_request_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (user_id),
    session_id BIGINT REFERENCES sessions (session_id),
    agent_run_id BIGINT REFERENCES agent_runs (agent_run_id),
    resource_type VARCHAR(64),
    resource_id BIGINT,
    operation VARCHAR(64) NOT NULL,
    parameters_digest VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    request_id VARCHAR(64),
    trace_id VARCHAR(64),
    idempotency_key VARCHAR(128) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ,
    executed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    CONSTRAINT chk_approval_requests_operation
        CHECK (operation IN ('create', 'update', 'delete', 'restore', 'save_plan')),
    CONSTRAINT chk_approval_requests_status
        CHECK (status IN ('pending', 'confirmed', 'rejected', 'expired', 'executed', 'failed', 'superseded'))
);

CREATE UNIQUE INDEX uk_approval_requests_user_idempotency
    ON approval_requests (user_id, idempotency_key)
    WHERE is_deleted = FALSE;

CREATE INDEX idx_approval_requests_user_status
    ON approval_requests (user_id, status, expires_at, is_deleted);

CREATE INDEX idx_approval_requests_resource
    ON approval_requests (resource_type, resource_id, status, is_deleted);

COMMENT ON TABLE food_logs IS '饮食记录餐次主表，食材明细和营养快照保存在 food_log_items。';
COMMENT ON COLUMN food_logs.agent_run_id IS '产生该记录的 AgentRun，可为空。';
COMMENT ON COLUMN food_logs.idempotency_key IS '创建写入的幂等键。';
COMMENT ON COLUMN food_logs.revision IS '编辑、删除和恢复使用的乐观并发版本，从 1 开始。';

COMMENT ON TABLE food_log_items IS '饮食记录食材明细表，保存原始份量、目录匹配和确定性营养快照。';
COMMENT ON COLUMN food_log_items.food_log_item_id IS '饮食记录明细主键。';
COMMENT ON COLUMN food_log_items.food_log_id IS '所属饮食记录餐次。';
COMMENT ON COLUMN food_log_items.item_order IS '同一餐次内的明细顺序。';
COMMENT ON COLUMN food_log_items.raw_name IS '用户输入的原始食材名称。';
COMMENT ON COLUMN food_log_items.nutrition_food_id IS '匹配的营养目录项。';
COMMENT ON COLUMN food_log_items.amount IS '用户输入的原始份量。';
COMMENT ON COLUMN food_log_items.unit IS '用户输入的原始单位。';
COMMENT ON COLUMN food_log_items.normalized_amount IS '换算后的标准份量。';
COMMENT ON COLUMN food_log_items.normalized_unit IS '换算后的标准单位，仅支持 g 或 ml。';
COMMENT ON COLUMN food_log_items.conversion_id IS '使用的单位换算规则。';
COMMENT ON COLUMN food_log_items.calories_kcal IS '按目录和份量计算后冻结的热量快照。';
COMMENT ON COLUMN food_log_items.protein_g IS '按目录和份量计算后冻结的蛋白质快照。';
COMMENT ON COLUMN food_log_items.fat_g IS '按目录和份量计算后冻结的脂肪快照。';
COMMENT ON COLUMN food_log_items.carbs_g IS '按目录和份量计算后冻结的碳水快照。';
COMMENT ON COLUMN food_log_items.nutrition_status IS '营养状态：matched、pending 或 invalid。';
COMMENT ON COLUMN food_log_items.nutrition_source IS '营养数据来源标识。';
COMMENT ON COLUMN food_log_items.nutrition_version IS '营养数据版本。';

COMMENT ON TABLE nutrition_foods IS '营养目录表，保存人工复核的每 100g 或每 100ml 基准营养值。';
COMMENT ON COLUMN nutrition_foods.nutrition_food_id IS '营养目录主键。';
COMMENT ON COLUMN nutrition_foods.standard_name IS '稳定的标准食材名称。';
COMMENT ON COLUMN nutrition_foods.chinese_name IS '面向用户的中文食材名称。';
COMMENT ON COLUMN nutrition_foods.aliases_json IS '可匹配的别名数组。';
COMMENT ON COLUMN nutrition_foods.category IS '食材分类。';
COMMENT ON COLUMN nutrition_foods.basis_unit IS '营养基准单位：g 或 ml。';
COMMENT ON COLUMN nutrition_foods.calories_kcal_per_100 IS '每 100 个基准单位的热量。';
COMMENT ON COLUMN nutrition_foods.protein_g_per_100 IS '每 100 个基准单位的蛋白质。';
COMMENT ON COLUMN nutrition_foods.fat_g_per_100 IS '每 100 个基准单位的脂肪。';
COMMENT ON COLUMN nutrition_foods.carbs_g_per_100 IS '每 100 个基准单位的碳水。';
COMMENT ON COLUMN nutrition_foods.source_name IS '营养数据来源名称。';
COMMENT ON COLUMN nutrition_foods.source_version IS '营养数据来源版本。';
COMMENT ON COLUMN nutrition_foods.review_status IS '目录状态：pending、approved 或 retired。';

COMMENT ON TABLE nutrition_unit_conversions IS '食材级单位换算表，保存可审计的原始单位到 g/ml 规则。';
COMMENT ON COLUMN nutrition_unit_conversions.conversion_id IS '单位换算规则主键。';
COMMENT ON COLUMN nutrition_unit_conversions.nutrition_food_id IS '对应的营养目录项。';
COMMENT ON COLUMN nutrition_unit_conversions.source_unit IS '用户输入的原始单位。';
COMMENT ON COLUMN nutrition_unit_conversions.target_unit IS '换算目标单位：g 或 ml。';
COMMENT ON COLUMN nutrition_unit_conversions.multiplier IS '1 个原始单位对应的目标单位数量。';
COMMENT ON COLUMN nutrition_unit_conversions.source_name IS '单位规则来源名称。';
COMMENT ON COLUMN nutrition_unit_conversions.source_version IS '单位规则来源版本。';
COMMENT ON COLUMN nutrition_unit_conversions.review_status IS '规则状态：pending、approved 或 retired。';

COMMENT ON TABLE approval_requests IS 'Agent 和自然语言写操作确认事实表。';
COMMENT ON COLUMN approval_requests.approval_request_id IS '确认请求主键。';
COMMENT ON COLUMN approval_requests.user_id IS '确认请求所属用户。';
COMMENT ON COLUMN approval_requests.session_id IS '来源会话，可为空。';
COMMENT ON COLUMN approval_requests.agent_run_id IS '来源 AgentRun，可为空。';
COMMENT ON COLUMN approval_requests.resource_type IS '确认绑定的资源类型。';
COMMENT ON COLUMN approval_requests.resource_id IS '确认绑定的资源 ID。';
COMMENT ON COLUMN approval_requests.operation IS '写操作类型。';
COMMENT ON COLUMN approval_requests.parameters_digest IS '请求参数摘要，参数变化会使确认失效。';
COMMENT ON COLUMN approval_requests.status IS '确认状态。';
COMMENT ON COLUMN approval_requests.request_id IS '关联请求 ID。';
COMMENT ON COLUMN approval_requests.trace_id IS '关联链路 ID。';
COMMENT ON COLUMN approval_requests.idempotency_key IS '确认和执行使用的幂等键。';
COMMENT ON COLUMN approval_requests.expires_at IS '确认失效时间。';
COMMENT ON COLUMN approval_requests.confirmed_at IS '确认时间。';
COMMENT ON COLUMN approval_requests.executed_at IS '业务写入执行完成时间。';


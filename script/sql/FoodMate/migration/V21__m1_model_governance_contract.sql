-- M1 model governance contract: provider/model catalog, versioned pricing and budget policy.
-- Manual execution only. Existing model route and usage rows are preserved.

CREATE TABLE IF NOT EXISTS model_providers (
    provider_id BIGINT PRIMARY KEY,
    provider_code VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    endpoint_config_key VARCHAR(128),
    capability_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    CONSTRAINT chk_model_providers_status CHECK (status IN ('active', 'disabled'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_model_providers_code
    ON model_providers (provider_code) WHERE is_deleted = FALSE;

CREATE TABLE IF NOT EXISTS model_catalog (
    model_id BIGINT PRIMARY KEY,
    provider_code VARCHAR(64) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    model_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    context_tokens INT,
    max_output_tokens INT,
    timeout_ms INT NOT NULL DEFAULT 15000,
    capability_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    revision BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    CONSTRAINT chk_model_catalog_status CHECK (status IN ('active', 'disabled')),
    CONSTRAINT chk_model_catalog_revision CHECK (revision >= 1),
    CONSTRAINT chk_model_catalog_timeout CHECK (timeout_ms > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_model_catalog_provider_name
    ON model_catalog (provider_code, model_name) WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_model_catalog_type_status
    ON model_catalog (model_type, status, is_deleted);

CREATE TABLE IF NOT EXISTS model_price_versions (
    price_version_id BIGINT PRIMARY KEY,
    provider_code VARCHAR(64) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    price_version VARCHAR(64) NOT NULL,
    input_price_per_million NUMERIC(12, 6) NOT NULL,
    output_price_per_million NUMERIC(12, 6) NOT NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'CNY',
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    effective_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    CONSTRAINT chk_model_price_status CHECK (status IN ('active', 'disabled')),
    CONSTRAINT chk_model_price_non_negative CHECK (
        input_price_per_million >= 0 AND output_price_per_million >= 0
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_model_price_version
    ON model_price_versions (provider_code, model_name, price_version)
    WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_model_price_active_effective
    ON model_price_versions (provider_code, model_name, status, effective_at DESC)
    WHERE is_deleted = FALSE;

CREATE TABLE IF NOT EXISTS model_budget_policies (
    budget_policy_id BIGINT PRIMARY KEY,
    policy_key VARCHAR(128) NOT NULL,
    scene VARCHAR(64) NOT NULL,
    scope_type VARCHAR(32) NOT NULL DEFAULT 'global',
    max_total_tokens INT NOT NULL,
    max_cost_cny NUMERIC(12, 6) NOT NULL,
    max_model_calls INT NOT NULL,
    max_step_retries INT NOT NULL DEFAULT 0,
    window_type VARCHAR(32) NOT NULL DEFAULT 'run',
    policy_version VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    revision BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    CONSTRAINT chk_model_budget_status CHECK (status IN ('active', 'disabled')),
    CONSTRAINT chk_model_budget_scope CHECK (scope_type IN ('global', 'scene', 'user')),
    CONSTRAINT chk_model_budget_window CHECK (window_type IN ('run', 'day')),
    CONSTRAINT chk_model_budget_values CHECK (
        max_total_tokens > 0 AND max_cost_cny >= 0 AND max_model_calls > 0
        AND max_step_retries >= 0 AND revision >= 1
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_model_budget_policy_version
    ON model_budget_policies (policy_key, policy_version) WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_model_budget_active_scene
    ON model_budget_policies (scene, scope_type, status, revision DESC)
    WHERE is_deleted = FALSE;

ALTER TABLE model_route_rules
    ADD COLUMN IF NOT EXISTS model_name VARCHAR(128),
    ADD COLUMN IF NOT EXISTS fallback_model_name VARCHAR(128),
    ADD COLUMN IF NOT EXISTS priority INT NOT NULL DEFAULT 100,
    ADD COLUMN IF NOT EXISTS route_version VARCHAR(64) NOT NULL DEFAULT 'legacy-v1',
    ADD COLUMN IF NOT EXISTS price_version VARCHAR(64),
    ADD COLUMN IF NOT EXISTS budget_policy_version VARCHAR(64),
    ADD COLUMN IF NOT EXISTS revision BIGINT NOT NULL DEFAULT 1;

ALTER TABLE model_usage_logs
    ADD COLUMN IF NOT EXISTS route_version VARCHAR(64),
    ADD COLUMN IF NOT EXISTS price_version VARCHAR(64),
    ADD COLUMN IF NOT EXISTS budget_policy_version VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_model_route_rules_active_priority
    ON model_route_rules (tenant_id, scene, model_type, status, priority, revision DESC)
    WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_model_usage_logs_governance
    ON model_usage_logs (provider_code, model_name, scene, created_at DESC)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE model_providers IS '模型供应商元数据；不保存 API Key 或可逆密钥材料。';
COMMENT ON COLUMN model_providers.provider_id IS '供应商主键。';
COMMENT ON COLUMN model_providers.provider_code IS '稳定供应商编码。';
COMMENT ON COLUMN model_providers.display_name IS '后台展示名称。';
COMMENT ON COLUMN model_providers.status IS '供应商启停状态。';
COMMENT ON COLUMN model_providers.endpoint_config_key IS '环境配置引用名，不是密钥或完整地址。';
COMMENT ON COLUMN model_providers.capability_json IS '非敏感能力摘要。';
COMMENT ON COLUMN model_providers.created_at IS '创建时间。';
COMMENT ON COLUMN model_providers.updated_at IS '更新时间。';
COMMENT ON COLUMN model_providers.created_by IS '创建人。';
COMMENT ON COLUMN model_providers.updated_by IS '更新人。';
COMMENT ON COLUMN model_providers.is_deleted IS '逻辑删除标记。';
COMMENT ON COLUMN model_providers.deleted_at IS '逻辑删除时间。';
COMMENT ON COLUMN model_providers.deleted_by IS '逻辑删除人。';

COMMENT ON TABLE model_catalog IS '供应商模型目录和能力限制。';
COMMENT ON COLUMN model_catalog.model_id IS '模型目录主键。';
COMMENT ON COLUMN model_catalog.provider_code IS '供应商编码。';
COMMENT ON COLUMN model_catalog.model_name IS '模型名称。';
COMMENT ON COLUMN model_catalog.model_type IS '模型能力类型。';
COMMENT ON COLUMN model_catalog.status IS '模型启停状态。';
COMMENT ON COLUMN model_catalog.context_tokens IS '上下文 Token 上限。';
COMMENT ON COLUMN model_catalog.max_output_tokens IS '输出 Token 上限。';
COMMENT ON COLUMN model_catalog.timeout_ms IS '单次调用超时。';
COMMENT ON COLUMN model_catalog.capability_json IS '非敏感模型能力摘要。';
COMMENT ON COLUMN model_catalog.revision IS '乐观并发版本。';
COMMENT ON COLUMN model_catalog.created_at IS '创建时间。';
COMMENT ON COLUMN model_catalog.updated_at IS '更新时间。';
COMMENT ON COLUMN model_catalog.created_by IS '创建人。';
COMMENT ON COLUMN model_catalog.updated_by IS '更新人。';
COMMENT ON COLUMN model_catalog.is_deleted IS '逻辑删除标记。';
COMMENT ON COLUMN model_catalog.deleted_at IS '逻辑删除时间。';
COMMENT ON COLUMN model_catalog.deleted_by IS '逻辑删除人。';

COMMENT ON TABLE model_price_versions IS '不可变模型价格版本，历史用量按快照计费。';
COMMENT ON COLUMN model_price_versions.price_version_id IS '价格版本主键。';
COMMENT ON COLUMN model_price_versions.provider_code IS '供应商编码。';
COMMENT ON COLUMN model_price_versions.model_name IS '模型名称。';
COMMENT ON COLUMN model_price_versions.price_version IS '价格版本。';
COMMENT ON COLUMN model_price_versions.input_price_per_million IS '每百万输入 Token 价格。';
COMMENT ON COLUMN model_price_versions.output_price_per_million IS '每百万输出 Token 价格。';
COMMENT ON COLUMN model_price_versions.currency IS '价格币种。';
COMMENT ON COLUMN model_price_versions.status IS '价格版本状态。';
COMMENT ON COLUMN model_price_versions.effective_at IS '生效时间。';
COMMENT ON COLUMN model_price_versions.created_at IS '创建时间。';
COMMENT ON COLUMN model_price_versions.updated_at IS '更新时间。';
COMMENT ON COLUMN model_price_versions.created_by IS '创建人。';
COMMENT ON COLUMN model_price_versions.updated_by IS '更新人。';
COMMENT ON COLUMN model_price_versions.is_deleted IS '逻辑删除标记。';
COMMENT ON COLUMN model_price_versions.deleted_at IS '逻辑删除时间。';
COMMENT ON COLUMN model_price_versions.deleted_by IS '逻辑删除人。';

COMMENT ON TABLE model_budget_policies IS '模型调用预算和配额策略版本。';
COMMENT ON COLUMN model_budget_policies.budget_policy_id IS '预算策略主键。';
COMMENT ON COLUMN model_budget_policies.policy_key IS '稳定策略键。';
COMMENT ON COLUMN model_budget_policies.scene IS '业务场景。';
COMMENT ON COLUMN model_budget_policies.scope_type IS '策略作用域。';
COMMENT ON COLUMN model_budget_policies.max_total_tokens IS '最大 Token 数。';
COMMENT ON COLUMN model_budget_policies.max_cost_cny IS '最大成本。';
COMMENT ON COLUMN model_budget_policies.max_model_calls IS '最大模型调用次数。';
COMMENT ON COLUMN model_budget_policies.max_step_retries IS '最大步骤重试次数。';
COMMENT ON COLUMN model_budget_policies.window_type IS '预算窗口。';
COMMENT ON COLUMN model_budget_policies.policy_version IS '策略版本。';
COMMENT ON COLUMN model_budget_policies.status IS '策略状态。';
COMMENT ON COLUMN model_budget_policies.revision IS '乐观并发版本。';
COMMENT ON COLUMN model_budget_policies.created_at IS '创建时间。';
COMMENT ON COLUMN model_budget_policies.updated_at IS '更新时间。';
COMMENT ON COLUMN model_budget_policies.created_by IS '创建人。';
COMMENT ON COLUMN model_budget_policies.updated_by IS '更新人。';
COMMENT ON COLUMN model_budget_policies.is_deleted IS '逻辑删除标记。';
COMMENT ON COLUMN model_budget_policies.deleted_at IS '逻辑删除时间。';
COMMENT ON COLUMN model_budget_policies.deleted_by IS '逻辑删除人。';

COMMENT ON COLUMN model_route_rules.model_name IS '主模型名称。';
COMMENT ON COLUMN model_route_rules.fallback_model_name IS '备用模型名称。';
COMMENT ON COLUMN model_route_rules.priority IS '路由优先级，数字越小越优先。';
COMMENT ON COLUMN model_route_rules.route_version IS '路由版本。';
COMMENT ON COLUMN model_route_rules.price_version IS '绑定的价格版本。';
COMMENT ON COLUMN model_route_rules.budget_policy_version IS '绑定的预算策略版本。';
COMMENT ON COLUMN model_route_rules.revision IS '乐观并发版本。';
COMMENT ON COLUMN model_usage_logs.route_version IS '调用时固化的路由版本。';
COMMENT ON COLUMN model_usage_logs.price_version IS '调用时固化的价格版本。';
COMMENT ON COLUMN model_usage_logs.budget_policy_version IS '调用时固化的预算策略版本。';

-- FoodMate 核心 PostgreSQL 数据库结构。
-- 主键 ID 由应用层 Snowflake 生成器生成。

CREATE TABLE users (
    user_id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    user_no VARCHAR(64) NOT NULL,
    username VARCHAR(64) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(128),
    role VARCHAR(32) NOT NULL DEFAULT 'user',
    avatar_url VARCHAR(512),
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    last_login_at TIMESTAMPTZ,
    password_updated_at TIMESTAMPTZ,
    login_failed_count INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    CONSTRAINT chk_users_role CHECK (role IN ('user', 'admin', 'operator')),
    CONSTRAINT chk_users_status CHECK (status IN ('active', 'disabled', 'locked'))
);

CREATE UNIQUE INDEX uk_users_user_no ON users (user_no);
CREATE UNIQUE INDEX uk_users_username ON users (username) WHERE is_deleted = FALSE;
CREATE UNIQUE INDEX uk_users_email ON users (email) WHERE is_deleted = FALSE;

CREATE TABLE user_profiles (
    profile_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (user_id),
    display_name VARCHAR(128),
    gender VARCHAR(32),
    birthday DATE,
    height_cm DECIMAL(6, 2),
    weight_kg DECIMAL(6, 2),
    activity_level VARCHAR(64),
    diet_goal VARCHAR(64),
    calorie_target INT,
    protein_target INT,
    allergens JSONB NOT NULL DEFAULT '[]'::jsonb,
    dislikes JSONB NOT NULL DEFAULT '[]'::jsonb,
    preferred_units JSONB NOT NULL DEFAULT '{}'::jsonb,
    profile_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT
);

CREATE UNIQUE INDEX uk_user_profiles_user_id ON user_profiles (user_id) WHERE is_deleted = FALSE;

CREATE TABLE auth_refresh_tokens (
    refresh_token_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (user_id),
    token_hash VARCHAR(255) NOT NULL,
    device_id VARCHAR(128),
    user_agent VARCHAR(512),
    ip_address VARCHAR(64),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    rotated_from_token_id BIGINT REFERENCES auth_refresh_tokens (refresh_token_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT
);

CREATE UNIQUE INDEX uk_auth_refresh_tokens_token_hash ON auth_refresh_tokens (token_hash);
CREATE INDEX idx_auth_refresh_tokens_user_expires_at ON auth_refresh_tokens (user_id, expires_at);

CREATE TABLE user_avatar_assets (
    avatar_asset_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (user_id),
    storage_key VARCHAR(255) NOT NULL,
    url VARCHAR(512) NOT NULL,
    mime_type VARCHAR(64),
    size_bytes BIGINT,
    width INT,
    height INT,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    CONSTRAINT chk_user_avatar_assets_status CHECK (status IN ('active', 'replaced', 'deleted'))
);

CREATE INDEX idx_user_avatar_assets_user_status ON user_avatar_assets (user_id, status);

CREATE TABLE sessions (
    session_id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    user_id BIGINT NOT NULL REFERENCES users (user_id),
    title VARCHAR(255),
    mode VARCHAR(32) NOT NULL DEFAULT 'agent',
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    last_message_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    CONSTRAINT chk_sessions_mode CHECK (mode IN ('agent', 'chat')),
    CONSTRAINT chk_sessions_status CHECK (status IN ('active', 'archived', 'closed'))
);

CREATE INDEX idx_sessions_user_last_message_at ON sessions (user_id, last_message_at DESC, is_deleted);

CREATE TABLE messages (
    message_id BIGINT PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES sessions (session_id),
    agent_run_id BIGINT,
    role VARCHAR(32) NOT NULL,
    content TEXT,
    structured_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    sequence_no INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    CONSTRAINT chk_messages_role CHECK (role IN ('user', 'assistant', 'system', 'tool'))
);

CREATE UNIQUE INDEX uk_messages_session_sequence ON messages (session_id, sequence_no) WHERE is_deleted = FALSE;
CREATE INDEX idx_messages_session_sequence ON messages (session_id, sequence_no, is_deleted);

CREATE TABLE agent_runs (
    agent_run_id BIGINT PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES sessions (session_id),
    user_message_id BIGINT REFERENCES messages (message_id),
    intent VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'queued',
    plan_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    result_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_code VARCHAR(64),
    trace_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    CONSTRAINT chk_agent_runs_status CHECK (
        status IN ('queued', 'routed', 'waiting_user', 'planning', 'retrieving', 'executing', 'validating', 'completed', 'failed', 'cancelled')
    )
);

ALTER TABLE messages
    ADD CONSTRAINT fk_messages_agent_run_id FOREIGN KEY (agent_run_id) REFERENCES agent_runs (agent_run_id);

CREATE INDEX idx_agent_runs_session_created_at ON agent_runs (session_id, created_at DESC, is_deleted);

CREATE TABLE tool_calls (
    tool_call_id BIGINT PRIMARY KEY,
    agent_run_id BIGINT NOT NULL REFERENCES agent_runs (agent_run_id),
    tool_name VARCHAR(64) NOT NULL,
    tool_version VARCHAR(32),
    input_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    output_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    latency_ms INT,
    error_code VARCHAR(64),
    trace_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    CONSTRAINT chk_tool_calls_status CHECK (status IN ('pending', 'running', 'success', 'failed', 'timeout', 'cancelled'))
);

CREATE INDEX idx_tool_calls_run_created_at ON tool_calls (agent_run_id, created_at DESC, is_deleted);

CREATE TABLE food_logs (
    food_log_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (user_id),
    session_id BIGINT REFERENCES sessions (session_id),
    meal_time TIMESTAMPTZ NOT NULL,
    meal_type VARCHAR(32),
    items_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    nutrition_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    notes TEXT,
    source VARCHAR(32) NOT NULL DEFAULT 'manual',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    CONSTRAINT chk_food_logs_source CHECK (source IN ('manual', 'agent', 'import'))
);

CREATE INDEX idx_food_logs_user_meal_time ON food_logs (user_id, meal_time DESC, is_deleted);

CREATE TABLE analysis_reports (
    report_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (user_id),
    session_id BIGINT REFERENCES sessions (session_id),
    agent_run_id BIGINT REFERENCES agent_runs (agent_run_id),
    report_type VARCHAR(64) NOT NULL,
    range_key VARCHAR(32),
    report_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'generated',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT
);

CREATE INDEX idx_analysis_reports_user_created_at ON analysis_reports (user_id, created_at DESC, is_deleted);

CREATE TABLE meal_plans (
    meal_plan_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (user_id),
    session_id BIGINT REFERENCES sessions (session_id),
    plan_name VARCHAR(128),
    days INT,
    budget NUMERIC(12, 2),
    constraints_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    plan_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    validation_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    CONSTRAINT chk_meal_plans_status CHECK (status IN ('draft', 'validated', 'saved'))
);

CREATE INDEX idx_meal_plans_user_status ON meal_plans (user_id, status, is_deleted);

CREATE TABLE shopping_lists (
    shopping_list_id BIGINT PRIMARY KEY,
    meal_plan_id BIGINT NOT NULL REFERENCES meal_plans (meal_plan_id),
    user_id BIGINT NOT NULL REFERENCES users (user_id),
    items_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    CONSTRAINT chk_shopping_lists_status CHECK (status IN ('draft', 'generated', 'confirmed'))
);

CREATE INDEX idx_shopping_lists_meal_plan ON shopping_lists (meal_plan_id, is_deleted);

CREATE TABLE user_memories (
    memory_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (user_id),
    memory_type VARCHAR(32) NOT NULL,
    memory_key VARCHAR(64) NOT NULL,
    memory_value JSONB NOT NULL DEFAULT '{}'::jsonb,
    confidence NUMERIC(5, 4),
    source VARCHAR(32),
    scope VARCHAR(32),
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    CONSTRAINT chk_user_memories_confidence CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1))
);

CREATE INDEX idx_user_memories_user_key ON user_memories (user_id, memory_type, memory_key, is_deleted);

CREATE TABLE session_summaries (
    summary_id BIGINT PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES sessions (session_id),
    summary_text TEXT,
    key_constraints JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT
);

CREATE UNIQUE INDEX uk_session_summaries_session_id ON session_summaries (session_id) WHERE is_deleted = FALSE;

CREATE TABLE knowledge_documents (
    document_id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    source_type VARCHAR(64),
    title VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'uploaded',
    version VARCHAR(32),
    storage_key VARCHAR(255),
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    CONSTRAINT chk_knowledge_documents_status CHECK (status IN ('uploaded', 'parsed', 'indexed', 'disabled'))
);

CREATE INDEX idx_knowledge_documents_tenant_status ON knowledge_documents (tenant_id, status, is_deleted);

CREATE TABLE knowledge_chunks (
    chunk_id BIGINT PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES knowledge_documents (document_id),
    chunk_no INT NOT NULL,
    chunk_text TEXT NOT NULL,
    section_path VARCHAR(255),
    tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    version VARCHAR(32),
    embedding_id VARCHAR(64),
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT
);

CREATE UNIQUE INDEX uk_knowledge_chunks_document_chunk_no ON knowledge_chunks (document_id, chunk_no) WHERE is_deleted = FALSE;
CREATE INDEX idx_knowledge_chunks_document_chunk_no ON knowledge_chunks (document_id, chunk_no, is_deleted);

CREATE TABLE data_sources (
    datasource_id BIGINT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    db_type VARCHAR(32) NOT NULL,
    purpose VARCHAR(128),
    visibility VARCHAR(32) NOT NULL DEFAULT 'restricted',
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    readonly BOOLEAN NOT NULL DEFAULT TRUE,
    connection_ref VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    CONSTRAINT chk_data_sources_readonly CHECK (readonly = TRUE)
);

CREATE UNIQUE INDEX uk_data_sources_name ON data_sources (name) WHERE is_deleted = FALSE;

CREATE TABLE schema_catalogs (
    schema_catalog_id BIGINT PRIMARY KEY,
    datasource_id BIGINT NOT NULL REFERENCES data_sources (datasource_id),
    schema_name VARCHAR(64) NOT NULL,
    table_name VARCHAR(128) NOT NULL,
    field_name VARCHAR(128) NOT NULL,
    field_desc VARCHAR(255),
    data_type VARCHAR(64),
    is_sensitive BOOLEAN NOT NULL DEFAULT FALSE,
    sample_sql TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT
);

CREATE INDEX idx_schema_catalogs_datasource_table ON schema_catalogs (datasource_id, schema_name, table_name, is_deleted);

CREATE TABLE sql_query_audits (
    sql_audit_id BIGINT PRIMARY KEY,
    session_id BIGINT REFERENCES sessions (session_id),
    agent_run_id BIGINT REFERENCES agent_runs (agent_run_id),
    datasource_id BIGINT REFERENCES data_sources (datasource_id),
    original_question TEXT,
    resolved_question TEXT,
    sql_text TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'drafted',
    reject_reason VARCHAR(255),
    row_count INT,
    latency_ms INT,
    trace_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    CONSTRAINT chk_sql_query_audits_status CHECK (status IN ('drafted', 'validated', 'rejected', 'executed'))
);

CREATE TABLE tool_registries (
    tool_id BIGINT PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    display_name VARCHAR(128),
    description VARCHAR(255),
    category VARCHAR(32),
    risk_level VARCHAR(16),
    availability_scope VARCHAR(32),
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    current_version VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT
);

CREATE UNIQUE INDEX uk_tool_registries_name ON tool_registries (name) WHERE is_deleted = FALSE;

CREATE TABLE tool_schema_versions (
    tool_schema_version_id BIGINT PRIMARY KEY,
    tool_id BIGINT NOT NULL REFERENCES tool_registries (tool_id),
    version VARCHAR(32) NOT NULL,
    input_schema JSONB NOT NULL DEFAULT '{}'::jsonb,
    output_schema JSONB NOT NULL DEFAULT '{}'::jsonb,
    permissions JSONB NOT NULL DEFAULT '{}'::jsonb,
    timeout_ms INT NOT NULL DEFAULT 5000,
    retryable BOOLEAN NOT NULL DEFAULT FALSE,
    idempotent BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT
);

CREATE UNIQUE INDEX uk_tool_schema_versions_tool_version ON tool_schema_versions (tool_id, version) WHERE is_deleted = FALSE;

CREATE TABLE model_usage_logs (
    model_usage_log_id BIGINT PRIMARY KEY,
    request_id VARCHAR(64) NOT NULL,
    trace_id VARCHAR(64),
    scene VARCHAR(64),
    provider_code VARCHAR(64),
    model_name VARCHAR(128),
    usage_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    latency_ms INT,
    cost_amount NUMERIC(12, 6),
    status VARCHAR(32) NOT NULL DEFAULT 'success',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT
);

CREATE UNIQUE INDEX uk_model_usage_logs_request_id ON model_usage_logs (request_id);

CREATE TABLE model_route_rules (
    model_route_rule_id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    scene VARCHAR(64) NOT NULL,
    model_type VARCHAR(32) NOT NULL,
    provider_code VARCHAR(64) NOT NULL,
    fallback_provider_code VARCHAR(64),
    max_cost NUMERIC(12, 6),
    max_latency_ms INT,
    rule_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT
);

CREATE INDEX idx_model_route_rules_scene_type ON model_route_rules (scene, model_type, status, is_deleted);

CREATE TABLE operation_audits (
    operation_audit_id BIGINT PRIMARY KEY,
    operator_id BIGINT,
    request_id VARCHAR(64),
    trace_id VARCHAR(64),
    target_type VARCHAR(64) NOT NULL,
    target_id VARCHAR(64),
    action VARCHAR(64) NOT NULL,
    result VARCHAR(32) NOT NULL,
    request_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    response_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT
);

CREATE INDEX idx_operation_audits_operator_created_at ON operation_audits (operator_id, created_at DESC);

COMMENT ON TABLE users IS '用户账号表，保存登录账号、安全状态和基础展示信息。';
COMMENT ON COLUMN users.user_id IS '用户主键，应用层生成的 Snowflake ID。';
COMMENT ON COLUMN users.tenant_id IS '租户 ID，用于预留多租户隔离。';
COMMENT ON COLUMN users.user_no IS '对外展示或业务引用的稳定用户编号。';
COMMENT ON COLUMN users.username IS '登录用户名。';
COMMENT ON COLUMN users.email IS '登录邮箱地址。';
COMMENT ON COLUMN users.password_hash IS '密码哈希值，不存储明文密码。';
COMMENT ON COLUMN users.nickname IS '用户昵称。';
COMMENT ON COLUMN users.role IS '用户角色，例如普通用户、管理员或运营人员。';
COMMENT ON COLUMN users.avatar_url IS '当前头像访问地址。';
COMMENT ON COLUMN users.status IS '账号状态，例如正常、禁用或锁定。';
COMMENT ON COLUMN users.last_login_at IS '最近一次成功登录时间。';
COMMENT ON COLUMN users.password_updated_at IS '最近一次修改密码时间。';
COMMENT ON COLUMN users.login_failed_count IS '连续登录失败次数。';
COMMENT ON COLUMN users.locked_until IS '账号锁定截止时间。';
COMMENT ON COLUMN users.created_at IS '记录创建时间。';
COMMENT ON COLUMN users.updated_at IS '记录最后更新时间。';
COMMENT ON COLUMN users.created_by IS '创建人用户 ID。';
COMMENT ON COLUMN users.updated_by IS '最后更新人用户 ID。';
COMMENT ON COLUMN users.is_deleted IS '逻辑删除标记。';
COMMENT ON COLUMN users.deleted_at IS '逻辑删除时间。';
COMMENT ON COLUMN users.deleted_by IS '执行逻辑删除的用户 ID。';

COMMENT ON TABLE user_profiles IS '用户资料表，保存身体信息、营养目标和饮食偏好。';
COMMENT ON COLUMN user_profiles.profile_id IS '用户资料主键。';
COMMENT ON COLUMN user_profiles.user_id IS '资料所属用户 ID。';
COMMENT ON COLUMN user_profiles.display_name IS '资料展示名称。';
COMMENT ON COLUMN user_profiles.gender IS '用户性别。';
COMMENT ON COLUMN user_profiles.birthday IS '用户生日。';
COMMENT ON COLUMN user_profiles.height_cm IS '用户身高，单位厘米。';
COMMENT ON COLUMN user_profiles.weight_kg IS '用户体重，单位千克。';
COMMENT ON COLUMN user_profiles.activity_level IS '活动水平，用于营养和热量估算。';
COMMENT ON COLUMN user_profiles.diet_goal IS '饮食或健康目标。';
COMMENT ON COLUMN user_profiles.calorie_target IS '每日热量目标。';
COMMENT ON COLUMN user_profiles.protein_target IS '每日蛋白质目标。';
COMMENT ON COLUMN user_profiles.allergens IS '过敏原列表，JSON 数组。';
COMMENT ON COLUMN user_profiles.dislikes IS '不喜欢的食物列表，JSON 数组。';
COMMENT ON COLUMN user_profiles.preferred_units IS '用户偏好的单位和展示设置。';
COMMENT ON COLUMN user_profiles.profile_json IS '用户资料扩展属性。';
COMMENT ON COLUMN user_profiles.created_at IS '记录创建时间。';
COMMENT ON COLUMN user_profiles.updated_at IS '记录最后更新时间。';
COMMENT ON COLUMN user_profiles.created_by IS '创建人用户 ID。';
COMMENT ON COLUMN user_profiles.updated_by IS '最后更新人用户 ID。';
COMMENT ON COLUMN user_profiles.is_deleted IS '逻辑删除标记。';
COMMENT ON COLUMN user_profiles.deleted_at IS '逻辑删除时间。';
COMMENT ON COLUMN user_profiles.deleted_by IS '执行逻辑删除的用户 ID。';

COMMENT ON TABLE auth_refresh_tokens IS '认证刷新令牌表，保存登录续期、撤销和轮换状态。';
COMMENT ON COLUMN auth_refresh_tokens.refresh_token_id IS '刷新令牌主键。';
COMMENT ON COLUMN auth_refresh_tokens.user_id IS '令牌所属用户 ID。';
COMMENT ON COLUMN auth_refresh_tokens.token_hash IS '刷新令牌哈希值。';
COMMENT ON COLUMN auth_refresh_tokens.device_id IS '客户端设备标识。';
COMMENT ON COLUMN auth_refresh_tokens.user_agent IS '客户端 User-Agent 信息。';
COMMENT ON COLUMN auth_refresh_tokens.ip_address IS '登录或刷新令牌的客户端 IP。';
COMMENT ON COLUMN auth_refresh_tokens.expires_at IS '令牌过期时间。';
COMMENT ON COLUMN auth_refresh_tokens.revoked_at IS '令牌撤销时间。';
COMMENT ON COLUMN auth_refresh_tokens.rotated_from_token_id IS '令牌轮换前的旧刷新令牌 ID。';
COMMENT ON COLUMN auth_refresh_tokens.created_at IS '记录创建时间。';
COMMENT ON COLUMN auth_refresh_tokens.updated_at IS '记录最后更新时间。';
COMMENT ON COLUMN auth_refresh_tokens.created_by IS '创建人用户 ID。';
COMMENT ON COLUMN auth_refresh_tokens.updated_by IS '最后更新人用户 ID。';
COMMENT ON COLUMN auth_refresh_tokens.is_deleted IS '逻辑删除标记。';
COMMENT ON COLUMN auth_refresh_tokens.deleted_at IS '逻辑删除时间。';
COMMENT ON COLUMN auth_refresh_tokens.deleted_by IS '执行逻辑删除的用户 ID。';

COMMENT ON TABLE user_avatar_assets IS '用户头像资源表，保存头像文件的对象存储元数据。';
COMMENT ON COLUMN user_avatar_assets.avatar_asset_id IS '头像资源主键。';
COMMENT ON COLUMN user_avatar_assets.user_id IS '头像所属用户 ID。';
COMMENT ON COLUMN user_avatar_assets.storage_key IS '对象存储中的文件 key。';
COMMENT ON COLUMN user_avatar_assets.url IS '头像访问地址。';
COMMENT ON COLUMN user_avatar_assets.mime_type IS '头像文件 MIME 类型。';
COMMENT ON COLUMN user_avatar_assets.size_bytes IS '头像文件大小，单位字节。';
COMMENT ON COLUMN user_avatar_assets.width IS '头像图片宽度，单位像素。';
COMMENT ON COLUMN user_avatar_assets.height IS '头像图片高度，单位像素。';
COMMENT ON COLUMN user_avatar_assets.status IS '头像资源状态，例如当前使用、已替换或已删除。';
COMMENT ON COLUMN user_avatar_assets.created_by IS '创建人用户 ID。';
COMMENT ON COLUMN user_avatar_assets.created_at IS '记录创建时间。';
COMMENT ON COLUMN user_avatar_assets.updated_at IS '记录最后更新时间。';
COMMENT ON COLUMN user_avatar_assets.updated_by IS '最后更新人用户 ID。';
COMMENT ON COLUMN user_avatar_assets.is_deleted IS '逻辑删除标记。';
COMMENT ON COLUMN user_avatar_assets.deleted_at IS '逻辑删除时间。';
COMMENT ON COLUMN user_avatar_assets.deleted_by IS '执行逻辑删除的用户 ID。';

COMMENT ON TABLE sessions IS '会话表，保存用户与助手的一次对话会话。';
COMMENT ON COLUMN sessions.session_id IS '会话主键。';
COMMENT ON COLUMN sessions.tenant_id IS '租户 ID，用于预留多租户隔离。';
COMMENT ON COLUMN sessions.user_id IS '会话所属用户 ID。';
COMMENT ON COLUMN sessions.title IS '会话标题。';
COMMENT ON COLUMN sessions.mode IS '会话模式，例如 Agent 模式或普通聊天模式。';
COMMENT ON COLUMN sessions.status IS '会话状态，例如活跃、归档或关闭。';
COMMENT ON COLUMN sessions.last_message_at IS '最近一条消息产生时间。';
COMMENT ON COLUMN sessions.created_at IS '记录创建时间。';
COMMENT ON COLUMN sessions.updated_at IS '记录最后更新时间。';
COMMENT ON COLUMN sessions.created_by IS '创建人用户 ID。';
COMMENT ON COLUMN sessions.updated_by IS '最后更新人用户 ID。';
COMMENT ON COLUMN sessions.is_deleted IS '逻辑删除标记。';
COMMENT ON COLUMN sessions.deleted_at IS '逻辑删除时间。';
COMMENT ON COLUMN sessions.deleted_by IS '执行逻辑删除的用户 ID。';

COMMENT ON TABLE messages IS '消息表，保存会话中的用户、助手、系统和工具消息。';
COMMENT ON COLUMN messages.message_id IS '消息主键。';
COMMENT ON COLUMN messages.session_id IS '消息所属会话 ID。';
COMMENT ON COLUMN messages.agent_run_id IS '消息关联的 Agent 运行 ID。';
COMMENT ON COLUMN messages.role IS '消息角色，例如用户、助手、系统或工具。';
COMMENT ON COLUMN messages.content IS '消息文本内容。';
COMMENT ON COLUMN messages.structured_payload IS '消息结构化负载。';
COMMENT ON COLUMN messages.sequence_no IS '消息在会话内的顺序号。';
COMMENT ON COLUMN messages.created_at IS '记录创建时间。';
COMMENT ON COLUMN messages.updated_at IS '记录最后更新时间。';
COMMENT ON COLUMN messages.created_by IS '创建人用户 ID。';
COMMENT ON COLUMN messages.updated_by IS '最后更新人用户 ID。';
COMMENT ON COLUMN messages.is_deleted IS '逻辑删除标记。';
COMMENT ON COLUMN messages.deleted_at IS '逻辑删除时间。';
COMMENT ON COLUMN messages.deleted_by IS '执行逻辑删除的用户 ID。';

COMMENT ON TABLE agent_runs IS 'Agent 运行表，保存一次用户请求触发的智能体执行过程。';
COMMENT ON COLUMN agent_runs.agent_run_id IS 'Agent 运行主键。';
COMMENT ON COLUMN agent_runs.session_id IS 'Agent 运行所属会话 ID。';
COMMENT ON COLUMN agent_runs.user_message_id IS '触发本次运行的用户消息 ID。';
COMMENT ON COLUMN agent_runs.intent IS '识别出的用户意图。';
COMMENT ON COLUMN agent_runs.status IS 'Agent 运行状态。';
COMMENT ON COLUMN agent_runs.plan_json IS 'Agent 执行计划。';
COMMENT ON COLUMN agent_runs.result_json IS 'Agent 最终结果。';
COMMENT ON COLUMN agent_runs.error_code IS '失败时的错误码。';
COMMENT ON COLUMN agent_runs.trace_id IS '链路追踪 ID。';
COMMENT ON COLUMN agent_runs.created_at IS '记录创建时间。';
COMMENT ON COLUMN agent_runs.updated_at IS '记录最后更新时间。';
COMMENT ON COLUMN agent_runs.created_by IS '创建人用户 ID。';
COMMENT ON COLUMN agent_runs.updated_by IS '最后更新人用户 ID。';
COMMENT ON COLUMN agent_runs.is_deleted IS '逻辑删除标记。';
COMMENT ON COLUMN agent_runs.deleted_at IS '逻辑删除时间。';
COMMENT ON COLUMN agent_runs.deleted_by IS '执行逻辑删除的用户 ID。';

COMMENT ON TABLE tool_calls IS '工具调用表，保存 Agent 执行中的每一次工具调用记录。';
COMMENT ON COLUMN tool_calls.tool_call_id IS '工具调用主键。';
COMMENT ON COLUMN tool_calls.agent_run_id IS '工具调用所属 Agent 运行 ID。';
COMMENT ON COLUMN tool_calls.tool_name IS '工具名称。';
COMMENT ON COLUMN tool_calls.tool_version IS '工具版本。';
COMMENT ON COLUMN tool_calls.input_json IS '工具输入参数。';
COMMENT ON COLUMN tool_calls.output_json IS '工具输出结果。';
COMMENT ON COLUMN tool_calls.status IS '工具调用状态。';
COMMENT ON COLUMN tool_calls.latency_ms IS '工具调用耗时，单位毫秒。';
COMMENT ON COLUMN tool_calls.error_code IS '失败时的错误码。';
COMMENT ON COLUMN tool_calls.trace_id IS '链路追踪 ID。';
COMMENT ON COLUMN tool_calls.created_at IS '记录创建时间。';
COMMENT ON COLUMN tool_calls.updated_at IS '记录最后更新时间。';
COMMENT ON COLUMN tool_calls.created_by IS '创建人用户 ID。';
COMMENT ON COLUMN tool_calls.updated_by IS '最后更新人用户 ID。';
COMMENT ON COLUMN tool_calls.is_deleted IS '逻辑删除标记。';
COMMENT ON COLUMN tool_calls.deleted_at IS '逻辑删除时间。';
COMMENT ON COLUMN tool_calls.deleted_by IS '执行逻辑删除的用户 ID。';

COMMENT ON TABLE food_logs IS '饮食记录表，保存用户实际摄入、就餐时间和营养估算。';
COMMENT ON COLUMN food_logs.food_log_id IS '饮食记录主键。';
COMMENT ON COLUMN food_logs.user_id IS '饮食记录所属用户 ID。';
COMMENT ON COLUMN food_logs.session_id IS '产生该饮食记录的会话 ID，可为空。';
COMMENT ON COLUMN food_logs.meal_time IS '就餐时间。';
COMMENT ON COLUMN food_logs.meal_type IS '餐别，例如早餐、午餐、晚餐或加餐。';
COMMENT ON COLUMN food_logs.items_json IS '食物条目列表，JSON 数组。';
COMMENT ON COLUMN food_logs.nutrition_json IS '营养估算结果。';
COMMENT ON COLUMN food_logs.notes IS '饮食记录备注。';
COMMENT ON COLUMN food_logs.source IS '记录来源，例如手动、Agent 或导入。';
COMMENT ON COLUMN food_logs.created_at IS '记录创建时间。';
COMMENT ON COLUMN food_logs.updated_at IS '记录最后更新时间。';
COMMENT ON COLUMN food_logs.created_by IS '创建人用户 ID。';
COMMENT ON COLUMN food_logs.updated_by IS '最后更新人用户 ID。';
COMMENT ON COLUMN food_logs.is_deleted IS '逻辑删除标记。';
COMMENT ON COLUMN food_logs.deleted_at IS '逻辑删除时间。';
COMMENT ON COLUMN food_logs.deleted_by IS '执行逻辑删除的用户 ID。';

COMMENT ON TABLE analysis_reports IS '分析报告表，保存饮食、营养和阶段性分析报告。';
COMMENT ON COLUMN analysis_reports.report_id IS '分析报告主键。';
COMMENT ON COLUMN analysis_reports.user_id IS '报告所属用户 ID。';
COMMENT ON COLUMN analysis_reports.session_id IS '报告来源会话 ID。';
COMMENT ON COLUMN analysis_reports.agent_run_id IS '报告来源 Agent 运行 ID。';
COMMENT ON COLUMN analysis_reports.report_type IS '报告类型。';
COMMENT ON COLUMN analysis_reports.range_key IS '报告覆盖的时间范围标识。';
COMMENT ON COLUMN analysis_reports.report_json IS '报告内容。';
COMMENT ON COLUMN analysis_reports.status IS '报告状态。';
COMMENT ON COLUMN analysis_reports.created_at IS '记录创建时间。';
COMMENT ON COLUMN analysis_reports.updated_at IS '记录最后更新时间。';
COMMENT ON COLUMN analysis_reports.created_by IS '创建人用户 ID。';
COMMENT ON COLUMN analysis_reports.updated_by IS '最后更新人用户 ID。';
COMMENT ON COLUMN analysis_reports.is_deleted IS '逻辑删除标记。';
COMMENT ON COLUMN analysis_reports.deleted_at IS '逻辑删除时间。';
COMMENT ON COLUMN analysis_reports.deleted_by IS '执行逻辑删除的用户 ID。';

COMMENT ON TABLE meal_plans IS '膳食计划表，保存生成或保存的备餐和膳食计划。';
COMMENT ON COLUMN meal_plans.meal_plan_id IS '膳食计划主键。';
COMMENT ON COLUMN meal_plans.user_id IS '膳食计划所属用户 ID。';
COMMENT ON COLUMN meal_plans.session_id IS '产生该计划的会话 ID。';
COMMENT ON COLUMN meal_plans.plan_name IS '膳食计划名称。';
COMMENT ON COLUMN meal_plans.days IS '计划覆盖天数。';
COMMENT ON COLUMN meal_plans.budget IS '计划预算金额。';
COMMENT ON COLUMN meal_plans.constraints_json IS '计划约束条件，例如过敏、预算、口味和营养目标。';
COMMENT ON COLUMN meal_plans.plan_json IS '生成的膳食计划内容。';
COMMENT ON COLUMN meal_plans.validation_json IS '计划校验结果。';
COMMENT ON COLUMN meal_plans.status IS '膳食计划状态。';
COMMENT ON COLUMN meal_plans.created_at IS '记录创建时间。';
COMMENT ON COLUMN meal_plans.updated_at IS '记录最后更新时间。';
COMMENT ON COLUMN meal_plans.created_by IS '创建人用户 ID。';
COMMENT ON COLUMN meal_plans.updated_by IS '最后更新人用户 ID。';
COMMENT ON COLUMN meal_plans.is_deleted IS '逻辑删除标记。';
COMMENT ON COLUMN meal_plans.deleted_at IS '逻辑删除时间。';
COMMENT ON COLUMN meal_plans.deleted_by IS '执行逻辑删除的用户 ID。';

COMMENT ON TABLE shopping_lists IS '购物清单表，保存从膳食计划生成的采购清单。';
COMMENT ON COLUMN shopping_lists.shopping_list_id IS '购物清单主键。';
COMMENT ON COLUMN shopping_lists.meal_plan_id IS '购物清单来源膳食计划 ID。';
COMMENT ON COLUMN shopping_lists.user_id IS '购物清单所属用户 ID。';
COMMENT ON COLUMN shopping_lists.items_json IS '采购条目列表。';
COMMENT ON COLUMN shopping_lists.status IS '购物清单状态。';
COMMENT ON COLUMN shopping_lists.created_at IS '记录创建时间。';
COMMENT ON COLUMN shopping_lists.updated_at IS '记录最后更新时间。';
COMMENT ON COLUMN shopping_lists.created_by IS '创建人用户 ID。';
COMMENT ON COLUMN shopping_lists.updated_by IS '最后更新人用户 ID。';
COMMENT ON COLUMN shopping_lists.is_deleted IS '逻辑删除标记。';
COMMENT ON COLUMN shopping_lists.deleted_at IS '逻辑删除时间。';
COMMENT ON COLUMN shopping_lists.deleted_by IS '执行逻辑删除的用户 ID。';

COMMENT ON TABLE user_memories IS '用户长期记忆表，保存助手沉淀出的偏好、约束和目标。';
COMMENT ON COLUMN user_memories.memory_id IS '用户记忆主键。';
COMMENT ON COLUMN user_memories.user_id IS '记忆所属用户 ID。';
COMMENT ON COLUMN user_memories.memory_type IS '记忆类型。';
COMMENT ON COLUMN user_memories.memory_key IS '记忆键。';
COMMENT ON COLUMN user_memories.memory_value IS '记忆值。';
COMMENT ON COLUMN user_memories.confidence IS '记忆置信度，范围 0 到 1。';
COMMENT ON COLUMN user_memories.source IS '记忆来源。';
COMMENT ON COLUMN user_memories.scope IS '记忆作用范围。';
COMMENT ON COLUMN user_memories.expires_at IS '记忆过期时间。';
COMMENT ON COLUMN user_memories.created_at IS '记录创建时间。';
COMMENT ON COLUMN user_memories.updated_at IS '记录最后更新时间。';
COMMENT ON COLUMN user_memories.created_by IS '创建人用户 ID。';
COMMENT ON COLUMN user_memories.updated_by IS '最后更新人用户 ID。';
COMMENT ON COLUMN user_memories.is_deleted IS '逻辑删除标记。';
COMMENT ON COLUMN user_memories.deleted_at IS '逻辑删除时间。';
COMMENT ON COLUMN user_memories.deleted_by IS '执行逻辑删除的用户 ID。';

COMMENT ON TABLE session_summaries IS '会话摘要表，保存长会话压缩摘要和关键约束。';
COMMENT ON COLUMN session_summaries.summary_id IS '会话摘要主键。';
COMMENT ON COLUMN session_summaries.session_id IS '摘要所属会话 ID。';
COMMENT ON COLUMN session_summaries.summary_text IS '会话摘要文本。';
COMMENT ON COLUMN session_summaries.key_constraints IS '会话中提取的关键约束。';
COMMENT ON COLUMN session_summaries.created_at IS '记录创建时间。';
COMMENT ON COLUMN session_summaries.updated_at IS '记录最后更新时间。';
COMMENT ON COLUMN session_summaries.created_by IS '创建人用户 ID。';
COMMENT ON COLUMN session_summaries.updated_by IS '最后更新人用户 ID。';
COMMENT ON COLUMN session_summaries.is_deleted IS '逻辑删除标记。';
COMMENT ON COLUMN session_summaries.deleted_at IS '逻辑删除时间。';
COMMENT ON COLUMN session_summaries.deleted_by IS '执行逻辑删除的用户 ID。';

COMMENT ON TABLE knowledge_documents IS '知识库文档表，保存知识库原始文档元数据。';
COMMENT ON COLUMN knowledge_documents.document_id IS '知识库文档主键。';
COMMENT ON COLUMN knowledge_documents.tenant_id IS '租户 ID，用于预留多租户隔离。';
COMMENT ON COLUMN knowledge_documents.source_type IS '文档来源类型。';
COMMENT ON COLUMN knowledge_documents.title IS '文档标题。';
COMMENT ON COLUMN knowledge_documents.status IS '文档处理状态。';
COMMENT ON COLUMN knowledge_documents.version IS '文档版本。';
COMMENT ON COLUMN knowledge_documents.storage_key IS '原始文档对象存储 key。';
COMMENT ON COLUMN knowledge_documents.metadata_json IS '文档元数据。';
COMMENT ON COLUMN knowledge_documents.created_at IS '记录创建时间。';
COMMENT ON COLUMN knowledge_documents.updated_at IS '记录最后更新时间。';
COMMENT ON COLUMN knowledge_documents.created_by IS '创建人用户 ID。';
COMMENT ON COLUMN knowledge_documents.updated_by IS '最后更新人用户 ID。';
COMMENT ON COLUMN knowledge_documents.is_deleted IS '逻辑删除标记。';
COMMENT ON COLUMN knowledge_documents.deleted_at IS '逻辑删除时间。';
COMMENT ON COLUMN knowledge_documents.deleted_by IS '执行逻辑删除的用户 ID。';

COMMENT ON TABLE knowledge_chunks IS '知识库切片表，保存用于检索的文档文本片段。';
COMMENT ON COLUMN knowledge_chunks.chunk_id IS '知识切片主键。';
COMMENT ON COLUMN knowledge_chunks.document_id IS '切片所属文档 ID。';
COMMENT ON COLUMN knowledge_chunks.chunk_no IS '文档内切片序号。';
COMMENT ON COLUMN knowledge_chunks.chunk_text IS '切片文本内容。';
COMMENT ON COLUMN knowledge_chunks.section_path IS '切片所在章节路径。';
COMMENT ON COLUMN knowledge_chunks.tags IS '切片标签列表。';
COMMENT ON COLUMN knowledge_chunks.version IS '切片版本。';
COMMENT ON COLUMN knowledge_chunks.embedding_id IS '向量库中的 embedding 标识。';
COMMENT ON COLUMN knowledge_chunks.metadata_json IS '切片元数据。';
COMMENT ON COLUMN knowledge_chunks.created_at IS '记录创建时间。';
COMMENT ON COLUMN knowledge_chunks.updated_at IS '记录最后更新时间。';
COMMENT ON COLUMN knowledge_chunks.created_by IS '创建人用户 ID。';
COMMENT ON COLUMN knowledge_chunks.updated_by IS '最后更新人用户 ID。';
COMMENT ON COLUMN knowledge_chunks.is_deleted IS '逻辑删除标记。';
COMMENT ON COLUMN knowledge_chunks.deleted_at IS '逻辑删除时间。';
COMMENT ON COLUMN knowledge_chunks.deleted_by IS '执行逻辑删除的用户 ID。';

COMMENT ON TABLE data_sources IS '数据源表，登记 SQL Agent 可访问的外部只读数据源。';
COMMENT ON COLUMN data_sources.datasource_id IS '数据源主键。';
COMMENT ON COLUMN data_sources.name IS '数据源名称。';
COMMENT ON COLUMN data_sources.db_type IS '数据库类型。';
COMMENT ON COLUMN data_sources.purpose IS '数据源业务用途。';
COMMENT ON COLUMN data_sources.visibility IS '数据源可见范围。';
COMMENT ON COLUMN data_sources.status IS '数据源状态。';
COMMENT ON COLUMN data_sources.readonly IS '是否只读，SQL Agent 数据源必须只读。';
COMMENT ON COLUMN data_sources.connection_ref IS '连接配置或密钥引用。';
COMMENT ON COLUMN data_sources.created_at IS '记录创建时间。';
COMMENT ON COLUMN data_sources.updated_at IS '记录最后更新时间。';
COMMENT ON COLUMN data_sources.created_by IS '创建人用户 ID。';
COMMENT ON COLUMN data_sources.updated_by IS '最后更新人用户 ID。';
COMMENT ON COLUMN data_sources.is_deleted IS '逻辑删除标记。';
COMMENT ON COLUMN data_sources.deleted_at IS '逻辑删除时间。';
COMMENT ON COLUMN data_sources.deleted_by IS '执行逻辑删除的用户 ID。';

COMMENT ON TABLE schema_catalogs IS 'Schema 白名单表，保存 SQL Agent 可见的表字段说明。';
COMMENT ON COLUMN schema_catalogs.schema_catalog_id IS 'Schema 元数据主键。';
COMMENT ON COLUMN schema_catalogs.datasource_id IS '所属数据源 ID。';
COMMENT ON COLUMN schema_catalogs.schema_name IS '数据库 schema 名称。';
COMMENT ON COLUMN schema_catalogs.table_name IS '数据库表名。';
COMMENT ON COLUMN schema_catalogs.field_name IS '数据库字段名。';
COMMENT ON COLUMN schema_catalogs.field_desc IS '字段业务说明。';
COMMENT ON COLUMN schema_catalogs.data_type IS '字段数据类型。';
COMMENT ON COLUMN schema_catalogs.is_sensitive IS '是否为敏感字段。';
COMMENT ON COLUMN schema_catalogs.sample_sql IS '经过审核的示例 SQL。';
COMMENT ON COLUMN schema_catalogs.created_at IS '记录创建时间。';
COMMENT ON COLUMN schema_catalogs.updated_at IS '记录最后更新时间。';
COMMENT ON COLUMN schema_catalogs.created_by IS '创建人用户 ID。';
COMMENT ON COLUMN schema_catalogs.updated_by IS '最后更新人用户 ID。';
COMMENT ON COLUMN schema_catalogs.is_deleted IS '逻辑删除标记。';
COMMENT ON COLUMN schema_catalogs.deleted_at IS '逻辑删除时间。';
COMMENT ON COLUMN schema_catalogs.deleted_by IS '执行逻辑删除的用户 ID。';

COMMENT ON TABLE sql_query_audits IS 'SQL 查询审计表，保存 SQL Agent 生成、校验和执行 SQL 的审计链路。';
COMMENT ON COLUMN sql_query_audits.sql_audit_id IS 'SQL 查询审计主键。';
COMMENT ON COLUMN sql_query_audits.session_id IS '来源会话 ID。';
COMMENT ON COLUMN sql_query_audits.agent_run_id IS '来源 Agent 运行 ID。';
COMMENT ON COLUMN sql_query_audits.datasource_id IS '目标数据源 ID。';
COMMENT ON COLUMN sql_query_audits.original_question IS '用户原始问题。';
COMMENT ON COLUMN sql_query_audits.resolved_question IS '改写或补全后的问题。';
COMMENT ON COLUMN sql_query_audits.sql_text IS '生成的 SQL 文本。';
COMMENT ON COLUMN sql_query_audits.status IS 'SQL 审计状态。';
COMMENT ON COLUMN sql_query_audits.reject_reason IS 'SQL 被拒绝的原因。';
COMMENT ON COLUMN sql_query_audits.row_count IS '查询返回行数。';
COMMENT ON COLUMN sql_query_audits.latency_ms IS '查询耗时，单位毫秒。';
COMMENT ON COLUMN sql_query_audits.trace_id IS '链路追踪 ID。';
COMMENT ON COLUMN sql_query_audits.created_at IS '记录创建时间。';
COMMENT ON COLUMN sql_query_audits.updated_at IS '记录最后更新时间。';
COMMENT ON COLUMN sql_query_audits.created_by IS '创建人用户 ID。';
COMMENT ON COLUMN sql_query_audits.updated_by IS '最后更新人用户 ID。';
COMMENT ON COLUMN sql_query_audits.is_deleted IS '逻辑删除标记。';
COMMENT ON COLUMN sql_query_audits.deleted_at IS '逻辑删除时间。';
COMMENT ON COLUMN sql_query_audits.deleted_by IS '执行逻辑删除的用户 ID。';

COMMENT ON TABLE tool_registries IS '工具注册表，保存可被 Agent 调用的工具元数据。';
COMMENT ON COLUMN tool_registries.tool_id IS '工具主键。';
COMMENT ON COLUMN tool_registries.name IS '工具唯一名称。';
COMMENT ON COLUMN tool_registries.display_name IS '工具展示名称。';
COMMENT ON COLUMN tool_registries.description IS '工具说明。';
COMMENT ON COLUMN tool_registries.category IS '工具分类。';
COMMENT ON COLUMN tool_registries.risk_level IS '工具风险等级。';
COMMENT ON COLUMN tool_registries.availability_scope IS '工具可用范围。';
COMMENT ON COLUMN tool_registries.status IS '工具状态。';
COMMENT ON COLUMN tool_registries.current_version IS '当前发布的工具版本。';
COMMENT ON COLUMN tool_registries.created_at IS '记录创建时间。';
COMMENT ON COLUMN tool_registries.updated_at IS '记录最后更新时间。';
COMMENT ON COLUMN tool_registries.created_by IS '创建人用户 ID。';
COMMENT ON COLUMN tool_registries.updated_by IS '最后更新人用户 ID。';
COMMENT ON COLUMN tool_registries.is_deleted IS '逻辑删除标记。';
COMMENT ON COLUMN tool_registries.deleted_at IS '逻辑删除时间。';
COMMENT ON COLUMN tool_registries.deleted_by IS '执行逻辑删除的用户 ID。';

COMMENT ON TABLE tool_schema_versions IS '工具 Schema 版本表，保存工具输入输出结构的版本化定义。';
COMMENT ON COLUMN tool_schema_versions.tool_schema_version_id IS '工具 Schema 版本主键。';
COMMENT ON COLUMN tool_schema_versions.tool_id IS '所属工具 ID。';
COMMENT ON COLUMN tool_schema_versions.version IS '工具 Schema 版本号。';
COMMENT ON COLUMN tool_schema_versions.input_schema IS '工具输入 JSON Schema。';
COMMENT ON COLUMN tool_schema_versions.output_schema IS '工具输出 JSON Schema。';
COMMENT ON COLUMN tool_schema_versions.permissions IS '调用工具所需权限。';
COMMENT ON COLUMN tool_schema_versions.timeout_ms IS '工具执行超时时间，单位毫秒。';
COMMENT ON COLUMN tool_schema_versions.retryable IS '失败后是否允许重试。';
COMMENT ON COLUMN tool_schema_versions.idempotent IS '重复调用是否幂等。';
COMMENT ON COLUMN tool_schema_versions.published_at IS '版本发布时间。';
COMMENT ON COLUMN tool_schema_versions.created_at IS '记录创建时间。';
COMMENT ON COLUMN tool_schema_versions.updated_at IS '记录最后更新时间。';
COMMENT ON COLUMN tool_schema_versions.created_by IS '创建人用户 ID。';
COMMENT ON COLUMN tool_schema_versions.updated_by IS '最后更新人用户 ID。';
COMMENT ON COLUMN tool_schema_versions.is_deleted IS '逻辑删除标记。';
COMMENT ON COLUMN tool_schema_versions.deleted_at IS '逻辑删除时间。';
COMMENT ON COLUMN tool_schema_versions.deleted_by IS '执行逻辑删除的用户 ID。';

COMMENT ON TABLE model_usage_logs IS '模型用量日志表，保存模型调用、耗时、用量和成本记录。';
COMMENT ON COLUMN model_usage_logs.model_usage_log_id IS '模型用量日志主键。';
COMMENT ON COLUMN model_usage_logs.request_id IS '模型网关请求 ID。';
COMMENT ON COLUMN model_usage_logs.trace_id IS '链路追踪 ID。';
COMMENT ON COLUMN model_usage_logs.scene IS '模型调用业务场景。';
COMMENT ON COLUMN model_usage_logs.provider_code IS '模型供应商编码。';
COMMENT ON COLUMN model_usage_logs.model_name IS '模型名称。';
COMMENT ON COLUMN model_usage_logs.usage_json IS 'Token 用量和供应商返回指标。';
COMMENT ON COLUMN model_usage_logs.latency_ms IS '模型调用耗时，单位毫秒。';
COMMENT ON COLUMN model_usage_logs.cost_amount IS '模型调用估算成本。';
COMMENT ON COLUMN model_usage_logs.status IS '模型调用状态。';
COMMENT ON COLUMN model_usage_logs.created_at IS '记录创建时间。';
COMMENT ON COLUMN model_usage_logs.updated_at IS '记录最后更新时间。';
COMMENT ON COLUMN model_usage_logs.created_by IS '创建人用户 ID。';
COMMENT ON COLUMN model_usage_logs.updated_by IS '最后更新人用户 ID。';
COMMENT ON COLUMN model_usage_logs.is_deleted IS '逻辑删除标记。';
COMMENT ON COLUMN model_usage_logs.deleted_at IS '逻辑删除时间。';
COMMENT ON COLUMN model_usage_logs.deleted_by IS '执行逻辑删除的用户 ID。';

COMMENT ON TABLE model_route_rules IS '模型路由规则表，保存不同场景下的模型供应商选择规则。';
COMMENT ON COLUMN model_route_rules.model_route_rule_id IS '模型路由规则主键。';
COMMENT ON COLUMN model_route_rules.tenant_id IS '租户 ID，用于预留多租户隔离。';
COMMENT ON COLUMN model_route_rules.scene IS '业务场景。';
COMMENT ON COLUMN model_route_rules.model_type IS '模型能力类型。';
COMMENT ON COLUMN model_route_rules.provider_code IS '主模型供应商编码。';
COMMENT ON COLUMN model_route_rules.fallback_provider_code IS '备用模型供应商编码。';
COMMENT ON COLUMN model_route_rules.max_cost IS '最大成本限制。';
COMMENT ON COLUMN model_route_rules.max_latency_ms IS '最大延迟限制，单位毫秒。';
COMMENT ON COLUMN model_route_rules.rule_json IS '模型路由扩展规则。';
COMMENT ON COLUMN model_route_rules.status IS '路由规则状态。';
COMMENT ON COLUMN model_route_rules.created_at IS '记录创建时间。';
COMMENT ON COLUMN model_route_rules.updated_at IS '记录最后更新时间。';
COMMENT ON COLUMN model_route_rules.created_by IS '创建人用户 ID。';
COMMENT ON COLUMN model_route_rules.updated_by IS '最后更新人用户 ID。';
COMMENT ON COLUMN model_route_rules.is_deleted IS '逻辑删除标记。';
COMMENT ON COLUMN model_route_rules.deleted_at IS '逻辑删除时间。';
COMMENT ON COLUMN model_route_rules.deleted_by IS '执行逻辑删除的用户 ID。';

COMMENT ON TABLE operation_audits IS '操作审计表，保存后台管理和敏感操作审计记录。';
COMMENT ON COLUMN operation_audits.operation_audit_id IS '操作审计主键。';
COMMENT ON COLUMN operation_audits.operator_id IS '操作人用户 ID。';
COMMENT ON COLUMN operation_audits.request_id IS '请求 ID。';
COMMENT ON COLUMN operation_audits.trace_id IS '链路追踪 ID。';
COMMENT ON COLUMN operation_audits.target_type IS '操作对象类型。';
COMMENT ON COLUMN operation_audits.target_id IS '操作对象 ID。';
COMMENT ON COLUMN operation_audits.action IS '操作动作。';
COMMENT ON COLUMN operation_audits.result IS '操作结果。';
COMMENT ON COLUMN operation_audits.request_json IS '脱敏后的请求内容。';
COMMENT ON COLUMN operation_audits.response_json IS '脱敏后的响应内容。';
COMMENT ON COLUMN operation_audits.error_code IS '失败时的错误码。';
COMMENT ON COLUMN operation_audits.created_at IS '记录创建时间。';
COMMENT ON COLUMN operation_audits.updated_at IS '记录最后更新时间。';
COMMENT ON COLUMN operation_audits.created_by IS '创建人用户 ID。';
COMMENT ON COLUMN operation_audits.updated_by IS '最后更新人用户 ID。';
COMMENT ON COLUMN operation_audits.is_deleted IS '逻辑删除标记。';
COMMENT ON COLUMN operation_audits.deleted_at IS '逻辑删除时间。';
COMMENT ON COLUMN operation_audits.deleted_by IS '执行逻辑删除的用户 ID。';

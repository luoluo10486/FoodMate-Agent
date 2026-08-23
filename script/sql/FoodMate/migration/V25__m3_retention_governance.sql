-- M3 retention governance. This migration creates plans and holds only;
-- no existing data is removed and no task is executed by the migration.

CREATE TABLE IF NOT EXISTS data_retention_policies (
    policy_id BIGINT PRIMARY KEY,
    resource_type VARCHAR(64) NOT NULL,
    retention_days INT NOT NULL CHECK (retention_days >= 0 AND retention_days <= 36500),
    hard_delete_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    policy_version VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_data_retention_policy_resource UNIQUE (resource_type),
    CONSTRAINT chk_data_retention_policy_status CHECK (status IN ('active', 'disabled')),
    CONSTRAINT chk_data_retention_policy_resource CHECK (resource_type IN ('knowledge_document', 'admin_export_job'))
);

INSERT INTO data_retention_policies(policy_id, resource_type, retention_days, hard_delete_enabled, policy_version)
VALUES
    (250001, 'knowledge_document', 365, FALSE, 'm3-default-v1'),
    (250002, 'admin_export_job', 30, FALSE, 'm3-default-v1')
ON CONFLICT (resource_type) DO NOTHING;

CREATE TABLE IF NOT EXISTS data_legal_holds (
    hold_id BIGINT PRIMARY KEY,
    resource_type VARCHAR(64) NOT NULL,
    resource_id BIGINT NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    placed_by BIGINT NOT NULL REFERENCES users(user_id),
    placed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_by BIGINT,
    released_at TIMESTAMPTZ,
    CONSTRAINT chk_data_legal_hold_status CHECK (status IN ('active', 'released')),
    CONSTRAINT chk_data_legal_hold_resource CHECK (resource_type IN ('knowledge_document', 'admin_export_job')),
    CONSTRAINT chk_data_legal_hold_release CHECK ((status = 'active') = (released_at IS NULL))
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_data_legal_hold_active_resource
    ON data_legal_holds(resource_type, resource_id) WHERE status = 'active';
CREATE INDEX IF NOT EXISTS idx_data_legal_holds_resource
    ON data_legal_holds(resource_type, resource_id, placed_at DESC);

CREATE TABLE IF NOT EXISTS data_purge_requests (
    request_id BIGINT PRIMARY KEY,
    resource_type VARCHAR(64) NOT NULL,
    resource_id BIGINT NOT NULL,
    policy_id BIGINT NOT NULL REFERENCES data_retention_policies(policy_id),
    requested_by BIGINT NOT NULL REFERENCES users(user_id),
    idempotency_key VARCHAR(128) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'requested',
    deleted_at_snapshot TIMESTAMPTZ NOT NULL,
    eligible_at TIMESTAMPTZ NOT NULL,
    approved_by BIGINT,
    approved_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    failure_code VARCHAR(64),
    failure_summary VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_data_purge_operator_idempotency UNIQUE (requested_by, idempotency_key),
    CONSTRAINT chk_data_purge_status CHECK (status IN ('requested', 'approved', 'running', 'completed', 'failed', 'cancelled')),
    CONSTRAINT chk_data_purge_approval CHECK ((status = 'requested') = (approved_at IS NULL AND approved_by IS NULL))
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_data_purge_active_resource
    ON data_purge_requests(resource_type, resource_id)
    WHERE status IN ('requested', 'approved', 'running');
CREATE INDEX IF NOT EXISTS idx_data_purge_requests_status
    ON data_purge_requests(status, eligible_at, created_at);

CREATE TABLE IF NOT EXISTS data_purge_tasks (
    task_id BIGINT PRIMARY KEY,
    request_id BIGINT NOT NULL REFERENCES data_purge_requests(request_id),
    task_type VARCHAR(24) NOT NULL,
    topic VARCHAR(128),
    target_ref JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    attempt_count INT NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    owner_token VARCHAR(128),
    lease_until TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error_code VARCHAR(64),
    last_error_summary VARCHAR(512),
    published_message_id VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    CONSTRAINT uk_data_purge_task_type UNIQUE (request_id, task_type),
    CONSTRAINT chk_data_purge_task_type CHECK (task_type IN ('object_storage', 'vector_index', 'database')),
    CONSTRAINT chk_data_purge_task_status CHECK (status IN ('pending', 'leased', 'published', 'succeeded', 'failed')),
    CONSTRAINT chk_data_purge_task_topic CHECK ((task_type = 'vector_index') = (topic = 'foodmate-knowledge-purge-v1')),
    CONSTRAINT chk_data_purge_task_succeeded CHECK (status <> 'succeeded' OR completed_at IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS idx_data_purge_tasks_pending
    ON data_purge_tasks(next_attempt_at, created_at)
    WHERE status IN ('pending', 'leased');
CREATE INDEX IF NOT EXISTS idx_data_purge_tasks_request
    ON data_purge_tasks(request_id, task_type, status);

COMMENT ON TABLE data_retention_policies IS '可审计的数据保留策略；默认不允许硬删除，需显式变更策略。';
COMMENT ON TABLE data_legal_holds IS '法律、争议或调查冻结；active hold 阻止清理申请审批和执行。';
COMMENT ON TABLE data_purge_requests IS '清理申请和 superadmin 审批事实；审批不等于已删除。';
COMMENT ON TABLE data_purge_tasks IS '对象存储、向量索引和数据库清理步骤；失败可重试且按任务类型幂等。';
COMMENT ON COLUMN data_purge_tasks.target_ref IS '受限内部目标引用；不进入管理 API、操作审计或日志。';

-- M1-4 continuation、superseded 终态与预算/超时快照追加结构。
-- 仅追加结构，不删除已有表；当前项目仍由发布流程人工执行迁移。

-- 1. agent_runs：父子 Run 关联、continuation 原因与结果类型。
ALTER TABLE agent_runs
    ADD COLUMN IF NOT EXISTS parent_run_id BIGINT,
    ADD COLUMN IF NOT EXISTS superseded_by_run_id BIGINT,
    ADD COLUMN IF NOT EXISTS continuation_reason VARCHAR(64),
    ADD COLUMN IF NOT EXISTS result_type VARCHAR(32);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_agent_runs_parent_run') THEN
        ALTER TABLE agent_runs ADD CONSTRAINT fk_agent_runs_parent_run
            FOREIGN KEY (parent_run_id) REFERENCES agent_runs(agent_run_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_agent_runs_superseded_by_run') THEN
        ALTER TABLE agent_runs ADD CONSTRAINT fk_agent_runs_superseded_by_run
            FOREIGN KEY (superseded_by_run_id) REFERENCES agent_runs(agent_run_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_agent_runs_continuation_reason') THEN
        ALTER TABLE agent_runs ADD CONSTRAINT chk_agent_runs_continuation_reason CHECK (
            continuation_reason IS NULL
            OR continuation_reason IN ('clarification', 'tool_approval', 'budget_extension')
        );
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_agent_runs_result_type') THEN
        ALTER TABLE agent_runs ADD CONSTRAINT chk_agent_runs_result_type CHECK (
            result_type IS NULL OR result_type IN ('normal', 'safety_degraded')
        );
    END IF;
    -- continuation 必须同时携带 parent_run_id 与 continuation_reason。
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_agent_runs_continuation_pair') THEN
        ALTER TABLE agent_runs ADD CONSTRAINT chk_agent_runs_continuation_pair CHECK (
            (parent_run_id IS NULL) = (continuation_reason IS NULL)
        );
    END IF;
END $$;

-- 2. 状态 CHECK 增加 superseded 终态。
ALTER TABLE agent_runs DROP CONSTRAINT IF EXISTS chk_agent_runs_status;
ALTER TABLE agent_runs ADD CONSTRAINT chk_agent_runs_status CHECK (
    status IN ('queued', 'routed', 'waiting_user', 'planning', 'retrieving', 'executing',
               'validating', 'completed', 'failed', 'cancelled', 'superseded')
);

-- 一个旧 Run 最多由一个有效 continuation Run 接续。
CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_runs_parent_active
    ON agent_runs(parent_run_id)
    WHERE parent_run_id IS NOT NULL AND is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_agent_runs_parent_run
    ON agent_runs(parent_run_id)
    WHERE parent_run_id IS NOT NULL;

-- 3. 预算与超时快照：接受 Run 时固化，追加预算生成新 revision。
CREATE TABLE IF NOT EXISTS agent_run_budget_snapshots (
    budget_snapshot_id BIGINT PRIMARY KEY,
    agent_run_id BIGINT NOT NULL REFERENCES agent_runs(agent_run_id),
    revision INT NOT NULL CHECK (revision >= 1),
    source VARCHAR(16) NOT NULL DEFAULT 'initial',
    max_total_tokens INT NOT NULL CHECK (max_total_tokens > 0),
    max_cost_cny NUMERIC(10, 4) NOT NULL CHECK (max_cost_cny > 0),
    max_step_retries INT NOT NULL CHECK (max_step_retries >= 0),
    max_replans INT NOT NULL CHECK (max_replans >= 0),
    max_answer_rewrites INT NOT NULL CHECK (max_answer_rewrites >= 0),
    max_total_steps INT NOT NULL CHECK (max_total_steps > 0),
    max_model_calls INT NOT NULL CHECK (max_model_calls > 0),
    queue_timeout_seconds INT NOT NULL CHECK (queue_timeout_seconds > 0),
    execution_timeout_seconds INT NOT NULL CHECK (execution_timeout_seconds > 0),
    node_timeout_seconds INT NOT NULL CHECK (node_timeout_seconds > 0),
    waiting_user_timeout_seconds INT NOT NULL CHECK (waiting_user_timeout_seconds > 0),
    config_version VARCHAR(64) NOT NULL,
    confirmation_digest VARCHAR(71),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_agent_run_budget_revision UNIQUE (agent_run_id, revision),
    CONSTRAINT chk_agent_run_budget_source CHECK (source IN ('initial', 'extension')),
    CONSTRAINT chk_agent_run_budget_confirmation CHECK (
        (source = 'initial' AND confirmation_digest IS NULL)
        OR (source = 'extension' AND confirmation_digest IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_agent_run_budget_run_revision
    ON agent_run_budget_snapshots(agent_run_id, revision DESC);

-- 4. 预算追加确认：Java HTTP 接受、校验并固化，之后才创建新 dispatch attempt。
CREATE TABLE IF NOT EXISTS agent_run_budget_extensions (
    budget_extension_id BIGINT PRIMARY KEY,
    agent_run_id BIGINT NOT NULL REFERENCES agent_runs(agent_run_id),
    extension_no INT NOT NULL CHECK (extension_no >= 1),
    additional_tokens INT NOT NULL CHECK (additional_tokens > 0),
    additional_cost_cny NUMERIC(10, 4) NOT NULL CHECK (additional_cost_cny > 0),
    confirmation_digest VARCHAR(71) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_agent_run_budget_extension_no UNIQUE (agent_run_id, extension_no),
    CONSTRAINT chk_agent_run_budget_extension_status CHECK (
        status IN ('pending', 'confirmed', 'expired', 'cancelled')
    ),
    CONSTRAINT chk_agent_run_budget_extension_confirmed CHECK (
        (status = 'confirmed') = (confirmed_at IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_agent_run_budget_extension_run
    ON agent_run_budget_extensions(agent_run_id, extension_no DESC);

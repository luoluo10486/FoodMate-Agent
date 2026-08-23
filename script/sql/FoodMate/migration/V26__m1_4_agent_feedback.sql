-- M1-4 结构化 Agent 反馈事实。
-- 仅追加结构；当前项目由发布流程人工执行迁移，不在应用启动时自动迁移。

CREATE TABLE IF NOT EXISTS agent_feedback (
    feedback_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(user_id),
    agent_run_id BIGINT NOT NULL REFERENCES agent_runs(agent_run_id),
    message_id BIGINT NOT NULL REFERENCES messages(message_id),
    helpful BOOLEAN NOT NULL,
    reason_codes JSONB NOT NULL DEFAULT '[]'::jsonb,
    comment VARCHAR(1000),
    trace_id VARCHAR(64),
    eval_id VARCHAR(128),
    model_route_version VARCHAR(64),
    prompt_version VARCHAR(64),
    rubric_version VARCHAR(64),
    high_risk BOOLEAN NOT NULL DEFAULT FALSE,
    idempotency_key VARCHAR(128) NOT NULL,
    parameters_digest VARCHAR(71) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL REFERENCES users(user_id),
    updated_by BIGINT NOT NULL REFERENCES users(user_id),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    CONSTRAINT chk_agent_feedback_comment_length CHECK (comment IS NULL OR char_length(comment) <= 1000),
    CONSTRAINT chk_agent_feedback_reason_codes_array CHECK (jsonb_typeof(reason_codes) = 'array'),
    CONSTRAINT uk_agent_feedback_user_message UNIQUE (user_id, message_id),
    CONSTRAINT uk_agent_feedback_user_idempotency UNIQUE (user_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_agent_feedback_run_created
    ON agent_feedback(agent_run_id, created_at DESC)
    WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_agent_feedback_high_risk_created
    ON agent_feedback(high_risk, created_at DESC)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE agent_feedback IS '用户对 Agent 回答的结构化反馈；不保存回答正文、Prompt 或敏感原始请求。';
COMMENT ON COLUMN agent_feedback.reason_codes IS '稳定原因代码数组，不保存自由文本原始标签。';
COMMENT ON COLUMN agent_feedback.high_risk IS '虚构执行或安全隐私问题等高优先级反馈标记。';

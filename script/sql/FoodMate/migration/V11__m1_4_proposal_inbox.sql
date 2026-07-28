-- M1-4 Proposal 消费幂等事实：同一 proposal_id 只允许一次业务执行。
-- Result 发布失败时保持 claimed，MQ 重投由业务层按同一 proposal_id 复用结果事实；
-- request_hash 冲突属于契约错误，不允许静默覆盖原 Proposal。
CREATE TABLE IF NOT EXISTS runtime_tool_proposal_inbox (
    proposal_id VARCHAR(128) PRIMARY KEY,
    request_hash VARCHAR(128) NOT NULL,
    payload_json JSONB NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'claimed',
    result_json JSONB,
    claimed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    CONSTRAINT chk_runtime_tool_proposal_inbox_status CHECK (status IN ('claimed', 'completed', 'failed'))
);

CREATE INDEX IF NOT EXISTS idx_runtime_tool_proposal_inbox_claimed
    ON runtime_tool_proposal_inbox(claimed_at)
    WHERE status = 'claimed';

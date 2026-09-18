-- M2-2：补齐单个工具步骤的可跳过策略、并发状态和控制命令审计。
-- Skip 只能在 Proposal 仍处于 claimed 时抢占；已经 executing 的工具不得被前端伪造中断。

ALTER TABLE tool_schema_versions
    ADD COLUMN IF NOT EXISTS skippable BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE tool_schema_versions s
   SET skippable = CASE
       WHEN t.name IN ('calculator', 'time_parser', 'knowledge_search', 'database_query', 'plan_validator')
           THEN TRUE
       ELSE FALSE
   END,
       updated_at = CURRENT_TIMESTAMP
  FROM tool_registries t
 WHERE t.tool_id = s.tool_id
   AND s.is_deleted = FALSE
   AND t.is_deleted = FALSE;

ALTER TABLE runtime_tool_proposal_inbox
    ADD COLUMN IF NOT EXISTS execution_started_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS skip_requested_at TIMESTAMPTZ;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conname = 'chk_runtime_tool_proposal_inbox_status'
    ) THEN
        ALTER TABLE runtime_tool_proposal_inbox
            DROP CONSTRAINT chk_runtime_tool_proposal_inbox_status;
    END IF;
    ALTER TABLE runtime_tool_proposal_inbox
        ADD CONSTRAINT chk_runtime_tool_proposal_inbox_status
        CHECK (status IN ('claimed', 'executing', 'skip_requested', 'completed', 'failed'));
END $$;

CREATE INDEX IF NOT EXISTS idx_runtime_tool_proposal_inbox_skip
    ON runtime_tool_proposal_inbox(status, skip_requested_at)
    WHERE status = 'skip_requested';

CREATE TABLE IF NOT EXISTS agent_run_tool_skips (
    agent_run_tool_skip_id BIGINT PRIMARY KEY,
    skip_id VARCHAR(128) NOT NULL UNIQUE,
    agent_run_id BIGINT NOT NULL REFERENCES agent_runs(agent_run_id),
    proposal_id VARCHAR(128) NOT NULL UNIQUE,
    invocation_id VARCHAR(128) NOT NULL,
    tool_name VARCHAR(128) NOT NULL,
    dispatch_id VARCHAR(64) NOT NULL,
    attempt INT NOT NULL CHECK (attempt >= 1),
    request_hash VARCHAR(128) NOT NULL,
    proposal_request_hash VARCHAR(128) NOT NULL,
    reason VARCHAR(256) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'requested',
    transport VARCHAR(16),
    mq_message_id VARCHAR(128),
    requested_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    applied_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_agent_run_tool_skips_status
        CHECK (status IN ('requested', 'dispatched', 'applied', 'rejected', 'failed'))
);

CREATE INDEX IF NOT EXISTS idx_agent_run_tool_skips_run_created
    ON agent_run_tool_skips(agent_run_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_agent_run_tool_skips_requested
    ON agent_run_tool_skips(status, created_at)
    WHERE status = 'requested';

COMMENT ON TABLE agent_run_tool_skips IS '用户跳过 Agent 单个工具步骤的控制命令和审计事实。';
COMMENT ON COLUMN agent_run_tool_skips.proposal_id IS '被跳过的唯一工具提案 ID。';
COMMENT ON COLUMN agent_run_tool_skips.tool_name IS '被跳过的工具名称，由 Java 从已持久化 Proposal 事实解析。';
COMMENT ON COLUMN agent_run_tool_skips.status IS 'requested、dispatched、applied、rejected 或 failed。';

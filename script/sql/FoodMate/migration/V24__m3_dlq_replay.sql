-- M3 DLQ 人工重放契约；仅新增可审计 Outbox 和原始消息文本快照，不自动执行重放。
-- 目标库已有 runtime_message_dlq 时，raw_payload_text 仅由后续新归档消息填充。

ALTER TABLE runtime_message_dlq
    ADD COLUMN IF NOT EXISTS raw_payload_text TEXT;

COMMENT ON COLUMN runtime_message_dlq.raw_payload_text IS '用于安全重放的原始消息文本；不暴露给管理查询。';

CREATE TABLE IF NOT EXISTS runtime_dlq_replay_outbox (
    replay_id BIGINT PRIMARY KEY,
    dlq_id BIGINT NOT NULL REFERENCES runtime_message_dlq(dlq_id),
    operator_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    consumer_group VARCHAR(128) NOT NULL,
    source_topic VARCHAR(128) NOT NULL,
    original_message_id VARCHAR(128) NOT NULL,
    message_key VARCHAR(128),
    run_id VARCHAR(64) NOT NULL,
    dispatch_id VARCHAR(64) NOT NULL,
    attempt INT,
    event_id VARCHAR(64) NOT NULL,
    event_seq BIGINT,
    request_hash VARCHAR(71) NOT NULL,
    payload_json JSONB NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    owner_token VARCHAR(128),
    lease_until TIMESTAMPTZ,
    send_attempts INT NOT NULL DEFAULT 0 CHECK (send_attempts >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error VARCHAR(512),
    broker_message_id VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    CONSTRAINT chk_runtime_dlq_replay_status CHECK (status IN ('pending','leased','published','failed')),
    CONSTRAINT chk_runtime_dlq_replay_published CHECK (
        status <> 'published' OR (broker_message_id IS NOT NULL AND published_at IS NOT NULL)
    ),
    CONSTRAINT uk_runtime_dlq_replay_idempotency UNIQUE (operator_id, idempotency_key)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_runtime_dlq_replay_active_dlq
    ON runtime_dlq_replay_outbox (dlq_id)
    WHERE status IN ('pending','leased','published');
CREATE INDEX IF NOT EXISTS idx_runtime_dlq_replay_pending
    ON runtime_dlq_replay_outbox (next_attempt_at, created_at)
    WHERE status IN ('pending','leased');

COMMENT ON TABLE runtime_dlq_replay_outbox IS '管理员确认后的 DLQ 重放 Outbox；发布前不改变业务终态。';
COMMENT ON COLUMN runtime_dlq_replay_outbox.replay_id IS '重放任务主键。';
COMMENT ON COLUMN runtime_dlq_replay_outbox.dlq_id IS '原始 DLQ 事实主键。';
COMMENT ON COLUMN runtime_dlq_replay_outbox.operator_id IS '发起重放的 superadmin 用户 ID。';
COMMENT ON COLUMN runtime_dlq_replay_outbox.idempotency_key IS '管理员重放请求幂等键。';
COMMENT ON COLUMN runtime_dlq_replay_outbox.original_message_id IS '原 Broker 消息 ID；新消息 ID 单独保存。';
COMMENT ON COLUMN runtime_dlq_replay_outbox.request_hash IS '原业务请求摘要，用于消费端幂等对账。';
COMMENT ON COLUMN runtime_dlq_replay_outbox.payload_json IS '原始消息 JSON 快照；只供受限 Relay 使用。';
COMMENT ON COLUMN runtime_dlq_replay_outbox.status IS 'pending/leased/published/failed 重放任务状态。';
COMMENT ON COLUMN runtime_dlq_replay_outbox.broker_message_id IS '本次重放由 Broker 返回的新消息 ID。';

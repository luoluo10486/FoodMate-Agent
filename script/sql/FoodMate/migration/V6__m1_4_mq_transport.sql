-- M1-4 RocketMQ 异步主通道追加结构（ADR-0005 阶段 D）。
-- 仅追加结构，不删除已有表；当前项目仍由发布流程人工执行迁移。

-- 1. dispatch outbox 增加 published 状态与 Broker 确认信息。
--    ADR-0005：published 只表示 Broker 已持久化确认，不表示 Python 业务处理完成。
--    HTTP 兼容通道继续使用 delivered，两种语义不混用。
ALTER TABLE runtime_dispatch_outbox
    ADD COLUMN IF NOT EXISTS transport VARCHAR(16) NOT NULL DEFAULT 'http',
    ADD COLUMN IF NOT EXISTS mq_topic VARCHAR(128),
    ADD COLUMN IF NOT EXISTS mq_message_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS published_at TIMESTAMPTZ;

ALTER TABLE runtime_dispatch_outbox DROP CONSTRAINT IF EXISTS chk_runtime_dispatch_outbox_status;
ALTER TABLE runtime_dispatch_outbox ADD CONSTRAINT chk_runtime_dispatch_outbox_status CHECK (
    status IN ('pending', 'leased', 'delivered', 'published', 'expired', 'failed')
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_runtime_dispatch_outbox_transport') THEN
        ALTER TABLE runtime_dispatch_outbox ADD CONSTRAINT chk_runtime_dispatch_outbox_transport
            CHECK (transport IN ('http', 'rocketmq'));
    END IF;
    -- published 必须携带 Broker 返回的消息标识，否则无法与 DLQ/重放对账。
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_runtime_dispatch_outbox_published') THEN
        ALTER TABLE runtime_dispatch_outbox ADD CONSTRAINT chk_runtime_dispatch_outbox_published CHECK (
            status <> 'published' OR (mq_message_id IS NOT NULL AND published_at IS NOT NULL)
        );
    END IF;
END $$;

-- 2. 取消命令的发布结果同样需要可追溯，避免「已 dispatched 但 Broker 未确认」。
ALTER TABLE agent_run_cancellations
    ADD COLUMN IF NOT EXISTS transport VARCHAR(16),
    ADD COLUMN IF NOT EXISTS mq_message_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS published_at TIMESTAMPTZ;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_agent_run_cancellation_transport') THEN
        ALTER TABLE agent_run_cancellations ADD CONSTRAINT chk_agent_run_cancellation_transport
            CHECK (transport IS NULL OR transport IN ('http', 'rocketmq'));
    END IF;
END $$;

-- 3. 消费端 DLQ 登记。
--    ADR-0005：DLQ 只表示消息处理耗尽重试，不能自动把 AgentRun 判为失败；
--    Reconciler 对账 Run、dispatch、checkpoint 和事件后才裁决。
CREATE TABLE IF NOT EXISTS runtime_message_dlq (
    dlq_id BIGINT PRIMARY KEY,
    consumer_group VARCHAR(128) NOT NULL,
    source_topic VARCHAR(128) NOT NULL,
    mq_message_id VARCHAR(128) NOT NULL,
    message_key VARCHAR(128),
    -- run_id 用文本保存：DLQ 里可能出现无法解析或已不存在的 Run，不能加外键。
    run_id VARCHAR(64),
    dispatch_id VARCHAR(64),
    attempt INT,
    event_id VARCHAR(64),
    event_seq BIGINT,
    request_hash VARCHAR(71),
    reconsume_times INT NOT NULL DEFAULT 0 CHECK (reconsume_times >= 0),
    error_code VARCHAR(64) NOT NULL,
    last_error TEXT,
    raw_payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    reconciliation_state VARCHAR(24) NOT NULL DEFAULT 'pending',
    reconciled_at TIMESTAMPTZ,
    reconciliation_note TEXT,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 同一条消息可能被 DLQ 消费者重复投递，按消费组 + 消息 ID 幂等。
    CONSTRAINT uk_runtime_message_dlq_message UNIQUE (consumer_group, mq_message_id),
    CONSTRAINT chk_runtime_message_dlq_state CHECK (
        reconciliation_state IN ('pending', 'resolved_duplicate', 'resolved_terminal', 'resolved_replayed', 'needs_attention')
    ),
    CONSTRAINT chk_runtime_message_dlq_reconciled CHECK (
        (reconciliation_state = 'pending') = (reconciled_at IS NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_runtime_message_dlq_pending
    ON runtime_message_dlq(first_seen_at)
    WHERE reconciliation_state = 'pending';
CREATE INDEX IF NOT EXISTS idx_runtime_message_dlq_run
    ON runtime_message_dlq(run_id, first_seen_at DESC);

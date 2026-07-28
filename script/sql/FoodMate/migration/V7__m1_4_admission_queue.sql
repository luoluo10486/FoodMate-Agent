-- M1-4 Redis 准入对应的 PostgreSQL Outbox 状态。
-- Redis 只协调短期 lease；queued/pending 是数据库事实，避免 Relay 绕过排队。

ALTER TABLE runtime_dispatch_outbox
    ADD COLUMN IF NOT EXISTS queued_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS queue_priority INT NOT NULL DEFAULT 0;

ALTER TABLE runtime_dispatch_outbox DROP CONSTRAINT IF EXISTS chk_runtime_dispatch_outbox_status;
ALTER TABLE runtime_dispatch_outbox ADD CONSTRAINT chk_runtime_dispatch_outbox_status CHECK (
    status IN ('pending', 'queued', 'leased', 'delivered', 'published', 'expired', 'failed')
);

CREATE INDEX IF NOT EXISTS idx_runtime_dispatch_outbox_queued
    ON runtime_dispatch_outbox(queue_priority DESC, queued_at, created_at)
    WHERE status = 'queued';

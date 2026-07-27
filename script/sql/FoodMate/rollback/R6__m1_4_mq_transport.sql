-- 回滚 V6__m1_4_mq_transport.sql。
-- 执行前确认没有 status='published' 的 outbox 行，否则回滚后 CHECK 会失败。

DROP INDEX IF EXISTS idx_runtime_message_dlq_run;
DROP INDEX IF EXISTS idx_runtime_message_dlq_pending;
DROP TABLE IF EXISTS runtime_message_dlq;

ALTER TABLE agent_run_cancellations DROP CONSTRAINT IF EXISTS chk_agent_run_cancellation_transport;
ALTER TABLE agent_run_cancellations
    DROP COLUMN IF EXISTS published_at,
    DROP COLUMN IF EXISTS mq_message_id,
    DROP COLUMN IF EXISTS transport;

ALTER TABLE runtime_dispatch_outbox DROP CONSTRAINT IF EXISTS chk_runtime_dispatch_outbox_published;
ALTER TABLE runtime_dispatch_outbox DROP CONSTRAINT IF EXISTS chk_runtime_dispatch_outbox_transport;

-- 先把 published 归一为 delivered，再恢复 V4 的状态取值集合。
UPDATE runtime_dispatch_outbox SET status = 'delivered' WHERE status = 'published';

ALTER TABLE runtime_dispatch_outbox DROP CONSTRAINT IF EXISTS chk_runtime_dispatch_outbox_status;
ALTER TABLE runtime_dispatch_outbox ADD CONSTRAINT chk_runtime_dispatch_outbox_status CHECK (
    status IN ('pending', 'leased', 'delivered', 'expired', 'failed')
);

ALTER TABLE runtime_dispatch_outbox
    DROP COLUMN IF EXISTS published_at,
    DROP COLUMN IF EXISTS mq_message_id,
    DROP COLUMN IF EXISTS mq_topic,
    DROP COLUMN IF EXISTS transport;

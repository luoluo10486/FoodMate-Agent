-- M1-4：补齐事件收件箱的 dispatch attempt。
-- V4 的 dispatch 表已经保存 attempt，但旧版 runtime_event_inbox_v2 漏了该列；
-- 这里使用可重复执行的追加迁移，兼容已经初始化的本地/测试数据库。

ALTER TABLE runtime_event_inbox_v2
    ADD COLUMN IF NOT EXISTS attempt INT NOT NULL DEFAULT 1;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_runtime_event_v2_attempt'
    ) THEN
        ALTER TABLE runtime_event_inbox_v2
            ADD CONSTRAINT chk_runtime_event_v2_attempt CHECK (attempt >= 1);
    END IF;
END $$;

COMMENT ON COLUMN runtime_event_inbox_v2.attempt IS 'Dispatch attempt for the event; recovery increments it for each new Run attempt.';

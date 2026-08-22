-- 仅在确认没有 replay outbox 事实且已完成人工导出后执行；默认不回滚。
DROP TABLE IF EXISTS runtime_dlq_replay_outbox;
ALTER TABLE runtime_message_dlq DROP COLUMN IF EXISTS raw_payload_text;

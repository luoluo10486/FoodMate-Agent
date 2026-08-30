-- M2-1 手动重试修正：允许同一条目追加新的索引 Outbox 事实。
-- 本迁移不删除既有消息、业务数据或历史 Outbox 记录。

ALTER TABLE knowledge_index_outbox
    DROP CONSTRAINT IF EXISTS uk_knowledge_index_outbox_item_topic;

CREATE INDEX IF NOT EXISTS idx_knowledge_index_outbox_item_topic
    ON knowledge_index_outbox (item_id, topic, outbox_id DESC);

COMMENT ON INDEX idx_knowledge_index_outbox_item_topic IS
    '按知识条目和主题查找最新索引 Outbox 事实，支持管理员重试载荷恢复。';

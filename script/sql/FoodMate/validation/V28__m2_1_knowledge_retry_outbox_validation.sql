-- V28 手动重试 Outbox 结构和数据安全校验；只读执行。

SELECT to_regclass('public.knowledge_index_outbox') AS knowledge_index_outbox_table;

SELECT indexname
FROM pg_indexes
WHERE schemaname = 'public'
  AND tablename = 'knowledge_index_outbox'
  AND indexname = 'idx_knowledge_index_outbox_item_topic';

SELECT conname
FROM pg_constraint
WHERE conrelid = 'public.knowledge_index_outbox'::regclass
  AND conname = 'uk_knowledge_index_outbox_item_topic';

SELECT item_id,
       topic,
       COUNT(*) AS fact_count
FROM knowledge_index_outbox
GROUP BY item_id, topic
HAVING COUNT(*) > 1
ORDER BY item_id, topic;

-- 该结果集应命名为 duplicate_item_topic_facts，供执行记录逐项登记。
SELECT COUNT(*) AS duplicate_item_topic_facts
FROM (
    SELECT item_id, topic
    FROM knowledge_index_outbox
    GROUP BY item_id, topic
    HAVING COUNT(*) > 1
) duplicates;

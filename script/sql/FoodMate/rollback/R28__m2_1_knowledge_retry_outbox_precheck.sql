-- Manual rollback precondition for V28.
-- 本文件只读，不自动删除重试事实，也不自动恢复唯一约束。

SELECT item_id,
       topic,
       COUNT(*) AS fact_count
FROM knowledge_index_outbox
GROUP BY item_id, topic
HAVING COUNT(*) > 1
ORDER BY item_id, topic;

SELECT COUNT(*) AS duplicate_item_topic_facts
FROM (
    SELECT item_id, topic
    FROM knowledge_index_outbox
    GROUP BY item_id, topic
    HAVING COUNT(*) > 1
) duplicates;

SELECT COUNT(*) AS pending_or_leased_retry_facts
FROM knowledge_index_outbox
WHERE status = 'pending'
   OR lease_until IS NOT NULL;

-- 仅在重复事实为 0、所有重试事实已完成对账且经变更审批后，
-- 才能由单独的人工迁移决定是否重新增加唯一约束。

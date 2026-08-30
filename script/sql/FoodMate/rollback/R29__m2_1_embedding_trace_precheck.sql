-- V29 回滚前置检查：仅输出待评审的数据量，不自动删除列或审计事实。
SELECT COUNT(*) AS provider_trace_id_rows
FROM knowledge_import_items
WHERE provider_trace_id IS NOT NULL;

-- 通过数据保留评审和变更审批后，才允许人工执行：
-- ALTER TABLE knowledge_import_items DROP COLUMN provider_trace_id;

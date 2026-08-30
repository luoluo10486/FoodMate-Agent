-- M2-1 Embedding 供应商 Trace 关联事实。
-- 只保存供应商返回的短追踪标识，不保存 API Key、请求正文或响应正文。

ALTER TABLE knowledge_import_items
    ADD COLUMN IF NOT EXISTS provider_trace_id VARCHAR(256);

COMMENT ON COLUMN knowledge_import_items.provider_trace_id IS
    'Embedding 供应商返回的短 Trace 标识；仅用于受控排障关联。';

-- M3 purge execution ledger. This migration creates facts only; it does not execute cleanup.

CREATE TABLE IF NOT EXISTS data_purge_task_results (
    result_id BIGINT PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES data_purge_tasks(task_id),
    request_id BIGINT NOT NULL REFERENCES data_purge_requests(request_id),
    resource_type VARCHAR(64) NOT NULL,
    resource_id BIGINT NOT NULL,
    task_type VARCHAR(24) NOT NULL,
    version VARCHAR(128) NOT NULL DEFAULT '',
    status VARCHAR(16) NOT NULL,
    backend VARCHAR(64) NOT NULL,
    deleted_count INT NOT NULL DEFAULT 0 CHECK (deleted_count >= 0),
    verified_absent BOOLEAN NOT NULL DEFAULT FALSE,
    message_id VARCHAR(256) NOT NULL DEFAULT '',
    result_digest VARCHAR(64) NOT NULL,
    error_code VARCHAR(64) NOT NULL DEFAULT '',
    error_summary VARCHAR(512) NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_data_purge_task_result_digest UNIQUE (task_id, result_digest),
    CONSTRAINT chk_data_purge_task_result_status CHECK (status IN ('succeeded', 'failed')),
    CONSTRAINT chk_data_purge_task_result_type CHECK (task_type IN ('object_storage', 'vector_index', 'database')),
    CONSTRAINT chk_data_purge_task_result_resource CHECK (resource_type IN ('knowledge_document', 'admin_export_job')),
    CONSTRAINT chk_data_purge_task_result_digest CHECK (result_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_data_purge_task_result_success_verification CHECK (status <> 'succeeded' OR verified_absent = TRUE)
);

CREATE INDEX IF NOT EXISTS idx_data_purge_task_results_request
    ON data_purge_task_results(request_id, created_at DESC);

COMMENT ON TABLE data_purge_task_results IS '清理步骤的安全执行对账事实；不保存对象键、向量或原始业务内容。';
COMMENT ON COLUMN data_purge_task_results.result_digest IS '由受控字段计算的幂等结果摘要。';

-- M2-3 bounded administrator export jobs.
-- Manual execution only. Export content is generated from redacted operational query DTOs.

CREATE TABLE IF NOT EXISTS admin_export_jobs (
    admin_export_job_id BIGINT PRIMARY KEY,
    operator_id BIGINT NOT NULL,
    resource VARCHAR(64) NOT NULL,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    fields_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    status VARCHAR(16) NOT NULL DEFAULT 'queued',
    object_key TEXT,
    failure_code VARCHAR(64),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    download_consumed_at TIMESTAMPTZ,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    CONSTRAINT chk_admin_export_jobs_status
        CHECK (status IN ('queued', 'running', 'completed', 'failed', 'expired')),
    CONSTRAINT chk_admin_export_jobs_resource
        CHECK (resource IN ('runs', 'users', 'tool-calls', 'sql-audits', 'tools', 'usage', 'knowledge', 'deleted', 'operation-audits'))
);

CREATE INDEX IF NOT EXISTS idx_admin_export_jobs_operator_created
    ON admin_export_jobs (operator_id, created_at DESC)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_admin_export_jobs_queue
    ON admin_export_jobs (status, created_at, admin_export_job_id)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE admin_export_jobs IS
    '有界管理运营导出任务；文件只包含已脱敏查询摘要，下载资格一次性消费。';

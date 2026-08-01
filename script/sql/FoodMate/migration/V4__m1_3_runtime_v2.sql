-- M1-3 / V2 双运行时可靠闭环追加结构。
-- 仅追加结构，不删除 V1 表；当前项目仍由发布流程人工执行迁移。

ALTER TABLE agent_runs
    ADD COLUMN IF NOT EXISTS active_dispatch_id BIGINT,
    ADD COLUMN IF NOT EXISTS admission_state VARCHAR(16) NOT NULL DEFAULT 'open',
    ADD COLUMN IF NOT EXISTS admission_epoch BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS cancellation_epoch BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS sse_last_stream_seq BIGINT NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_agent_runs_admission_state') THEN
        ALTER TABLE agent_runs ADD CONSTRAINT chk_agent_runs_admission_state CHECK (admission_state IN ('open', 'closed'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_agent_runs_admission_epoch') THEN
        ALTER TABLE agent_runs ADD CONSTRAINT chk_agent_runs_admission_epoch CHECK (admission_epoch >= 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_agent_runs_cancellation_epoch') THEN
        ALTER TABLE agent_runs ADD CONSTRAINT chk_agent_runs_cancellation_epoch CHECK (cancellation_epoch >= 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_agent_runs_sse_last_stream_seq') THEN
        ALTER TABLE agent_runs ADD CONSTRAINT chk_agent_runs_sse_last_stream_seq CHECK (sse_last_stream_seq >= 0);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS agent_run_dispatches (
    agent_run_dispatch_id BIGINT PRIMARY KEY,
    agent_run_id BIGINT NOT NULL REFERENCES agent_runs(agent_run_id),
    dispatch_id VARCHAR(64) NOT NULL UNIQUE,
    attempt INT NOT NULL CHECK (attempt >= 1),
    active_epoch BIGINT NOT NULL CHECK (active_epoch >= 1),
    fencing_token VARCHAR(128) NOT NULL UNIQUE,
    admission_epoch BIGINT NOT NULL CHECK (admission_epoch >= 0),
    dispatch_arbitration_state VARCHAR(16) NOT NULL DEFAULT 'active',
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    deadline_at TIMESTAMPTZ NOT NULL,
    last_event_seq BIGINT NOT NULL DEFAULT 0 CHECK (last_event_seq >= 0),
    accepted_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_agent_run_dispatch_attempt UNIQUE (agent_run_id, attempt),
    CONSTRAINT uk_agent_run_dispatch_epoch UNIQUE (agent_run_id, active_epoch),
    CONSTRAINT chk_agent_run_dispatch_arbitration CHECK (dispatch_arbitration_state IN ('active', 'superseded', 'expired')),
    CONSTRAINT chk_agent_run_dispatch_status CHECK (status IN ('pending', 'leased', 'delivered', 'expired', 'failed'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_run_dispatch_active
    ON agent_run_dispatches(agent_run_id)
    WHERE dispatch_arbitration_state = 'active';

CREATE INDEX IF NOT EXISTS idx_agent_run_dispatch_deadline
    ON agent_run_dispatches(dispatch_arbitration_state, deadline_at);
CREATE INDEX IF NOT EXISTS idx_agent_run_dispatch_run_created
    ON agent_run_dispatches(agent_run_id, created_at DESC);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_agent_runs_active_dispatch') THEN
        ALTER TABLE agent_runs ADD CONSTRAINT fk_agent_runs_active_dispatch
            FOREIGN KEY (active_dispatch_id) REFERENCES agent_run_dispatches(agent_run_dispatch_id);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS runtime_dispatch_outbox (
    outbox_id BIGINT PRIMARY KEY,
    agent_run_dispatch_id BIGINT NOT NULL UNIQUE REFERENCES agent_run_dispatches(agent_run_dispatch_id),
    agent_run_id BIGINT NOT NULL REFERENCES agent_runs(agent_run_id),
    dispatch_id VARCHAR(64) NOT NULL UNIQUE,
    run_id VARCHAR(64) NOT NULL,
    attempt INT NOT NULL CHECK (attempt >= 1),
    schema_version VARCHAR(16) NOT NULL,
    deadline_at TIMESTAMPTZ NOT NULL,
    fencing_epoch BIGINT NOT NULL CHECK (fencing_epoch >= 1),
    payload_json JSONB NOT NULL,
    request_hash VARCHAR(71) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    owner_token VARCHAR(128),
    lease_until TIMESTAMPTZ,
    send_attempts INT NOT NULL DEFAULT 0 CHECK (send_attempts >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at TIMESTAMPTZ,
    CONSTRAINT chk_runtime_dispatch_outbox_status CHECK (status IN ('pending', 'leased', 'delivered', 'expired', 'failed')),
    CONSTRAINT chk_runtime_dispatch_outbox_lease CHECK (
        (status = 'leased' AND owner_token IS NOT NULL AND lease_until IS NOT NULL)
        OR (status <> 'leased' AND owner_token IS NULL AND lease_until IS NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_runtime_dispatch_outbox_pending
    ON runtime_dispatch_outbox(next_attempt_at, created_at)
    WHERE status = 'pending';
CREATE INDEX IF NOT EXISTS idx_runtime_dispatch_outbox_leased
    ON runtime_dispatch_outbox(lease_until)
    WHERE status = 'leased';

CREATE TABLE IF NOT EXISTS runtime_event_inbox_v2 (
    runtime_event_inbox_id BIGINT PRIMARY KEY,
    agent_run_id BIGINT NOT NULL REFERENCES agent_runs(agent_run_id),
    dispatch_id VARCHAR(64) NOT NULL,
    attempt INT NOT NULL DEFAULT 1 CHECK (attempt >= 1),
    event_id VARCHAR(64) NOT NULL,
    event_seq BIGINT NOT NULL CHECK (event_seq >= 1),
    event_type VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    payload_json JSONB NOT NULL,
    request_hash VARCHAR(71) NOT NULL,
    processing_status VARCHAR(16) NOT NULL DEFAULT 'accepted',
    applied_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_runtime_event_v2_idempotency UNIQUE (agent_run_id, event_id),
    CONSTRAINT uk_runtime_event_v2_sequence UNIQUE (dispatch_id, event_seq),
    CONSTRAINT chk_runtime_event_v2_status CHECK (processing_status IN ('accepted', 'applied'))
);

CREATE INDEX IF NOT EXISTS idx_runtime_event_v2_run_seq
    ON runtime_event_inbox_v2(agent_run_id, event_seq);

CREATE TABLE IF NOT EXISTS runtime_event_rejections (
    rejection_id VARCHAR(64) PRIMARY KEY,
    agent_run_id BIGINT REFERENCES agent_runs(agent_run_id),
    dispatch_id VARCHAR(64) NOT NULL,
    attempt INT NOT NULL CHECK (attempt >= 1),
    event_id VARCHAR(64) NOT NULL,
    event_seq BIGINT NOT NULL CHECK (event_seq >= 1),
    request_hash VARCHAR(71) NOT NULL,
    reason VARCHAR(128) NOT NULL,
    error_code VARCHAR(64) NOT NULL,
    raw_envelope_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_runtime_event_rejections_run_created
    ON runtime_event_rejections(agent_run_id, created_at DESC);

CREATE TABLE IF NOT EXISTS runtime_error_inbox (
    runtime_error_inbox_id BIGINT PRIMARY KEY,
    agent_run_id BIGINT NOT NULL REFERENCES agent_runs(agent_run_id),
    error_id VARCHAR(64) NOT NULL,
    related_event_id VARCHAR(64),
    related_event_seq BIGINT,
    request_hash VARCHAR(71) NOT NULL,
    error_json JSONB NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_runtime_error_idempotency UNIQUE (agent_run_id, error_id),
    CONSTRAINT chk_runtime_error_event_pair CHECK ((related_event_id IS NULL) = (related_event_seq IS NULL))
);

CREATE TABLE IF NOT EXISTS protocol_error_audits (
    protocol_error_audit_id BIGINT PRIMARY KEY,
    request_id VARCHAR(64) NOT NULL,
    fingerprint VARCHAR(71) NOT NULL,
    error_code VARCHAR(64) NOT NULL,
    raw_envelope_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_protocol_error_request_fingerprint UNIQUE (request_id, fingerprint)
);

CREATE TABLE IF NOT EXISTS agent_run_cancellations (
    cancellation_id BIGINT PRIMARY KEY,
    agent_run_id BIGINT NOT NULL REFERENCES agent_runs(agent_run_id),
    cancel_id VARCHAR(64) NOT NULL UNIQUE,
    dispatch_id VARCHAR(64),
    request_hash VARCHAR(71) NOT NULL,
    reason VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'requested',
    requested_at TIMESTAMPTZ NOT NULL,
    acknowledged_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_agent_run_cancellation_status CHECK (status IN ('requested', 'dispatched', 'acknowledged', 'resolved', 'failed'))
);

CREATE TABLE IF NOT EXISTS agent_run_sse_outbox (
    agent_run_sse_outbox_id BIGINT PRIMARY KEY,
    agent_run_id BIGINT NOT NULL REFERENCES agent_runs(agent_run_id),
    sse_event_id VARCHAR(64) NOT NULL UNIQUE,
    stream_seq BIGINT NOT NULL CHECK (stream_seq >= 1),
    source_event_key VARCHAR(128) NOT NULL UNIQUE,
    event_type VARCHAR(64) NOT NULL,
    payload_json JSONB NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    owner_token VARCHAR(128),
    lease_until TIMESTAMPTZ,
    retry_count INT NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
    next_retry_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMPTZ,
    CONSTRAINT uk_agent_run_sse_stream_seq UNIQUE (agent_run_id, stream_seq),
    CONSTRAINT chk_agent_run_sse_status CHECK (status IN ('pending', 'leased', 'sent', 'dead_letter'))
);

CREATE INDEX IF NOT EXISTS idx_agent_run_sse_outbox_pending
    ON agent_run_sse_outbox(next_retry_at, created_at)
    WHERE status = 'pending';
CREATE INDEX IF NOT EXISTS idx_agent_run_sse_run_stream
    ON agent_run_sse_outbox(agent_run_id, stream_seq);

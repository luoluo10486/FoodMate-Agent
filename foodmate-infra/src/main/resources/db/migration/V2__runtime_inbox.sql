CREATE TABLE runtime_runs (
    run_id VARCHAR(128) PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE runtime_dispatches (
    dispatch_id VARCHAR(128) PRIMARY KEY,
    run_id VARCHAR(128) NOT NULL REFERENCES runtime_runs (run_id),
    request_fingerprint VARCHAR(256) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE runtime_cancels (
    cancel_id VARCHAR(128) PRIMARY KEY,
    run_id VARCHAR(128) NOT NULL REFERENCES runtime_runs (run_id),
    request_fingerprint VARCHAR(256) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE runtime_event_inbox (
    run_id VARCHAR(128) NOT NULL REFERENCES runtime_runs (run_id),
    event_id VARCHAR(128) NOT NULL,
    event_seq BIGINT NOT NULL,
    event_fingerprint VARCHAR(512) NOT NULL,
    state VARCHAR(32) NOT NULL,
    payload_json JSONB,
    occurred_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (run_id, event_id),
    UNIQUE (run_id, event_seq)
);

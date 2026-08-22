-- M2-1 public knowledge import and RAG control-plane schema.
-- Manual execution only. Existing knowledge documents remain private drafts until explicitly published.

ALTER TABLE knowledge_documents
    ALTER COLUMN version TYPE VARCHAR(128),
    ADD COLUMN IF NOT EXISTS source_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS source_version VARCHAR(128),
    ADD COLUMN IF NOT EXISTS license_notice VARCHAR(1024),
    ADD COLUMN IF NOT EXISTS visibility VARCHAR(32) NOT NULL DEFAULT 'draft',
    ADD COLUMN IF NOT EXISTS current_version BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS indexed_at TIMESTAMPTZ;

ALTER TABLE knowledge_chunks
    ALTER COLUMN version TYPE VARCHAR(128),
    ADD COLUMN IF NOT EXISTS document_version VARCHAR(128) NOT NULL DEFAULT '1',
    ADD COLUMN IF NOT EXISTS acl_metadata JSONB NOT NULL DEFAULT '{"tenant_id":0,"scope":"public"}'::jsonb;

ALTER TABLE knowledge_documents DROP CONSTRAINT IF EXISTS chk_knowledge_documents_visibility;
ALTER TABLE knowledge_documents ADD CONSTRAINT chk_knowledge_documents_visibility
    CHECK (visibility IN ('draft', 'published', 'disabled', 'deleted'));

CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_documents_current_source_version
    ON knowledge_documents (tenant_id, source_name, source_version)
    WHERE current_version = TRUE AND is_deleted = FALSE AND source_name IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_knowledge_documents_public_search
    ON knowledge_documents (tenant_id, visibility, status, current_version)
    WHERE is_deleted = FALSE;
CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_chunks_embedding_id
    ON knowledge_chunks (embedding_id) WHERE embedding_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS knowledge_import_jobs (
    job_id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0 CHECK (tenant_id = 0),
    operator_id BIGINT NOT NULL REFERENCES users(user_id),
    idempotency_key VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'queued',
    requested_mode VARCHAR(16) NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    source_name VARCHAR(255) NOT NULL,
    source_version VARCHAR(128) NOT NULL,
    license_notice VARCHAR(1024) NOT NULL,
    trace_id VARCHAR(64),
    request_id VARCHAR(64),
    token_count BIGINT NOT NULL DEFAULT 0,
    cost_amount NUMERIC(18, 8) NOT NULL DEFAULT 0,
    price_version VARCHAR(128),
    error_code VARCHAR(64),
    error_summary VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    CONSTRAINT chk_knowledge_import_jobs_status CHECK (status IN ('queued','uploading','uploaded','indexing','completed','partial_failed','failed','cancelled')),
    CONSTRAINT chk_knowledge_import_jobs_mode CHECK (requested_mode IN ('stub','local')),
    CONSTRAINT chk_knowledge_import_jobs_idempotency CHECK (length(btrim(idempotency_key)) > 0),
    CONSTRAINT uk_knowledge_import_jobs_operator_idempotency UNIQUE (operator_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS knowledge_import_items (
    item_id BIGINT PRIMARY KEY,
    job_id BIGINT NOT NULL REFERENCES knowledge_import_jobs(job_id),
    document_id BIGINT NOT NULL REFERENCES knowledge_documents(document_id),
    filename VARCHAR(512) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    file_size BIGINT NOT NULL CHECK (file_size > 0 AND file_size <= 20971520),
    upload_status VARCHAR(32) NOT NULL DEFAULT 'queued',
    index_status VARCHAR(32) NOT NULL DEFAULT 'pending',
    attempt_count INT NOT NULL DEFAULT 0 CHECK (attempt_count BETWEEN 0 AND 3),
    lease_owner VARCHAR(128),
    lease_until TIMESTAMPTZ,
    error_code VARCHAR(64),
    error_summary VARCHAR(512),
    token_count BIGINT NOT NULL DEFAULT 0,
    cost_amount NUMERIC(18, 8) NOT NULL DEFAULT 0,
    model_version VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    indexed_at TIMESTAMPTZ,
    CONSTRAINT chk_knowledge_import_items_upload CHECK (upload_status IN ('queued','uploading','uploaded','upload_failed')),
    CONSTRAINT chk_knowledge_import_items_index CHECK (index_status IN ('pending','parsing','parsed','indexing','indexed','index_failed')),
    CONSTRAINT uk_knowledge_import_items_document UNIQUE (document_id)
);

CREATE INDEX IF NOT EXISTS idx_knowledge_import_items_claim
    ON knowledge_import_items (index_status, lease_until, created_at)
    WHERE index_status IN ('pending','parsed','indexing');
CREATE INDEX IF NOT EXISTS idx_knowledge_import_items_job_status
    ON knowledge_import_items (job_id, index_status);

CREATE TABLE IF NOT EXISTS knowledge_index_outbox (
    outbox_id BIGINT PRIMARY KEY,
    item_id BIGINT NOT NULL REFERENCES knowledge_import_items(item_id),
    topic VARCHAR(128) NOT NULL,
    payload_json JSONB NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    attempt_count INT NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    CONSTRAINT chk_knowledge_index_outbox_status CHECK (status IN ('pending','published','failed')),
    CONSTRAINT chk_knowledge_index_outbox_topic CHECK (topic = 'foodmate-knowledge-index-v1'),
    CONSTRAINT uk_knowledge_index_outbox_item_topic UNIQUE (item_id, topic)
);
CREATE INDEX IF NOT EXISTS idx_knowledge_index_outbox_pending
    ON knowledge_index_outbox (available_at, outbox_id) WHERE status = 'pending';

CREATE TABLE IF NOT EXISTS knowledge_import_sse_outbox (
    event_id BIGINT PRIMARY KEY,
    job_id BIGINT NOT NULL REFERENCES knowledge_import_jobs(job_id),
    item_id BIGINT REFERENCES knowledge_import_items(item_id),
    event_type VARCHAR(64) NOT NULL,
    payload_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_knowledge_import_sse_job_event
    ON knowledge_import_sse_outbox (job_id, event_id);

COMMENT ON TABLE knowledge_import_jobs IS 'M2-1 public knowledge import batch authority; tenant_id is fixed to 0.';
COMMENT ON TABLE knowledge_import_items IS 'Per-file ingest/index state. A worker lease never authorizes public visibility.';
COMMENT ON TABLE knowledge_index_outbox IS 'Atomic Java-to-Python knowledge indexing dispatch outbox.';

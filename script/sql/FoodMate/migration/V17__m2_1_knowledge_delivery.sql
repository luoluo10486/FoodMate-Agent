-- M2-1: durable delivery and result-consumption facts for public knowledge indexing.
ALTER TABLE knowledge_index_outbox
    ADD COLUMN IF NOT EXISTS owner_token VARCHAR(128),
    ADD COLUMN IF NOT EXISTS lease_until TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_error VARCHAR(512),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE knowledge_import_items
    ADD COLUMN IF NOT EXISTS chunk_count INT;

-- A batch legitimately contains several files from one source/version. The V16
-- source-only key would reject the second file, so document title completes it.
DROP INDEX IF EXISTS uk_knowledge_documents_current_source_version;
CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_documents_current_source_document_version
    ON knowledge_documents (tenant_id, source_name, source_version, title)
    WHERE current_version = TRUE AND is_deleted = FALSE AND source_name IS NOT NULL;

CREATE TABLE IF NOT EXISTS knowledge_visibility_outbox (
    outbox_id BIGINT PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES knowledge_documents(document_id),
    topic VARCHAR(128) NOT NULL,
    payload_json JSONB NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    attempt_count INT NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    owner_token VARCHAR(128),
    lease_until TIMESTAMPTZ,
    last_error VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    CONSTRAINT chk_knowledge_visibility_outbox_status CHECK (status IN ('pending','published','failed'))
);
CREATE INDEX IF NOT EXISTS idx_knowledge_visibility_outbox_pending
    ON knowledge_visibility_outbox (available_at, outbox_id) WHERE status = 'pending';

CREATE TABLE IF NOT EXISTS knowledge_index_result_inbox (
    item_id BIGINT NOT NULL REFERENCES knowledge_import_items(item_id),
    document_version VARCHAR(128) NOT NULL,
    attempt_count INT NOT NULL,
    payload_hash VARCHAR(128) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (item_id, document_version, attempt_count)
);

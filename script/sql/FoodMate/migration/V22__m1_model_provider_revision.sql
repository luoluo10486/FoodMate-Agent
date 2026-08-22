-- M1 provider management follow-up: add optimistic concurrency to provider status changes.
-- Manual execution only. Existing provider rows start at revision 1.

ALTER TABLE model_providers
    ADD COLUMN IF NOT EXISTS revision BIGINT NOT NULL DEFAULT 1;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_model_providers_revision'
          AND conrelid = 'model_providers'::regclass
    ) THEN
        ALTER TABLE model_providers
            ADD CONSTRAINT chk_model_providers_revision CHECK (revision >= 1);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_model_providers_revision
    ON model_providers (provider_id, revision) WHERE is_deleted = FALSE;

COMMENT ON COLUMN model_providers.revision IS '供应商状态管理的乐观并发版本。';

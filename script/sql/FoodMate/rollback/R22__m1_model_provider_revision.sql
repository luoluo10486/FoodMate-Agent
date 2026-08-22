-- V22 rollback precondition: stop provider status writes and confirm no active operation relies on
-- provider revision. This rollback leaves provider rows and all other M1 governance objects.

DROP INDEX IF EXISTS idx_model_providers_revision;
ALTER TABLE model_providers DROP CONSTRAINT IF EXISTS chk_model_providers_revision;
ALTER TABLE model_providers DROP COLUMN IF EXISTS revision;

-- M1-5：为所有饮食写操作补齐统一幂等审计字段。
-- Manual execution only. Flyway remains disabled by policy.

ALTER TABLE operation_audits
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128),
    ADD COLUMN IF NOT EXISTS parameters_digest VARCHAR(128);

CREATE UNIQUE INDEX IF NOT EXISTS uk_operation_audits_operator_idempotency
    ON operation_audits (operator_id, idempotency_key)
    WHERE operator_id IS NOT NULL
      AND idempotency_key IS NOT NULL
      AND is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_operation_audits_idempotency_lookup
    ON operation_audits (operator_id, idempotency_key, created_at)
    WHERE idempotency_key IS NOT NULL
      AND is_deleted = FALSE;

COMMENT ON COLUMN operation_audits.idempotency_key IS '写操作幂等键；相同操作者和幂等键只允许一个事实。';
COMMENT ON COLUMN operation_audits.parameters_digest IS '写操作参数摘要；参数变化时返回幂等冲突。';


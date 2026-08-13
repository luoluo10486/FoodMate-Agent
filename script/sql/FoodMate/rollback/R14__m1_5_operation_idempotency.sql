-- 回滚 V14 M1-5 统一幂等审计字段。
-- 执行前确认没有依赖这些字段的业务代码或审计数据。

DROP INDEX IF EXISTS idx_operation_audits_idempotency_lookup;
DROP INDEX IF EXISTS uk_operation_audits_operator_idempotency;

ALTER TABLE operation_audits
    DROP COLUMN IF EXISTS idempotency_key,
    DROP COLUMN IF EXISTS parameters_digest;


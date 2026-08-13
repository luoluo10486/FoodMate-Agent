-- V14 M1-5 执行后校验。只读，不创建或修改对象。

SELECT column_name, data_type, character_maximum_length
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'operation_audits'
  AND column_name IN ('idempotency_key', 'parameters_digest')
ORDER BY column_name;

SELECT indexname
FROM pg_indexes
WHERE schemaname = 'public'
  AND indexname IN (
      'uk_operation_audits_operator_idempotency',
      'idx_operation_audits_idempotency_lookup'
  )
ORDER BY indexname;


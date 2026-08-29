-- V27 execution ledger validation. Run after the migration in the target database.
SELECT to_regclass('public.data_purge_task_results') AS data_purge_task_results_table;

SELECT indexname
FROM pg_indexes
WHERE schemaname = 'public'
  AND tablename = 'data_purge_task_results'
  AND indexname IN ('uk_data_purge_task_result_digest', 'idx_data_purge_task_results_request');

SELECT conname
FROM pg_constraint
WHERE conrelid = 'public.data_purge_task_results'::regclass
  AND conname IN (
      'chk_data_purge_task_result_status',
      'chk_data_purge_task_result_type',
      'chk_data_purge_task_result_success_verification'
  );

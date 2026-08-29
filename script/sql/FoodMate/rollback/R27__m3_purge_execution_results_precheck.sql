-- Manual rollback precondition for V27.
-- This file intentionally does not drop execution facts. Export and retention approval are required.
SELECT COUNT(*) AS purge_execution_result_rows FROM data_purge_task_results;
SELECT COUNT(*) AS successful_unverified_rows
FROM data_purge_task_results
WHERE status = 'succeeded' AND verified_absent = FALSE;
-- After an approved retention decision, an operator may remove this table with an explicit change ticket.

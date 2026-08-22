-- Manual rollback precondition: no data purge request/task may be active.
-- Do not run this script against a database with retention evidence to preserve.
DROP TABLE IF EXISTS data_purge_tasks;
DROP TABLE IF EXISTS data_purge_requests;
DROP TABLE IF EXISTS data_legal_holds;
DROP TABLE IF EXISTS data_retention_policies;

-- 回滚 V5：删除 M1-4 continuation 与预算快照结构。
-- 回滚前必须确认没有 Run 依赖 superseded 状态或预算快照数据。

DROP TABLE IF EXISTS agent_run_budget_extensions;
DROP TABLE IF EXISTS agent_run_budget_snapshots;

DROP INDEX IF EXISTS uk_agent_runs_parent_active;
DROP INDEX IF EXISTS idx_agent_runs_parent_run;

-- 状态 CHECK 还原为 V1 集合；存在 superseded 行时该语句会失败，需先人工处理数据。
ALTER TABLE agent_runs DROP CONSTRAINT IF EXISTS chk_agent_runs_status;
ALTER TABLE agent_runs ADD CONSTRAINT chk_agent_runs_status CHECK (
    status IN ('queued', 'routed', 'waiting_user', 'planning', 'retrieving', 'executing',
               'validating', 'completed', 'failed', 'cancelled')
);

ALTER TABLE agent_runs DROP CONSTRAINT IF EXISTS chk_agent_runs_continuation_pair;
ALTER TABLE agent_runs DROP CONSTRAINT IF EXISTS chk_agent_runs_result_type;
ALTER TABLE agent_runs DROP CONSTRAINT IF EXISTS chk_agent_runs_continuation_reason;
ALTER TABLE agent_runs DROP CONSTRAINT IF EXISTS fk_agent_runs_superseded_by_run;
ALTER TABLE agent_runs DROP CONSTRAINT IF EXISTS fk_agent_runs_parent_run;

ALTER TABLE agent_runs
    DROP COLUMN IF EXISTS result_type,
    DROP COLUMN IF EXISTS continuation_reason,
    DROP COLUMN IF EXISTS superseded_by_run_id,
    DROP COLUMN IF EXISTS parent_run_id;

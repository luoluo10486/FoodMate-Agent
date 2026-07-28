-- 同一 Run 的同一用户确认摘要只能产生一次预算追加。
CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_run_budget_extension_digest
    ON agent_run_budget_extensions(agent_run_id, confirmation_digest)
    WHERE status = 'confirmed';

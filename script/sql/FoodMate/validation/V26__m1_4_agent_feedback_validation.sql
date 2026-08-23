-- V26 结构化 Agent 反馈人工执行后校验。
SELECT to_regclass('public.agent_feedback') AS agent_feedback_table;
SELECT indexname
FROM pg_indexes
WHERE schemaname = 'public'
  AND tablename = 'agent_feedback'
  AND indexname IN ('uk_agent_feedback_user_message', 'uk_agent_feedback_user_idempotency');

SELECT conname
FROM pg_constraint
WHERE conrelid = 'public.agent_feedback'::regclass
  AND conname IN ('chk_agent_feedback_comment_length', 'chk_agent_feedback_reason_codes_array');

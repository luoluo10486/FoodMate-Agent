-- FoodMate 首版核心表回滚脚本。
-- 用途：在开发或测试环境中撤销 V1__init_core_schema.sql 创建的核心表。
-- 注意：该脚本会删除表和表内数据，生产环境执行前必须先完成备份和人工审批。

BEGIN;

-- messages 和 agent_runs 之间存在互相引用，先解除 messages 指向 agent_runs 的外键。
ALTER TABLE IF EXISTS messages
    DROP CONSTRAINT IF EXISTS fk_messages_agent_run_id;

-- 按依赖关系反向删除表，避免外键阻止回滚。
DROP TABLE IF EXISTS operation_audits;
DROP TABLE IF EXISTS model_route_rules;
DROP TABLE IF EXISTS model_usage_logs;
DROP TABLE IF EXISTS tool_schema_versions;
DROP TABLE IF EXISTS tool_registries;
DROP TABLE IF EXISTS sql_query_audits;
DROP TABLE IF EXISTS schema_catalogs;
DROP TABLE IF EXISTS data_sources;
DROP TABLE IF EXISTS knowledge_chunks;
DROP TABLE IF EXISTS knowledge_documents;
DROP TABLE IF EXISTS session_summaries;
DROP TABLE IF EXISTS user_memories;
DROP TABLE IF EXISTS shopping_lists;
DROP TABLE IF EXISTS meal_plans;
DROP TABLE IF EXISTS analysis_reports;
DROP TABLE IF EXISTS food_logs;
DROP TABLE IF EXISTS tool_calls;
DROP TABLE IF EXISTS agent_runs;
DROP TABLE IF EXISTS messages;
DROP TABLE IF EXISTS sessions;
DROP TABLE IF EXISTS user_avatar_assets;
DROP TABLE IF EXISTS auth_refresh_tokens;
DROP TABLE IF EXISTS user_profiles;
DROP TABLE IF EXISTS users;

COMMIT;

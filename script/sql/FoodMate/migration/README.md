# FoodMate 数据库增量变更目录

本目录仅存放经评审、编号递增的人工执行增量 SQL，例如 `V2__add_runtime_outbox.sql`。

- 不由 Java 启动自动扫描或执行。
- 新增脚本必须同时提供对应的校验 SQL、回滚前置条件和变更说明；历史脚本的配套完整性以本文末尾矩阵为准。
- 已执行脚本不得原地修改；修正必须创建新的递增版本。
- 执行前先备份并记录数据库、执行人、时间、版本和校验结果。

当前增量顺序：

- `V4`：双运行时 dispatch、事件 Inbox、SSE Outbox 和取消结构。
- `V5`：continuation、`superseded` 和预算快照。
- `V6`：RocketMQ 发布状态与 DLQ 对账。
- `V7`：Redis admission 对应的 `queued` Outbox 状态。
- `V8`：摘要覆盖范围、CAS 版本、Prompt 版本和 digest。
- `V9`：预算追加确认摘要的幂等唯一约束。

`V7`-`V9` 仍需在 PostgreSQL 实例上人工执行并完成校验后，才能开启本地真实 Redis admission 和预算追加恢复。
`V10__m1_4_memory_confirmation.sql`：长期记忆确认状态、冲突隔离索引和用户确认后的 Context 放行。

`V12__m1_4_event_attempt_compatibility.sql`：为已有 `runtime_event_inbox_v2` 补齐 `dispatch attempt` 字段，兼容恢复 Run 的事件顺序校验。该脚本使用 `IF NOT EXISTS`，可重复执行；执行前仍需按项目规则完成备份和执行记录。

`V13__m1_5_food_log_nutrition_approval.sql`：M1-5 饮食记录主表、食材明细、营养目录、单位换算和写确认事实。该脚本要求 `food_logs` 为空，删除旧 `items_json/nutrition_json`，不包含任何营养种子数值；当前本地库已存在该结构，本轮只读复核且未重复执行。营养目录数据另由 `seed/V1__nutrition_usda_seed.sql` 和 `seed/V2__nutrition_usda_portion_seed.sql` 人工导入，当前各有 5 条已核验 seed/rule 并通过校验。配套校验为 `validation/V13__m1_5_food_log_nutrition_approval_validation.sql`、`validation/V1__nutrition_usda_seed_validation.sql` 和 `validation/V2__nutrition_usda_portion_seed_validation.sql`，回滚为 `rollback/R13__m1_5_food_log_nutrition_approval.sql`。

`V14__m1_5_operation_idempotency.sql`：为 `operation_audits` 增加统一写操作幂等键、参数摘要和唯一索引，覆盖创建、删除、恢复等不应重复执行的业务写入。当前本地库已存在该结构，本轮只读复核且未重复执行。配套校验为 `validation/V14__m1_5_operation_idempotency_validation.sql`，回滚为 `rollback/R14__m1_5_operation_idempotency.sql`。

`V15__m1_5_meal_plan_lifecycle.sql`：为 `meal_plans` 增加计划写入幂等键、乐观并发 `revision` 及对应索引，支持计划修改、软删除、恢复和状态变更。该脚本已在当前本地库人工执行并通过校验，保留现有 2 条计划和其余数据。配套校验为 `validation/V15__m1_5_meal_plan_lifecycle_validation.sql`，回滚为 `rollback/R15__m1_5_meal_plan_lifecycle.sql`。

`V19__m2_2_database_query_structured_contract.sql`：在不修改 V18 的前提下，为 `database_query` 发布结构化输入、候选 SQL、规划模式和 SQL 审计 ID 的 v2 注册表 Schema，并将当前版本切换到 v2；通信包的 `schema_version` 仍为 v1。执行前必须确认 V18 的 `database_query` 注册表当前版本为 v1。配套校验为 `validation/V19__m2_2_database_query_structured_contract_validation.sql`，回滚为 `rollback/R19__m2_2_database_query_structured_contract.sql`。

`V20__m2_3_admin_management_contract.sql`：为管理员状态写入、工具启停和软删除恢复增加 `revision` 乐观并发版本；管理写接口同时要求 `Idempotency-Key`，高风险工具和恢复操作要求确认摘要。该脚本仅人工执行，不由 Java 启动自动迁移；配套校验为 `validation/V20__m2_3_admin_management_contract_validation.sql`，回滚为 `rollback/R20__m2_3_admin_management_contract.sql`。

`V21__m1_model_governance_contract.sql`：增加供应商/模型目录、价格版本和预算策略，并为路由和模型用量事实补齐路由、价格与预算版本快照。治理表不保存 API Key、Secret 或可逆凭据；当前运行时没有匹配的数据库路由时仍使用显式配置的 deterministic/stub 默认快照。该脚本仅人工执行，不清理已有路由或用量；配套校验为 `validation/V21__m1_model_governance_contract_validation.sql`，回滚为 `rollback/R21__m1_model_governance_contract.sql`。

`V22__m1_model_provider_revision.sql`：为供应商启停补齐乐观并发版本，已有供应商从 revision 1 开始。配套校验为 `validation/V22__m1_model_provider_revision_validation.sql`，回滚为 `rollback/R22__m1_model_provider_revision.sql`。

`V23__m2_3_admin_export_jobs.sql`：管理员运营导出任务、受限资源枚举和一次性下载事实。导出内容必须来自已脱敏查询 DTO，不允许把原始 Prompt、令牌、对象存储凭据或完整业务请求写入导出文件。配套校验为 `validation/V23__m2_3_admin_export_jobs_validation.sql`，回滚为 `rollback/R23__m2_3_admin_export_jobs.sql`。

`V24__m3_dlq_replay.sql`：DLQ 原始消息受限快照和管理员确认后的重放 Outbox。迁移只创建事实和索引，不自动重放消息。配套校验为 `validation/V24__m3_dlq_replay_validation.sql`，回滚为 `rollback/R24__m3_dlq_replay.sql`。

`V25__m3_retention_governance.sql`：保留策略、法律冻结、清理请求和对象/向量/数据库任务。默认关闭硬删除，数据库清理必须等待对象存储和向量任务成功。配套校验为 `validation/V25__m3_retention_governance_validation.sql`，回滚为 `rollback/R25__m3_retention_governance.sql`。

## 配套文件矩阵

| 版本 | validation | rollback | 处理边界 |
|---|---|---|---|
| V2 | 有 | 有 | 当前已登记的账户与隐私迁移 |
| V3-V4 | 无 | 无 | 历史运行时结构；只读复核，不生成反向删除 |
| V5-V6 | 无 | 有 | 仅使用已评审回滚前置条件；执行前必须确认数据范围 |
| V7-V12 | 无 | 无 | 历史运行时、记忆和兼容结构；以数据库事实和新增迁移修正 |
| V13-V25 | 有 | 有 | 当前目录约定，按版本保存 validation 和 rollback |

该矩阵描述文件现状，不代表任何迁移已在当前数据库执行。实际执行状态、validation 输出、失败与补偿必须以 `../EXECUTION_RECORD.md` 为准。历史版本若需补充校验，优先新增只读 SQL 文档；若需修复结构，创建更高版本迁移，不原地修改已执行脚本，不执行宽泛删除或 `TRUNCATE`。

完整的人工执行顺序、备份要求、目录职责和台账要求见上级目录 `script/sql/FoodMate/README.md`。

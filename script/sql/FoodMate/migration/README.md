# FoodMate 数据库增量变更目录

本目录仅存放经评审、编号递增的人工执行增量 SQL，例如 `V2__add_runtime_outbox.sql`。

- 不由 Java 启动自动扫描或执行。
- 每个脚本必须同时提供对应的校验 SQL、回滚前置条件和变更说明。
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

`V13__m1_5_food_log_nutrition_approval.sql`：M1-5 饮食记录主表、食材明细、营养目录、单位换算和写确认事实。该脚本要求 `food_logs` 为空，删除旧 `items_json/nutrition_json`，不包含任何营养种子数值；当前本地库已存在该结构，本轮只读复核且未重复执行。营养目录当前仍为 0 条。配套校验为 `validation/V13__m1_5_food_log_nutrition_approval_validation.sql`，回滚为 `rollback/R13__m1_5_food_log_nutrition_approval.sql`。

`V14__m1_5_operation_idempotency.sql`：为 `operation_audits` 增加统一写操作幂等键、参数摘要和唯一索引，覆盖创建、删除、恢复等不应重复执行的业务写入。当前本地库已存在该结构，本轮只读复核且未重复执行。配套校验为 `validation/V14__m1_5_operation_idempotency_validation.sql`，回滚为 `rollback/R14__m1_5_operation_idempotency.sql`。

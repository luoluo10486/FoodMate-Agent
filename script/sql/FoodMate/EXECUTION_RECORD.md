# FoodMate 数据库人工执行记录

> 模板记录。实际执行后必须由执行人填写，不能用应用启动日志替代。

## V2 临时 PostgreSQL 演练（非目标库）

| 字段 | 内容 |
|---|---|
| 数据库 | 临时 Docker PostgreSQL 16，数据库 `FoodMate` |
| 环境 | local SQL rehearsal，仅验证脚本，不是用户现有 PostgreSQL |
| 脚本版本 | `V1__init_core_schema.sql` + `V2__m1_account_and_privacy.sql` |
| 执行时间（UTC） | 2026-07-22T13:39:31Z |
| 执行结果 | 基线成功；V2 首次成功；V2 重复执行成功；V2 validation 成功 |
| 备份 | 未对用户目标库执行 DDL，故未创建目标库备份 |
| 回滚结论 | 临时容器验证后已删除；目标库未执行、无需回滚 |

> 该演练不构成目标 `FoodMate` 数据库的正式执行证据。正式执行前仍须按 `BACKUP_AND_ROLLBACK.md` 完成备份、恢复演练和执行人登记。

| 字段 | 内容 |
|---|---|
| 数据库 | `FoodMate` |
| 环境 | 待填写：local/dev/staging/prod |
| 脚本版本 | 待填写：V1 或 Vn |
| 执行人 | 待填写 |
| 执行时间（UTC） | 待填写 |
| 备份位置与校验和 | 待填写 |
| 执行命令/客户端版本 | 待填写 |
| 执行结果 | 待填写：成功/失败 |
| `validation.sql` 结果 | 待填写 |
| 回滚结论 | 待填写：未执行/已执行及原因 |

执行失败时，保留完整错误、已执行语句范围和恢复动作；不得覆盖原记录。

## V13 M1-5 饮食记录与营养目录（本轮已复核，未重复执行）

| 字段 | 内容 |
|---|---|
| 数据库 | `FoodMate` |
| 环境 | local，运行中的 Docker PostgreSQL 16 容器 `foodmate-postgres` |
| 脚本版本 | `V13__m1_5_food_log_nutrition_approval.sql` |
| 执行人 | 本轮未执行迁移；复核人为当前 Codex 会话 |
| 执行时间（UTC） | 未知；不补写历史执行时间 |
| 前置确认 | 当前只读查询：`food_logs=1`，因此不能按 V13 的空表前置条件重复执行 |
| 备份 | 当前开发阶段按用户决策暂不做数据库备份；正式生产流程后置 |
| 执行命令/客户端版本 | `docker exec foodmate-postgres psql`，PostgreSQL 16.14 |
| 执行结果 | 未重复执行；本轮只读校验确认五张表、字段、约束、索引存在 |
| 校验脚本 | `validation/V13__m1_5_food_log_nutrition_approval_validation.sql` |
| 校验结果 | 通过：V13 validation 查询确认表/字段/约束/索引存在，旧 `items_json`/`nutrition_json` 不存在；当前 `nutrition_foods=0`、`nutrition_unit_conversions=0` |
| 回滚结论 | 未执行；未运行回滚 SQL，也未修改数据 |

执行 V13 前必须确认当前本地数据库无历史 FoodMate 业务数据；如果 `food_logs` 非空，保留脚本异常并先进行数据评审，不能直接绕过前置条件。

## V14 M1-5 写操作统一幂等（本轮已复核，未重复执行）

| 字段 | 内容 |
|---|---|
| 数据库 | `FoodMate` |
| 环境 | local，运行中的 Docker PostgreSQL 16 容器 `foodmate-postgres` |
| 脚本版本 | `V14__m1_5_operation_idempotency.sql` |
| 执行人 | 本轮未执行迁移；复核人为当前 Codex 会话 |
| 执行时间（UTC） | 未知；不补写历史执行时间 |
| 执行结果 | 未重复执行；本轮只读校验确认 `operation_audits` 幂等字段和索引存在 |
| 校验脚本 | `validation/V14__m1_5_operation_idempotency_validation.sql` |
| 校验结果 | 通过：V14 validation 查询确认 `idempotency_key`、`parameters_digest` 和对应索引存在；审批审计 `approval.propose/confirm/execute` 各 1 条成功记录 |
| 回滚结论 | 未执行；未运行回滚 SQL，也未修改数据 |

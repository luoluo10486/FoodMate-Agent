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

## V1 营养目录 seed（2026-08-14 已执行并校验，历史快照）

| 字段 | 内容 |
|---|---|
| 数据库 | `FoodMate` |
| 环境 | local，Docker PostgreSQL 16 容器 `foodmate-postgres` |
| 脚本版本 | `seed/V1__nutrition_usda_seed.sql` |
| 执行方式 | 人工 `psql` 执行；Java 启动不会自动执行 seed |
| 来源 | USDA FoodData Central `SR Legacy`，数据发布时间 `2019-04-01`，API Guide 许可证为 `CC0 1.0` |
| 执行结果 | 成功导入 5 条 `approved` 食材；重复执行返回 `INSERT 0 0`，未重复创建 |
| 校验脚本 | `validation/V1__nutrition_usda_seed_validation.sql` |
| 校验结果 | 通过：非法 seed 行数为 0，5 条均为每 100g 基准；`nutrition_unit_conversions=0`，未推断家庭单位换算 |
| 当时数据量 | `food_logs=8`、`food_log_items=11`、`nutrition_foods=5`、`nutrition_unit_conversions=0`、`approval_requests=6`、`runtime_tool_proposal_inbox=69`；food log 已匹配 7 条、`pending` 4 条 |
| 备份/回滚 | 当前开发阶段按用户决策暂不做数据库备份；seed 使用 `ON CONFLICT DO NOTHING`，未执行回滚 |

## M1-5 food_log_writer 第一切片跨进程回归（历史记录，2026-08-14）

| 项目 | 结果 |
|---|---|
| `M15FoodLogWriterHttpE2ETest` | 通过：真实随机端口 HTTP、Ed25519 Service JWT、PostgreSQL 写入、匹配营养目录和 HTTP 重放不重复创建 |
| `M15FoodLogWriterProposalResultE2ETest` | 通过：真实 RocketMQ Proposal/Result、确认绑定、PostgreSQL 写入、资源 ID 回填和 Proposal 重放不重复创建 |
| 当日范围边界 | 截至该次执行只验证 `food_log.create` 第一切片；拒绝、失败、`superseded` 和其他写操作的完整跨进程验收见下方 2026-08-15 记录 |

## V2 营养单位换算 seed（本地当前复核，2026-08-15）

| 字段 | 内容 |
|---|---|
| 数据库 | `FoodMate` |
| 环境 | local，运行中的 Docker PostgreSQL 16 容器 `foodmate-postgres` |
| 脚本版本 | `seed/V2__nutrition_usda_portion_seed.sql` |
| 执行方式 | 历史人工 `psql` 执行；本轮只读复核，Java 启动不会自动执行 seed |
| 来源 | USDA FoodData Central `foodPortions`，规则保留 FDC ID 和 portion 序号 |
| 执行结果 | 当前库存在 5 条未删除 `approved` 规则：米饭、鸡胸肉、熟鸡蛋、三文鱼、苹果 |
| 校验脚本 | `validation/V2__nutrition_usda_portion_seed_validation.sql` |
| 校验结果 | 通过：5 条规则的目标单位均为 `g`，倍率和来源版本均符合校验；三文鱼 `3 oz=85 g` 已归一化为 `28.3333 g/oz` |
| 当前只读数据量 | `nutrition_foods=5`、`nutrition_unit_conversions=5`、`food_logs=95`、`food_log_items=128`、`approval_requests=114`、`runtime_tool_proposal_inbox=98`、`operation_audits=569` |
| 备份/回滚 | 当前开发阶段按用户决策暂不做数据库备份；seed 使用幂等 upsert，未执行回滚 |

> 历史执行人和精确执行时间未记录，本条不补写；上面的数据库数量是本轮只读复核快照。

## M1-5 写确认扩展与跨进程回归（2026-08-15）

| 项目 | 结果 |
|---|---|
| Java application | 已实现 `reject`、`failed`、`superseded` 状态；失败时业务执行事务回滚，独立事务写入 `failed` 状态和失败审计 |
| `food_log_writer` | 已支持 `create`、`update`、`delete`、`restore`，update/delete/restore 强制使用资源归属和 `revision` |
| Tool Gateway | 已校验 `proposal_type=tool` 与 `tool_name=food_log_writer`，并映射 confirmation_required、rejected、failed、superseded |
| Java 定向测试 | `ApprovalServiceImplTest` 和 `ToolGatewayServiceTest` 已覆盖新增分支；本轮通过临时本地 JUnit Launcher 实际执行合计 26 条 |
| HTTP 跨进程回归 | `M15FoodLogWriterHttpE2ETest` 通过 11/11：create 基线、rejected、failed 回滚与失败审计、superseded、update、delete、restore、revision 冲突、成功 Proposal 幂等重放、foodPortions 换算 matched 和无规则 pending |
| RocketMQ 跨进程回归 | `M15FoodLogWriterProposalResultE2ETest` 通过 11/11：同上场景；真实 Proposal/Result consumer 验证 Inbox `completed`、结果重放一致和单一完成事实 |
| 数据库与审计断言 | 覆盖资源归属、营养明细快照、换算 `conversion_id`/标准份量、revision 递增/不递增、软删除可见性、审批终态、`approval.failed`、`food_log.*` 业务幂等审计和无额外写入 |
| 数据隔离与范围 | 每个用例使用随机用户、Session、AgentRun、Proposal 和幂等键；未新增表、未执行迁移、未删除既有本地数据、未执行数据库备份 |
| 完整 Maven 验证 | 2026-08-15 最近一次 `mvnw.cmd verify` 已成功完成：6 个 Reactor 模块构建成功；Surefire 执行 221 条测试，0 失败、0 错误，48 条因 Docker/真实环境条件跳过；Spotless 全部通过 |

## V13 M1-5 饮食记录与营养目录（本轮已复核，未重复执行）

| 字段 | 内容 |
|---|---|
| 数据库 | `FoodMate` |
| 环境 | local，运行中的 Docker PostgreSQL 16 容器 `foodmate-postgres` |
| 脚本版本 | `V13__m1_5_food_log_nutrition_approval.sql` |
| 执行人 | 本轮未执行迁移；复核人为当前 Codex 会话 |
| 执行时间（UTC） | 未知；不补写历史执行时间 |
| 前置确认 | 当时只读查询：`food_logs=1`，因此不能按 V13 的空表前置条件重复执行；当前数据量见上方 V1 seed 记录 |
| 备份 | 当前开发阶段按用户决策暂不做数据库备份；正式生产流程后置 |
| 执行命令/客户端版本 | `docker exec foodmate-postgres psql`，PostgreSQL 16.14 |
| 执行结果 | 未重复执行；本轮只读校验确认五张表、字段、约束、索引存在 |
| 校验脚本 | `validation/V13__m1_5_food_log_nutrition_approval_validation.sql` |
| 校验结果 | 通过：V13 validation 查询确认表/字段/约束/索引存在，旧 `items_json`/`nutrition_json` 不存在；本轮当前只读复核为 `nutrition_foods=5`、`nutrition_unit_conversions=5`。seed 由上方 V1/V2 记录覆盖 |
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

## V15 M1-5 餐食计划生命周期（本轮已执行并校验）

| 字段 | 内容 |
|---|---|
| 数据库 | `FoodMate` |
| 环境 | local，运行中的 Docker PostgreSQL 16 容器 `foodmate-postgres` |
| 脚本版本 | `V15__m1_5_meal_plan_lifecycle.sql` |
| 执行人 | 当前 Codex 会话；未补写未单独记录的具体执行时间 |
| 前置确认 | 保留现有餐食计划数据后执行，未删除既有计划或购物清单 |
| 备份 | 当前开发阶段按用户决策暂不做数据库备份；正式生产流程后置 |
| 执行命令/客户端版本 | `docker exec foodmate-postgres psql`，PostgreSQL 16.14 |
| 执行结果 | 成功：新增 `meal_plans.idempotency_key`、`meal_plans.revision`、版本约束和两个索引；现有计划与购物清单保留 |
| 校验脚本 | `validation/V15__m1_5_meal_plan_lifecycle_validation.sql` |
| 校验结果 | 通过：字段、索引存在，`invalid_meal_plan_revisions=0` |
| 回滚结论 | 未执行；未运行回滚 SQL |

本轮另完成本地 Java HTTP 回归：创建幂等重放、计划查询/修改、stale `revision` 返回 409、校验/保存、购物清单聚合、修改后清单失效、软删除隐藏和恢复均通过。测试账号、计划和清单已在回归结束后清理。

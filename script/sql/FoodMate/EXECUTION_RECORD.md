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

## M1-6 统一审计与指标代码验证（2026-08-18）

| 字段 | 内容 |
|---|---|
| 环境 | 当前 Codex Windows 工作区；Docker Desktop 未运行，因此未执行真实 PostgreSQL/Redis/RocketMQ 流量或故障注入 |
| 实现 | 新增统一 `OperationAuditPort` PostgreSQL 适配器、失败独立事务审计、脱敏安全摘要、低基数 Java Micrometer 与 Python readiness RuntimeMetrics；饮食/餐食计划审计回放只保留资源摘要 |
| Java 定向验证 | `.\mvnw.cmd --% -pl foodmate-infra,foodmate-application -am -Dtest=OperationAuditServiceTest,FoodLogServiceImplTest,MealPlanServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test`：18 tests，0 failure/error |
| Python 定向验证 | 先前本轮已执行 `.\agent-runtime\.venv\Scripts\python.exe -m pytest agent-runtime\tests\test_eval_metrics.py agent-runtime\tests\test_mq_runtime.py agent-runtime\tests\test_runtime_server.py -q`：40 passed，1 warning |
| 流量/故障入口 | 已新增 `script/local/m1-6-traffic-recovery.ps1`；默认仅 readiness/Compose 预检，`-EnableFaultInjection` 才重启 Redis。当前未运行，未产生吞吐、延迟、队列或恢复时间数据 |
| 结论 | 审计与观测代码测试通过；共享 Redis/RocketMQ Agent 业务流量、PostgreSQL/Java/Python/RocketMQ 重启、ACK 丢失、重复投递与 SSE 恢复仍未执行，M1-6 整体保持未完成 |

## M2-3 管理后台真实接口与前端业务切片（2026-08-22）

| 项目 | 结果 |
|---|---|
| 环境 | Windows 本地工作区 `D:\develop\FoodMate`；未启动生产/staging，不执行数据库迁移、清库、备份恢复或性能测试 |
| 分支 | `codex/m2-functional-completion` |
| 后端范围 | 真实管理查询、用户状态/会话撤销、工具/知识库/回收站/审计操作、模型治理接口的分页、RBAC、revision、幂等、确认和审计契约已接入 |
| 前端范围 | real 模式用户管理、AgentRun/ToolCall/SQLAudit 分页查询、工具/知识库/审计/回收站/模型治理，以及概览真实运行查询和加载/空态/错误态 |
| 用户切片提交 | `1d0d875 feat(管理后台): 接入真实用户管理接口` |
| AgentRun 切片提交 | `af3b761 feat(管理后台): 接入 AgentRun 分页查询` |
| 概览切片提交 | `fa30227 fix(管理后台): 移除概览页真实模式伪造指标` |
| 前端验证 | `npm run typecheck` 通过；UsersTab 4/4、RunsTab 3/3、AdminPage 8/8 通过 |
| Java 验证 | `AdminUserControllerRbacTest`、`AdminManagementControllerTest` 共 4/4 通过；API/application 编译通过 |
| 失败记录 | 首次从 `foodmate-ui` 子目录执行仓库根路径 `git add`，路径不匹配且未提交；随后从仓库根目录按文件范围正确提交，未改变其他工作区文件 |
| 结论 | M2-3 管理后台核心业务切片已完成；M2-1 知识库真实跨运行时闭环、M2-2 Tool Gateway/SQL Agent、全量 `verify`/Docker 联调和生产强化仍未完成 |

## M2-1/M2-2 核心业务定向验证（2026-08-22）

| 项目 | 结果 |
|---|---|
| Java 命令 | `mvnw.cmd -pl foodmate-application -am -Dtest=KnowledgeServiceImplTest,KnowledgeOutboxPublisherTest,KnowledgeIndexResultMessageProcessorTest,ToolRegistryServiceTest,ToolPolicyGatewayServiceTest,ToolGatewayServiceTest,ToolGatewayAstGuardTest,SqlSchemaCatalogServiceTest,SqlQueryPlanValidatorTest,JSqlParserQueryGuardTest -Dsurefire.failIfNoSpecifiedTests=false test` |
| Java 结果 | 51/51 通过，0 failure/error；覆盖知识状态/Outbox/结果处理、工具 Registry/Policy、SQL Catalog/AST Guard 和计划校验 |
| Python 命令 | `agent-runtime/.venv/Scripts/python.exe -m pytest tests/test_knowledge_rag.py tests/test_knowledge_worker.py tests/test_sql_planner.py -q`（工作目录 `agent-runtime`） |
| Python 结果 | 21/21 通过；覆盖解析/分块、stub/local 检索与 SQL Planner 契约 |
| 环境边界 | 本轮未启动 Docker Milvus/PostgreSQL/RocketMQ，未调用付费 embedding/API Key，未执行跨进程数据库查询或知识引用 SSE 回归 |
| 结论 | M2-1/M2-2 核心代码和业务定向测试通过；真实本地依赖联调仍未完成，性能、重启、ACK、重复投递和生产验证继续暂缓 |

## M2-1/M2-2/D1 业务门禁复核（2026-08-22）

| 项目 | 结果 |
|---|---|
| 环境 | Windows 本地工作区 `D:\develop\FoodMate`；未启动 Docker、staging/production，不执行数据库迁移、清库、备份恢复或付费模型调用 |
| 分支与提交 | `codex/m2-functional-completion`；知识索引闭环格式修复 `b02e8b2`，其前置索引结果重试/检索/可见性提交为 `70b5fc9`、`ed32411`、`58c3d57` |
| Java 知识业务测试 | `mvnw.cmd --% -pl foodmate-application -am test -Dtest=KnowledgeIndexResultMessageProcessorTest,KnowledgeOutboxPublisherTest,KnowledgeUploadValidationTest,KnowledgeServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`：15/15 通过 |
| Java 全量业务测试 | `mvnw.cmd verify` 已完成 Shared 12/12、Application 125/125，0 failure/error；随后在 Application Spotless 阶段因用户未提交的 `OperationAuditService.java` import 顺序失败，未进入后续模块 |
| Python 业务测试 | `agent-runtime\.venv\Scripts\python.exe -m pytest`：92 passed、1 skipped、2 warnings；跳过项为真实云集成 |
| 前端业务门禁 | `npm.cmd run typecheck` 通过；`npm.cmd run build` 通过 |
| 已验证范围 | Java 索引结果校验/重试边界/Outbox、Python PDF/DOCX/Markdown/TXT 解析与 stub/local RAG、Redis 索引逻辑、可见性版本隔离、工具/SQL 业务契约和管理端核心查询/权限切片 |
| 未执行范围 | 真实 PostgreSQL/Redis/RocketMQ/Milvus 联调、上传 -> 索引 -> 发布 -> AgentRun -> SSE、SQL Agent 真实数据库联调、吞吐/延迟/积压统计、组件重启、ACK 丢失、重复投递、SSE Last-Event-ID 故障验证 |
| 数据与迁移 | 本轮未执行迁移、truncate、备份恢复或既有本地数据清理 |
| 结论 | M2-1/M2-2 核心代码与业务测试完成，M2-3 核心管理切片已有证据；真实依赖闭环和性能/故障/生产验证保持后置，不更新为整体完成 |

## M2-1 用户真实检索与餐食规划前端切片（2026-08-22）

| 项目 | 结果 |
|---|---|
| 环境 | Windows 本地工作区 `D:\develop\FoodMate`；未启动 Docker、staging/production，不执行数据库迁移、清库、备份恢复或付费模型调用 |
| 后端提交 | `3db1001 feat(计划): 增加用户计划列表查询`；新增 `GET /api/meal-plans`，按当前用户返回计划及软删除状态，application/infra/API 定向测试通过 |
| 前端提交 | `bff8bec feat(计划): 接入真实餐食规划页面`；real 模式读取 `/api/meal-plans`，列表按服务端状态筛选，详情使用服务端 `days_plan`/约束；fixture 模式保持不变 |
| 前端测试 | `npm test`：33 个测试文件、163 项通过；`npm run typecheck` 通过；`npm run build` 通过；目标文件 Prettier 检查通过 |
| Java 定向测试 | 计划 application/API 测试共 11 项通过：`MealPlanServiceImplTest` 7/7、`MealPlanControllerTest` 4/4 |
| 业务边界 | 本轮只完成用户计划列表/详情读取和页面 real 接入；规划创建向导写入、重新生成、购物清单真实查询仍未接入 |
| 未执行范围 | 知识库上传 -> 索引 -> 发布 -> AgentRun -> SSE 真实跨运行时闭环、Milvus/Redis/RocketMQ 联调、SQL Agent 真实数据库联调、吞吐/延迟/积压统计、组件重启、ACK 丢失、重复投递、SSE Last-Event-ID 故障验证 |
| 结论 | 餐食规划读取主路径具备代码和业务测试证据；M2-1 整体仍不标记完成，真实依赖、跨运行时和性能/故障验证继续后置 |

## M2-1 用户餐食规划 real 前端切片（2026-08-22）

| 项目 | 结果 |
|---|---|
| 环境 | Windows 本地工作区 `D:\develop\FoodMate`；未启动 Docker、staging/production，不执行数据库迁移、清库、备份恢复或付费模型调用 |
| 分支 | `codex/m2-functional-completion` |
| 功能提交 | `3db1001` 计划列表 API；`bff8bec` 真实列表/详情；`be75aba` 已保存计划购物清单；`97af296` 真实创建向导；`132cd40` 空计划用户进入真实创建向导 |
| 业务行为 | real 模式读取 `/api/meal-plans`；创建向导通过带 `Idempotency-Key` 的 `POST /api/meal-plans` 创建确定性本地餐表；详情展示服务端 `days_plan`/约束；saved 计划读取 `/api/meal-plans/{id}/shopping-list` |
| 前端验证 | `npm.cmd test`：33 个测试文件、165/165 通过；`npm.cmd run typecheck` 通过；`npm.cmd run build` 通过 |
| 测试重点 | 覆盖服务端计划展示、创建向导提交、空计划进入向导、购物清单读取；fixture 模式既有交互保持通过 |
| 业务边界 | 创建使用确定性本地餐表，不调用云模型；重新生成仍需通过 AgentRun；本轮未执行真实 Java HTTP 服务、Docker 依赖或知识库跨运行时索引/引用闭环 |
| 数据与迁移 | 未执行迁移、truncate、备份恢复或既有本地数据清理 |
| 结论 | 餐食规划用户 real 前端主路径具备业务测试证据；M2-1 知识库整体状态不变，真实依赖闭环与性能/故障验证继续后置 |

## M2-1 管理批次与聊天引用前端切片（2026-08-22）

| 项目 | 结果 |
|---|---|
| 环境 | Windows 本地工作区 `D:\develop\FoodMate`；未启动 Docker、staging/production，不调用付费模型或真实 embedding，不执行数据库迁移 |
| 分支 | `codex/m2-functional-completion` |
| 功能提交 | `2aeda60` 聊天知识库引用改为可展开控件；`b744858` 管理端批次上传后的进度状态、失败重试反馈与 real 业务测试 |
| 管理端行为 | real 模式批量上传携带来源/版本/授权和 `Idempotency-Key`；读取批次详情/SSE；索引失败条目显示错误码，重试期间禁用按钮，重试失败显示告警，成功后刷新条目状态和文档列表 |
| 聊天行为 | `run.completed.citations` 继续由 SSE 注入运行轨迹，引用标题、版本/章节和安全片段通过可展开引用块展示，不显示对象存储地址 |
| 前端验证 | `npm.cmd test`：35 个测试文件、167/167 通过；`npm.cmd run typecheck` 通过；`npm.cmd run build` 通过 |
| 业务边界 | 本轮验证的是前端 API 契约与状态行为；未执行真实 Java/Python/RocketMQ/Milvus 上传 -> 索引 -> 发布 -> AgentRun 闭环，性能、重启、ACK 和重复投递测试继续暂缓 |
| 数据与迁移 | 未执行迁移、truncate、备份恢复或既有本地数据清理 |
| 结论 | K4 管理端批次和聊天引用前端主路径已有业务测试证据；M2-1 整体仍不更新为真实跨运行时完成 |

## M2 业务门禁最终复核（2026-08-22）

| 项目 | 结果 |
|---|---|
| Python 命令 | `agent-runtime\.venv\Scripts\python.exe -m pytest -q` |
| Python 结果 | 92 passed、1 skipped、2 warnings；跳过项为真实云集成，未调用付费模型或真实 embedding |
| Java 命令 | `mvnw.cmd -pl foodmate-application -am test -Dtest=KnowledgeIndexResultMessageProcessorTest,KnowledgeOutboxPublisherTest,KnowledgeUploadValidationTest,KnowledgeServiceImplTest,ToolRegistryServiceTest,ToolPolicyGatewayServiceTest,ToolGatewayServiceTest,ToolGatewayAstGuardTest,SqlSchemaCatalogServiceTest,SqlQueryPlanValidatorTest,JSqlParserQueryGuardTest -Dsurefire.failIfNoSpecifiedTests=false` |
| Java 结果 | 56/56 通过，0 failure/error/skipped；覆盖知识索引结果/Outbox/上传校验、Tool Registry/Policy/Gateway、SQL Catalog/AST Guard/计划校验 |
| 前端结果 | 本轮最终 `npm.cmd test` 为 35 个测试文件、167/167 通过；`npm.cmd run typecheck` 和 `npm.cmd run build` 通过 |
| 未执行范围 | Docker、Milvus、真实 PostgreSQL/Redis/RocketMQ 跨运行时闭环、吞吐/延迟/积压、组件重启、ACK 丢失、重复投递和 SSE Last-Event-ID 故障验证 |
| 结论 | M2 核心业务代码和业务门禁通过；真实依赖联调、性能和故障恢复不作为当前完成证据，保持后置 |

## M2-3 受控脱敏运营导出与 Java 格式基线（2026-08-22）

| 项目 | 结果 |
|---|---|
| 环境 | Windows 本地工作区 `D:\develop\FoodMate`；未执行数据库迁移、清库、备份恢复或真实模型/Embedding 调用 |
| 分支与提交 | `codex/m2-remaining-business`；`78302d5 style(java): 统一后台模块 Java 格式`；`3d2959b feat(admin): 增加受控脱敏运营导出` |
| 后端范围 | 新增 V23 管理导出任务表、application 导出白名单/角色/幂等/任务处理、私有对象存储下载和统一审计；最大 100 条，仅输出安全运营摘要 |
| API 范围 | `POST /api/admin/exports`、`GET /api/admin/exports/{id}`、`POST /api/admin/exports/{id}/download`；admin 禁止 users/deleted 资源，superadmin 才可导出全安全资源 |
| 前端范围 | 操作审计 real 页面增加当前结果导出、任务状态查询和一次性 JSON 下载；fixture 模式不调用真实导出接口 |
| Java 业务验证 | `mvnw.cmd -pl foodmate-api -am -Dtest=AdminExportServiceTest,AdminExportControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`：7/7 通过 |
| Java 编译/格式 | API、application、infra 编译通过；受影响模块 Spotless 通过；同时修复后台基线 11 个 Java 文件格式 |
| 前端验证 | `npm.cmd run typecheck` 通过；`npm.cmd run build` 通过 |
| SQL 状态 | 已新增 `V23__m2_3_admin_export_jobs.sql`、validation 和 rollback 前置检查；本轮未执行迁移，未改变本地数据 |
| 环境边界 | 未启动 Docker/Milvus；未执行真实对象存储下载、跨运行时索引、性能压测、组件重启或故障注入 |
| 结论 | 管理端脱敏运营导出业务代码和定向门禁通过；M2 总体和真实依赖闭环状态不变 |

## M2-1 本地 deterministic embedding 与 Milvus 业务路径（2026-08-22）

| 项目 | 结果 |
|---|---|
| 环境 | Windows 本地工作区 `D:\develop\FoodMate`；仅启动 Compose 的 `milvus`、`milvus-etcd`、`milvus-minio`；未启动 Java、PostgreSQL、Redis、RocketMQ，不执行迁移、清库、备份恢复或付费模型调用 |
| 配置 | `FOODMATE_RAG_MODE=local`、`FOODMATE_RAG_EMBEDDING_PROVIDER=deterministic`、16 维向量、隔离集合 `foodmate_knowledge_codex_local_20260822` |
| Python 业务测试 | `.\agent-runtime\.venv\Scripts\python.exe -m pytest -q`：99 passed、1 skipped、2 warnings；知识 RAG/Worker 定向测试 28 passed |
| Docker 静态检查 | `docker compose --env-file .env -f docker/compose.yml config --quiet`：通过 |
| 首次联调失败 | Milvus 2.5.5 创建字符串主键时要求 `max_length`；同时 Compose 使用了该镜像不识别的 `MINIO_ACCESS_KEY`/`MINIO_SECRET_KEY`，导致 MinIO 认证失败和 Milvus 退出 |
| 修复 | Milvus collection 创建增加 `max_length=128`；改用 `MINIO_ACCESS_KEY_ID`/`MINIO_SECRET_ACCESS_KEY` 传递 Compose 凭据 |
| 实际业务结果 | deterministic 向量生成、按实际维度建集合、chunk upsert、发布 metadata、带 ACL 过滤检索均通过；返回标题 `Local RAG Guide`、版本 `v1`、章节 `Recovery`、chunk `emb_local_1` |
| 数据处理 | 测试集合为本轮专用命名空间，验证后删除该集合；未删除任何既有业务集合或命名卷；容器已停止但卷保留 |
| 未执行范围 | 真实 embedding API、Java -> RocketMQ -> Python 跨运行时上传闭环、性能压测、组件重启、ACK 丢失、重复投递、SSE Last-Event-ID 故障验证继续暂缓 |
| 结论 | local deterministic + Milvus 业务适配和 Docker 依赖路径具备本地证据；真实 provider 仍需显式配置后单独验证，M2-1 整体不据此标记完成 |

## M3 可审计人工 DLQ 重放 Outbox（2026-08-23）

| 项目 | 结果 |
|---|---|
| 执行时间 | 2026-08-23 00:43-00:47 (Asia/Shanghai) |
| 环境 | Windows 本地工作区 `D:\develop\FoodMate`；Java 21；未启动 Java、PostgreSQL、Redis、RocketMQ；未执行迁移、清库、备份恢复或消息重放 |
| 功能提交 | `1cad651 feat(dlq): 增加可审计的人工重放 Outbox` |
| 数据变更 | 新增 `V24__m3_dlq_replay.sql`、validation、rollback；增加 `raw_payload_text` 和 `runtime_dlq_replay_outbox`；本轮未运行迁移 |
| API | `POST /api/admin/dlq/{dlqId}/replay`；仅 superadmin，必须确认摘要和幂等键；响应不返回 payload |
| Relay | 仅 `foodmate.runtime.transport=rocketmq` 时发布；保留原消息身份属性，记录新的 Broker message ID；发布确认后才收敛原 DLQ |
| 安全边界 | 原始 payload 只保存在受限 replay Outbox；不进入审计 metadata、管理查询 DTO 或 API 响应；Topic/Group 必须匹配配置 |
| Java 验证 | `mvnw.cmd -pl foodmate-application,foodmate-api,foodmate-infra -am test -Dtest=RuntimeDlqReplayServiceImplTest,RuntimeDlqReplayPublisherTest,AdminDlqReplayControllerTest,FlywayV24MigrationScriptTest -Dsurefire.failIfNoSpecifiedTests=false`：10/10 通过 |
| 格式验证 | `mvnw.cmd -pl foodmate-application,foodmate-api,foodmate-infra -am spotless:check`：通过 |
| 未执行范围 | 未连接 RocketMQ 实际发布/消费，未执行重试耗尽、真实重放后的业务副作用对账、性能压测、组件重启、ACK/重复投递和 SSE 故障验证 |
| 结论 | 人工重放 Outbox 的权限、确认、幂等、失败关闭、发布属性和迁移契约具备代码及业务测试证据；不计为真实消息重放完成 |

## M3 前置：DLQ 安全摘要运营可见性（2026-08-23）

| 项目 | 结果 |
|---|---|
| 环境 | Windows 本地工作区 `D:\develop\FoodMate`；未启动 Java、PostgreSQL、Redis、RocketMQ，不执行迁移、清库、备份恢复或消息重放 |
| 分支与提交 | `codex/m2-remaining-business`；`9d4cea9 feat(admin): 增加死信摘要查询`；`f76fbcf feat(admin-ui): 接入死信摘要治理视图` |
| 后端范围 | 管理查询资源 `GET /api/admin/queries/dlq`；支持关键词、对账状态、排序和分页；复用既有 `runtime_message_dlq` 表，不新增迁移 |
| 安全范围 | DTO/SQL/API/UI 只返回消息身份、来源、关联标识、attempt/reconsume 次数、稳定错误码、对账状态和时间；不返回 `raw_payload_json`、`last_error` |
| Java 验证 | `mvnw.cmd -pl foodmate-application,foodmate-api -am test -Dtest=AdminOperationalQueryServiceImplTest,AdminOperationalQueryControllerTest -Dsurefire.failIfNoSpecifiedTests=false`：应用 4/4、API 3/3 通过；infra `FlywayV6MigrationScriptTest` 3/3 通过；受影响模块 Spotless 通过 |
| 前端验证 | `npm.cmd run typecheck` 通过；`npm.cmd run test -- --run src/pages/AdminPage/tabs/RunsTab.test.tsx src/pages/AdminPage/tabs/RunsTab.real.test.tsx`：4/4 通过；`npm.cmd run build` 通过；全量 `format:check` 仍受仓库既有 77 个未格式化文件阻塞，未执行整库格式化 |
| 未执行范围 | 人工重放、Run 终态改写、死信删除、真实 RocketMQ 对账、性能/故障矩阵和生产验证 |
| 结论 | 仅完成 DLQ 安全摘要的运营可见性；不将其计为人工重放、完整死信处理或 M3 完成证据 |

## M3 前置：运营审计只读报告（2026-08-23）

| 项目 | 结果 |
|---|---|
| 执行时间（本地） | 2026-08-23T00:24:36+08:00 |
| 环境 | Windows 本地工作区；未启动 Java、PostgreSQL、Redis、RocketMQ；未执行迁移、truncate、备份恢复或消息重放 |
| 功能提交 | `850ba8f feat(audit): 增加运营审计只读报告` |
| API | `GET /api/admin/audit-reports/current`；只读返回审计、Outbox、知识索引、DLQ 的聚合计数、最早时间和稳定原因码 |
| 安全校验 | infra Mapper 契约测试确认查询不包含 `request_json`、`response_json`、`raw_payload_json` 或 `last_error`；普通用户 API 鉴权返回 `FORBIDDEN` |
| Java 验证 | application 3/3、API 2/2、infra 1/1；受影响模块编译通过；Spotless 检查通过 |
| 失败/阻塞 | 首次并行 Maven 定向测试因未使用 `-am` 和 `target` 并发产生既有依赖编译/测试选择错误；改为串行 reactor 命令后通过，未修改业务代码 |
| 数据影响 | 仅新增代码和测试，未连接目标数据库、未写入或清理本地业务数据 |
| 结论 | 报告第一切片的代码/业务测试证据成立；实时数据库结果、历史归档和生产告警仍未验证 |

## M3 数据保留治理与外部清理任务（2026-08-23）

| 项目 | 结果 |
|---|---|
| 执行时间 | 2026-08-23 01:19-01:29 (Asia/Shanghai) |
| 环境 | Windows 本地工作区 `D:\develop\FoodMate`；Java 21；使用 `agent-runtime\.venv`；未启动 Java、PostgreSQL、Redis、RocketMQ、对象存储或 Milvus |
| 功能提交 | `68bba07`/`67c3b18` 保留策略、冻结和审批；`1939f92` Python 清理 Worker；`f98d8c8` Java 清理 Relay、结果消费者和 active hold 原子领取 |
| Java 代码范围 | `DataRetentionTaskPublisher` 对象存储受限删除和向量 Topic 投递；`DataRetentionResultMessageProcessor` 消费 `foodmate-knowledge-purge-result-v1`；任务成功/失败/重试和申请状态回写；数据库任务明确保持 pending |
| Python 代码范围 | stub/Redis/Milvus 按 `document_id + version` 删除；`task_id + mode` 完成事实；重复清理不重复产生副作用；index、visibility、purge 三个 Worker consumer 独立发布通道 |
| Python 命令 | 在 `agent-runtime` 执行 `\.venv\Scripts\python.exe -m pytest -q` |
| Python 结果 | `107 passed、1 skipped、1 warning`；跳过项为真实云集成，未调用付费模型或 embedding |
| Java 命令 | `mvnw.cmd -pl foodmate-application,foodmate-infra -am test -Dtest=DataRetentionTaskPublisherTest,DataRetentionResultMessageProcessorTest,DataRetentionDeliveryServiceImplTest,DataRetentionServiceImplTest,FlywayV25MigrationScriptTest -Dsurefire.failIfNoSpecifiedTests=false` |
| Java 结果 | application 保留测试 `15/15`、infra V25 migration 结构测试 `2/2` 通过；编译通过 |
| 格式验证 | `mvnw.cmd -pl foodmate-application,foodmate-infra -am spotless:check` 通过 |
| 安全/数据边界 | `hard_delete_enabled=false` 默认关闭；active legal hold 在任务领取 SQL 中原子阻断；未执行迁移、truncate、对象/向量实际删除、数据库硬删除或现有数据清理 |
| 未执行范围 | 未做真实 RocketMQ 发布/消费、对象存储和 Milvus 联调、性能压测、组件重启、ACK 丢失、重复投递、SSE Last-Event-ID 故障验证；这些按当前决策暂缓 |
| 结论 | 保留治理和清理任务业务代码及定向测试证据成立；真实依赖执行、实际删除和 M3 整体完成状态保持后置 |

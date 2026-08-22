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

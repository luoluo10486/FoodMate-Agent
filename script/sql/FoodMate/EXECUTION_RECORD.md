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

## M3/M2-1 本地依赖与 deterministic RAG 业务核验（2026-08-23）

| 项目 | 结果 |
|---|---|
| 执行时间 | 2026-08-23 01:39-01:48 (Asia/Shanghai) |
| 环境 | Windows 本地工作区 `D:\develop\FoodMate`；Docker Desktop 28.5.1；16 CPU；约 7.4 GiB Docker 内存；Java 21；Python 使用 `agent-runtime\.venv`；未配置真实 embedding API Key、未调用云模型 |
| Docker 启动 | `docker compose --env-file .env -f docker/compose.yml up -d postgres redis minio`；随后分组启动 RocketMQ 和 Milvus 依赖 |
| Docker readiness | PostgreSQL、Redis、MinIO、RocketMQ NameServer、Broker、Proxy、Milvus、Milvus etcd 和 Milvus MinIO 均 healthy；未执行 `down -v`，命名卷保留 |
| RocketMQ 初始化 | `rocketmq-init` 退出码 0；日志确认创建 `foodmate-knowledge-purge-v1`、`foodmate-knowledge-purge-result-v1` 以及 `foodmate-python-knowledge-purge-v1`、`foodmate-java-knowledge-purge-result-v1`；其余 Agent/Knowledge Topic/group 也完成初始化 |
| Compose 校验 | `docker compose --env-file .env -f docker/compose.yml config --quiet` 通过；`init-topics.sh` shell 语法校验通过 |
| Python 业务回归 | `agent-runtime\.venv\Scripts\python.exe -m pytest -q`：107 passed、1 skipped、1 warning；跳过项为显式真实云集成 |
| Milvus 业务核验 | 使用随机隔离集合和 `local + deterministic`：实际向量写入、`published` metadata 更新、`public_published` ACL 检索和引用返回通过；随后删除本轮集合，不删除既有集合或命名卷 |
| Java 业务回归 | `mvnw.cmd -pl foodmate-application,foodmate-infra -am test -Dtest=KnowledgeServiceImplTest,KnowledgeOutboxPublisherTest,KnowledgeIndexResultMessageProcessorTest,KnowledgeSearchServiceImplTest,DataRetentionTaskPublisherTest,DataRetentionResultMessageProcessorTest,DataRetentionDeliveryServiceImplTest,DataRetentionServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`：29/29 通过 |
| Java 格式 | `mvnw.cmd -pl foodmate-application,foodmate-infra -am spotless:check`：通过 |
| 数据边界 | PostgreSQL 仅做只读 schema 检查；未执行 Flyway/手工迁移、truncate、备份恢复、对象/向量实际保留清理或消息故障注入 |
| 未完成范围 | Java/Python 应用未纳入 Compose，未完成管理员上传 -> Java Outbox -> RocketMQ -> Python Worker -> Java 回写 -> 发布 -> AgentRun/SSE 的真实跨运行时闭环；吞吐、性能、重启、ACK 丢失、重复投递和 Last-Event-ID 故障验证按当前决策暂缓 |
| 结论 | 本轮证明本地依赖 readiness、RocketMQ 清理契约、Python 业务回归、Milvus deterministic 适配和 Java 保留/知识定向业务测试通过；不将 M2-1/M3 整体标记为完成 |

## RocketMQ 业务 E2E 前置核验（2026-08-23）

| 项目 | 结果 |
|---|---|
| 执行时间 | 2026-08-23 01:52 (Asia/Shanghai) |
| 命令 | `mvnw.cmd -pl foodmate-bootstrap -am -Dfoodmate.local-mq-e2e=true -Dtest=M14RocketMqTransportE2ETest,M14ProposalResultE2ETest -Dsurefire.failIfNoSpecifiedTests=false test` |
| RocketMQ Outbox 主链路 | `M14RocketMqTransportE2ETest`：1/1 通过；Java Outbox 消息真实到达 Broker 自测消费组，Envelope、request_hash、dispatch_id、消息属性和 published 状态断言通过 |
| Proposal/Result 链路 | `M14ProposalResultE2ETest`：0/2 通过；不是性能或消息传输结论，测试上下文受到数据库前置缺失影响 |
| 真实阻塞证据 | 当前 FoodMate PostgreSQL 未执行 V17/V18/V23/V24/V25 手工 SQL；应用启动后的定时任务查询 `knowledge_index_outbox`、`admin_export_jobs`、`runtime_dlq_replay_outbox` 等不存在表，Tool Registry/SQL Agent 所需业务数据也不完整 |
| 数据边界 | 测试使用随机账号/Run；本轮未执行迁移、truncate、删除、备份恢复或其他故障注入 |
| 处理结论 | 保留 `M14RocketMqTransportE2ETest` 真实通过证据；Proposal/Result 只记录为 schema 前置阻塞，不修改生产代码绕过，也不把该 E2E 记为通过 |

## M2-1 索引闭环本地业务核验（2026-08-23）

| 项目 | 结果 |
|---|---|
| 执行时间 | 2026-08-23（本地业务联调轮次） |
| 环境 | Windows 本地工作区 `D:\develop\FoodMate`；Java 21；Python 使用 `agent-runtime\.venv`；Docker Desktop 本地依赖；未调用付费模型或真实 embedding API |
| 迁移 | 实际执行 V16-V25 增量 SQL，全部成功；未执行 truncate、回滚、备份恢复；保留既有本地数据 |
| 应用配置 | Java 与 Python 显式使用 `deterministic:local`；RAG 业务路径使用本地 deterministic 模式；Compose 运行时地址固定为 `http://agent-runtime:9000` |
| Java 验证 | 知识导入、索引 Outbox、结果消费、批次状态、发布/可见性、用户检索及保留治理定向测试通过；知识与保留相关定向测试合计 29/29 通过 |
| Python 验证 | `agent-runtime\\.venv\\Scripts\\python.exe -m pytest -q`：107 passed、1 skipped、1 warning；跳过项为显式真实云集成 |
| Docker 校验 | `docker compose --env-file .env -f docker/compose.yml config --quiet` 通过；PostgreSQL、Redis、MinIO、RocketMQ、Milvus 依赖 readiness 已验证 |
| 跨运行时业务结果 | Java Outbox -> RocketMQ -> Python Worker -> MinIO 读取 -> Java 索引结果回写成功；首次 MinIO 凭据错误产生 `RAG_OBJECT_UNAVAILABLE`，管理员重试后成功；批次最终为 `completed`；文档发布后可见性同步和 Java 用户检索成功；deterministic AgentRun 完成并通过 SSE 返回 2 条安全 citations |
| 测试数据 | 使用随机用户、批次、文档、条目和隔离 RAG 命名空间；本轮测试生成的数据在收尾阶段清理；不删除既有业务数据、命名卷或既有 Milvus 集合 |
| 未执行范围 | Docker 应用镜像因 Docker Hub 网络阻塞未完成真实构建/启动证据；真实云模型/embedding、吞吐与性能压测、Java/Python/数据库/Redis/RocketMQ 重启、ACK 丢失、重复投递故障矩阵和 SSE Last-Event-ID 故障验证继续暂缓 |
| 结论 | M2-1 deterministic 本地业务闭环具备代码、业务测试和依赖联调证据；不将需要真实 Docker 应用镜像或性能/故障证据的范围标记为完成 |

## M2-1 收尾门禁与测试数据清理（2026-08-23）

| 项目 | 结果 |
|---|---|
| Java 门禁 | `\.\mvnw.cmd verify` 最终通过；Shared 12/12、Application 155/155、Infrastructure 68/68（11 skipped）、API 58/58、Bootstrap 57/57（37 skipped），Spotless 和 Spring Boot repackage 均通过 |
| Python 门禁 | `agent-runtime\\.venv\\Scripts\\python.exe -m pytest -q`：107 passed、1 skipped、2 warnings；未调用付费模型或真实 embedding |
| Compose 门禁 | `docker compose --env-file .env -f docker/compose.yml config --quiet` 通过；本地 PostgreSQL、Redis、MinIO、RocketMQ、Milvus 依赖保持 healthy |
| 门禁修复 | `FoodMateApplicationTest` 暴露 `local-stub` 缺少 `AdminAuditReportRepository` 替身；补齐零数据 stub 后启动上下文通过，提交 `2680b46 fix(local-stub): 补齐运营审计报告替身` |
| PostgreSQL 清理 | 仅清理本轮 3 个随机用户、2 个 Session、2 个 AgentRun、4 条消息、知识批次/条目/文档及其 Outbox/Inbox/SSE/审计事实；事务提交后用户、文档、批次、Run、Session、审计残留均为 0 |
| MinIO 清理 | 精确删除 `foodmate-private/knowledge/public/349616464812052480/guide.md`；删除后 `mc stat` 确认对象不存在 |
| Redis 清理 | 删除本轮 6 个 stub chunk hash 条目、索引完成事实和 4 个 Agent checkpoint key；未删除日期级预算统计或其他隔离空间 |
| Git 边界 | 当前分支 `codex/m2-remaining-business`；本轮提交 `44130fe`、`1b423a5`、`2680b46`；既有 UI/QA 改动保留未提交 |
| 未执行范围 | Docker Java/Python 应用镜像因 Docker Hub 网络阻塞未完成真实构建/启动；性能压测、组件重启、ACK 丢失、重复投递故障矩阵、SSE Last-Event-ID 专项验证、真实云模型/Embedding 和生产环境范围继续暂缓 |

## M2-1 deterministic 跨运行时业务闭环补充（2026-08-23）

| 项目 | 结果 |
|---|---|
| 执行时间 | 2026-08-23 03:02-03:24 (Asia/Shanghai) |
| 分支 | `codex/m2-remaining-business`；功能提交 `d2eac6e`、`e3f6b3f`、`de60de2`；用户既有 UI/QA 和 `tmp/` 改动未暂存 |
| 环境 | Windows；Java 21 宿主进程 `18080`；Python 使用 `agent-runtime\\.venv` 宿主进程 `19000`；Docker PostgreSQL、Redis、RocketMQ NameServer/Broker/Proxy、MinIO 均已就绪；Python 使用 Redis checkpoint、RocketMQ 和知识 Worker |
| 上传入口 | 首次使用非法 `source_type=external_import` 被拒绝为 `KNOWLEDGE_SOURCE_UNAUTHORIZED`；合法 `admin_upload` PDF 请求先暴露宿主 multipart 默认 1 MiB 限制，补齐 20 MiB 单文件/420 MiB 请求上限后上传成功 |
| 索引失败与重试 | PDF 首次因宿主 Worker 未注入 MinIO 凭据收敛为 `RAG_OBJECT_UNAVAILABLE`，Java 自动重试至 3 次；管理员重试后该 PDF 被安全解析器拒绝为 `RAG_PDF_UNSAFE` 并再次收敛。新增空分块失败关闭，避免空索引回报成功 |
| 成功索引链路 | 随机 Markdown 批次 `349632053559431168`：Java Outbox -> RocketMQ -> Python MinIO 读取 -> Redis stub index -> `foodmate-knowledge-index-result-v1` -> Java 权威回写，批次 `completed`、条目 `indexed`、attempt `1`；发布后 visibility Outbox 已发布，Redis metadata 为 `published/indexed/current_version` |
| 中文检索 | 修复中文二字片段检索；普通用户查询“低盐饮食 钠含量”返回 1 条安全引用，包含标题、版本、章节、chunk ID 和片段，不含对象键/地址 |
| AgentRun | deterministic 运行 `349633092236873728` 真实完成；SSE 事件序号 `1..6` 连续，Composer `provider_code=deterministic`、Eval `DETERMINISTIC_RULES_PASSED`、`run.completed` 返回 1 条 citations，成本为 `0` |
| 可见性业务 | 同一文档下线后普通用户检索 0 条；恢复只回到 `draft`，检索仍为 0；删除后 PostgreSQL 为 `indexed|deleted|true`，删除 visibility Outbox 已发布 |
| 清理 | 精确清理本轮 2 个随机用户、2 个 Session、2 个 AgentRun、4 条消息、3 个批次/条目/文档及其 Outbox/Inbox/SSE/审计事实；MinIO 删除 3 个本轮对象；Redis 删除隔离 chunks 和 2 个 Worker 完成事实；SQL 复核 users/jobs/docs/runs/sessions 均为 0；未删除日期预算键、既有数据、命名卷或既有 Milvus 集合 |
| 重要纠正 | 第一条 AgentRun 因启动命令错误继承 `.env` 云模型路由，实际产生了 1 次云 Composer 和 1 次云 Judge 请求；该结果不计入 deterministic 证据。随后已重启 Python 并显式锁定全部模型 tier 为 `deterministic:local`，后续有效 AgentRun 未调用云模型。 |
| 未执行范围 | Docker Java/Python 应用镜像因 Docker Hub 网络阻塞未完成构建/启动；真实 embedding API、吞吐/延迟/积压压测、Java/Python/PostgreSQL/Redis/RocketMQ 重启、ACK 丢失、重复投递和 SSE `Last-Event-ID` 专项验证继续暂缓 |
| 结论 | M2-1 deterministic 本地业务闭环的真实上传、索引、发布、检索、AgentRun 引用、下线/恢复业务证据成立；生产强化和用户明确暂缓的测试不计入完成门槛 |

## M2-1 Chat SSE V1 路由收尾（2026-08-23）

| 项目 | 结果 |
|---|---|
| 执行时间 | 2026-08-23 04:28-04:35（Asia/Shanghai） |
| 分支与提交 | `codex/m2-remaining-business`；`c37a2fc 修复(运行时): 统一V1聊天SSE回放入口` |
| 代码变更 | `/api/chat/runs/{runId}/stream` 对已存在的数值型 V1 Run 分流到持久化 `V1RuntimeEventService`；保留旧字符串 Run 的 RuntimeGateway 内存订阅路径；新增 `RunStreamControllerTest` |
| Java 定向验证 | `mvnw.cmd -pl foodmate-api -am test -Dtest=RunStreamControllerTest,ChatControllerTest -Dsurefire.failIfNoSpecifiedTests=false`：3/3 通过；`mvnw.cmd -pl foodmate-bootstrap -am package -DskipTests`：构建通过 |
| 真实业务验证 | 本地随机账号创建 deterministic AgentRun `349652008543719424`，最终状态 `completed`；请求 `/api/chat/runs/349652008543719424/stream` 携带 `Last-Event-ID: 5`，成功回放 `run.completed`，返回稳定 `sse_*` 事件 ID；Run 事件总数为 6，未出现 `runId does not exist`、重复终态或 SSE 缺口 |
| 运行环境 | Windows、Java 21、宿主 Java `18080`、宿主 Python Runtime `19000`、Docker PostgreSQL/Redis/RocketMQ 依赖；模型 tier 固定 `deterministic:local`，未调用付费模型或真实 embedding API |
| 清理 | 精确删除本轮用户 `349652007885213696`、Session `349652008514359296`、Run `349652008543719424`、消息 `349652008778600448` 及关联 Inbox/Outbox/SSE/审计；Redis checkpoint 2 个键删除；SQL 复核 users/sessions/runs/messages/audits 均为 0；删除本轮 Python 启动脚本和日志 |
| 未执行范围 | 吞吐/延迟/积压压测、Java/Python/PostgreSQL/Redis/RocketMQ 重启、ACK 丢失、重复投递故障矩阵、真实云模型/Embedding、Docker 应用镜像和生产环境继续暂缓 |
| 结论 | Chat 兼容入口现在能够复用 V1 持久化 SSE 回放服务；本轮只补齐业务正确性证据，不将 Last-Event-ID 业务回放扩大解释为故障恢复矩阵完成 |

## M2-2 database_query 多轮 AgentRun 业务收尾（2026-08-23）

| 项目 | 结果 |
|---|---|
| 执行时间 | 2026-08-23 04:48-05:20（Asia/Shanghai） |
| 代码变更 | 修复带 `sql_audit_id` 的 `time_parser` 结果误判为 `database_query` 已完成；数据库 Proposal 的回退 `invocation_id` 纳入 `run_id`，避免跨 Run 复用 `proposal_id`；多轮工具执行的 `run.checkpoint_saved` 事件 ID 纳入事件序号，避免 Java Inbox 去重阻断后续事件 |
| Python 定向验证 | `agent-runtime\\.venv\\Scripts\\python.exe -m pytest tests/test_runtime_server.py -q`：41 passed；新增跨 Run Proposal 唯一性和多轮事件 ID 唯一性回归断言 |
| Python 全量验证 | `agent-runtime\\.venv\\Scripts\\python.exe -m pytest -q`：113 passed、1 skipped、1 warning；跳过项为显式真实云集成，未调用付费模型或真实 embedding |
| 真实业务验证 | 随机用户创建 AgentRun `349662250480439296`，通过 Java `18080` -> RocketMQ -> Python `19000` -> Java 结果回写完成；`time_parser` 与 `database_query` Proposal 均为 `succeeded`，生成 2 条 `sql_query_audits` 且状态均为 `executed`（行数 1、0），Run 事件 `1..14` 连续，最终 `status=completed`、`result_type=normal` |
| 运行态 | Python readiness HTTP 200；Redis checkpoint、RocketMQ command/result consumer 和 Java Outbox/Inbox 均可用；最终恢复 Python 标准 consumer group，删除本轮临时 consumer group/retry Topic |
| 清理 | 精确清理本轮 10 个 `codex_sql_*` 用户、7 个 AgentRun、Session、消息、SQL/运行时/统一审计及 Outbox/Inbox；PostgreSQL 用户、Run、Session、审计、SQL 审计和运行时 Inbox/Outbox 复核均为 0；Redis 本轮 checkpoint、command/result Inbox 复核无残留；临时脚本和日志已删除 |
| 失败记录 | 修复前 4 个 Run 因跨 Run Proposal ID 冲突或重复 checkpoint ID 卡在工具等待并最终失败；另有 2 个 Run 受本地旧 RocketMQ consumer group 位点干扰；均已纳入本轮清理，不修改既有业务数据 |
| 未执行范围 | 吞吐/延迟/积压压测、Java/Python/PostgreSQL/Redis/RocketMQ 重启、ACK 丢失、重复投递故障矩阵、SSE `Last-Event-ID` 故障恢复、真实云模型/Embedding、Docker 应用镜像和生产环境继续暂缓 |
| 结论 | M2-2 结构化分析的真实业务主路径已补齐多轮工具、SQL 审计、事件连续性和 AgentRun 终态证据；本轮不据此扩大性能或故障恢复范围 |

## M3 受控数据库清理执行收尾（2026-08-23）

| 项目 | 结果 |
|---|---|
| 执行时间 | 2026-08-23 05:00-05:30（Asia/Shanghai） |
| 功能提交 | `c866460 feat(retention): 完成受控数据库清理执行` |
| 代码范围 | 增加受控数据库清理 Port/Adapter；知识文档子表按依赖顺序删除，限定 `is_deleted=TRUE`；对象存储和向量索引任务完成后才允许数据库任务领取；修复清理请求收敛到 `completed` 的 SQL。 |
| 安全边界 | `hard_delete_enabled=false` 默认关闭；本轮未执行迁移、truncate、宽泛删除、真实硬删除或备份恢复；未触碰现有本地业务数据。 |
| Java 验证 | Retention Application 定向测试 `13/13`；Retention Infrastructure/V25 定向测试 `5/5`；受影响模块 Spotless 通过。 |
| 结论 | 受控清理业务代码、状态收敛和依赖顺序具备测试证据；真实硬删除和生产删除演练继续后置。 |

## D1 全量业务门禁复核（2026-08-23）

| 项目 | 结果 |
|---|---|
| Java 命令 | `.\mvnw.cmd clean verify` |
| Java 结果 | BUILD SUCCESS；Shared `12/12`、Application `155/155`、Infrastructure `71/71`（11 skipped）、API `59/59`、Bootstrap `58/58`（37 skipped）；Spotless 和 Spring Boot repackage 通过。 |
| Python 命令 | `agent-runtime\\.venv\\Scripts\\python.exe -m pytest -q` |
| Python 结果 | `113 passed、1 skipped、1 warning`；跳过项为显式真实云集成，未调用付费模型或 embedding。 |
| 前端结果 | 36 个测试文件、`170 passed`；`npm.cmd run typecheck` 通过。 |
| 失败记录 | 同日首次 Maven 运行因本轮宿主 Java `18080` 进程占用 Bootstrap JAR，repackage 无法重命名；停止已确认的联调进程后重跑成功，未修改业务代码。 |
| 未执行范围 | 性能压测、Docker 应用镜像启动、真实云服务、Java/Python/PostgreSQL/Redis/RocketMQ 重启、ACK 丢失、重复投递、SSE 故障矩阵、数据库备份恢复和生产环境验证继续暂缓。 |
| 结论 | 当前代码与业务测试门禁通过；本地功能版可继续收尾，不将后置性能/故障/生产项标记为完成。 |

## D2 代码规范、失败补偿与业务门禁复核（2026-08-23）

| 项目 | 结果 |
|---|---|
| 代码规范命令 | `\.\mvnw.cmd -Palibaba-code-style verify -DskipTests` |
| 代码规范结果 | BUILD SUCCESS；Checkstyle 9.3 Java 21 可执行子集 0 violations，Spotless 通过。旧 P3C/PMD 规则因不能解析 Java 21 record 已移除，不把解析错误当作通过；手册完整条款继续人工审查。 |
| Java 业务门禁 | `\.\mvnw.cmd clean verify` BUILD SUCCESS；Shared `12/12`、Application `156/156`、Infrastructure `71/71`（11 skipped）、API `59/59`、Bootstrap `58/58`（37 skipped）。 |
| Python 业务门禁 | `agent-runtime\\.venv\\Scripts\\python.exe -m pytest -q`：`113 passed、1 skipped、2 warnings`；跳过项为显式真实云集成，未调用付费模型或真实 embedding。 |
| 前端业务门禁 | `npm.cmd run typecheck`、`npm.cmd test -- --run`、`npm.cmd run build` 均通过；36 个测试文件、170 个测试通过。 |
| Compose 校验 | `docker compose --env-file .env -f docker/compose.yml config --quiet` 通过。 |
| 业务修复 | 单文件知识上传在对象写入后 PostgreSQL 失败会精确删除新对象；批次补偿删除失败保留为 suppressed exception；新增回归测试，KnowledgeServiceImplTest `7/7`。 |
| 文档与迁移 | 新增 `script/sql/FoodMate/README.md`，补齐 V23-V25 的人工执行、validation、rollback、seed 和台账边界；未执行迁移、truncate、回滚或备份恢复。 |
| Git 提交 | `4caa4d2`、`d945784`、`55a16ca`、`73f1f89`；用户既有 UI/Figma/ChatPage 改动未暂存。 |
| 未执行范围 | Docker 应用镜像构建、真实模型/embedding、吞吐/延迟压测、Java/Python/PostgreSQL/Redis/RocketMQ 重启、ACK 丢失、重复投递故障注入、SSE Last-Event-ID 故障矩阵、备份恢复和生产环境继续暂缓。 |
| 结论 | 当前业务代码、Java 21 规范子集、Python、前端和 Compose 配置门禁均通过；不能据此宣称后置性能、故障恢复或生产范围完成。 |

## D3 协议错误审计失败重试（2026-08-23）

| 项目 | 结果 |
|---|---|
| 代码提交 | `824ffe9 fix(runtime): 保留协议审计失败重试` |
| 场景 | Python -> Java 的不可解析 RunEvent 没有可信 `run_id`；协议错误审计成功后 REJECT，审计存储失败改为 RETRY，避免 ACK 后静默丢失审计事实。 |
| Java 定向验证 | `mvnw.cmd -pl foodmate-application -am test -Dtest=RuntimeEventMessageProcessorTest -Dsurefire.failIfNoSpecifiedTests=false`：`7/7` 通过；Spotless apply 通过。 |
| 边界 | 只修改协议错误审计失败分类；业务事件、数据库重试、DLQ 和用户可见状态未改变。 |

## D4 最终 Java 门禁数字复核（2026-08-23）

| 项目 | 结果 |
|---|---|
| 复核命令 | `mvnw.cmd verify` |
| 最终 Java 结果 | BUILD SUCCESS；Shared `12/12`、Application `157/157`、Infrastructure `71/71`（11 skipped）、API `59/59`、Bootstrap `58/58`（37 skipped）；Spotless、编译和 Spring Boot repackage 均通过。 |
| 数字更正 | D2 的 Application `156/156` 是当时记录值；以本次最终复核的 `157/157` 为准，未改写历史执行记录。 |
| 结论 | 当前业务代码 Java 门禁保持通过；性能压测、组件重启、ACK/重复投递故障注入、SSE Last-Event-ID 专项和生产范围仍按用户决定暂缓。 |

## M2-1 deterministic 宿主跨运行时业务 smoke（2026-08-23）

| 项目 | 结果 |
|---|---|
| 执行时间 | 2026-08-23 06:39-06:57（Asia/Shanghai） |
| 环境 | Windows；宿主 Java 21 `18080`；项目 `agent-runtime\\.venv` Python `19000`；Docker PostgreSQL、Redis、MinIO、RocketMQ NameServer/Broker/Proxy、Milvus 依赖保持 healthy；未调用真实模型或 embedding API |
| 配置 | Java 使用 `local + rocketmq + stub + deterministic`；Python 显式启用 `FOODMATE_KNOWLEDGE_INDEX_WORKER_ENABLED=true`、MinIO 读取和隔离 Redis 前缀；Python readiness HTTP 200，Redis/checkpoint/RocketMQ consumer 均 ready |
| 上传与索引 | 管理员批次 multipart 上传真实返回 `202`；Java `knowledge_index_outbox` 发布到 `foodmate-knowledge-index-v1`；Python Worker 从 MinIO 读取 Markdown、解析/分块、写入 Redis stub；`foodmate-knowledge-index-result-v1` 回写后批次 `completed`、条目 `indexed`、attempt `1` |
| 发布与检索 | 发布接口成功；公共检索按 `tenant_id=0/public_published` 返回当前文档安全引用，未暴露对象键或地址；同查询中的历史 smoke 文档通过按 `document_id` 复核排除 |
| AgentRun | 真实 Java -> RocketMQ -> Python -> Java 路径完成 3 个 deterministic AgentRun；有效收尾 Run 事件 6 条连续、状态 `completed`、`result_type=normal`、`run.completed` 包含 2 条引用，模型成本为 `0` |
| 可见性 | 当前文档下线后该文档不再出现在检索结果；恢复接口只回到 `draft`，恢复后仍不可检索；visibility Outbox 由 Java 权威状态产生并由 Worker 投影 |
| 失败记录与修正 | 首次脚本因 Python Worker 未显式启用停在 `uploaded/pending`，重启项目 `.venv` Runtime 后自动收敛；重复来源/版本/标题被 PostgreSQL 唯一约束正确拒绝；脚本先误读 camelCase 批次字段、后误把其他 smoke 文档命中计入下线断言，均修正为按业务字段和 `document_id` 断言 |
| 清理 | 精确删除本轮 operator `349684404412485632`、5 个批次/条目/文档、3 个 AgentRun、Session、消息、Outbox/Inbox/SSE/统一审计事实；MinIO 5 个测试对象确认不存在；Redis 隔离 chunks、5 条 Worker 完成事实和 3 个 checkpoint 删除；PostgreSQL 复核 user/jobs/docs/runs/sessions 均为 `0` |
| 未执行范围 | Docker Java/Python 应用镜像构建与启动、真实 embedding/云模型、吞吐/延迟/积压压测、Java/Python/PostgreSQL/Redis/RocketMQ 重启、ACK 丢失、重复投递故障矩阵、SSE `Last-Event-ID` 故障恢复、备份恢复和生产环境继续暂缓 |
| 结论 | M2-1 deterministic 公共知识库的上传 -> Java Outbox -> RocketMQ -> Python Worker -> Java 状态回写 -> 发布 -> 用户检索 -> AgentRun 引用 -> 下线/恢复业务闭环具备本轮真实证据；不据此扩大后置测试或生产完成范围 |

## D5 业务门禁复跑（2026-08-23）

| 项目 | 结果 |
|---|---|
| Java | `./mvnw.cmd verify`（Windows 等价命令 `mvnw.cmd verify`）BUILD SUCCESS；Shared `12/12`、Application `157/157`、Infrastructure `71/71`（11 skipped）、API `59/59`、Bootstrap `58/58`（37 skipped）；Spotless、编译、Spring Boot repackage 和 ArchUnit 通过 |
| Python | `agent-runtime\\.venv\\Scripts\\python.exe -m pytest -q`：`113 passed、1 skipped、1 warning`；跳过项为显式真实云集成，未调用付费模型或真实 embedding |
| 工作区与运行态 | 本轮临时 Java/Python 进程已停止；Docker 依赖仍 healthy；`git diff --check` 通过；用户已有 UI/Figma/`tmp` 改动未暂存 |
| 结论 | M2-1 deterministic 业务实现和当前 Java/Python 业务门禁通过；性能、重启、ACK/重复投递、真实服务和生产范围继续后置 |

## D6 文档与功能版范围收尾（2026-08-23）

| 项目 | 结果 |
|---|---|
| 执行时间 | 2026-08-23（文档收尾轮次） |
| 分支 | `codex/m2-remaining-business` |
| 变更范围 | 对齐 `路线图.md`、`完整功能实施TODO.md`、`M2剩余功能执行计划.md`、`测试策略.md`、`本地开发指南.md` 和 `配置指南.md`；未修改业务代码、数据库或用户已有 UI/QA 改动 |
| 业务测试复核 | `mvnw.cmd -pl foodmate-application,foodmate-infra,foodmate-api -am test -Dtest=DataRetentionServiceImplTest,DataRetentionDeliveryServiceImplTest,DataRetentionTaskPublisherTest,DataRetentionResultMessageProcessorTest,DataRetentionDatabasePurgeAdapterTest,AdminRetentionControllerTest -Dsurefire.failIfNoSpecifiedTests=false`：21/21 通过 |
| 当前完成口径 | M2-1/M2-2/M2-3 业务功能和核心业务测试完成；M3 运营审计、DLQ 重放契约、保留治理、对象/向量清理和受控数据库清理代码切片完成 |
| 明确后置 | M1-6 Agent 业务压测、吞吐/延迟/积压、组件重启、ACK 丢失、重复投递、SSE 故障恢复；真实云服务、Docker 应用镜像、生产部署、备份恢复、发布回滚、真实依赖清理和不可逆数据库硬删除 |
| 安全边界 | `hard_delete_enabled=false` 默认关闭；本轮未执行迁移、truncate、真实对象/向量/数据库删除或备份恢复 |
| 文档结论 | D1 文档状态和业务完成边界已与代码、定向测试和既有执行证据对齐；不将后置范围标记为完成 |

## D7 M2/M3 代码规范收口与本地环境复核（2026-08-23）

| 项目 | 结果 |
|---|---|
| 执行时间 | 2026-08-23 07:17-07:20（Asia/Shanghai） |
| 代码变更 | 为知识索引结果处理、知识投递/检索/上传服务、保留投递、知识 Mapper、PostgreSQL 仓储适配器和管理端控制器补充职责 Javadoc；`KnowledgeIndexResultMessageProcessor.hash()` 将泛化异常捕获收紧为 `NoSuchAlgorithmException`，消息 ACK/RETRY/REJECT 行为不变 |
| Git 提交 | `f9e85ba 规范(知识库): 补充核心类注释并收紧异常捕获` |
| Java 定向测试 | `mvnw.cmd -pl foodmate-application,foodmate-infra,foodmate-api -am test '-Dtest=KnowledgeIndexResultMessageProcessorTest,KnowledgeServiceImplTest,KnowledgeSearchServiceImplTest,DataRetentionDeliveryServiceImplTest,KnowledgeRepositoryAdapterTest,KnowledgeControllerTest' '-Dsurefire.failIfNoSpecifiedTests=false'`：Application 14/14、Infrastructure 5/5、API 3/3，共 22/22 通过 |
| Java 规范门禁 | `mvnw.cmd -Palibaba-code-style verify -DskipTests`：六个模块 Spotless 通过，Checkstyle 均为 0 violations，Bootstrap repackage 通过 |
| Compose/依赖复核 | `docker compose --env-file .env -f docker/compose.yml config --quiet` 通过；PostgreSQL、Redis、MinIO、RocketMQ NameServer/Broker/Proxy、Milvus 及其 etcd/MinIO 容器均 healthy |
| Docker 应用边界 | 本机没有可复用的 FoodMate 应用镜像；现有 RocketMQ Proxy 占用宿主 `8080/8081`。本轮未启动/重建应用容器，未重启现有依赖，不将静态配置或依赖健康误记为应用联调完成 |
| 未执行范围 | 真实 embedding/云模型、应用镜像启动、吞吐/延迟/积压压测、组件重启、ACK 丢失、重复投递故障矩阵、SSE 故障恢复、备份恢复、真实清理和生产环境继续暂缓 |
| 工作树保护 | 仅提交 8 个 Java 业务文件；用户已有 UI/Figma、`tmp` 和 Python 缓存改动未暂存 |
| 结论 | M2/M3 核心代码规范收口和业务门禁具备可复核证据；后置性能、故障、生产和不可逆删除范围保持未完成 |

## D8 生产 Java 规范与 SQL 配套矩阵复核（2026-08-23）

| 项目 | 结果 |
|---|---|
| 执行时间 | 2026-08-23 07:51-08:00（Asia/Shanghai） |
| Git 提交 | `8043f10 规范(java): 收紧异常边界并显式化生产导入`；`e2a81a3 docs(sql): 补齐迁移配套状态说明`；计划证据追加 `b763a5f docs(计划): 记录规范与SQL收口证据` |
| 代码规范修复 | 收紧生产源码泛化异常捕获；补齐 ZIP `IOException` 处理、JSON 协议错误分类和 RocketMQ 合约错误分类；生产源码 `catch (Exception/Throwable)` 扫描为 0 |
| 导入规范修复 | 移除 Shared、Application、Infrastructure、API、Bootstrap 生产源码中的通配符 import；MyBatis 注解统一为显式导入 |
| Java 业务门禁 | `mvnw.cmd -pl foodmate-shared,foodmate-application,foodmate-infra,foodmate-api,foodmate-bootstrap -am test -DskipTests=false`：BUILD SUCCESS；Shared 12/12、Application 157/157、Infrastructure 71/71（11 skipped）、API 59/59、Bootstrap 58/58（37 skipped） |
| 格式门禁 | `mvnw.cmd spotless:apply` 通过；随后编译与测试通过 |
| SQL 文档 | 更新 SQL 根 README 和 `migration/README.md`，增加 V2-V25 配套文件矩阵，明确历史 V3-V12 不补危险反向删除；未执行迁移、validation、rollback、truncate 或数据清理 |
| 数据边界 | 未修改 PostgreSQL、Redis、RocketMQ、MinIO、Milvus 中的业务数据；用户已有 UI/Figma、`tmp` 和 Python 缓存未暂存 |
| 未执行范围 | 性能压测、组件重启、ACK 丢失、重复投递、SSE 故障恢复、真实云模型/Embedding、应用 Docker 镜像和生产环境继续暂缓 |
| 结论 | Java 业务门禁和当前可执行规范子集通过；SQL 历史配套状态可追溯；不将后置性能、故障恢复或生产项标记为完成 |

## D9 最终业务门禁复跑（2026-08-23）

| 项目 | 结果 |
|---|---|
| Java | `mvnw.cmd verify`：BUILD SUCCESS；Shared `12/12`、Application `157/157`、Infrastructure `71/71`（11 skipped）、API `59/59`、Bootstrap `58/58`（37 skipped）；Spotless、编译、ArchUnit 和 Spring Boot repackage 通过 |
| Python | `agent-runtime/.venv/Scripts/python.exe -m pytest -q`：`113 passed、1 skipped、2 warnings`；跳过项为显式真实云集成，未调用付费模型或真实 embedding |
| Compose | `docker compose --env-file .env -f docker/compose.yml config --quiet` 通过；未启动或重启应用容器及基础设施 |
| 代码状态 | Java 规范提交 `8043f10`、SQL 矩阵提交 `e2a81a3`、计划证据提交 `b763a5f`、台账证据提交 `ea7cc1b` 已落库 |
| 工作区保护 | UI/Figma/QA、`tmp` 和 Python `__pycache__` 改动仍未暂存；未执行迁移、truncate、宽泛删除、备份恢复或实际清理 |
| 未执行范围 | 性能压测、组件重启、ACK 丢失、重复投递、SSE `Last-Event-ID` 故障矩阵、真实云服务、Docker 应用镜像和生产环境继续暂缓 |
| 结论 | 当前功能版 Java/Python 业务门禁和可执行 Java 规范子集复跑通过；后置性能、故障恢复和生产强化不计入完成 |

## D10 Context 来源 ID 审计业务切片（2026-08-23）

| 项目 | 结果 |
|---|---|
| 代码范围 | Python ContextBuilder 增加受控观察回调；Runtime 发布非终态 `run.context_assembled`，payload 仅包含 `message_id/summary_id/memory_id/citation_id`；Java 接受并通过统一 `OperationAuditService` 写入 `agent_run.context.assembled`，不保存正文、Prompt 或 Chain-of-Thought |
| Java 验证 | `mvnw.cmd -pl foodmate-application -am '-Dtest=V1RuntimeContextAuditTest,RuntimeEventMessageProcessorTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`：9/9 通过；覆盖来源 ID、事件投影和审计失败时阻止事件落库 |
| Python 验证 | `agent-runtime/.venv/Scripts/python.exe -m pytest -q tests/test_runtime_server.py`：42/42 通过；覆盖正常、澄清、工具等待、恢复和来源脱敏路径 |
| 格式 | `mvnw.cmd -pl foodmate-application -am spotless:apply` 通过；新增/修改 Java 文件已格式化 |
| 数据边界 | 未执行迁移、清库、truncate、真实模型/embedding、性能压测或组件故障注入；用户已有 UI/Figma、`tmp` 和 Python 缓存改动未暂存 |
| 结论 | Context 来源 ID 已具备业务级可审计闭环；生产 Trace 聚合、预算/Eval 指标平台和用户反馈入口仍属于后续切片，不因本项完成而标记完成；已创建独立功能提交 |

## D11 M2-1 联调资源清理与终态核验（2026-08-23）

| 项目 | 结果 |
|---|---|
| 执行环境 | Windows 工作区 `D:\develop\FoodMate`；使用项目 `agent-runtime\\.venv` 的 `pymilvus` 客户端访问本地 Milvus `http://127.0.0.1:19530`。 |
| 进程清理 | 宿主 Java `18080` 与 Python Runtime `19000` 已停止；复核时两个端口均无监听进程。 |
| Milvus 清理命令 | `agent-runtime\\.venv\\Scripts\\python.exe -c "...MilvusClient...drop_collection('foodmate_knowledge_codex_20260823')..."`；清理前目标集合存在，清理后集合列表仅保留其他 3 个集合。 |
| 保留范围 | `foodmate_knowledge_codex_audit_20260823`、`foodmate_knowledge_codex_m22_20260823`、`foodmate_knowledge_chunks` 未删除；未操作 Milvus 命名卷。 |
| 依赖状态 | PostgreSQL、Redis、MinIO、RocketMQ NameServer/Broker/Proxy、Milvus 及其依赖容器保持 healthy；未执行 `docker compose down -v`。 |
| 数据边界 | 未执行 PostgreSQL 迁移、truncate、备份恢复、数据库硬删除或宽泛数据清理；用户已有 UI/Figma、`tmp` 和 Python 缓存改动未暂存。 |
| 结论 | 本轮 M2-1 真实 deterministic 业务证据对应的运行进程和隔离集合已清理；业务代码、核心业务测试和执行证据保持有效，Docker 应用镜像、真实云服务、性能压测、组件重启、ACK 丢失、重复投递和 SSE 故障矩阵继续暂缓。 |

## D12 最终业务门禁复跑与测试上下文修复（2026-08-23）

| 项目 | 结果 |
|---|---|
| 执行时间 | 2026-08-23 09:05-09:14（Asia/Shanghai） |
| Java | `mvnw.cmd clean verify`：BUILD SUCCESS；Shared `12/12`、Application `159/159`、Infrastructure `71/71`（11 skipped）、API `59/59`、Bootstrap `58/58`（37 skipped）；Spotless、ArchUnit、编译和 repackage 通过。 |
| Python | `agent-runtime\\.venv\\Scripts\\python.exe -m pytest -q`：`114 passed、1 skipped、1 warning`；跳过项为显式真实云集成。 |
| 首次失败与修复 | 首次 Maven 复跑发现 API `ChatControllerTest` 因 `RunStreamController` 新增共享 `TaskScheduler` 依赖而无法加载测试上下文；补充 `@MockitoBean TaskScheduler` 后定向测试 `2/2` 通过，随后全量 Maven 通过。 |
| Docker | `docker compose --env-file .env -f docker/compose.yml config --quiet` 通过；现有 PostgreSQL、Redis、MinIO、RocketMQ NameServer/Broker/Proxy、Milvus 及依赖容器均为 healthy。 |
| 数据边界 | 未执行迁移、truncate、备份恢复、数据库硬删除、组件重启、消息重放或真实云服务调用；未启动 Docker 应用镜像。 |
| Git | `42c051b 修复(测试): 补齐聊天流测试调度器`；用户已有 UI/Figma、`tmp` 和 Python 缓存改动未暂存。 |
| 结论 | 当前代码和业务测试门禁通过；性能压测、依赖重启、ACK 丢失、重复投递、SSE 故障恢复、真实云服务、生产部署和不可逆清理仍后置。 |

## D13 Docker 应用容器与 M2-1 双模式业务闭环（2026-08-23）

| 项目 | 结果 |
|---|---|
| Docker 构建/启动 | `docker compose --env-file .env -f docker/compose.yml up -d --build foodmate agent-runtime` 成功；Java `foodmate` 和 Python `agent-runtime` 均在容器内运行。由于 MinIO 使用宿主 `9000`，Runtime 宿主映射修正为 `9002:9000`；RocketMQ 初始化 CLI 增加 30 秒超时。 |
| Readiness | Java `/actuator/health/readiness` HTTP 200；Python `/foodmate/internal/health/ready` HTTP 200，Redis 与 RocketMQ 协调依赖均 ready。 |
| Stub 验证 | 批次 `349734074186731520` 完成索引，发布后检索返回 1 条引用；Run `349734865958080512` 经 RocketMQ 完成，`run.completed` 和 `Last-Event-ID: 6` 回放均包含同一安全引用；文档下线及恢复为 `draft` 后检索为空。 |
| Local 验证 | `FOODMATE_RAG_MODE=local`、`FOODMATE_RAG_EMBEDDING_PROVIDER=deterministic`、隔离集合 `foodmate_knowledge_codex_docker_local_20260823`；批次 `349737110476951552` 完成，Milvus 实际创建 64 维集合并返回引用。未调用真实 embedding API。 |
| 失败与清理 | 只有 Markdown heading 的首轮输入按规则三次失败为 `RAG_EMPTY_DOCUMENT`；有效正文批次成功。测试文档已软删除，3 个 MinIO 对象、Redis stub 索引字段/完成事实和隔离 Milvus 集合已精确清理；未执行数据库硬删除。 |
| 未执行 | 性能/吞吐、积压、组件重启、ACK 丢失、重复消息、SSE 故障恢复、真实云服务、备份恢复和生产环境仍按用户要求暂缓。 |

## D14 全量业务门禁、规范与 Docker 证据同步（2026-08-23）

| 项目 | 结果 |
|---|---|
| Java 全量门禁 | `mvnw.cmd clean verify`：BUILD SUCCESS；Shared `12/12`、Application `159/159`、Infrastructure `71/71`（11 skipped）、API `59/59`、Bootstrap `58/58`（37 skipped）；Spotless、ArchUnit、编译和 Spring Boot repackage 通过。 |
| Java 规范门禁 | `mvnw.cmd -Palibaba-code-style verify -DskipTests` 通过；Spotless 通过，Checkstyle `0 violations`。生产源码泛化 `catch (Exception/Throwable)`、通配符 import、`System.out/err`、`printStackTrace` 和 `MAX(id)+1` 扫描均为 0。 |
| Python 业务门禁 | `agent-runtime\\.venv\\Scripts\\python.exe -m pytest -q`：`114 passed、1 skipped、1 warning`；跳过项为显式真实云集成，未调用付费模型或真实 embedding。 |
| 前端门禁 | `foodmate-ui` 的 `npm run typecheck`、`npm run build` 通过；用户已有 UI/Figma/QA 改动未纳入本轮文档提交。 |
| SQL 组织复核 | SQL 根 README 和 `migration/README.md` 已记录 V2-V25 配套文件矩阵；未执行迁移、validation、rollback、truncate、备份恢复或数据库硬删除。 |
| Docker 配置与 readiness | `docker compose --env-file .env -f docker/compose.yml config --quiet` 通过；`foodmate` `/actuator/health/readiness` 和 `agent-runtime` `/foodmate/internal/health/ready` 均 HTTP 200，应用容器与 PostgreSQL、Redis、MinIO、RocketMQ、Milvus 依赖保持 healthy。 |
| M2-1 业务证据 | Docker stub 使用 Redis 确定性索引，Docker local 使用 deterministic embedding 与实际 64 维 Milvus 集合；两种模式均完成上传、索引、发布、检索和 AgentRun 引用路径。真实 embedding、性能/积压、组件重启、ACK 丢失、重复消息和 SSE 故障恢复仍暂缓。 |
| 工作树保护 | 仅计划范围文档和执行台账进入本轮提交；用户已有 UI/Figma/QA、`tmp` 与 Python `__pycache__` 改动未暂存。 |
| 结论 | 当前功能版业务代码、业务测试、Java 可执行规范子集和 Docker 应用 readiness 均有可复核证据；后置性能、故障、生产和不可逆清理范围保持未完成。 |

## D15 结构化 Agent 反馈业务切片（2026-08-23）

| 项目 | 结果 |
|---|---|
| 分支与提交 | `codex/m2-remaining-business`；`2d5bd05 feat(feedback): 增加结构化Agent反馈入口` |
| 代码范围 | 新增 V26 `agent_feedback` 迁移、validation 和只读 rollback precheck；Application 反馈服务与端口；PostgreSQL Mapper/适配器；`POST /api/agent-runs/{runId}/messages/{messageId}/feedback`；聊天页反馈组件和业务测试。 |
| 业务规则 | 仅允许当前用户对已完成 AgentRun 的 assistant message 提交一次；负面反馈至少一个稳定原因；幂等键参数一致时重放既有事实；同一回答并发冲突不重复写入；高风险原因可标记高优先级审计。 |
| 安全边界 | 反馈和审计不保存回答正文、Prompt、原始业务请求、密码、令牌或敏感内容；审计只保存关联 ID、结果、原因数量、幂等键和参数摘要。 |
| Java 验证 | `mvnw.cmd -pl foodmate-infra -am "-Dtest=FlywayV26MigrationScriptTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`：2/2；Application：3/3；API：2/2；`mvnw.cmd -Palibaba-code-style verify -DskipTests`：Spotless 通过、Checkstyle `0 violations`。 |
| 前端验证 | `foodmate-ui` `npm run typecheck` 通过；`npm run test -- --run src/components/agent/AgentFeedback.test.tsx`：2/2；反馈组件 Prettier 检查通过。全量 Prettier 仍有既有 52 个文件格式问题，本轮未格式化无关文件。 |
| 数据库边界 | 未执行 V26 目标数据库迁移、truncate、备份恢复或清理；rollback 文件仅为人工只读前置检查。 |
| 未执行范围 | 不包含性能压测、组件重启、ACK 丢失、重复投递、SSE 故障恢复、真实云模型/embedding、生产部署或不可逆删除。 |
| 结论 | 结构化反馈代码与业务主路径测试完成；V26 迁移需按人工数据库流程另行执行并登记，不因本切片完成将 M1-6 或 M3 整体标记为完成。 |

## D16 反馈模块边界修复与全量 Java 门禁（2026-08-23）

| 项目 | 结果 |
|---|---|
| 修复提交 | `e0e6d5b fix(feedback): 隔离API与持久化视图依赖`；API 改为依赖 Application Service 的 `FeedbackResult`，不再引用 `port.out` 持久化视图。 |
| 全量验证 | `mvnw.cmd verify`：BUILD SUCCESS；Shared `12/12`、Application `162/162`、Infrastructure `73/73`（11 skipped）、API `61/61`、Bootstrap `58/58`（37 skipped）；ArchUnit、Spotless、编译和 Spring Boot repackage 通过。 |
| 失败与修正 | 首次全量验证发现 ArchUnit 拒绝 Controller 直接依赖 `AgentFeedbackRepository.FeedbackView`；已提取 Application Service 返回契约，并将幂等重放测试改为逐字段断言。 |
| 数据与运行边界 | 未执行 V26 迁移、真实 PostgreSQL 写入、性能压测、组件重启、ACK 丢失、重复投递或 SSE 故障恢复；用户已有 UI/Figma/QA、Python 缓存和 `tmp` 未提交。 |
| 结论 | 反馈业务代码通过全量 Java 业务门禁，模块依赖边界符合 ArchUnit；数据库迁移和后置运维测试仍未完成。 |

## D17 长期记忆三层数据边界（2026-08-23）

| 项目 | 结果 |
|---|---|
| 执行时间 | 2026-08-23 12:00-12:02（Asia/Shanghai） |
| Git 提交 | `063e85d feat(记忆): 固化长期记忆三层数据边界` |
| 代码范围 | Java 记忆候选增加允许类型白名单；拒绝饮食记录、餐食计划、周食谱、购物清单、Profile、营养目标等权威实体或字段，并拒绝过敏/医疗/诊断/处方等高影响健康事实；AgentRun Context 查询同步移除业务实体记忆类型。 |
| 业务验证 | `mvnw.cmd -pl foodmate-application,foodmate-infra -am test '-Dtest=MemoryCandidateServiceImplTest,FlywayMigrationScriptTest' '-Dsurefire.failIfNoSpecifiedTests=false'`：MemoryCandidateServiceImplTest `4/4`、FlywayMigrationScriptTest `7/7` 通过；随后 `mvnw.cmd verify` 全量通过（Shared `12/12`、Application `164/164`、Infrastructure `73/73`，11 skipped、API `61/61`、Bootstrap `58/58`，37 skipped）；Spotless、ArchUnit、编译和 repackage 通过。 |
| 规范门禁 | `mvnw.cmd -Palibaba-code-style verify -DskipTests` 通过，六个模块 Checkstyle 均为 `0 violations`；`git diff --check` 无错误。 |
| 数据边界 | 未执行迁移、真实业务数据写入、truncate、硬删除或缓存清理；用户已有 UI/Figma/QA、Python 缓存和 `tmp` 未暂存。 |
| 未执行范围 | 性能压测、队列积压、组件重启、ACK 丢失、重复消息、SSE 故障恢复、真实云模型/embedding、备份恢复、生产部署和发布回滚继续暂缓。 |
| 结论 | 长期记忆不再复制领域权威事实，稳定偏好/习惯仍可进入记忆候选；三层数据边界业务切片完成。 |

## D18 回答分片时间调度状态校正（2026-08-23）

| 项目 | 结果 |
|---|---|
| 执行时间 | 2026-08-23 12:05-12:07（Asia/Shanghai） |
| 既有实现 | `22499a1 feat(runtime): 增加回答分片间隔配置` 已实现 `FOODMATE_AGENT_STREAM_CHUNK_INTERVAL_MS`（默认 150ms）和 `FOODMATE_AGENT_STREAM_CHUNK_MAX_BYTES`；本轮未重复修改 Runtime 代码。 |
| 文档范围 | 将完整 TODO、配置指南、Runtime 架构和 M1-4 方案中的“尚未实现”描述校正为当前实际能力；明确 Eval 通过后才发布、按 UTF-8 字节切片、按分片间隔调度且不逐 Token 发布。 |
| 业务验证 | `agent-runtime\\.venv\\Scripts\\python.exe -m pytest -q tests/test_runtime_server.py -p no:cacheprovider`：`44 passed、1 warning`；覆盖 150ms 间隔和非法配置。使用 `PYTHONDONTWRITEBYTECODE=1`，未新增缓存写入。 |
| 未执行范围 | 未进行性能容量推断、生产长压、队列积压、组件重启、ACK 丢失或 SSE 故障矩阵；时间间隔测试只验证业务契约，不构成性能 SLO。 |
| 结论 | 回答分片 150ms 配置能力与文档状态已对齐；该能力不再作为未完成业务项。 |

## D19 V26 SQL 配套台账校正（2026-08-23）

| 项目 | 结果 |
|---|---|
| 执行时间 | 2026-08-23 12:16-12:18（Asia/Shanghai） |
| Git 提交 | `a04e271 docs(sql): 对齐V26迁移配套台账` |
| 文档范围 | SQL 根 README、migration README、CHANGELOG 和 M2 计划统一记录当前最高版本 V26；补充 `agent_feedback` 的 validation、只读 rollback precheck、数据安全边界和未执行说明。历史迁移文件未原地修改。 |
| 静态业务验证 | `mvnw.cmd -pl foodmate-infra -am test '-Dtest=FlywayV26MigrationScriptTest,FlywayV16V17KnowledgeMigrationScriptTest,FlywayV25MigrationScriptTest' '-Dsurefire.failIfNoSpecifiedTests=false'`：`6/6` 通过；覆盖 V16/V17、V25 和 V26 配套脚本。 |
| 数据库边界 | 未执行 V26 或其他迁移、validation、rollback、truncate、备份恢复或数据清理；未改变目标 PostgreSQL 状态。 |
| 结论 | SQL 目录当前版本说明与实际文件一致；V26 迁移仍需单独人工授权、备份和目标库校验后才可执行。 |

## D20 最终业务门禁与前端构建（2026-08-23）

| 项目 | 结果 |
|---|---|
| 前端 | 在 `foodmate-ui` 执行 `npm.cmd run build`：TypeScript 两套配置检查通过，Vite 生产构建通过（2010 modules transformed）。未将用户已有 UI/Figma/QA 改动纳入本轮提交。 |
| Java | 复用本轮已登记的 `mvnw.cmd clean verify` 结果：BUILD SUCCESS；Shared `12/12`、Application `164/164`、Infrastructure `73/73`（11 skipped）、API `61/61`、Bootstrap `58/58`（37 skipped）；Spotless、ArchUnit、编译和 repackage 通过。 |
| Java 规范 | 复用本轮已登记的 `mvnw.cmd -Palibaba-code-style verify -DskipTests` 结果：六个模块 Checkstyle 均为 `0 violations`；通配符 import、泛化 `catch (Exception/Throwable)`、`System.out/err`、`printStackTrace` 和 `MAX(id)+1` 扫描为 0。 |
| Python | 复用本轮已登记的项目 `.venv` pytest 结果：`116 passed、1 skipped、1 warning`；跳过项为显式真实云集成，未调用付费模型或真实 embedding。 |
| Docker | `docker compose --env-file .env -f docker/compose.yml config --quiet` 通过；Java `/actuator/health/readiness` 和 Python `/foodmate/internal/health/ready` 均已登记 HTTP 200，应用及 PostgreSQL、Redis、MinIO、RocketMQ、Milvus 依赖保持 healthy。 |
| PostgreSQL | 只读核验已登记：当前数据库未执行迁移；`flyway_schema_history`、`agent_feedback` 不存在，`knowledge_import_jobs`、`knowledge_index_outbox` 存在；未执行 truncate、备份恢复或硬删除。 |
| 数据与工作区 | 未修改或清理用户已有 UI/Figma/QA、Python `__pycache__` 和 `tmp` 改动；未新增业务数据、迁移或宽泛清理。 |
| 暂缓范围 | 性能压测、吞吐/延迟/积压、Java/Python/PostgreSQL/Redis/RocketMQ 重启、ACK 丢失、重复投递、SSE 故障恢复、真实云模型/Embedding、staging/production、备份恢复、发布回滚和不可逆硬删除继续暂缓。 |
| 结论 | 当前功能版 Java、Python、前端业务门禁、Docker 配置/readiness 和 M2-1 deterministic 业务闭环证据齐全；后置性能、故障、真实外部服务和生产项不标记为完成。 |

## D21 公共契约注释与业务门禁复核（2026-08-23）

| 项目 | 结果 |
|---|---|
| 代码范围 | `foodmate-shared` 的 `EventInbox` 补充类、方法和结果枚举的行为注释；Spotless 格式已对齐。未新增业务逻辑、迁移或运行时配置。 |
| Java 业务验证 | `mvnw.cmd clean verify`：BUILD SUCCESS；Shared `12/12`、Application `165/165`、Infrastructure `76`（14 skipped）、API `61/61`、Bootstrap `58`（37 skipped）；编译、单元测试、ArchUnit、Spotless 和 Spring Boot repackage 通过。 |
| Python 业务验证 | `agent-runtime\\.venv\\Scripts\\python.exe -m pytest -q -p no:cacheprovider`：`116 passed、1 skipped、1 warning`；使用 `PYTHONDONTWRITEBYTECODE=1`，未调用真实模型或 embedding。 |
| Java 规范验证 | `mvnw.cmd -Palibaba-code-style verify '-DskipTests'`：六个模块 Checkstyle 均为 `0 violations`；生产源码泛化异常捕获、标准输出、堆栈打印和 `MAX(id)+1` 扫描均为 0。 |
| 前端与 Docker | `foodmate-ui` 的 `npm.cmd run typecheck` 和 `npm.cmd run build` 通过（Vite `2010 modules transformed`）；`docker compose --env-file .env -f docker/compose.yml config --quiet` 通过，当前 foodmate、agent-runtime、PostgreSQL、Redis、MinIO、RocketMQ 和 Milvus 容器均 healthy。 |
| 数据边界 | 未执行迁移、validation、rollback、truncate、备份恢复、数据库硬删除或宽泛清理；未新增测试业务数据。现有用户 UI/Figma/QA、Python 缓存和 `tmp` 改动未主动清理。 |
| 暂缓范围 | 性能压测、吞吐/延迟/积压、组件重启、ACK 丢失、重复投递、SSE 故障恢复、真实云模型/Embedding、staging/production、备份恢复、发布回滚和不可逆硬删除继续暂缓。 |
| 结论 | 当前本地功能版业务测试、Java 规范门禁、前端构建和 Docker 配置状态均可复核；后置性能、故障、真实外部服务和生产项不标记为完成。 |

## D22 管理仪表盘安全摘要收口（2026-08-23）

| 项目 | 结果 |
|---|---|
| Git 提交 | `a805a87 修复(admin): 脱敏后台运行摘要` |
| 代码范围 | 管理仪表盘 SQL 审计仅返回 `query_hash` 摘要；知识文档仅返回 `source_name/source_type`，不返回原始 SQL 或对象存储 `storage_key`。 |
| 业务验证 | `mvnw.cmd -pl foodmate-infra -am '-Dtest=AdminDashboardMapperContractTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`：`1/1` 通过；契约测试同时拒绝原始 SQL 和对象 key 投影。 |
| 数据边界 | 未执行迁移、SQL 写入、truncate、备份恢复、硬删除或清理现有数据；未触碰用户已有 UI/Figma/QA、Python 缓存和 `tmp` 改动。 |
| 未执行范围 | 性能压测、组件重启、ACK 丢失、重复消息、SSE 故障恢复、真实云模型/embedding、生产价格审计和生产只读账号隔离继续后置。 |
| 结论 | 管理端安全摘要业务契约已收口；生产安全与运维门禁不由本地 Mapper 测试替代。 |

## D23 Docker M2-1 索引闭环与 AgentRun 引用复核（2026-08-23）

| 项目 | 结果 |
|---|---|
| 执行环境 | `codex/m2-remaining-business`；Docker Compose `.env`；Java `foodmate`、Python `foodmate-agent-runtime`、PostgreSQL、Redis、RocketMQ、MinIO、Milvus 均 healthy。Java readiness 和 Python `/foodmate/internal/health/ready` 均 HTTP 200。 |
| 配置 | `FOODMATE_RAG_MODE=local`、`FOODMATE_RAG_EMBEDDING_PROVIDER=deterministic`、64 维向量、隔离集合 `foodmate_knowledge_codex_chunks_20260823`；未读取真实 API Key，未调用付费服务。 |
| 上传与解析 | 管理员批次 `349798831908458496` 上传 Markdown；`knowledge_import_items` 为 `indexed`，批次为 `completed`，共 21 个切片，`attempt_count=1`，模型版本 `deterministic-local-v1`。 |
| Java Outbox/结果回写 | 索引 Outbox 状态为 `published`；结果回写同时更新条目、文档、批次和 `knowledge_chunks` 权威事实。批次 SSE 从游标 0 回放 `knowledge.index.indexed`、`knowledge.batch.progress` 两个事件。 |
| Milvus | 隔离集合实际存在，`num_entities=21`，schema 的向量维度为 `64`；发布可见性 Outbox 状态为 `published`。 |
| 检索与 AgentRun | 显式发布后公共检索返回安全引用；Docker AgentRun `349800593365143552` 完成，`run.completed` 含 2 条 citations，来源 ID 同时出现在 context 事件。用 `Last-Event-ID` 从中间事件回放可补发唯一 `run.completed` 终态。 |
| 可见性门禁 | 文档下线后检索引用数为 `0`；恢复仅回到 `draft`，检索仍为 `0`；随后通过删除接口将本轮文档置为 `deleted`。 |
| 清理与数据边界 | 本轮会话已通过业务删除接口软删除；知识文档、切片、Outbox、Redis/Milvus 去重或索引事实不做物理删除，避免破坏可追溯事实和其他历史数据。未执行迁移、truncate、数据库硬删除、备份恢复或宽泛清理。 |
| 暂缓范围 | 性能吞吐/延迟/积压、Java/Python/PostgreSQL/Redis/RocketMQ 重启、ACK 丢失、重复消息故障矩阵、真实 embedding、生产环境和发布回滚继续暂缓。 |
| 结论 | M2-1 Docker `local` deterministic 业务闭环已取得可复核证据：上传、解析、索引 Outbox、RocketMQ Worker、Java 结果消费、Milvus 写入、显式发布、检索、AgentRun 引用和批次/Chat SSE 回放均通过；后置性能与故障门禁不因此标记完成。 |

## D24 当前分支最终业务门禁复跑（2026-08-23）

| 项目 | 结果 |
|---|---|
| 分支 | `codex/m2-remaining-business`；本轮未新增业务代码，未改变数据库状态 |
| Java | `mvnw.cmd verify`：BUILD SUCCESS；Shared `12/12`、Application `166/166`、Infrastructure `81/81`（17 skipped）、API `61/61`、Bootstrap `58/58`（37 skipped）；Spotless、ArchUnit、编译和 Spring Boot repackage 通过 |
| Python | 使用项目 `agent-runtime\\.venv` 执行全量 pytest：`116 passed、1 skipped、1 warning`；跳过项为显式真实云集成，未调用真实模型或 embedding |
| 前端 | Vitest `37` 个测试文件、`189 passed`；`npm.cmd run typecheck` 通过；`npm.cmd run build` 通过，Vite 转换 `2010` 个模块 |
| 前端规范提示 | `npm.cmd run lint` 未作为业务门禁通过：仓库既有 CRLF/Prettier 规则产生 `10675 warnings`，`0 errors`；本轮未全仓格式化，避免覆盖用户 UI/Figma 改动 |
| 工作树保护 | 用户已有 UI/Figma/QA、Python `__pycache__`、`tmp` 和未提交 Chat CSS 差异均未暂存、未回滚 |
| 数据边界 | 未执行迁移、truncate、备份恢复、数据库硬删除、组件重启、消息重放或真实云服务调用 |
| 暂缓范围 | 性能压测、吞吐/延迟/积压、Java/Python/PostgreSQL/Redis/RocketMQ 重启、ACK 丢失、重复投递、SSE 故障恢复、真实 embedding、staging/production、发布回滚和不可逆清理继续暂缓 |
| 结论 | 当前功能版业务代码和业务测试门禁通过；M1-6/M3 的生产强化与真实依赖故障证据不因本轮复跑标记完成 |

## D25 M2-1 AgentRun HTTP SSE 回放与测试数据清理（2026-08-23）

| 项目 | 结果 |
|---|---|
| 执行环境 | Docker Compose `foodmate`；Java `127.0.0.1:8080`；使用本轮随机账号和既有完成 Run `349815929648975872`，未调用真实模型或 embedding 服务。 |
| SSE 验证 | `GET /api/chat/runs/349815929648975872/stream` 携带 `Last-Event-ID: 6` 返回 HTTP 200；仅回放 1 个 `run.completed`，稳定事件 ID 为 `sse_349815932530462720`，无重复终态；payload 含安全 `citations`，不含对象存储地址。 |
| PostgreSQL 事实 | Run 状态为 `completed`；7 个 `runtime_event_inbox_v2` 事件均为 `applied`；`run.completed` 的 `citation_count=1`；dispatch 为 `delivered`，RocketMQ dispatch outbox 为 `published`。 |
| 可见性清理 | 文档 `349815171083931648`、`349815899194134528` 均通过正式 `POST /api/admin/knowledge-documents/{id}/delete` 软删除；两条可见性 Outbox 已为 `published`，当前公共已发布可检索文档数量为 `0`。 |
| 数据边界 | 未执行 truncate、数据库硬删除、迁移、备份恢复或宽泛清理；知识切片、Outbox、Redis/Milvus 去重事实保留以维持审计和可追溯性；临时恢复用于 SSE 归属校验的测试账号已还原为禁用。 |
| 结论 | M2-1 本地 deterministic AgentRun 引用和 Chat 兼容 SSE `Last-Event-ID` 业务回放已取得直接 HTTP 证据；性能、重启、ACK 丢失、重复消息故障矩阵和真实外部服务仍按当前决策暂缓。 |

## D26 认证构造器注入修复与代码门禁复核（2026-08-23）

| 项目 | 结果 |
|---|---|
| 失败与修正 | 首次 `mvnw.cmd verify` 因 `AuthController` 存在两个构造器且未标记 Spring 注入构造器，API Spring 测试上下文出现 `No default constructor found`；已在主构造器补充 `@Autowired`，保留测试用简化构造器。首次失败另因该文件补丁换行格式混用，已使用 Spotless 自动修复。 |
| 定向验证 | `mvnw.cmd -pl foodmate-api -am '-Dtest=AuthCookieMatrixTest,P1AccountControllerTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`：`6/6` 通过。 |
| 全量 Java | 修复后 `mvnw.cmd verify`：BUILD SUCCESS；Shared `12/12`、Application `166/166`、Infrastructure `81/81`（17 skipped）、API `61/61`、Bootstrap `58/58`（37 skipped）；编译、Spotless、ArchUnit、Spring Boot repackage 通过。 |
| 代码规范 | `mvnw.cmd -Palibaba-code-style verify '-DskipTests'`：六个模块 Checkstyle 均为 `0 violations`。 |
| 数据与工作区 | 未执行迁移、数据库写入、truncate、备份恢复或运行时故障注入；用户已有 UI/Figma/QA 改动未暂存、未回滚。 |
| 暂缓范围 | 性能压测、吞吐/延迟/积压、Java/Python/PostgreSQL/Redis/RocketMQ 重启、ACK 丢失、重复投递、SSE 故障恢复、真实云模型/Embedding、staging/production、备份恢复、发布回滚和不可逆清理继续暂缓。 |
| 结论 | 认证控制器 Spring 注入问题已修复，当前 Java 业务测试、格式检查、架构检查和 Alibaba 规范门禁通过；环境依赖型测试仍按现有开关跳过。 |

## D27 Docker M2-1 stub 索引闭环与可见性验证（2026-08-23）

| 项目 | 结果 |
|---|---|
| 执行环境 | 分支 `codex/business-database-contracts`；Docker Compose `.env`；Java `foodmate`、Python `foodmate-agent-runtime`、PostgreSQL、Redis、RocketMQ、MinIO、Milvus 均 healthy；Java `/actuator/health/readiness` 和 Python `/foodmate/internal/health/ready` 均 HTTP 200。 |
| Docker 修复 | `docker/rocketmq/init-topics.sh` 移除 `grep -q` 管道早退，并将 consumer group 输出落到临时文件后校验；RocketMQ 初始化容器最终退出码 `0`，知识索引/结果/可见性 Topic 和 consumer group 创建成功。 |
| 配置边界 | `FOODMATE_RAG_MODE=stub`、`FOODMATE_RAG_EMBEDDING_PROVIDER=deterministic`；仅使用 Redis 确定性索引，不读取 API Key，不连接 Milvus 写入，不调用付费服务。 |
| 上传与索引 | 管理员批次 `349866183727517696` 上传 `README.md`；条目 `349866185271021569`、文档 `349866185271021568`；批次 `completed`，条目 `indexed`，`attempt_count=1`，解析生成 7 个 PostgreSQL chunk，索引 Outbox 为 `published`，Redis stub 共享索引键已产生。 |
| 发布与检索 | 显式发布后，普通用户 `POST /api/knowledge-base/search` 查询 `Agent Runtime` 返回 2 条安全 citations；引用不含对象存储地址。 |
| AgentRun | 普通用户创建真实 `/api/chat/runs`，Run `349867538139582464` 通过 RocketMQ 完成；事件序号连续 `1..7`，`run.completed` 包含 2 条 citations，来源 ID 同时出现在 context 事件。 |
| 可见性门禁 | 依次调用 disable、restore、publish、delete；检索引用数分别为 `0`、`0`、`2`、`0`。恢复仅回到 `draft`，未自动发布；5 条可见性 Outbox 均为 `published`，文档最终为 `visibility=deleted,is_deleted=true`。 |
| 审计与数据边界 | 本轮文档的管理员写操作产生 5 条 `operation_audits`；仅通过正式删除接口软删除本轮文档，保留 PostgreSQL chunk、Outbox、Redis 去重/索引事实以维持审计和可追溯性。未执行迁移、truncate、数据库硬删除、备份恢复或宽泛清理。 |
| 暂缓范围 | 性能吞吐/延迟/积压、Java/Python/PostgreSQL/Redis/RocketMQ 重启、ACK 丢失、重复投递故障矩阵、真实 embedding、staging/production 和发布回滚继续暂缓。 |
| 结论 | M2-1 Docker `local-stub` 业务主路径取得直接证据：上传、RocketMQ 索引、Java 结果回写、Redis 检索、显式发布、AgentRun 引用、下线/恢复/删除可见性门禁均通过；后置性能与故障类门禁不因此标记完成。 |

## D28 业务契约注释、导入规范与功能版门禁复核（2026-08-23）

| 项目 | 结果 |
|---|---|
| Git 提交 | `af294f3 fix(规范): 消除测试源码通配符导入`；`60ba6f4 规范(知识库): 补充跨模块契约注释`。用户已有 `foodmate-ui` CSS/TSX、QA 截图和 `tmp` 未暂存、未回滚。 |
| 代码规范 | 测试源码通配符 import 扫描为 `0`；生产源码控制台输出、堆栈打印、泛化异常捕获和 `MAX(id)+1` 扫描保持 `0`。受影响模块 Spotless check 和 Java 编译通过。 |
| Java 业务验证 | 知识库索引/检索/上传、DLQ 重放、保留治理和管理控制器定向测试共 `56` 个通过：Application `39`、Infrastructure `9`、API `8`；未开启本地依赖 E2E 的测试仍按开关跳过。 |
| Python 业务验证 | `agent-runtime\\.venv\\Scripts\\python.exe -m pytest -q`：`116 passed、1 skipped、2 warnings`；跳过项为显式真实云集成，未调用真实模型或 embedding。 |
| Docker 验证 | 使用临时显式环境变量执行 `docker compose -f docker/compose.yml config --quiet`，结果为 `COMPOSE_CONFIG_OK`；foodmate、agent-runtime、PostgreSQL、Redis、RocketMQ、MinIO 和 Milvus 相关容器均 healthy。 |
| SQL 目录 | `migration` V2-V26 共 25 个增量脚本，`validation` 18 个，`rollback` 18 个；V3-V12 历史缺失配套仍按 README 矩阵说明，不新增危险删除脚本，不执行迁移或校验写操作。 |
| 数据边界 | 未执行迁移、validation、rollback、truncate、数据库硬删除、备份恢复或宽泛清理；没有调用真实云模型/embedding，也未执行性能压测或故障矩阵。 |
| 结论 | M2-1/M2-2/M2-3 与 M3 当前业务代码及业务测试门禁保持通过；性能、重启、ACK/重复消息、SSE 故障恢复、真实外部服务、生产部署和不可逆清理继续后置。 |

## D29 全量功能版门禁与工作区收口（2026-08-23）

| 项目 | 结果 |
|---|---|
| Java 全量验证 | `mvnw.cmd clean verify`：`BUILD SUCCESS`；Shared `12/12`、Application `166/166`、Infrastructure `81`（17 skipped）、API `61/61`、Bootstrap `58`（37 skipped）；Spotless、ArchUnit、编译和 Spring Boot repackage 通过。 |
| Alibaba profile | `mvnw.cmd -Palibaba-code-style verify -DskipTests`：六个模块 Checkstyle 均为 `0 violations`。该 profile 是项目内可执行子集，不替代人工完整手册审查。 |
| Python | 使用项目 `agent-runtime\\.venv` 执行 pytest：`116 passed、1 skipped、2 warnings`；真实云集成保持显式跳过。 |
| 前端 | 稳定参数下 Vitest `37` 个测试文件、`190/190` 通过；`npm.cmd run build`（含 typecheck 和 Vite）通过，转换 `2010` 个模块。默认并行模式的两个管理页超时在单 worker、15 秒门禁下全部通过，未修改其测试超时配置。 |
| 工作区与临时文件 | 用户已有聊天页/QA 变更已由提交 `c28a4bc fix(聊天): 对齐SSE重连状态与验收证据` 保留；阿里手册临时 PDF `tmp/pdfs` 已清理，当前 Git 工作树干净。 |
| 数据与暂缓边界 | 未执行迁移、validation、rollback、truncate、备份恢复、数据库硬删除、性能压测、依赖重启、ACK/重复消息故障注入或真实云模型/embedding 调用。 |
| 结论 | 当前业务功能、测试、Java 格式/架构/代码规范、Python 运行时和前端构建门禁均可复核；M1-6 性能/故障类门禁及 M3 生产运维项继续后置。 |

## D30 Refresh Token 业务路径接入与轮换验证（2026-08-23）

| 项目 | 结果 |
|---|---|
| 代码范围 | 接入已有 V1 `auth_refresh_tokens` 表：Java application/infrastructure 增加 refresh token 端口和 PostgreSQL 原子 claim；登录/注册设置 HttpOnly refresh Cookie；`POST /api/auth/refresh` 轮换 session、CSRF 和 refresh Cookie；注销、改密、密码重置、账号注销和管理员撤销全部会话联动撤销 refresh token；前端 API Client 对普通 API 401 做一次共享刷新后重试。 |
| 安全边界 | 数据库只保存 token hash、过期、撤销、轮换来源和设备摘要；明文 refresh token 不进入 JSON、日志或 localStorage。Refresh endpoint 不要求旧 session 的 CSRF，但强制同源；缺失、过期或已消费 token 返回 `AUTH_REFRESH_TOKEN_INVALID`。 |
| Java 业务测试 | `mvnw.cmd -pl foodmate-api -am test '-Dtest=AuthCookieMatrixTest,P1AccountControllerTest,AdminManagementControllerTest' '-Dsurefire.failIfNoSpecifiedTests=false'`：`11/11` 通过；随后最终认证用例复跑 `AuthCookieMatrixTest,P1AccountControllerTest` 为 `9/9`；覆盖 Cookie 属性、明文 token 不进 JSON、轮换后旧 token 拒绝、注销撤销、缺失 token 稳定错误和管理员相关上下文。 |
| 前端业务测试 | `npm.cmd test -- --run`：`38` 个测试文件、`192/192` 通过；`npm.cmd run typecheck` 通过；新增 401 刷新重试和 refresh endpoint 不递归测试。 |
| 全量 Java 门禁 | `mvnw.cmd clean verify`：`BUILD SUCCESS`；Shared `12/12`、Application `166/166`、Infrastructure `81`（17 skipped）、API `64/64`、Bootstrap `58`（37 skipped）；Spotless、ArchUnit 和 Spring Boot repackage 通过。 |
| 数据边界 | 未新增迁移，未执行迁移、truncate、备份恢复、数据库硬删除或生产数据库写入；V1 表和索引作为现有契约使用。工作树中用户已有 Planning/QA 文件未暂存、未回滚。 |
| 暂缓范围 | 未进行真实 PostgreSQL refresh HTTP 联调、性能压测、组件重启、ACK/重复消息故障注入、SSE 故障矩阵、真实云模型/embedding、staging/production、发布回滚和不可逆清理。 |
| 结论 | 刷新令牌核心业务代码、API 契约、前端恢复行为和业务测试已完成；头像写路径独立验收、M1-6 性能/故障类门禁及 M3 生产运维项保持未完成。 |

## D31 头像安全写路径与补偿验收（2026-08-23）

| 项目 | 结果 |
|---|---|
| 执行环境 | 分支 `codex/business-database-contracts`；Java 21；项目 `foodmate-ui` Node 依赖；未调用真实 MinIO、云模型或生产服务。 |
| 代码范围 | 头像上传增加 PNG/JPEG/WebP 实际签名、解码、尺寸、像素、字节数和路径穿越校验；对象键不再包含原始文件名；保存尺寸、原始文件名和 SHA-256 摘要；数据库/统一审计失败时补偿删除新对象；新增独立头像下载失败错误码；头像响应不暴露对象存储键。 |
| Java 定向测试 | `mvnw.cmd -pl foodmate-application -am test '-Dtest=PersonalDataServiceImplTest' '-Dsurefire.failIfNoSpecifiedTests=false'`：`5/5` 通过，覆盖合法 PNG、伪造 MIME、数据库失败补偿删除、对象删除失败关闭和下载错误码。 |
| Java API/全量测试 | 头像相关账户 API 定向测试此前 `6/6` 通过；本轮 `mvnw.cmd verify`：BUILD SUCCESS；Shared `12/12`、Application `171/171`、Infrastructure `81`（17 skipped）、API `64/64`、Bootstrap `58`（37 skipped）；Spotless、ArchUnit、编译和 Spring Boot repackage 通过。 |
| 前端业务测试 | `npm.cmd test -- --run`：`38` 个测试文件、`192/192` 通过；前端头像响应类型和当前用户头像路径类型变更未引入业务回归。 |
| 失败记录 | 首次定向 Maven 命令因 PowerShell 未引用 `-D...=...` 被解析为非法生命周期阶段，未启动测试；改用项目既有引号写法后 `5/5` 通过。该命令行问题不属于代码失败。 |
| 数据与工作树边界 | 未执行迁移、truncate、备份恢复、数据库硬删除、真实 MinIO E2E 或宽泛清理；用户已有 Planning/QA 文件及其他未纳入本轮的改动未暂存、未回滚。 |
| 暂缓范围 | 性能压测、吞吐/延迟/积压、组件重启、ACK 丢失、重复投递、SSE 故障矩阵、真实云模型/embedding、staging/production、发布回滚和不可逆清理继续暂缓。 |
| 结论 | 头像安全业务写路径、对象补偿、统一审计失败记录、稳定资源路径和前端契约已通过业务门禁；真实对象存储联调及性能/故障类门禁不因本轮标记完成。 |

## D32 前端业务质量门禁复核（2026-08-26）

| 项目 | 结果 |
|---|---|
| 执行环境 | `foodmate-ui`；Node 依赖使用项目现有安装；未调用真实云模型、Embedding 或外部生产服务。 |
| Git 提交 | `9a33bec fix(前端): 收口业务代码质量门禁`。 |
| 代码质量 | `npm.cmd run lint`：退出码 `0`，无 ESLint 错误或未使用禁用指令；Prettier `endOfLine` 调整为 `auto`，避免对现有 LF/CRLF 文件进行全仓换行改写。 |
| 业务测试 | `npm.cmd test -- --run`：38 个测试文件、`192/192` 通过。 |
| 类型与构建 | `npm.cmd run typecheck` 通过；`npm.cmd run build` 通过，Vite 转换 `2010` 个模块。 |
| 代码范围 | 收口 Composer 无效 props、管理/业务页面数据订阅 effect 的规则提示和依赖、无效导入/变量；未暂存用户已有 `PlanningPage` CSS、Figma/QA JSON 和截图。 |
| 数据边界 | 未执行迁移、truncate、数据库硬删除、备份恢复、性能压测、组件重启、ACK/重复消息故障注入或真实云服务调用。 |
| 结论 | 当前前端业务质量门禁通过；性能、故障恢复、真实外部服务和生产环境门禁继续按项目决策后置。 |

## D33 当前分支全量业务门禁复跑（2026-08-26）

| 项目 | 结果 |
|---|---|
| 执行环境 | 分支 `codex/final-business-quality`；Java 21、项目 `agent-runtime\\.venv`；未调用真实云模型或付费 Embedding。 |
| Git 提交 | `2e83e7b docs(门禁): 同步前端业务验证状态`、`bae0d2e docs(执行记录): 登记全量业务门禁复跑`。 |
| Java 全量验证 | `.\mvnw.cmd verify`：`BUILD SUCCESS`；Shared `12/12`、Application `171/171`、Infrastructure `81/81`（17 skipped）、API `64/64`、Bootstrap `58/58`（37 skipped）；Spotless、ArchUnit 和 Spring Boot repackage 通过。 |
| Alibaba 规范 | `.\mvnw.cmd -Palibaba-code-style verify -DskipTests`：六个模块 Checkstyle 均 `0 violations`。 |
| Python 业务测试 | `agent-runtime\\.venv\\Scripts\\python.exe -m pytest -q`：`116 passed、1 skipped、1 warning`；跳过项为显式真实云集成，未调用付费服务。 |
| 前端业务测试 | D32 已记录：lint、typecheck、Vitest `192/192` 和 Vite build 均通过。 |
| 数据与运行边界 | 未执行迁移、truncate、数据库硬删除、备份恢复、性能压测、组件重启、ACK/重复消息故障注入或生产环境操作。 |
| 结论 | 当前分支业务代码、Java/Python/前端业务门禁及 Java Alibaba 可执行规范子集均通过；真实依赖故障、性能、生产安全和不可逆清理继续后置。 |

## D34 本地依赖业务主路径回归（2026-08-26）

| 项目 | 结果 |
|---|---|
| 执行环境 | 分支 `codex/final-business-quality`；Java 21；Docker Engine `28.5.1`；未调用真实云模型或付费 Embedding。 |
| 依赖状态 | PostgreSQL 容器此前停止，本轮执行 `docker compose --env-file .env -f docker/compose.yml up -d postgres` 后恢复为 `healthy`；foodmate、agent-runtime、Redis、MinIO、RocketMQ NameServer/Broker/Proxy 保持 healthy。 |
| HTTP 业务回归 | `.\mvnw.cmd -pl foodmate-bootstrap -am test "-Dfoodmate.local-http-e2e=true" "-Dtest=M15FoodLogWriterHttpE2ETest" "-Dsurefire.failIfNoSpecifiedTests=false"`：`11/11` 通过，0 失败、0 错误。 |
| RocketMQ 业务回归 | `.\mvnw.cmd -pl foodmate-bootstrap -am test "-Dfoodmate.local-mq-e2e=true" "-Dtest=M15FoodLogWriterProposalResultE2ETest" "-Dsurefire.failIfNoSpecifiedTests=false"`：`11/11` 通过，0 失败、0 错误；覆盖 Proposal/Result 消息主路径。 |
| 数据边界 | 未执行迁移、truncate、数据库硬删除、备份恢复、性能压测或故障注入；未清理现有本地数据。 |
| 暂缓范围 | Docker 流量统计、组件重启矩阵、ACK 丢失、重复投递故障注入、SSE 故障恢复、真实云模型/embedding、staging/production、发布回滚和不可逆清理继续暂缓。 |
| 结论 | PostgreSQL 恢复后，HTTP 与 RocketMQ 两条 `food_log_writer` 业务主路径均取得真实本地依赖回归证据；该证据不扩大 M1-6 性能与故障类门禁范围。 |

## D35 项目产品与实现入口状态收口（2026-08-26）

| 项目 | 结果 |
|---|---|
| 执行环境 | 分支 `codex/final-business-quality`；未调用真实云模型、付费 Embedding 或生产服务。 |
| Git 提交 | `1969e22 docs(项目): 对齐产品与实现入口状态`；仅更新产品范围/需求、Agent 架构、双运行时契约、后端现状、前端实现入口和前端实现清单 7 个文档。 |
| 文档对齐 | M2-1 公共知识库 deterministic 上传/索引/发布/检索/引用、M2-2 deterministic Tool/SQL 和 M2-3 管理核心切片按现有代码与业务证据登记；真实云服务、生产长稳、性能和故障门禁明确保留为后置范围。 |
| 文档校验 | 上述 7 个文档执行 `git diff --check`，无空白错误；未修改用户已有前端/Figma/QA 文件。 |
| 业务门禁依据 | 沿用 D32-D34：Java `verify`、Python `116 passed/1 skipped`、前端 `192/192` 及 lint/typecheck/build 通过，HTTP/MQ `food_log_writer` 各 `11/11` 通过。 |
| 数据与运行边界 | 未执行迁移、truncate、数据库硬删除、备份恢复、性能压测、组件重启、ACK/重复消息故障注入、SSE 故障恢复或生产环境操作。 |
| 结论 | 产品、架构、契约和实现入口已与当前 deterministic 本地业务状态一致；未将 deterministic 证据扩展为真实云、性能、故障恢复或生产完成。 |

## D36 历史状态文档口径修正（2026-08-26）

| 项目 | 结果 |
|---|---|
| 执行环境 | 分支 `codex/final-business-quality`；未调用真实云模型、付费 Embedding 或生产服务。 |
| Git 提交 | `9a0fbac docs(项目): 标注历史状态与当前闭环`；更新 MVP 主链路状态和 V2 双运行时迁移设计 2 个历史入口。 |
| 文档修正 | 更新主链路状态日期与 M2-1/M2-2/M2-3 当前 deterministic 业务闭环；将 V2 设计中的旧 Tool/SQL 判断明确标注为原始维护基线历史事实。 |
| 文档校验 | 两个文档执行 `git diff --check`，无空白错误；全量复核文档与执行记录执行 `git diff --check`，无错误。 |
| 数据与运行边界 | 未执行迁移、truncate、数据库硬删除、备份恢复、性能压测、组件重启、ACK/重复消息故障注入、SSE 故障恢复或生产环境操作；用户已有 Figma/QA 和前端修改未暂存、未回滚。 |
| 结论 | 历史设计入口已明确与当前实现状态的时间边界；当前业务代码、测试与后置生产范围保持可追溯。 |

## D37 运行时异常处理与业务门禁复核（2026-08-26）

| 项目 | 结果 |
|---|---|
| 执行环境 | Windows 工作区 `D:\develop\FoodMate`；分支 `codex/final-business-quality`；Java 21；未调用真实云模型、付费 Embedding 或生产服务。 |
| 代码提交 | `b3a089d fix(运行时): 补齐异常日志与代码规范`；仅修改 `RuntimeGatewayServiceImpl`，为监听器、超时取消和事件载荷解析异常补充结构化日志，补齐匿名实现 `@Override`、显式类型导入和控制语句大括号。 |
| 定向验证 | `.\mvnw.cmd --% -pl foodmate-application -am -Dtest=RuntimeGatewayServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`：`5/5` 通过。首次未带 `-am` 的命令因未构建 reactor 依赖导致共享类型缺失，已使用正确命令重跑成功。 |
| Java 全量验证 | `.\mvnw.cmd verify`：`BUILD SUCCESS`；Shared `12/12`、Application `171/171`、Infrastructure `81/81`（17 skipped）、API `64/64`、Bootstrap `58/58`（37 skipped）；Spotless、ArchUnit 和 Spring Boot repackage 通过。 |
| Alibaba 规范 | `.\mvnw.cmd --% -Palibaba-code-style verify -DskipTests`：根项目及六个模块均 `0 Checkstyle violations`。未使用默认 sun_checks 结果作为项目门禁。 |
| 只读审查 | 生产 Java 超长行共 `313` 条，主要来自既有 MyBatis SQL 注解；数字解析、反射查找中的 `ignored` 捕获属于预期控制流，未扩大为无关重构。 |
| 数据与暂缓边界 | 未执行迁移、truncate、数据库硬删除、备份恢复、性能压测、组件重启、ACK/重复消息故障注入或生产环境操作；用户已有前端/Figma/QA 修改未暂存、未回滚。 |
| Python 业务验证 | `.\agent-runtime\.venv\Scripts\python.exe -m pytest -q`：`116 passed、1 skipped、2 warnings`；跳过项为显式真实云集成，未调用真实模型或 Embedding。 |
| Docker 验证 | `docker compose --env-file .env -f docker/compose.yml config --quiet`：`COMPOSE_CONFIG_OK`；Java、Python、PostgreSQL、Redis、RocketMQ NameServer/Broker/Proxy、MinIO、Milvus 及其依赖当前均 healthy。 |
| 结论 | 运行时高置信度规范问题已修复并通过业务门禁；M2-1 deterministic 本地闭环沿用 D27/D34 直接证据，性能、故障恢复和真实外部服务继续后置。 |

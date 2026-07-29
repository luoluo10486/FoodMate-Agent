# M1-4 Python Agent Runtime 实现逻辑

> 模板提示：后续 AI 阅读本文档时，必须按功能点拆分为独立小节，不能把多个功能写成一大段；必须区分“目标设计、正在实现、已验证”，不得把本文方案或一次模型调用伪装成 M1-4 已完成。

## 当前复核结论（2026-07-29，优先级最高）

> 本节是本文的当前事实入口。后文保留历史实现记录；若与本节冲突，以本节和实际代码/测试结果为准。

### 已通过的真实联调

| 功能点 | 执行方式 | 结论 |
|---|---|---|
| PostgreSQL 用户主链路 | `LocalPostgresE2ETest` 连接本地 Docker PostgreSQL | 注册、登录、Cookie/CSRF、会话和消息读写通过。 |
| Java MQ Transport | `M14RocketMqTransportE2ETest` 连接本地 RocketMQ | Dispatch Outbox 到 RocketMQ 再到 Consumer 通过；校验 `request_hash`、`dispatch_id`、`run_id`。 |
| Proposal/Result | `M14ProposalResultE2ETest` 连接 PostgreSQL 与 RocketMQ | 只读 SQL 成功、SQL 失败审计、Result 发布、重复 Proposal 幂等均通过。 |
| 全量 Java 回归 | `mvn -pl foodmate-bootstrap -am test` | BUILD SUCCESS。 |

### 本轮修复并验证的运行问题

1. MyBatis Mapper 扫描范围改为 `com.foodmate.infrastructure.persistence`，让 `local` profile 能装配真实持久化 Store；`local-stub` 不加载 Mapper。
2. 本地 RocketMQ Broker 广播地址随当前 Docker 主机网络更新；这是开发环境配置，网络变化后必须重新核对，不能复用为生产配置。
3. `sql_query_audits.reject_reason` 限制为 255 字符，写审计前截断异常文本，避免业务 SQL 失败被二次数据库异常覆盖。
4. Proposal Inbox 使用 `claimed_at` 的 5 分钟 lease 回收旧 claim，消息仍按至少一次投递处理，必须依靠 `proposal_id + request_hash` 与业务幂等防止副作用重复。

### 尚未通过或尚未执行

1. 浏览器登录、会话、消息、MQ 消费、最终 `run.answer_stream/run.completed` 的完整 E2E 尚无通过证据。
2. Python 真实模型、RAG、Tool/SQL Proposal 生成到 Result 回注的完整跨进程 Agent 编排尚无通过证据。
3. 生产级长时间压力、多实例业务流量、P95/P99 与 Redis、RocketMQ、PostgreSQL 进程级恢复演练尚未完成。
4. 真实供应商的生产价格表审计与成本数据治理尚未完成；默认模型仍是 `deterministic:local`。

## 当前验收状态（2026-07-28）

> 本节覆盖前文历史检查项；历史记录中的“浏览器 E2E 未完成”等表述不再代表当前状态。

### 已验证

- [x] 浏览器登录、创建会话、发送消息、SSE `run.accepted/run.routed/run.model_usage/run.answer_stream/run.completed`、助手消息持久化和刷新恢复已通过。
- [x] Proposal/Result 真实 RocketMQ E2E 已通过；SQL 执行失败故障注入、`SQL_EXECUTION_FAILED`、rejected audit、Inbox completed 和 Result 发布均已验证。
- [x] 重复 Proposal 不重复执行 Tool；同一 `proposal_id` 只产生一条 SQL audit。
- [x] Redis 双 Java 实例并发、全局 active 上限、队列满、continuation 优先、队列租约过期和 Redis 不可用错误码已通过 6/6。
- [x] Python RocketMQ producer/consumer 启动均有超时，Proxy route 阻塞返回 `RUNTIME_MQ_STARTUP_FAILED`；Python pytest 28 passed。

### 仍需外部条件

- [ ] 真实云供应商联调仍需配置 primary/backup 真实 `BASE_URL` 与 `API_KEY`；gated pytest 在无凭据时明确 skip，fixture 不代替云端证据。
- [ ] 生产级长时间压测、Redis/RocketMQ/PostgreSQL 进程级中断恢复和多实例吞吐 P95/P99 仍需单独执行。

## 1. 文档边界

| 项目 | 内容 |
|---|---|
| 功能阶段 | M1-4 Python Agent Runtime |
| 当前结论 | 代码级最小治理闭环已实现并通过本地测试，真实基础设施和原生 LangGraph 联调未完成 |
| 权威方案 | [M1-4 最小真实闭环实施方案](../项目/M1-4%20Python%20Agent%20Runtime最小真实闭环实施方案.md) |

## 2. 状态机与步骤校验

### 2.1 代码做什么

1. `WorkflowGraph` 固定 `router -> planner/execution -> validator -> composer -> eval -> terminal` 的允许边。
2. 每次进入节点都会累计步骤数，超过 `max_total_steps` 时停止后续模型调用。
3. `StepValidator` 检查计划步骤白名单、复杂任务是否包含事实校验、缺少参数时是否只允许 clarification。
4. 非法步骤或上下文来源会进入安全降级，不继续生成答案。

### 2.2 已验证

- Python 测试覆盖非法状态边、最大步骤终止和复杂计划缺少事实校验。
- 依赖无关状态图已经可运行；原生 LangGraph 适配层已预留，但当前虚拟环境未安装 LangGraph，未宣称原生联调完成。

## 3. 模型适配、Eval 与预算

### 3.1 代码做什么

1. `ModelProvider` 隔离 deterministic provider 与 OpenAI-compatible 云端 provider。
2. 路由按 `high/standard/economy/eval` 选择供应商，并只对超时、限流和临时不可用执行白名单 fallback。
3. 复杂请求使用独立 Eval 调用；Eval 通过前不发送 `run.answer_stream`。
4. 预算达到 70%、85%、100% 时依次关闭 reflection/retrieval/replan/rewrite，并在硬上限返回 `requires_confirmation`。
5. 每次模型调用记录 model call、provider attempt、Token、成本和延迟。

### 3.2 已验证

- 本地 provider、fallback、Eval 拒绝、预算策略和 UTF-8 分片均有 Python 测试。
- 未使用真实云凭据，真实供应商价格审计和多云切换仍待联调。

## 4. 短期记忆与摘要

### 4.1 代码做什么

1. Java 只装配当前用户有权限的最近 8 条有效消息、会话摘要和已确认长期记忆。
2. 第 9 条有效消息写入后，`SessionSummaryService` 压缩旧消息，并保存覆盖范围、来源数量、Prompt 版本、digest 和 CAS version。
3. 用户更正或删除消息时，`SessionController` 调用摘要失效；下一次超过阈值时重新从有效权威消息生成摘要。
4. Python 只接收授权后的 Context，并记录消息、摘要和记忆来源 ID。

### 4.2 当前限制

- 摘要仍是确定性短摘要，未接入摘要模型。
- 分布式缓存和长期记忆变更后的联动失效还需要基础设施级验证。

## 5. 长期记忆治理

### 5.1 代码做什么

1. Python 只提交带来源、类型、置信度和 scope 的记忆候选。
2. Java 校验用户归属、来源消息、敏感内容、置信度和冲突。
3. 冲突候选写入 `confirmation_status=conflict`，默认不会进入 Agent Context。
4. 用户通过 `/api/memories` 查询、修改、逻辑删除或确认冲突记忆。
5. `V10__m1_4_memory_confirmation.sql` 增加确认状态约束和 Context 索引。

### 5.2 当前实现边界

1. 当前 Python 主要识别“我喜欢”“我不吃”类偏好，尚不是完整类型白名单。
2. 当前 Java 按更新时间读取最近 20 条已确认记忆，尚未在 Router 后按意图筛选。
3. `expires_at` 已存在并参与读取过滤，但候选写入尚未按类型自动分配 TTL。
4. Session 摘要当前为确定性文本拼接，不是结构化语义摘要。
5. M1 明确不引入 `pgvector`，当前使用 PostgreSQL 结构化过滤；未来是否启用必须由检索 Eval 结果决定。

### 5.3 接口

| 方法 | 路径 | 作用 |
|---|---|---|
| GET | `/api/memories` | 查询当前用户未过期记忆 |
| PATCH | `/api/memories/{memoryId}` | 修改记忆值和作用范围 |
| DELETE | `/api/memories/{memoryId}` | 逻辑删除记忆 |
| POST | `/api/memories/{memoryId}/confirm` | 确认冲突记忆 |

## 6. 并发、队列与超时

### 6.1 代码做什么

1. Redis Lua 脚本协调全局 active Run、用户 active Run、队列和 permit lease。
2. 同一 Session 的串行事实仍由 PostgreSQL 维护，不增加多余 Session permit。
3. 队列使用时间排序，并对 continuation 使用有限 priority；相同优先级保持先入先出，避免无限插队。
4. Reconciler 根据 PostgreSQL queued_at 和 deadline 扫描 queue timeout、execution timeout，并释放 permit。
5. Redis 不可用时新请求返回 `RUNTIME_COORDINATION_UNAVAILABLE`，不切换到进程内 semaphore。

### 6.2 当前限制

- Redis 故障注入、长期防饥饿和多实例压力测试尚未完成。
- node、waiting_user、cancel drain 的独立执行器仍是后续强化项。

## 7. Proposal 边界

1. `proposal_protocol.py` 定义 `tool` 和 `sql_read` 两类最小 Proposal。
2. SQL Proposal 必须以 `SELECT` 开始，并拒绝写操作、DDL 和多语句。
3. Python 只生成描述，不持有 FoodMate PostgreSQL 凭据，也不执行工具或 SQL。
4. Java Tool Gateway、SQL AST Guard、Result Topic 和真实执行链路属于后续 Tool/SQL 阶段，当前不能标记完成。

## 8. 验证结果

- Python：`agent-runtime/.venv/Scripts/python.exe -m pytest`，21 项通过。
- Java：`Shared 10 + Gateway 8 + Application 11 + API 27`，全部通过。
- 前端：`npm.cmd run build`，通过。
- Docker Redis/RocketMQ/PostgreSQL 故障注入、真实云模型和浏览器 E2E：当前环境未完成，不作为已验证证据。
# M1-4 Python Agent Runtime 实现逻辑补充

> 模板提示：后续 AI 阅读本文档时，必须按功能点拆分为独立小节，不能把多个功能写成一大段；必须区分“目标设计、正在实现、已验证”，不得把本地单元测试伪装成真实基础设施或真实云模型完成。

## 1. 原生 LangGraph

- 已实现：`agent-runtime/langgraph_adapter.py` 用白名单节点和显式条件边包装运行图。
- 已验证：Python pytest 22 项通过，包含图编译和多出口更新冲突回归。
- 未完成：Reflector、完整 Step Validator 和生产级 checkpoint 恢复联调。

## 2. Java Tool Gateway

- 已实现：Java 只接受 `sql_read`，SQL 必须以 `SELECT` 开始，并拒绝写操作关键字和分号。
- 已实现：执行结果写入 `sql_query_audits`；数据库不可用、Run 不存在、Run ID 非法和 SQL 执行异常均返回结构化错误。
- 已实现：Proposal consumer 使用 `runtime_tool_proposal_inbox` 的 `proposal_id + request_hash` 幂等事实；已完成 Result 重复消费时复用原 Result。
- 已实现：Python Result consumer 使用 Redis Inbox 按 `proposal_id + request_hash` 幂等，重复 Result 不重复回调。
- 已验证：Java command -> RocketMQ -> Java consumer 真实传输 E2E 通过；Proposal -> Java Tool Gateway -> PostgreSQL SQL 审计 -> Result 的真实 E2E 通过，并校验 Proposal Inbox 幂等记录。

## 3. 当前验证证据

- Python：`agent-runtime\\.venv\\Scripts\\python.exe -m pytest`，23 passed。
- Java Application：Maven 测试，14 passed；新增 Tool Gateway 覆盖写 SQL 拒绝、Run ID 校验和查询审计。
- 前端：`npm.cmd run build` 通过。
- 已验证：Docker 本地 PostgreSQL、Redis、RocketMQ Broker 停止/恢复注入通过；RocketMQ command 真实 E2E 通过。
- 未完成证据：真实云供应商调用、Proposal/Result 业务故障注入、浏览器完整 E2E 和生产级并发压测尚未完成。

## 4. 最新状态覆盖

- 本文前面的历史记录保留当时的阶段证据；若与本补充章节冲突，以本章节和 M1-4 实施方案的最新状态为准。
- 当前已完成 Proposal Publisher、Result Consumer 和 Proposal/Result 本地真实 E2E。
- 当前仍未完成真实云供应商联调、Proposal/Result 业务故障注入、浏览器完整 E2E 和生产级并发验证。
## 9. 2026-07-28 最新验证记录

### 9.1 已验证

- [x] 真实模式首页任务路由、数据库会话创建、登录态和用户消息落库已通过浏览器实际操作。
- [x] 会话/消息 ID 已改为字符串传输；浏览器 URL 与 PostgreSQL 的 64 位 ID 完全一致，修复 JavaScript 精度丢失导致的 `session not found`。
- [x] Vite 代理 Origin 已跟随 API 目标端口，CSRF 不再因旧端口固定值失败。
- [x] Python pytest 27 项、Java API/依赖模块测试 27 项、前端 typecheck/build 通过。

### 9.2 未通过或受环境限制

- [ ] 浏览器完整 SSE 闭环仍未通过：RocketMQ Proxy/consumer 重启和更换 consumer group 后，最新 Run 仍停在 `queued/routing`，没有得到可交付的 `run.answer_stream` 与 `run.completed`。
- [ ] 真实云模型供应商仍无凭据，只完成 OpenAI-compatible fixture 契约验证。
- [ ] Proposal/Result 业务故障注入和生产级并发/多实例验证仍未完成；现有单测不能替代 Broker 故障、双 Java 实例和长时间 aging 压测。

### 9.3 诊断改动

- Python Runtime 对未预期执行异常补充 `run.failed` 事件和堆栈日志，避免线程异常造成 Run 永久停留在 `routed`；待 MQ 消费稳定后重新验证。

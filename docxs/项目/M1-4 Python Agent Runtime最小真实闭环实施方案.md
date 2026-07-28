# M1-4 Python Agent Runtime 最小真实闭环实施方案

> 模板提示：后续 AI 阅读本文档时，必须按功能点拆分为独立小节，不能把多个功能写成一大段；必须区分“目标设计、正在实现、已验证”，不得把本文方案或一次模型调用伪装成 M1-4 已完成。

## 当前验收状态（2026-07-28）

### 已完成并有运行证据

- [ ] 浏览器完整真实登录、会话、消息和 SSE 闭环尚未通过；当前仅验证了 mock 页面交互与 SSE 展示，真实 Java API 注册请求返回 `500 INTERNAL_ERROR`。
- [x] Proposal/Result 真实 RocketMQ 往返、Tool Gateway SQL 失败审计、Result 发布完成和重复 Proposal 幂等已通过。
- [x] Redis 多实例准入 6/6 通过：并发上限、队列容量、continuation 优先、队列 lease 回收和协调不可用错误码均已覆盖。
- [x] Python MQ producer/consumer 启动超时已实现，启动失败返回 `RUNTIME_MQ_STARTUP_FAILED`；Python pytest 29 passed、真实云 gated 测试 1 passed。

### 未完成条件

- [x] 真实云模型供应商联调已通过：官方 `/v1/models` 返回 200，模型列表包含 `deepseek-ai/DeepSeek-V4-Flash`；Python 适配器真实调用和 primary + Eval gated 测试均通过。默认仍使用无凭证 deterministic stub。
- [ ] 生产级长时间吞吐、P95/P99、进程级故障恢复和真正多 Java 实例部署演练仍未完成。

## 1. 文档信息

| 项目 | 内容 |
|---|---|
| 功能编号/阶段 | M1-4 |
| 功能名称 | Python Agent Runtime 最小真实模型闭环与生产治理基线 |
| 文档状态 | M1-4 本地最小真实闭环、真实云模型和 Proposal/Result E2E 已通过；完整浏览器路径和生产级质量门禁仍未完成 |
| 前置阶段 | M1-3 Java -> Python 确定性 stub -> Java -> SSE 已完成 |
| 方案日期 | 2026-07-26 |
| 架构依据 | `Agent运行架构.md`、`Python智能体运行时设计.md`、`ADR-0005-RocketMQ异步主通道.md`、`配置指南.md` |

### 1.2 本方案与总 TODO 的关系

本文件是[完整功能实施 TODO](./完整功能实施TODO.md)中 M1-4 未完成项的唯一实施分解和验收依据。总 TODO 记录范围与最终完成判定；本文件记录依赖顺序、代码边界、契约变更和测试证据。发生冲突时，以代码/迁移/测试事实优先，其次是 ADR 与契约，再由本方案和总 TODO 同步修正。

普通缺参 continuation 与 `superseded` 已在 V5、Java 事务、SSE 和前端映射中完成，不再作为 M1-4 待实现项。本阶段不因基础设施已完成而降低真实模型、预算、Eval、记忆、并发和可观测性门槛。

## 1.1 当前验证结论

- [x] Java PostgreSQL AgentRun、Dispatch 和 Outbox 已提交，并由 Outbox Relay 发布到 RocketMQ command topic。
- [x] Python Runtime 已通过 RocketMQ consumer 接收命令，并使用 Redis Inbox 按 `dispatch_id + request_hash` 幂等。
- [x] Python 确定性 stub 已产生 `run.accepted`、`run.routed`、两条 `run.answer_stream` 和 `run.completed`。
- [x] Python Event Outbox 在 Broker ACK 后清理；Java RocketMQ consumer 已将 5 条事件写入 PostgreSQL Inbox、AgentRun 和 SSE Outbox。
- [x] 已验证结果：AgentRun 为 `completed`，PostgreSQL Inbox 为 5 条且全部 `applied`，SSE stream sequence 为 1 到 5。
- [x] 前置门禁已验证：Python pytest 6/6、Java 全模块 Maven test 退出码 0、Compose 示例环境配置校验通过，RocketMQ 四个业务 Topic 已初始化。
- [x] 联调清理规则已补充：Python 缓存、egg-info 和联调日志属于生成物并已加入 `.gitignore`，历史过期 Outbox 仅用于故障审计，不回写为成功。
- [ ] 完整 Step Validator、Reflector、浏览器真实 E2E 和生产级故障注入仍未完成；当前已落实原生 LangGraph 白名单图、模型适配器、Eval/预算、Redis checkpoint、准入、摘要和记忆候选边界，并完成真实云联调与 Broker 运行中故障注入。

## 2. 阶段目标

### 2.1 最小真实闭环

- 用受控模型调用替换确定性回答生成，但保留 M1-3 的 Java AgentRun、dispatch、事件与 SSE 权威链路。
- 使用 LangGraph 建立 Router、Planner、Execution、Validator、Composer、Final Eval 和终态裁决。
- 简单问答允许走短路径；复杂任务只能沿确定的图边执行，Agent 不得任意跳转。
- 候选答案通过 Final Eval 后才向前端发送正文。
- 使用 RocketMQ 作为 Java/Python 异步正式主通道；M1-3 HTTP 适配器只保留用于本地兼容切换、契约测试和诊断。

### 2.2 本阶段不包含

- 不实现完整 RAG 知识库、SQL Agent 或全部业务 Tool。
- 不建设账户余额、租户额度或供应商余额查询。
- 不建设人工审核工作台，也不新增 `waiting_review`。
- 不让 Python 直接访问 FoodMate PostgreSQL 业务库。
- 不把完整 M2 治理后台纳入 M1-4 完成门槛。

### 2.3 已确认的模型策略

- 先实现供应商无关的 Model Adapter 与确定性本地 provider；它用于无云凭据时完成编排、预算、Eval、降级和审计自动化测试。
- 真实云模型通过环境变量配置 `high`、`standard`、`economy` 和 `eval` 逻辑别名；同一别名可按部署映射不同供应商与具体模型。
- Model Routing Policy 是确定性代码，Agent 只能声明能力、质量等级和结构化输出要求，不能自行选择供应商或越过预算切换模型。
- `eval` 使用独立逻辑别名。资源不足时可与生成模型同一供应商，但必须独立调用、独立 Prompt/rubric、独立 usage 记录，并在 Trace 标记 `judge_independence=degraded`。
- 供应商 timeout、rate limit 或明确可重试故障可以按配置切换兼容 fallback；安全、权限、schema、预算耗尽和 Eval 硬失败不得通过换模型绕过。
- 不在仓库中写入任何供应商密钥、默认厂商或价格猜测。接入真实供应商前必须补 provider contract fixture、价格表版本和失败注入测试。

## 3. 实施前契约门禁

### 3.1 当前 V1 可复用内容

- 复用 RunCommand、RunEvent、RuntimeError、Service JWT 和 canonical digest。
- 复用 Java dispatch outbox、事件 inbox、AgentRun 状态投影和 SSE outbox。
- 复用现有取消、幂等、乱序拒绝和终态竞争规则。
- 复用既有消息 envelope 与 canonical digest，不创建 MQ 专用 DTO。

### 3.2 已完成的契约与数据结构升级

- `superseded` AgentRun 终态。
- `parent_run_id`、`continuation_reason`。
- `agent_run_budget_snapshots`、预算 revision 和基础超时快照字段。
- `result_type=safety_degraded` 的外部响应位置。

上述结构已由 V5 迁移、Java continuation 事务、SSE 和前端状态映射落地。它们是后续预算、恢复和安全降级能力的基础，不代表对应运行时行为已经全部实现。

### 3.3 仍需升级或实现的行为

- 预算达到 100% 后的确认请求、确认摘要、追加结果和前端确认交互。
- 工具审批或预算追加时恢复原 Run，并创建新 `dispatch_id + attempt`。
- `TimeoutSnapshot` 在准入、排队、执行、节点和等待用户阶段的实际执行与恢复语义。
- `result_type=safety_degraded` 与 Eval `request_review` 的端到端安全降级行为。

这些行为必须先补齐契约细节、Java/Python 实现和前端映射，再进入真实模型主链路；不得仅凭 V5 表结构将其标记为完成。

### 3.4 Eval 与记忆的契约门禁

- 当前 Java 只接受既有 V1 `event_type`，不能直接发送未被消费端识别的 `run.eval`。第一版 Eval 结果必须作为 `run.completed`、`run.failed` 或安全降级结果的受控 payload/Trace 字段落库；若新增独立 `run.evaluated`，必须先同时更新 V1 契约、Java `statusFor`、Inbox 测试和前端事件映射。
- `run.model_usage` 是模型和 Judge 用量的唯一运行时事件；生成模型与 Eval 模型分别产生稳定 `model_call_id/provider_attempt_id`，不能把 Eval 成本混入回答模型调用。
- `session_summaries` 的版本/CAS、摘要候选写入和 `user_memories` 候选协议必须先定义 Java HTTP/MQ DTO、权限校验和删除失效事件，再允许 Python 提交候选。
- Python 不直接写 FoodMate PostgreSQL，也不把 checkpoint、进程内状态或模型输出当作用户记忆真值。

## 4. 目标消息链路

```text
用户请求
  -> Java PostgreSQL：AgentRun + 排队事实
  -> Redis：P0-P3 调度 + 用户/全局 permit
  -> Java PostgreSQL：Dispatch + RunCommand Outbox
  -> Outbox Relay -> RocketMQ command topic
  -> Python Redis Inbox -> LangGraph
  -> Python Redis：checkpoint + Event/Proposal Outbox
  -> Event Relay -> RocketMQ event/proposal topic
  -> Java PostgreSQL Inbox + AgentRun 投影 + SSE Outbox
  -> SSE -> foodmate-ui
```

RocketMQ 只负责跨服务可靠运输；Redis 负责准入、优先级、lease 和 Python 技术状态；PostgreSQL 保存 Java 业务真值。任何组件不可用时不得自动切换为另一条业务派发通道。

![RocketMQ 异步主链路](../架构/资源/RocketMQ异步主链路.svg)

## 5. 功能点实施方案

### 5.1 RocketMQ Topic 与普通消息

- 本地 Compose 增加单 NameServer + 单 Broker，不设计本阶段生产集群、TLS 或 ACL。
- Agent Topic 固定为 `foodmate-agent-command-v1`、`foodmate-agent-event-v1`、`foodmate-agent-proposal-v1` 和 `foodmate-agent-result-v1`。
- 后台域预留 knowledge、audit 和 notification Topic，但 M1-4 只实现所需 Agent Topic。
- 不使用 RocketMQ 事务消息；Java 使用 PostgreSQL Outbox/Inbox，Python 使用 Redis AOF Outbox/Inbox。
- 所有 Agent 消息使用 `run_id` 作为局部顺序键，不要求 Topic 全局有序。

### 5.2 Java Outbox Relay 与消费事务

- Java 获得 Redis permit 后，在 PostgreSQL 事务中创建 Dispatch 和不可变 RunCommand Outbox。
- Relay 使用 lease/CAS 领取 `pending` 消息，Broker 持久化确认后才标记 `published`。
- 重试保持原 `message_id/dispatch_id/attempt/request_hash/payload`；不得重新组装消息。
- Java 消费 RunEvent/Proposal 时，在 PostgreSQL 事务中完成 Inbox、状态机、审计和 SSE Outbox，提交后才 ACK。
- 数据库提交后、ACK 前崩溃由 MQ 重投，PostgreSQL Inbox 吸收重复。

### 5.3 Python Redis Inbox、Outbox 与 Relay

- Redis namespace 分离为 `foodmate:agent:mq:inbox:*`、`foodmate:agent:mq:outbox:*` 和 `foodmate:agent:checkpoint:*`。
- Python 消费 RunCommand 后先登记 `dispatch_id + request_hash`；同 ID 同 hash 为重投，不启动第二次执行。
- checkpoint 与 Event/Proposal Outbox 使用 Lua/Transaction/CAS 原子写入。
- Event Relay 收到 Broker 确认后将 Outbox 标为 `published`；Inbox 和已发布 Outbox 默认保留 7 天。
- Redis 不可用时 readiness 失败并停止消费，不在进程内降级保存。

### 5.4 LangGraph 图装配

- 在 `agent-runtime` 内建立 `graph`、`nodes`、`policies` 和 `context` 四类职责目录，不拆分新微服务。
- `builder` 只注册白名单节点和条件边；图状态只保存技术执行信息，不替代 Java AgentRun。
- 每次节点进入增加总步骤计数；重试、重规划和重写分别使用独立计数器。
- 超出任一循环预算后进入 Terminal Arbiter，不允许模型自行决定继续。

### 5.5 Router 与 Planner

- Router 输出结构化 intent、复杂度、风险级别、所需能力和缺失参数。
- 简单任务直接进入 Composer；复杂任务进入 Planner。
- Planner 生成有界步骤列表、每步输入输出、依赖、失败动作和预计预算。
- 缺少用户专属参数时进入 Clarification，不允许猜测过敏、疾病、预算等关键事实。

### 5.6 Execution 与 Step Validator

- Execution 首期只支持模型调用和明确允许的无副作用能力；Tool/SQL 仅生成 proposal。
- 每一步执行后由确定性 Step Validator 检查 schema、事实来源、完成状态和权限边界。
- 失败只能选择固定的 retry、replan、degrade 或 terminate 动作。
- Reflection 默认可执行一次，但达到预算阈值后优先关闭。

### 5.7 Composer、Final Eval 与回答分片

- Composer 只使用已验证事实、工具结果摘要和明确失败信息生成候选答案。
- 确定性硬规则对所有任务执行，不能被 LLM Judge 覆盖。
- 复杂、RAG 和高风险任务强制 LLM Judge；低风险任务默认 20% 抽样。
- Eval 通过前候选正文只存受限服务端缓冲，不产生 `run.answer_stream`。
- `request_review` 在无人审核条件下转安全降级，不交付被拦截候选答案。
- Eval 通过后按时间或大小切分 `run.answer_stream`，默认 150ms 或 2048 字节满足其一即生成事件；不得逐 Token 发布 MQ 消息。

### 5.8 模型适配与路由

- 定义 high、standard、economy、eval 四个逻辑模型别名，部署环境映射真实供应商模型。
- 模型选择由确定性 Model Routing Policy 完成，Agent 不能自选供应商。
- 记录逻辑调用、供应商 attempt、Token、估算成本、延迟和错误分类。
- timeout、rate limit 或供应商故障只能按配置走兼容 fallback；安全拒绝不得换模型绕过。

### 5.9 Token 与成本预算

- 新 Run 默认最多 30000 Token、估算成本 ¥0.50，实际值全部由环境变量配置。
- 70% 停止非必要 Reflection 并减少可选检索；85% 禁止重规划/重写并允许经济模型；100% 停止新调用。
- 100% 时返回可信部分结果或进入预算确认，前端必须显示追加 Token 和成本。
- 用户每次确认默认最多追加 30000 Token、¥1.00；每次追加生成新 BudgetSnapshot revision 和 dispatch attempt。

### 5.10 Redis 并发与队列

- PostgreSQL 保证同 Session 最多一个 active Run。
- Redis 保证同一用户默认最多 2 个活跃 Session、Runtime 全局默认最多 20 个 active Run。
- 全局队列默认最多 100，采用 P0 审批/预算恢复、P1 continuation、P2 普通请求、P3 后台任务。
- priority burst 和 aging 防止普通请求长期饥饿。
- Redis 不可用时新 Agent 请求 fail closed，返回 503 `RUNTIME_COORDINATION_UNAVAILABLE`，不退回进程内业务计数。
- 只有取得 permit 后才发布 RunCommand；不能让 Python 消费 MQ 后再反复抢 permit。

### 5.11 超时与 permit 释放

- queue timeout 默认 30 秒，execution timeout 120 秒，node timeout 30 秒。
- waiting_user timeout 默认 86400 秒，cancel drain timeout 10 秒。
- 排队时间不消耗 execution timeout，但必须服从请求级绝对 deadline。
- 完成、失败、取消、超时和进程异常后均需通过 owner token/CAS 可靠释放 permit。

### 5.12 Context Builder、摘要与记忆

- 短期记忆由最近 8 条有效原始消息、Session 摘要、`unresolved_slots`、当前 Run 追问和 checkpoint 技术状态组成，只在授权 Session/Run 内使用。
- 第 9 条有效消息写入后，对尚未覆盖的旧消息增量更新会话摘要，再继续保留最近 8 条。
- 摘要携带覆盖消息 ID 区间、版本、来源数量、Prompt 版本和 digest；Java 使用版本/CAS 写入 `session_summaries`。
- 上下文按系统安全指令、当前用户输入、近期消息、摘要、授权长期记忆和检索结果分配 Token，并记录实际使用的来源 ID。
- Python 只能生成长期记忆候选；Java 校验来源、归属、敏感性、冲突、scope、置信度和有效期后写入 `user_memories`。
- 模型推测、一次性参数、预算确认、工具审批和医疗诊断不得自动写入长期记忆；高影响冲突必须请求用户确认。
- 用户删除/更正消息或记忆后，相关摘要、缓存和上下文引用必须失效；摘要失败时保留近期消息并明确降级，不能使用不完整摘要伪造用户偏好。

### 5.13 Redis checkpoint

- 使用独立 namespace、AOF、CAS、TTL、大小限制和应用层加密。
- 简单直接问答不强制 checkpoint。
- 规划完成、工具结果确认、进入等待、预算确认和 Eval 前后保存安全恢复点。
- 恢复前必须与 Java 对账终态、取消、active dispatch 和已完成 Tool/SQL，禁止重复副作用。

### 5.14 continuation、取消与恢复

![FoodMate Agent 任务恢复机制](../架构/资源/Agent任务恢复机制.svg)

#### 5.14.1 从 checkpoint 恢复的目标闭环

- 与上图一致，恢复不是重新创建业务任务：保留原 `AgentRun`，由 Java 创建新的 `dispatch_id + attempt`，Python 从 checkpoint 的 `current_node` 恢复未完成编排。
- 在恢复前，Java 对账 `AgentRun` 终态、取消标记、绝对 deadline、当前 fencing owner、已完成 Tool/SQL invocation 和预算 revision；Python 不能只凭 Redis 值直接执行。
- checkpoint 必须在每个可恢复安全点原子写入，保存节点、工作流/Prompt 版本、已完成节点、待处理 proposal、事件序号、预算、deadline、已完成 invocation 和 CAS version。
- 用户关闭页面不取消后台 Run；SSE 重连从 Java SSE Outbox 补发。用户补参、预算追加或工具审批恢复时使用新 dispatch attempt；用户改变目标时创建新 AgentRun。
- 当前仅完成 checkpoint 存储基础，尚未完成 `load -> Java 对账 -> current_node` 的真实恢复执行器和故障恢复 E2E，不能提前标记为完成。

- 普通缺参补充创建新 AgentRun，并关联 `parent_run_id + continuation_reason`。
- 旧 Run 目标终态为 `superseded`，不再占用 Session active 位或 Redis permit。
- 工具审批和预算追加恢复原 Run，但创建新的 `dispatch_id + attempt`。
- 用户明显改变任务目标时创建普通新 Run，不恢复旧 checkpoint。
- 浏览器取消仍调用 Java HTTP；Java 落库后通过 command Topic 可靠发布 CancelCommand。
- Tool/SQL 审批和预算追加由 Java HTTP 接受并校验，再创建新 dispatch attempt 通过 MQ 发送。

### 5.15 Tool/SQL Proposal 与 Result

- Python 根据版本化 Schema Catalog 生成 ToolProposal 或 SqlProposal，通过 proposal Topic 发送。
- Java执行权限、确认、SQL AST、只读、白名单、用户过滤、限行、超时、脱敏和审计。
- Java 已接入 Proposal consumer、Java-only SQL Guard 和 Result producer；`runtime_tool_proposal_inbox` 以 `proposal_id + request_hash` 固化消费事实，重复消息复用已完成 Result，未完成执行保持重试。
- Python Proposal Publisher/Result consumer 已接入 Redis Outbox/Inbox；Java -> Tool Gateway -> PostgreSQL 审计 -> Result 的业务消息往返 E2E 已通过。
- Python 不持有 FoodMate PostgreSQL 凭据，也不直接执行 SQL 或业务工具。

### 5.16 DLQ 与对账

- 可重试异常走 RocketMQ retry；schema、digest、权限和 fencing 错误直接 rejection，不无意义重试。
- 重试耗尽进入所属 consumer group 的 DLQ，不建立万能共享 DLQ。
- DLQ 不自动把 AgentRun 标记失败；Java Reconciler 对账 Run、dispatch、checkpoint 和事件后裁决。
- 重放必须保持原消息身份和摘要，不能借 DLQ 重放创建新业务操作。

### 5.17 Trace、反馈与隐私

- Trace 保存节点、模型、Token、成本、预算 revision、超时、Eval、错误和脱敏摘要。
- 默认不保存完整 Prompt、原始模型响应或 Chain-of-Thought。
- 用户反馈只进入待审核离线 Eval 数据，不直接修改 Prompt、记忆或模型路由。
- checkpoint、日志和错误响应不得包含 Secret、业务数据库凭据或未脱敏工具结果。

### 5.18 前端治理交互

- 503 协调故障显示友好的“系统暂时异常”，不暴露 Redis 细节。
- 70%/85% 显示预算状态，100% 展示预算追加确认卡。
- continuation 在 UI 中关联父任务；`superseded` 显示“已由后续任务接续”。
- 安全降级结果建议用户咨询医生或注册营养师，不显示虚假人工审核等待。

### 5.19 模型适配、用量与降级实现切片

- `providers/` 定义统一 `ModelProvider`、`ModelRequest`、`ModelResponse`、`ProviderAttempt` 和结构化错误分类；本地 deterministic provider 与云 provider 共享同一接口。
- `model_router.py` 按 `high/standard/economy/eval` 别名、风险、能力、预算等级和 fallback 白名单选择 provider，不从用户消息读取模型名或 endpoint。
- 每次逻辑调用生成 `model_call_id`，每次供应商尝试生成 `provider_attempt_id`；成功、失败、timeout、取消、Token、估算成本、价格表版本和延迟全部形成脱敏 Trace，并按现有 `run.model_usage` 契约上报。
- 70% 停止非必要 Reflection、缩减可选检索；85% 禁止 replan/rewrite 并允许低风险节点改走 economy；100% 不发起新调用，转可信部分结果或预算确认。
- 未配置真实 provider 时只允许 deterministic provider；`high/standard/economy/eval` 任一被路由到未配置的云 provider 时返回明确可观测错误，不猜测或静默回落。

### 5.20 方案状态与代码推进规则

- 在本方案完成前，不把 Router、Context Builder 或本地 checkpoint 的局部代码标记为 M1-4 完成；每项必须同时满足契约、Java/Python、配置、测试与前端条件。
- 所有新事件先由 Java 消费端接受或明确不发送；禁止 Python 单侧新增 `event_type` 破坏现有 Inbox 状态机。
- 每个实现切片完成后，更新本文件的对应 `[x]`、总 TODO 的同一项和功能实现说明；未完成能力继续保持 `[ ]`。

### 5.21 当前模型适配实现切片

- 已实现：`agent-runtime/model_provider.py` 提供 `ModelProvider`、`ModelRequest`、`ModelResponse`、`ProviderAttempt` 和结构化错误分类。
- 已实现：OpenAI-compatible 适配器同时接受 `/v1` 基地址和完整 `/v1/chat/completions` 地址，避免完整地址被重复拼接；两种配置形式均有自动化测试。
- 已实现：默认 `deterministic:local` provider 用于本地自动化测试；它不联网，不能表述为已接入真实大模型。
- 已实现：任意数量的 OpenAI Chat Completions 兼容云端点可由 `provider_id:model_name` 逻辑别名配置；`high/standard/economy/eval` 由确定性路由选择。
- 已实现：只有 timeout、限流和供应商暂不可用才按白名单 tier fallback；安全、权限、schema 和预算问题不能通过换模型绕过。
- 已实现：每次逻辑调用生成 `model_call_id`，每次 provider 尝试生成 `provider_attempt_id`，并通过已有 `run.model_usage` V1 事件上报 Token、成本估算、延迟和状态。
- 已验证：本地 pytest 覆盖别名路由、可重试 fallback、不可重试拒绝和 V1 usage payload；SiliconFlow 模型列表和 `deepseek-ai/DeepSeek-V4-Flash` 的真实 Chat Completions、primary + Eval gated 测试也已通过。
- 未完成：真实供应商价格表审计仍属于 M1-4 后续切片；LLM Judge 已有独立调用，Java 已将 `run.model_usage` 幂等写入 `model_usage_logs`。

### 5.22 当前 Eval、预算与 checkpoint 实现切片

- 已实现：复杂任务强制执行独立 `scene=eval` 调用；低风险任务按 `FOODMATE_AGENT_LLM_EVAL_SAMPLE_RATIO` 和 `run_id` 稳定采样。
- 已实现：Eval 与回答生成分别生成 `model_call_id`，每个供应商尝试生成独立 `provider_attempt_id`，并复用 `run.model_usage` 上报。
- 已实现：Eval 结构无效、Judge 拒绝、供应商不可用和高风险无人审核均不会发布候选正文；高风险统一返回安全降级理由。
- 已实现：预算策略输出 `allow_reflection`、`allow_optional_retrieval`、`allow_replan`、`allow_answer_rewrite`、`allow_new_model_call` 和 `requires_confirmation`，覆盖 70%/85%/100% 阈值。
- 已实现：Redis checkpoint 支持独立 key namespace、原子 CAS、TTL、大小限制和 Fernet 应用层加密；本地默认仍可使用内存后端运行单元测试。
- 已验证：Python pytest 已覆盖 Eval 独立调用、预算动作和 Redis checkpoint 加密/CAS；真实云端点已联调，Redis 容器故障注入和 Java 对账恢复执行器仍未完成。

### 5.23 当前状态图与上下文实现切片

- 已实现：`WorkflowGraph` 固定 `router/planner/execution/validator/composer/eval/terminal` 白名单节点与条件边；非法边返回 `WORKFLOW_EDGE_NOT_ALLOWED`。
- 已实现：每次节点进入计入 `max_total_steps`；超限进入 `terminal`，不再发起模型调用，并把节点、边和终止原因写入 checkpoint 与 `run.routed/run.completed` payload。
- 已实现：Context Builder 保留最近消息和当前输入优先级，并按 `FOODMATE_AGENT_CONTEXT_MAX_TOKENS` 裁剪旧消息，记录估算 Token 与来源 ID。
- 已实现：Java Redis admission/queued Outbox/lease/reconciler，摘要元数据 CAS、最近 8 条 Context 装配和长期记忆候选校验写入。
- 已实现：长期记忆管理 API 支持用户查询、修改、逻辑删除和冲突确认；冲突状态不会进入 Agent Context。
- 已实现：消息更正/删除 API 会使摘要失效；下一次超过最近 8 条阈值时按有效权威消息重建。Python 已加入最小 Step Validator，Proposal 协议拒绝非只读 SQL。
- 未完成：当前状态图仍是依赖无关实现，不是 LangGraph 包装；记忆变更后的摘要/缓存联动失效、生产级 aging 防饥饿和完整 Validator/Reflector 仍未完成。

## 6. 实施顺序与当前状态

- [x] 固化 ADR-0005、Topic、consumer group、消息 header 和传输无关 envelope。
- [x] 通过 V5/V6 补齐 MQ 基础结构、`superseded`、父子 Run 和预算快照基础数据模型。
- [x] 在 Compose 增加本地单节点 RocketMQ 和 Topic 初始化，并完成基础消息往返验证。
- [x] Java 完成 PostgreSQL Outbox Relay、MQ Event Consumer/Inbox 和基础 DLQ 对账。
- [x] Java 完成 Redis admission、queued Outbox、permit lease、queue/execution 超时释放和有限 priority + FIFO aging 基础；Java Proposal consumer、Tool Gateway、SQL Guard 和 Result producer 已接入，真实 Proposal/Result 消息及 Broker 故障注入已验证。
- [x] Python 完成 Redis Inbox/Event Outbox Repository 与 MQ command/event consumer/producer。
- [x] Python 完成 Redis checkpoint CAS/TTL/加密与 Event Outbox，并加入原生 LangGraph 白名单图包装；Proposal Outbox 业务协议、Result consumer 与 Java Tool Gateway 的真实往返已通过，恢复执行器仍未完成。
- [x] Python 建立依赖无关状态图、模型适配、预算、Context Builder、Composer、最小 Step Validator、Final Eval 和 Eval 前缓冲；Reflector 和完整 Validator 仍未完成。
- [x] 完成 Python Result consumer、Java Proposal consumer、Tool Gateway 和 Result producer 的本地协议接入；Java command RocketMQ 真实传输 E2E 已通过。
- [x] 完成 Python Proposal Publisher/Result consumer 与 Java Tool/SQL 控制面的真实 Proposal/Result RocketMQ 往返；E2E 已验证只读 SQL、审计和 Proposal Inbox 幂等。
- [x] 前端完成 continuation 与 `superseded` 状态展示。
- [x] 前端完成 503、预算确认和安全降级交互；浏览器 E2E 仍未完成。
- [x] 完成新增能力的 Python/Java 单元测试和前端生产构建。
- [x] 完成本地 PostgreSQL、Redis、RocketMQ Broker 停止/恢复注入；恢复后 PostgreSQL、Redis 和 Broker 均重新健康。
- [ ] 完成浏览器完整真实 E2E 和生产级并发验证；真实云模型联调及 Proposal/Result Broker 故障注入已完成，仍需更长时间的 Outbox 重试容量验证。

## 7. 验收门槛

### 7.1 Python

- 项目 `.venv` 中 pytest 全部通过。
- LangGraph 所有边、循环预算、超时、取消、checkpoint 恢复和 Eval 退回均有测试。
- 真实模型调用产生可核对的 Token、成本和路由记录。
- Redis Inbox/Outbox、重复消费、Broker ACK 丢失和进程重启测试通过。

### 7.2 Java 与数据库

- PostgreSQL Testcontainers 证明 Session 单 active、continuation 事务、状态约束和预算 revision。
- Redis 故障、permit 过期接管、队列满、排队超时和取消释放均有自动化断言。
- V1 兼容与新契约双端 fixture、canonical digest 测试通过。
- Outbox 提交后崩溃、Broker 发布重试、消费提交后 ACK 前崩溃、局部顺序、DLQ 对账均有断言。

### 7.3 前端

- 浏览器实际验证真实模型回答只在 Eval 后出现。
- 503、预算 70%/85%/100%、追加确认、continuation、安全降级和取消摘要均有 E2E。
- 已有 `run_id` 的失败不会自动创建重复 Run。

### 7.4 完成判定

- “模型能返回文本”不等于 M1-4 完成。
- 代码、迁移、配置、自动化测试和浏览器验证全部具备证据后，才创建 `功能实现说明/M1-4-...实现逻辑.md`。
- 未实现的 RAG、Tool/SQL 和业务写入继续留在 M1-5/M2，不得包装为本阶段完成。

### 7.5 总 TODO 对齐矩阵

| 总 TODO 未完成项 | 本方案实施章节 | 代码完成证据 |
|---|---|---|
| LangGraph Router/Planner/Validator/Reflector/Composer/Terminal Arbiter | 5.4-5.7 | 白名单图边、循环上限和每条退回边的单元测试 |
| 短期上下文、摘要压缩、删除重建与来源 ID | 5.12、3.4 | Java 权威读取/写入、CAS、失效传播和来源审计测试 |
| 长期记忆候选与查看/更正/删除 | 5.12、3.4 | Python 候选、Java 校验 API、冲突确认和删除 E2E |
| Redis 并发、队列、lease、aging 与故障关闭 | 5.10-5.11 | Redis 协调测试、503 映射、permit 释放和恢复对账 |
| 四类超时与 TimeoutSnapshot | 5.11、3.3 | queue/execution/node/waiting_user/cancel drain 自动化测试 |
| 多供应商模型、降级、Token/成本与追加预算 | 2.3、5.8-5.9、5.19 | provider fixture、usage、70/85/100%、预算 revision 与前端确认 E2E |
| Redis checkpoint、AOF、CAS、加密与 Java 对账 | 5.13 | 重启、CAS 冲突、加密、TTL 和副作用不重放测试 |
| 确定性 Eval、LLM Judge、离线回归与安全降级 | 5.7、3.4、5.19 | Eval fixture、独立 Judge 标记、正文延迟发布和 `request_review` 测试 |
| 回答分片 | 5.7 | Eval 通过后按 150ms/2048 字节的顺序事件测试 |
| 工具审批、预算恢复、Proposal/Result 与 SQL 边界 | 5.14-5.15 | 新 dispatch attempt、幂等、确认失效和 Java-only SQL Guard 测试 |
| Trace、指标、反馈与隐私 | 5.17-5.18 | 脱敏断言、无 Chain-of-Thought、反馈关联和高风险审计测试 |
## 8. 2026-07-28 最新复核记录

### 8.1 已完成

- [x] 真实云模型适配器契约联调：本地 OpenAI-compatible HTTP fixture 已验证成功响应、Authorization、模型名、Token 解析、429 fallback、非法响应和成本记录。
- [x] Proposal/Result 业务幂等故障单测：Result 发布失败返回 RETRY；Inbox 已完成时重投不重复执行 Tool；相同 proposal_id 不同 request_hash 被拒绝。
- [x] 修复真实浏览器路径的前置缺陷：真实模式首页不再把任务模板 ID 当作 session_id；会话/消息大整数 ID 改为字符串传输，避免 JavaScript 精度丢失；Vite 代理 Origin 跟随 API 目标端口。
- [x] Python pytest 27 项、Java API/依赖模块测试 27 项、前端 typecheck/build 均通过。

### 8.2 仍未完成

- [x] 真实云供应商调用：SiliconFlow `/v1/models` 和 `DeepSeek-V4-Flash` Chat Completions 均已成功；真实调用记录了 provider request ID、Token usage 和 latency。当前联调价格使用 0 占位，生产价格仍需配置真实值。
- [x] Proposal/Result 真实 Broker 故障注入：停止 Broker 时 Proposal 发送得到 `No route info of this topic`；恢复 Broker 后 Proposal -> Tool Gateway -> Result 测试成功，未删除 volume。
- [ ] 浏览器完整登录、会话、消息、SSE E2E：mock 页面已走到消息、工具成功和写入确认；真实 API 注册请求返回 `500 INTERNAL_ERROR`，因此尚不能标记真实登录和真实 SSE 闭环通过。
- [ ] 生产级并发、队列防饥饿和多实例：当前只有 Redis Lua/ZSET 代码基础，没有完成双 Java 实例共享 Redis、长时间 aging、租约回收和容量压力证据。

## M1-4 2026-07-28 本轮联调与故障演练记录

- 真实云模型：早期使用旧进程/旧状态测试时曾得到 HTTP 401；本轮重新请求 `/v1/models` 返回 200，并用 `DeepSeek-V4-Flash` 完成真实 Chat Completions、provider request ID、usage 和 gated Eval 验证。默认 tier 未改动，仍为 `deterministic:local`。
- 长时间并发基线：新增 `M14AdmissionLongStressTest`，显式开启后使用真实 Redis 和两个 admission service 实例运行。30 秒结果为 204 次完成准入、active 峰值 20、0 次协调错误、P50 4.705ms、P95 12.313ms、P99 123.302ms；容量拒绝 367731 次。该结果是本机单 Redis 基准，不是生产容量承诺。
- 多 JVM：两个独立 Java JVM 已在 18082/18083 同时启动，共享本地 PostgreSQL/Redis，并且两个 liveness 均返回 200；临时 JVM 已停止。跨实例 admission 规则仍由 Redis 共享服务测试覆盖，尚未完成正式部署拓扑和多实例业务流量验证。
- 进程故障恢复：PostgreSQL、Redis、RocketMQ Proxy、Broker 均完成停止后端口不可达、重新启动并恢复 healthy 的演练；NameServer 受 `restart: unless-stopped` 影响快速自动拉起，未形成可观测的长时间端口中断，但最终 healthy。未删除任何 volume。
- Proposal/Result 运行中故障注入：Broker 停止期间发送失败，恢复后同一真实成功测试通过；该证据证明失败可见和恢复可用，但还不是跨重启 Outbox 长时间重试容量结论。
- 浏览器：5173 当前为 mock 模式，真实模式 API 端口 18080 的注册接口返回 `500 INTERNAL_ERROR`；因此真实登录、真实会话创建、真实消息落库和真实 SSE 仍保持未完成。
- 结论：以上补齐了真实云调用和 Broker 故障注入恢复证据；浏览器完整真实 SSE E2E、生产级长时间容量结论和价格表审计仍保持未完成。

### 8.3 当前阻塞证据

- 本地 RocketMQ Proxy 曾报告创建 DefaultHeartBeatSyncerTopic 失败；重启 Proxy 和更换 Python consumer group 后，最新 Run 仍停在 queued，需要继续处理 Proxy/SDK 消费稳定性。
- Python Runtime 已补充未预期异常的 run.failed 记录，避免异常线程让 Run 永久停在 routed；该修复仍需在稳定 MQ 消费后重新跑 E2E。

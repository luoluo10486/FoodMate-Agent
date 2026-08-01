# M1-4 Python Agent Runtime 实现逻辑

> 模板提示：后续 AI 阅读本文档时，必须按功能点拆分为独立小节，不能把多个功能写成一大段；必须区分“目标设计、正在实现、已验证”，不得把本文方案或一次模型调用伪装成 M1-4 已完成。

## 0. 当前结论

本文描述 M1-4 的 Python Agent Runtime 最小真实闭环。当前代码覆盖 Runtime 接入、确定性模型、可配置云模型适配器、固定状态机、上下文装配、预算治理、Proposal/Result、checkpoint、Eval Gate、RocketMQ/Redis 技术链路和 Java 事件回传。

当前准确结论：

- 本地确定性 Runtime 闭环已完成并有自动化测试。
- 本地 PostgreSQL、Redis、RocketMQ、Java Tool Gateway、前端 SSE 闭环已完成联调。
- Eval 已进入 Runtime 主路径：run.eval_decided 在 run.answer_stream 之前发布，Eval 不通过时不发布候选正文。
- 默认模型仍为 deterministic:local；真实云模型必须通过环境变量显式配置。
- M1-4 仍不能宣称生产级整体完成。生产长压、容量 P95/P99、队列防饥饿、多实例 Agent 业务流量、进程故障恢复指标、真实云长时间稳定性、正式价格审计和生产 Eval 治理仍需独立验收。

### 0.1 当前验证证据

| 功能点 | 当前证据 | 结论边界 |
|---|---|---|
| Python Runtime | agent-runtime/.venv 执行 56 passed, 1 skipped | 覆盖本地协议、状态机、模型、Eval、恢复和传输测试，不代表生产容量 |
| Eval Gate | Golden、Judge schema/provider fail-closed、安全降级、正文延迟发布测试通过 | 是本地质量门，不是生产质量结论 |
| 浏览器闭环 | 登录、会话、消息、RocketMQ、Runtime、Java Inbox、最终 SSE 已完成本地验证 | 不代表生产网络和浏览器兼容性验收 |
| Proposal/Result | Python -> RocketMQ -> Java Tool Gateway -> PostgreSQL -> Result -> Python 已完成本地真实 E2E | 当前主要覆盖只读 SQL 和最小工具链路 |
| checkpoint 恢复 | Java 认证恢复入口、Redis/内存 CAS、事件对账和新 dispatch attempt 已完成本地验证 | 生产故障期间的恢复指标仍未完成 |
| 并发与长压 | Redis 本地基线已采集 P50/P95/P99 | 本机 Docker Redis 基线不能代替生产容量承诺 |

## 1. 文档范围与模块边界

### 1.1 Python Runtime 负责什么

1. 接收 Java 控制面的 RunCommand。
2. 按固定状态图执行路由、规划、上下文装配、模型调用、工具等待、候选生成和 Eval。
3. 只产生协议事件、模型调用摘要、记忆候选和 Tool Proposal。
4. 使用 Redis 保存技术性 checkpoint、Inbox 和 Outbox。
5. 通过 RocketMQ 或 HTTP 回传事件，不直接写 FoodMate 业务 PostgreSQL 表。
6. 收到 Java Tool Result 后继续执行后续 Composer，并重新经过 Eval Gate。

### 1.2 Python Runtime 不负责什么

1. 不直接读取用户、会话、消息、饮食日志或其他业务表。
2. 不持有 FoodMate 业务 PostgreSQL 用户名和密码。
3. 不直接执行 SQL；SQL 只能作为受控 Proposal 交给 Java Tool Gateway。
4. 不决定用户权限、Run 所有权、最终状态和业务持久化事实。
5. 不把模型输出直接当作可交付答案；候选必须经过硬规则和必要的独立 Eval。
6. 不把一次真实云调用成功当作供应商稳定性或生产质量验收。

### 1.3 主要代码映射

| 功能 | 主要文件 | 作用 |
|---|---|---|
| Agent 内核 | agent-runtime/agent_core.py | 路由、规划、上下文、状态图、预算、Composer、Eval、Proposal |
| 模型适配 | agent-runtime/model_provider.py | Provider、模型别名、tier 路由、fallback、超时、用量和成本 |
| Runtime 接入 | agent-runtime/runtime_server.py | HTTP 接入、事件发布、执行线程、取消、checkpoint、Tool Result 等待 |
| MQ/Redis | agent-runtime/mq_runtime.py | RocketMQ producer/consumer、Redis checkpoint、Inbox、Outbox |
| 恢复协议 | agent-runtime/recovery_protocol.py | checkpoint digest、旧 dispatch 和恢复命令校验 |
| LangGraph 包装 | agent-runtime/langgraph_adapter.py | 将固定白名单状态图包装为图执行入口 |
| Proposal 契约 | agent-runtime/proposal_protocol.py | Proposal 字段、request hash 和只读 SQL 校验 |
| Eval | agent-runtime/eval/ | Golden、Judge、校准样本和运行时指标 |

## 2. RunCommand 接入与 Dispatch 幂等

### 2.1 入口和字段

Runtime 暴露：

- POST /foodmate/internal/v1/runs：接收新 dispatch 或恢复 dispatch。
- POST /foodmate/internal/v1/runs/{run_id}/cancel：接收取消命令。

RunCommand 至少包含：

1. run_id：业务任务 ID；同一业务 Run 的多个接续 attempt 可以共享它。
2. dispatch_id：一次执行尝试的唯一 ID；恢复时必须生成新值。
3. attempt：当前 dispatch attempt 序号。
4. deadline_at：Java 固化的绝对截止时间。
5. message：当前用户消息。
6. authorized_context：Java 已完成权限过滤的消息、摘要、记忆、引用和工具结果。
7. runtime_options 或 budget_snapshot：Prompt、预算和上下文配置快照。
8. trace_id、request_id 等链路追踪字段。

### 2.2 处理步骤

1. Handler 检查路径是否为 dispatch 或 cancel。
2. 生产模式校验 Bearer JWT 的发行方、受众、scope、过期时间和 jti。
3. 校验 X-Contract-Version 是否匹配。
4. 解析 JSON，检查 run_id、dispatch_id、attempt、deadline_at。
5. 以 dispatch_id 查询幂等事实。
6. 相同 dispatch_id 且请求体相同：返回 202、duplicate=true，不重复启动线程。
7. 相同 dispatch_id 但请求体不同：返回 409/RUNTIME_DISPATCH_IDEMPOTENCY_CONFLICT。
8. 新 dispatch 启动后台执行线程并立即返回 202。
9. 业务状态、用户权限和持久化事实仍由 Java 负责，Python 进程内字典不是业务权威。

### 2.3 失败分支和状态

- 字段缺失或 JSON 无效：400/RUNTIME_CONTRACT_INVALID。
- JWT 或合约版本错误：401/RUNTIME_AUTH_INVALID。
- dispatch 内容冲突：409/RUNTIME_DISPATCH_IDEMPOTENCY_CONFLICT。
- 执行阶段失败：发布 run.failed，不只写 Python 日志。
- 当前状态：接入、认证、合约版本、幂等和取消入口已实现并有回归测试。
- 生产待办：进程重启后 dispatch 事实继续依赖 Java Outbox、RocketMQ Inbox 和业务状态对账。

## 3. 固定状态机与 LangGraph 包装

### 3.1 节点职责

1. start：初始化状态图。
2. router：识别意图、复杂度、风险和缺参槽位。
3. planner：生成固定步骤。
4. clarification：缺参时生成澄清候选。
5. execution：准备上下文、Proposal 或模型调用。
6. validator：检查计划、来源和工具结果。
7. composer：生成候选答案。
8. eval：执行硬规则和独立 Judge。
9. terminal：结束 dispatch。

### 3.2 允许的边

固定边为：

- start -> router
- router -> planner、composer 或 terminal
- planner -> clarification、execution、composer 或 terminal
- clarification -> composer 或 terminal
- execution -> validator 或 terminal
- validator -> composer、planner 或 terminal
- composer -> eval 或 terminal
- eval -> terminal

### 3.3 推进步骤

1. WorkflowGraph.enter 先检查当前节点是否允许目标边。
2. 检查节点数量是否达到 max_total_steps。
3. 记录 nodes 和 from/to transition。
4. 非法边抛出 WORKFLOW_EDGE_NOT_ALLOWED。
5. 达到上限时转 terminal，原因写为 MAX_TOTAL_STEPS，禁止后续模型调用。
6. langgraph_adapter.py 只包装固定节点和条件边，不开放模型动态加边或任意工具调用。

### 3.4 状态

- 已实现：固定状态图和 LangGraph 白名单包装。
- 已验证：非法边、最大步骤、复杂任务路径和图编译。
- 生产待办：完整业务节点、可持久化图状态和多实例恢复运行验证。

## 4. Router：意图、复杂度、风险和缺参

### 4.1 当前识别规则

- record：记录、吃了、早餐、午餐、晚餐。
- planning：计划、食谱、购物清单。
- analysis：分析、营养、蛋白质、热量。
- knowledge_qna：未命中以上意图时的默认知识问答。

### 4.2 处理步骤

1. 清理消息文本。
2. 先识别疾病、诊断、处方、过敏反应等高风险词。
3. 根据关键词选择 intent。
4. 规划/食谱请求不包含“天”时增加 days 缺失槽位。
5. 记录类文本超过长度阈值时标记 complex，否则 simple。
6. 未命中专用意图时返回 knowledge_qna。
7. 输出 intent、complexity、risk_level、missing_slots。

### 4.3 安全规则和状态

- Router 不识别模型返回的状态边、工具名或权限。
- 高风险只影响安全策略和 Eval，不允许直接给出医疗结论。
- 缺参时后续只能走 clarification，不能同时执行工具或生成完整业务答案。
- 当前已实现并由 Golden 覆盖；后续扩展意图需要误判样本和人工校准，不直接把 LLM Router 接入主状态机。

## 5. Planner 与 Step Validator

### 5.1 Planner

1. 有 missing_slots：只生成 clarify。
2. simple：生成 compose。
3. complex：生成 retrieve_authorized_context、validate_facts、compose。
4. 计划带 route 和 plan_version，后续不能只相信步骤字符串。

### 5.2 Validator

1. 检查步骤非空。
2. 检查步骤属于允许集合。
3. 检查 plan.route 与 Router 结果完全一致。
4. clarify 必须有缺参。
5. complex 必须包含 validate_facts。
6. 有缺参时计划必须只有 clarify，禁止副作用。
7. 所有 Context source ID 必须非空。
8. 工具结果 invocation_id 必须存在且不重复。
9. 工具结果 status 只能是 succeeded、failed 或 rejected。
10. 工具结果 invocation_id 必须存在于 Context 的 invocation 来源集合。
11. 任意失败都生成安全降级结果，不再调用模型。

### 5.3 状态

- 已实现：固定计划、来源校验、工具结果校验。
- 已验证：非法步骤、复杂计划缺事实校验、重复 invocation 和来源不一致。
- 后续增强：Tool Registry 和细粒度 Schema 属于 Java Tool Gateway，不在 Python 复制。

## 6. 上下文装配：短期记忆、摘要和长期记忆

### 6.1 数据边界

Java 先按用户和 Session 权限装配 authorized_context：

1. recent_messages：最近有效消息。
2. session_summary：旧消息摘要。
3. long_term_memories：确认且未过期的用户记忆。
4. citations：有权限的知识引用。
5. tool_results：Java Tool Gateway 已执行的结果。

Python 不查询 PostgreSQL，也不根据未授权输入拼接其他用户数据。

### 6.2 最近消息步骤

1. 读取 authorized_context.recent_messages。
2. 追加当前 message；已有同 message_id 时不重复添加。
3. 保留最后 FOODMATE_AGENT_CONTEXT_MAX_RECENT_MESSAGES 条，默认 8。
4. 估算 Context token，超过 FOODMATE_AGENT_CONTEXT_MAX_TOKENS 时从最旧消息开始裁剪。
5. 始终保留当前输入。
6. 保存消息、摘要、记忆、工具结果、sources 和 estimated_tokens。

### 6.3 摘要步骤

1. Java 在有效消息超过 8 条后更新会话摘要，Python 不写权威摘要。
2. Python 将摘要与最近 8 条消息一起交给 Composer。
3. summary_id 写入 sources.summary_id。
4. 原消息更正或删除时，Java 先失效摘要，再从有效消息重新生成。
5. Python 不把摘要视为高于当前消息的绝对事实，仍受 Validator 和 Eval 约束。

### 6.4 长期记忆步骤

1. Java 负责用户归属、确认状态、有效期、冲突和逻辑删除。
2. M1-4 只接收已授权记忆，不把所有历史消息自动变成记忆。
3. memory_id 写入 sources.memory_id。
4. 过期、冲突、未确认、已删除记忆不得进入 authorized_context。
5. 当前最多注入最近 8 条授权记忆；意图精细检索、衰减和删除防再生仍是后续治理项。
6. 用户偏好、饮食日志、周食谱等权威实体仍由 Java 关系表保存，不复制成 Python 长期记忆文本。
7. M1 不引入 pgvector；只有结构化检索经 Eval 证明不足时才评估向量检索。

### 6.5 状态

- 已实现：8 条消息、摘要、记忆、引用、工具结果、来源 ID 和 token 裁剪。
- 已验证：当前消息保留、来源追踪和 token 上限。
- 生产待办：摘要/记忆联动失效、精细检索、来源落库和长期记忆治理。

## 7. 模型适配器、Tier、Fallback 和超时

### 7.1 Provider

ModelProvider 提供 complete(model_name, request) 边界：

1. DeterministicModelProvider：本地测试 stub，不发网络请求。
2. OpenAICompatibleModelProvider：调用兼容 Chat Completions 的云服务。

### 7.2 云请求步骤

1. ModelRouter 根据 scene 选择 standard、high、economy 或 eval tier。
2. 从 FOODMATE_MODEL_TIER_<TIER> 解析 provider:model。
3. 根据 provider ID 读取 base URL 和 API key。
4. base URL 是 /v1 时补充 /chat/completions；完整路径不重复补充。
5. 发送 model、messages、temperature、max_tokens；Eval 可附带 response_format 和 extra_body。
6. 使用 Bearer 认证，密钥只来自环境变量，不写代码、文档和日志。
7. 解析 choices[0].message.content 和 usage。
8. 记录 provider request ID、输入/缓存输入/输出 token、延迟和 price_version。

### 7.3 默认模型

1. standard、high、economy、eval 未配置时均使用 deterministic:local。
2. 只有显式设置 FOODMATE_MODEL_TIER_* 云别名才发网络请求。
3. 存在 API key 不等于自动启用云模型，避免开发测试意外消耗额度。
4. Composer 和 Eval 可以使用不同 tier。

### 7.4 Fallback

1. 先调用主 tier。
2. 只有 MODEL_TIMEOUT、MODEL_RATE_LIMIT、MODEL_PROVIDER_UNAVAILABLE 允许 fallback。
3. MODEL_PROVIDER_REJECTED、MODEL_ALIAS_INVALID、MODEL_PRICE_UNCONFIGURED 不自动切换掩盖配置问题。
4. 每次尝试记录 ProviderAttempt；同一逻辑调用共享 model_call_id。
5. 全部失败时抛出 ModelProviderError，Runtime 发布 run.failed。

### 7.5 超时

1. Provider 有自己的超时。
2. Composer 和 Eval 有局部 timeout 环境变量。
3. Java 固化 Run deadline_at。
4. HTTP timeout 取 provider、节点和 Run 剩余时间的较小值。
5. deadline 已过期时返回 RUNTIME_DEADLINE_EXCEEDED，不能延长业务 deadline。

### 7.6 状态

- 已实现：确定性 provider、OpenAI-compatible provider、多 tier、fallback、局部超时、usage 和成本。
- 已验证：本地 provider、fixture provider、真实云单次适配和独立 Eval 调用。
- 生产待办：真实云长时间稳定性、多供应商容量、正式价格核准和账单对账。

## 8. Token/成本预算和分级降级

### 8.1 BudgetSnapshot

Java 传入：

- max_total_tokens：总 token 上限。
- max_cost_cny：单次 Run 成本上限。
- max_total_steps：状态图步骤上限。
- max_model_calls：模型调用次数上限。
- max_replans：重新规划次数上限。
- max_answer_rewrites：答案重写次数上限。
- revision：预算追加或变更版本。
- config_version：预算配置版本。

### 8.2 计算步骤

1. 每个 ProviderAttempt 记录输入、缓存输入、输出和总 token。
2. ModelRouter 根据命中价格版本计算 CNY 成本。
3. Usage 汇总 token、成本、调用次数和状态图步骤。
4. 预算比例取 token 比例和成本比例的较大值。
5. 开启 FOODMATE_MODEL_PRICE_AUDIT_REQUIRED=true 且缺少云价格时，在请求前返回 MODEL_PRICE_UNCONFIGURED。
6. deterministic provider 不需要云价格，但仍记录本地 usage。

### 8.3 阈值动作

| 使用比例 | 模式 | 动作 |
|---|---|---|
| 小于 70% | normal | 允许 reflection、可选检索、replan、answer rewrite 和新模型调用 |
| 70% 到小于 85% | reduced_reflection | 关闭 reflection，保留有限 replan/rewrite 和新模型调用 |
| 85% 到小于 100% | economy | 关闭 reflection、可选检索、replan、rewrite，只保留必要调用 |
| 大于等于 100% 或达到调用上限 | partial | 禁止新模型调用，返回降级结果并标记 requires_confirmation |

### 8.4 用户确认追加预算

1. Runtime 不接受用户直接修改的预算数字作为权威值。
2. 达到硬上限时发布 requires_confirmation=true 和预算动作。
3. Java 前端展示追加预算确认。
4. 用户确认后，Java 校验用户授权额度、账户/租户剩余额度、供应商可用额度和系统单次安全上限。
5. 通过后创建新的预算 revision 和 dispatch attempt，旧 dispatch 不复用。
6. Python 恢复前校验 budget_revision。
7. 未确认或额度不足时返回安全降级结果，不继续调用模型。

### 8.5 状态

- 已实现：预算快照、token/成本汇总、70%/85%/100% 固定动作和确认标记。
- 已验证：阈值、硬上限和 UTF-8 分片。
- 生产待办：正式额度账单、价格人工复核和预算告警。

## 9. Composer 和 Reflection

### 9.1 Composer 步骤

1. Router 输出 route。
2. ContextBuilder 输出授权 Context。
3. Planner 输出固定计划。
4. Validator 校验计划和来源。
5. DeterministicComposer 生成本地可重复候选，云模式由 ModelRouter 生成候选。
6. 候选只在 Runtime 内存中存在，Eval 前不发布 run.answer_stream。

### 9.2 Reflection 步骤

1. 检查答案非空。
2. 检查答案长度不超过 12000 字符。
3. 缺参回答必须包含需要补充的槽位。
4. 复杂任务必须存在授权消息来源。
5. 成功工具结果不能同时没有 rows 和 error_code。
6. 检查失败时替换为安全结果，后续仍继续经过 Eval。

### 9.3 状态

- 已实现：确定性 Composer 和 Reflection。
- 已验证：空答案、超长答案、缺参和不完整工具结果。
- 边界：Reflection 不是 Chain-of-Thought，也不能代替完整质量评测；真实业务、RAG 和完整 Tool/SQL 场景仍需长时间验证。

## 10. Tool Proposal：Python 只提议，Java 执行

### 10.1 Proposal 生成条件

1. authorized_context.sql_read_request 必须由 Java 提供。
2. route intent 必须为 record 或 analysis。
3. 请求有 statement 和 invocation_id；缺少 invocation_id 时用 statement digest 生成稳定 ID。
4. 已有 tool_results 时不生成重复 Proposal。
5. 包装为 proposal_type=sql_read、schema_version=v1 的 Proposal。
6. validate_proposal 校验字段和 request_hash。

### 10.2 SQL 安全限制

1. 必须是单条只读查询。
2. 必须以 SELECT 开始。
3. 拒绝 INSERT、UPDATE、DELETE、DROP、ALTER、TRUNCATE 等写操作和 DDL。
4. Python 不持有数据库凭据。
5. Python 不拼接业务 SQL，不绕过 Java Policy。

### 10.3 发布和 Java 执行

1. Python 保存 tool_wait checkpoint。
2. 发布 run.checkpoint_saved，只传版本、digest、invocation 摘要。
3. 发布 run.tool_started，不传 SQL 原文。
4. Proposal 进入 RocketMQ Tool Proposal Topic。
5. Java 以 proposal_id + request_hash 写 Inbox 去重。
6. Java 校验 Run 所有权、工具类型、SQL Guard、用户过滤、行数和超时。
7. Java 执行只读 SQL并写入 sql_query_audits。
8. Java 在 Result Topic 返回 invocation_id、status、rows 或结构化 error。

## 11. Tool Result 回注和二次 Composer

### 11.1 处理步骤

1. Result Consumer 收到 Java Result。
2. Redis Result Inbox 按 proposal_id + request_hash claim。
3. 重复 Result 直接 ACK，不重复回调。
4. 新 Result 按 proposal_id 放入等待表并唤醒 await_result。
5. Runtime 校验 invocation_id、request_hash 和 status。
6. 发布 run.tool_finished，只携带 Proposal ID、invocation ID、状态和错误码。
7. 以 CAS 更新 checkpoint 的 current_node=execution 和 completed_invocation_ids。
8. 将 Result 加入 authorized_context.tool_results。
9. 再次执行 run_deterministic。
10. 合并两次 Composer 的 usage、cost、model_attempts。
11. 合并候选重新执行 Reflection、硬规则 Eval 和必要的 LLM Judge。

### 11.2 失败分支

- Java status=failed：保留失败状态，不能伪装为成功数据。
- Java status=rejected：不自动重试策略拒绝。
- 超过 FOODMATE_AGENT_TOOL_RESULT_TIMEOUT_SECONDS：run.failed，错误码 TOOL_RESULT_TIMEOUT，由 Java 决定 retry attempt。
- Redis/MQ 消费失败：不 ACK，让 RocketMQ 至少一次重投；Inbox 和 invocation 幂等吸收重复。

### 11.3 状态

- 已实现和验证：Proposal Publisher、Result Consumer、二次 Composer、SQL 审计、重复 Proposal/Result 幂等和工具超时。
- 生产待办：RAG、完整业务 Tool、复杂 SQL Agent 和长时间跨进程编排。

## 12. Checkpoint 保存和恢复

### 12.1 可恢复边界

1. tool_wait：Proposal 已生成并等待 Java Result。
2. execution：Result 已对账，准备继续 Composer。

普通中间变量、完整 Prompt 和模型内部推理不作为公开恢复事件内容。

### 12.2 保存步骤

1. 写入 schema_version、workflow_version、prompt_version。
2. 写入 run_id、dispatch_id、attempt、current_node。
3. 写入 Java 固化的 deadline_at 和 budget_revision。
4. 写入 completed_invocation_ids 和 pending_proposals。
5. 通过 backend 保存并返回递增 checkpoint_version。
6. 计算 checkpoint_digest。
7. 发布 run.checkpoint_saved，只传版本、digest、节点、预算 revision 和 invocation 摘要。
8. tool_wait 同时保留不可变 recovery snapshot，避免 Result 更新覆盖恢复边界。

### 12.3 Redis Checkpoint

1. 默认测试后端是 InMemoryCheckpoint。
2. FOODMATE_AGENT_CHECKPOINT_BACKEND=redis 时使用 Redis。
3. key 由前缀、Run 和 dispatch 组成。
4. 保存使用版本 CAS；版本不符返回 CHECKPOINT_CAS_CONFLICT。
5. 超过 FOODMATE_AGENT_CHECKPOINT_MAX_BYTES 返回 CHECKPOINT_TOO_LARGE。
6. 使用 TTL 清理技术状态，默认 7 天。
7. 开启加密时必须有 FOODMATE_AGENT_CHECKPOINT_ENCRYPTION_KEY。
8. 不保存 Chain-of-Thought、完整 Prompt、API key 或未脱敏敏感信息。

### 12.4 Java 恢复流程

1. Python 发布 run.checkpoint_saved。
2. Java Event Inbox 持久化 checkpoint version、digest、节点和 budget revision。
3. 用户或恢复执行器调用 /api/agent-runs/{runId}/recover-from-checkpoint。
4. Java 校验用户、Session、Run 所有权、当前状态、取消状态、deadline 和预算 revision。
5. Java 对账 PostgreSQL Inbox 的 checkpoint、已完成工具调用和 Result。
6. 创建新的 dispatch_id + attempt，不能复用旧 dispatch。
7. 将已对账 checkpoint 元数据和 Tool Result 发给 Python。
8. Python 校验旧 dispatch、attempt、version/digest、Run ID、节点、deadline、预算 revision 和 invocation 集合。
9. 通过后将 Result 放回授权 Context，从可恢复节点继续。

### 12.5 恢复失败

- 上下文不是字典：RECOVERY_CONTEXT_INVALID。
- 复用旧 dispatch：RECOVERY_DISPATCH_REUSED。
- attempt 不递增：RECOVERY_ATTEMPT_INVALID。
- checkpoint 不存在或 digest/version 不一致：RECOVERY_CHECKPOINT_NOT_FOUND 或 RECOVERY_CHECKPOINT_CONFLICT。
- Run、节点、deadline、预算或 invocation 不一致：返回对应恢复错误。
- Java 不得把失效、取消或过期 Run 强行重新激活。

### 12.6 状态

- 已实现：内存/Redis CAS、TTL、可选加密、checkpoint 事件、Java 对账和新 attempt 协议。
- 已验证：Python 恢复校验、Java 控制器鉴权、PostgreSQL Inbox 对账和本地重启恢复闭环。
- 生产待办：多实例故障恢复耗时、成功率、保留策略和 Redis 故障指标。

## 13. Eval Gate：答案交付前的质量门

### 13.1 执行顺序

1. Composer 生成候选。
2. Reflection 执行确定性结构检查。
3. DeterministicEvalGate 检查空答案、预算耗尽和高风险。
4. 复杂/高风险请求调用独立 Judge；低风险请求按 Run ID 稳定 hash 采样。
5. Judge 只接收必要题目、候选、风险和 rubric。
6. Judge 必须返回 passed、score、reason。
7. LlmEvalGate 校验 JSON、布尔值、分数有限性、分数范围和最低阈值。
8. 生成 EvalDecision。
9. 先发布 run.eval_decided，再决定正文。

### 13.2 Fail-closed

以下情况都不允许发布候选正文：

1. 候选为空。
2. 预算耗尽。
3. Judge JSON 无法解析或缺字段。
4. passed 不是布尔值。
5. score 不是有限数或不在 0..1。
6. 阈值配置非法。
7. Judge provider 超时、限流、拒绝或不可用。
8. Judge 拒绝或分数低于阈值。
9. 高风险请求没有真实审核人员。

### 13.3 高风险处理

当前没有真实审核人员，因此不进入 waiting_review：

1. 不交付高风险候选。
2. 返回安全降级回答。
3. 告知用户需要医生或注册营养师判断。
4. 记录 request_review 原因供离线审计。
5. Eval 事件只传 result、reason、score、evaluator_version，不传候选正文、Prompt 或 Chain-of-Thought。

### 13.4 正文发布

1. Eval pass：按 UTF-8 字节上限切分并发布 run.answer_stream。
2. Eval degrade/reject：不发布候选正文，只在 run.completed 中返回安全降级结果和原因。
3. answer_stream 必须发生在 eval_decided 之后。
4. 单个事件默认最多 2048 字节。

### 13.5 Golden 与生产 Eval 的区别

1. Golden 回归使用固定 eval/golden_cases.json 和确定性 Runtime，检查路由、复杂度、风险、Eval reason、答案关键字和模型场景是否回退。
2. Golden 回归是代码行为契约测试，不是生产真实用户质量评测。
3. 生产 Eval 还需要固定 Prompt、模型、价格版本、人工 reviewed calibration、统一指标、告警和账单关联。
4. eval/calibration_samples.json 的样本必须人工审核，pending 不能算校准证据。

### 13.6 状态

- 已实现：硬规则、独立 Judge、schema/provider fail-closed、风险降级、正文延迟发布、Golden 和进程内 P95/P99。
- 已验证：Python .venv 56 passed, 1 skipped。
- 生产待办：人工校准、统一指标库、通过/降级/失败/P95/P99 告警和生产质量结论。

## 14. 事件生命周期与 SSE

### 14.1 公共字段

每个事件包含 schema_version、event_id、run_id、dispatch_id、attempt、event_seq、request_id、trace_id、request_hash、occurred_at、event_type 和 payload。

### 14.2 正常流程

run.accepted -> run.routed -> run.model_usage(Composer) -> run.model_usage(可选 Judge) -> run.eval_decided -> run.answer_stream(仅 pass) -> run.completed

### 14.3 缺参流程

run.accepted -> run.routed -> run.checkpoint_saved -> run.eval_decided -> run.clarification_requested

这里不发布完整正文。用户补充参数后，Java 创建新的 continuation Run，旧 Run 进入 superseded；工具审批或预算追加恢复原业务 Run，但创建新的 dispatch attempt。

### 14.4 Tool 流程

run.accepted -> run.routed -> run.checkpoint_saved -> run.tool_started -> Java Tool Gateway -> run.tool_finished -> run.model_usage -> run.eval_decided -> run.answer_stream -> run.completed

### 14.5 取消和失败

- 取消：accepted -> routed -> cancel_acknowledged -> cancelled。
- 模型失败：先发布已经发生的 model_usage，再发布 failed。
- Tool 超时：发布 failed，错误码 TOOL_RESULT_TIMEOUT。
- 未预期异常：打印堆栈并尽力发布 RUNTIME_EXECUTION_FAILED，避免 Java/前端永久停在 routed。

### 14.6 序号规则

1. accepted 从 1 开始。
2. routed 固定为 2，必须在模型、工具、恢复校验前发布。
3. 后续事件在当前 dispatch 内递增。
4. Java 用 event_seq 检查连续性。
5. event_id/request_hash 由 Java Inbox 吸收重复事件。

## 15. RocketMQ、Redis Inbox 和 Outbox

### 15.1 事件发布

1. Runtime 生成事件 envelope 和 request hash。
2. RocketMQ 模式下先写 Redis Event Outbox。
3. Producer 用相同 event_id、request_hash 和 payload 发布。
4. Broker 确认后 ACK/删除 Outbox。
5. Java Event Consumer 先 Inbox 去重，再更新 AgentRun、Dispatch、SSE 和审计。
6. RocketMQ 是至少一次投递，不宣称 exactly-once。

### 15.2 Proposal Outbox

1. Proposal 原文先写 Redis Proposal Outbox。
2. Producer 发布 Tool Proposal Topic。
3. 失败不删除 Outbox，同一 Proposal 可重发。
4. Java 用 proposal_id + request_hash 去重。
5. Broker 确认只代表消息已接收，不代表业务 Tool 已执行。

### 15.3 Command/Result Inbox

1. Python Command Consumer 收到 Java dispatch。
2. Redis Command Inbox 用 dispatch_id + request_hash claim。
3. 相同命令直接 ACK，不重复启动。
4. Result Inbox 用 proposal_id + request_hash claim。
5. Redis 或执行失败不 ACK，让 MQ 重投或进入死信。

### 15.4 启动和 readiness

1. Producer/Consumer 启动有 startup timeout，避免 Proxy route 查询永久阻塞。
2. readiness 报告 checkpoint、Redis、Event Producer、Proposal Producer、Command Consumer、Result Consumer。
3. 依赖不可用时返回 503/RUNTIME_COORDINATION_UNAVAILABLE。
4. 不因 Redis/MQ 不可用切换到进程内 semaphore 或丢弃事件。

### 15.5 状态

- 已实现和验证：Redis Outbox/Inbox、RocketMQ Producer/Consumer、至少一次、启动超时、重复消息和 Broker 故障恢复发送。
- 生产待办：跨实例 Outbox 长重试容量、死信运营、Broker 集群容量和故障期间业务恢复指标。

## 16. 取消、超时和资源释放

### 16.1 取消

1. Java 生成带 cancel_id 的命令。
2. Runtime 校验 path Run ID 和 body Run ID 一致。
3. 将 Run ID 加入取消集合。
4. accepted 后、Eval 后、正文分片前后检查取消状态。
5. 发布 cancel_acknowledged 和 cancelled，不继续正文。
6. Java 负责最终 Run 状态、permit 和持久化队列事实释放。

### 16.2 超时层级

1. Queue timeout：Java/Redis 准入层控制，排队不应吞掉执行时间。
2. Run deadline：Java 接受时固化。
3. Node/provider timeout：Python 为 Composer、Eval、Tool Result 设置局部上限。
4. Checkpoint TTL：只控制技术数据保留，不延长业务 Run。
5. 超时后发布结构化 failed，并标记是否可重试。

### 16.3 状态

- 已实现：模型局部 timeout、Tool Result timeout、deadline 传递、取消事件和失败事件。
- 生产待办：node timeout、waiting_user 专用 deadline、cancel drain 和进程异常后的全量 permit 释放验证。

## 17. 日志、Trace 和敏感数据

### 17.1 可记录

1. run_id、dispatch_id、attempt、request_id、trace_id。
2. 状态节点、事件类型、序号、耗时和错误码。
3. provider code、model name、provider request ID、usage、cost、price_version。
4. Eval result、reason、score、evaluator_version 和 gate latency。
5. Proposal ID、invocation ID、Tool 状态和审计 ID。

### 17.2 禁止记录

1. API key、Authorization header 和完整密钥。
2. Chain-of-Thought 或模型内部推理。
3. 未脱敏完整 Prompt 和完整原始模型响应。
4. SQL 原文进入 Runtime 事件正文。
5. 不必要的用户敏感资料。

### 17.3 状态

- 已实现：模型调用摘要、Eval 元数据、事件 hash、checkpoint digest 和敏感字段边界。
- 生产待办：统一 Trace、脱敏、用户反馈、告警和 Java/Python 指标关联。

## 18. 关键环境变量

实际值以项目根目录 .env、docker/.env.example 和部署环境注入结果为准；密钥不得写入 Git。

| 环境变量 | 默认值 | 作用 |
|---|---:|---|
| FOODMATE_AGENT_TRANSPORT | http | http 使用 Java callback，rocketmq 使用 MQ |
| FOODMATE_AGENT_CHECKPOINT_BACKEND | inmemory | 本地内存，真实运行 redis |
| FOODMATE_AGENT_CONTEXT_MAX_RECENT_MESSAGES | 8 | 最近原始消息上限 |
| FOODMATE_AGENT_CONTEXT_MAX_TOKENS | 12000 | Context 估算 token 上限 |
| FOODMATE_AGENT_STREAM_CHUNK_MAX_BYTES | 2048 | 单个回答事件 UTF-8 字节上限 |
| FOODMATE_AGENT_MAX_TOTAL_STEPS | 30 | 状态机步骤上限 |
| FOODMATE_AGENT_MAX_MODEL_CALLS | 12 | Run 模型调用上限 |
| FOODMATE_AGENT_MAX_STEP_RETRIES | 2 | 步骤重试预算 |
| FOODMATE_AGENT_MAX_REPLANS | 1 | 重新规划预算 |
| FOODMATE_AGENT_MAX_ANSWER_REWRITES | 1 | 答案重写预算 |
| FOODMATE_AGENT_TOOL_RESULT_TIMEOUT_SECONDS | 30 | Tool Result 等待上限 |
| FOODMATE_AGENT_COMPOSER_TIMEOUT_SECONDS | 45 | Composer provider 上限 |
| FOODMATE_AGENT_EVAL_TIMEOUT_SECONDS | 20 | Eval provider 上限 |
| FOODMATE_AGENT_LLM_EVAL_ENABLED | true | 独立 Judge 开关 |
| FOODMATE_AGENT_EVAL_SAMPLE_RATIO | 0.20 | 简单低风险请求采样比例 |
| FOODMATE_AGENT_EVAL_MIN_SCORE | 0.75 | Judge 最低分 |
| FOODMATE_MODEL_TIER_STANDARD | deterministic:local | 普通 Composer |
| FOODMATE_MODEL_TIER_HIGH | deterministic:local | 高复杂度 Composer |
| FOODMATE_MODEL_TIER_ECONOMY | deterministic:local | 预算降级模型 |
| FOODMATE_MODEL_TIER_EVAL | deterministic:local | 独立 Eval 模型 |
| FOODMATE_MODEL_FALLBACK_ENABLED | true | fallback 开关 |
| FOODMATE_MODEL_PRICE_AUDIT_REQUIRED | false | 云模型价格审计开关 |
| FOODMATE_AGENT_CHECKPOINT_TTL_DAYS | 7 | Redis checkpoint 保留天数 |
| FOODMATE_AGENT_CHECKPOINT_MAX_BYTES | 262144 | checkpoint 最大字节 |
| FOODMATE_AGENT_CHECKPOINT_ENCRYPTION_ENABLED | true | checkpoint 加密开关 |
| FOODMATE_ROCKETMQ_STARTUP_TIMEOUT_SECONDS | 15 | MQ 启动上限 |

### 18.1 读取顺序

1. 进程环境变量优先级最高。
2. runtime_env.py 启动时读取项目根目录 .env，用于本地开发。
3. Docker Compose 可由 docker/.env 或环境注入覆盖。
4. 上表中的 Run 预算变量由 Java 的 AgentRunBudgetDefaults 读取并固化进 BudgetSnapshot；Python 主要消费 Java 传入的快照，不自行重新决定业务预算。
5. Java 和 Python 可以读取同一份 .env；生产推荐使用 Secret/Config 注入，不把密钥放进镜像或 Git。
6. 修改模型、价格、预算或 MQ 配置后重启对应进程，并通过 readiness 和配置审计确认生效。

## 19. 测试和验收

### 19.1 Python 测试

执行：

    cd D:\develop\FoodMate\agent-runtime
    .venv\Scripts\python.exe -m pytest -q

重点覆盖：

1. Router、Planner、WorkflowGraph 和 StepValidator。
2. 最近 8 条消息、摘要、记忆、来源和 token 裁剪。
3. 预算阈值、模型调用上限和 UTF-8 分片。
4. Provider alias、fallback、endpoint、timeout、usage 和 price fail-closed。
5. Proposal 只读校验、request hash 和 invocation 幂等。
6. checkpoint CAS、恢复 digest、deadline 和预算 revision。
7. Eval Golden、Judge schema/provider failure、安全降级和正文延迟发布。
8. RocketMQ/Redis startup timeout、Inbox、Outbox 和重复消息。

### 19.2 Golden 回归

1. 读取 agent-runtime/eval/golden_cases.json。
2. 用 rubric.command_for 生成固定命令。
3. 用 run_deterministic 执行，不需要云凭据。
4. 用 rubric.check_case 对比 intent、complexity、risk、Eval result、Eval reason、答案片段和模型场景。
5. 代码修改后必须确认 Golden 变化是业务规则有意变更，而不是随意修改期望值来通过测试。

### 19.3 Java 和浏览器联调

1. 启动 PostgreSQL、Redis、RocketMQ Proxy/Broker。
2. 启动 Java 控制面和 Python Runtime。
3. 浏览器完成登录、创建会话、发送消息。
4. 校验 Java Dispatch Outbox -> RocketMQ -> Python -> Event Inbox -> SSE。
5. 校验 Proposal -> Tool Gateway -> PostgreSQL audit -> Result -> Python。
6. 校验 eval_decided 在 answer_stream 之前。
7. 注入重复消息、Tool 超时、Broker 中断、Runtime 重启和 checkpoint 恢复。

### 19.4 生产尚缺证据

1. 生产资源长时间压力和容量 P95/P99。
2. 队列 aging、防饥饿和队列满时的用户确认/降级。
3. 多 Java 实例真实 Agent 业务流量。
4. Redis、RocketMQ、PostgreSQL 进程中断期间的恢复时间和一致性。
5. 真实云长时间重复运行、fallback 和限流恢复。
6. SiliconFlow 正式价格核准、price_version 人工复核和账单抽样对账。
7. 人工 reviewed calibration、统一 Eval 指标和生产告警。

## 20. 典型执行链路

### 20.1 简单低风险请求

1. Java 校验用户/Session，装配最近 8 条消息、摘要和记忆。
2. Java 创建 Run、Dispatch、Outbox 后发布 Command。
3. Python 校验并发布 accepted、routed。
4. Router 识别意图，Planner 生成 compose。
5. ContextBuilder 生成带来源 ID 的 Context。
6. Composer 生成候选并记录 usage。
7. Reflection 和硬规则 Eval 通过；命中采样时调用独立 Judge。
8. 发布 eval_decided。
9. Eval 通过后切分并发布 answer_stream。
10. 发布 completed，Java 持久化并向浏览器 SSE 推送。

### 20.2 缺少食谱天数

1. Router 识别 planning 并发现 days 缺失。
2. Planner 只能生成 clarify。
3. Validator 拒绝工具或其他副作用。
4. 保存 clarification checkpoint。
5. 发布 eval_decided 和 clarification_requested，不发布完整正文。
6. 用户补充天数后，Java 创建 continuation Run，旧 Run 标记 superseded。

### 20.3 只读 SQL 回注

1. Java 把授权 SQL read request 放入 Context。
2. Python 生成 Proposal，校验 hash 和 invocation ID。
3. 保存 tool_wait checkpoint，发布 checkpoint/tool-started。
4. Java Tool Gateway 去重、Policy、SQL Guard、执行和审计。
5. Java 发布 Result；Python Inbox 去重并回注。
6. Python CAS 更新 checkpoint，执行第二次 Composer。
7. 第二次候选再次经过 Eval，只有通过才发送正文。

### 20.4 高风险请求

1. Router 标记 risk_level=high。
2. Runtime 仍按状态图处理，但不交付高风险候选。
3. Eval 最终原因为 REQUEST_REVIEW_NO_HUMAN_REVIEWER。
4. 不进入虚假的 waiting_review。
5. 返回安全降级回答，提示医生或注册营养师判断。
6. 记录 request_review 原因和 Eval 元数据。

## 21. 变更规则

1. 先更新 Command/Event/Proposal/Result 契约和版本说明。
2. 再更新 Java Inbox、Outbox、数据库迁移或状态投影。
3. 再更新 Python Runtime 和适配器。
4. 补充单元测试、Golden 回归和跨进程测试。
5. 运行 Python、Java、前端和基础设施联调。
6. 文档明确写“已实现、已验证、生产待办”，不能只修改勾选框。
7. 新模型、价格、预算和记忆策略必须有版本号，避免历史 Run 使用不一致配置。

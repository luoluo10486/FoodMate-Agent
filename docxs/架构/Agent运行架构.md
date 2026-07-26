# FoodMate Agent 运行架构

版本：v1.0 目标设计  
维护基线：2026-07-26
文档定位：本文是 Workflow、Agent 局部决策、人工审批、在线 Eval Gate 和退回规则的唯一架构依据。代码是否完成必须另查实现状态文档，不能根据本文推断。

## 1. 架构结论

FoodMate 采用生产型 `Workflow + Agent` 混合架构：

- Java 控制面维护 AgentRun 权威状态、权限、事务、副作用和审计。
- Python Workflow 固定主流程、节点契约、循环上限和恢复点。
- Agent 只在 Router、Planner、Reflector、Composer 等局部节点内做受约束决策。
- Tool、SQL 和业务写入只能由 Java 在策略校验后执行。
- Human Approval 是高风险动作的显式等待节点，不由模型代替用户确认。
- Validator 执行确定性硬校验；在线 Eval Gate 评价候选答案和执行轨迹。
- Eval 只能返回规定动作，不能直接执行工具、扩大权限或修改业务状态。

![FoodMate 目标运行模式](./资源/Agent目标运行模式.svg)

## 2. 三层状态

### 2.1 AgentRun 生命周期

Java 保存面向业务和前端的粗粒度权威状态：

当前 V1 数据库只允许 `queued/routed/waiting_user/planning/retrieving/executing/validating/completed/failed/cancelled`。确认后的目标状态额外增加终态 `superseded`，用于表示旧 Run 已被 continuation Run 接续；它需要后续契约、Flyway、Java 状态机和前端映射共同落地，当前代码尚不能写入。

该状态用于查询、SSE、取消和审计，不承担 Python 内部节点游标的职责。Java 必须按合法迁移矩阵拒绝状态回退和终态后事件。

### 2.2 Workflow 节点状态

Python checkpoint 保存当前 dispatch 的节点、计划版本、循环预算、待处理 proposal、已完成步骤和事件游标。节点状态不得覆盖 Java AgentRun，也不能被前端当作业务真值。

### 2.3 局部决策结果

模型节点只输出结构化决策，例如 `route`、`plan`、`next_action`、`candidate_answer` 和 `eval_verdict`。Workflow 校验 schema 和动作白名单后才选择下一条边；模型不得直接指定任意函数或跳转任意节点。

## 3. 内部编排图

![Agent 内部编排图](./资源/Agent内部编排图.svg)

### 3.1 固定主流程

1. `Intake` 校验命令、deadline、取消标记和授权上下文。
2. `Router` 输出意图、置信度、所需能力和缺失参数。
3. 缺少必要参数时进入 `Clarification`，由 Java 将 AgentRun 置为 `waiting_user`。
4. 简单且无工具任务可走 `Direct Compose`；复杂任务进入 `Planner`。
5. `Execution Engine` 按计划选择 RAG、Model 或向 Java 提交 Tool/SQL proposal。
6. 高风险 proposal 经 Java Policy 判定后进入 `Human Approval`。
7. 每个执行结果先经过 `Step Validator`，再由 `Reflector` 决定继续、有限重试、重规划或降级。
8. `Answer Composer` 只使用已验证事实、引用和明确失败信息生成候选答案。
9. `Final Eval Gate` 评价候选答案与轨迹，只有 `pass` 才允许完成。

### 3.2 简单任务快速路径

满足以下全部条件时允许跳过 Planner：

- Router 置信度达到配置阈值。
- 不缺必要参数。
- 不需要 Tool、SQL、RAG 或业务副作用。
- 不属于高风险营养或医疗表达。
- 输出仍必须经过 Composer、确定性检查和 Final Eval Gate。

### 3.3 可配置的有界循环

循环次数不是写死的架构常量。Python Runtime 启动时从环境变量读取并校验预算；下表数值只是默认值：

| 环境变量 | 默认值 | 含义 |
|---|---:|---|
| `FOODMATE_AGENT_MAX_STEP_RETRIES` | `2` | 单个可重试步骤最多追加执行次数 |
| `FOODMATE_AGENT_MAX_REPLANS` | `1` | 一次 Run 最多重新规划次数 |
| `FOODMATE_AGENT_MAX_ANSWER_REWRITES` | `1` | Final Eval 触发的答案重写次数 |
| `FOODMATE_AGENT_MAX_TOTAL_STEPS` | `30` | 一次 Run 可推进的 Workflow 节点总数 |
| `FOODMATE_AGENT_MAX_MODEL_CALLS` | `12` | 一次 Run 可发起的逻辑模型调用总数 |

计数口径：`MAX_STEP_RETRIES`、`MAX_REPLANS` 和 `MAX_ANSWER_REWRITES` 都只计算首次动作之后的追加次数，因此配置为 `0` 表示禁止对应的追加尝试；`MAX_TOTAL_STEPS` 统计每次进入 Workflow 节点；`MAX_MODEL_CALLS` 统计逻辑模型调用，不把同一逻辑调用的供应商网络 attempt 重复计算。带中文注释的环境配置示例见[配置指南](../项目/配置指南.md#51-workflow-预算环境变量)。

- 所有配置必须是非负整数；总步骤数和模型调用数必须大于零。非法配置使 Runtime readiness 失败，不能静默回退。
- Runtime 接受 Run 时生成不可变 `RunBudgetSnapshot`，记录解析后的预算值和配置版本，并写入 checkpoint/trace。
- 环境变量变更只影响变更后新接受的 Run；正在执行或恢复的 Run 继续使用原预算快照。
- RunCommand 后续可以下发更严格的单 Run 限制；最终有效预算取 Runtime 上限与 RunCommand 限制的较小值，Java 不能通过命令扩大 Python Runtime 上限。
- 同一工具 invocation 不由 Python 自行重复执行；重试必须复用幂等键并由 Java 裁决。
- deadline、取消、token/cost 限制可以先于次数预算终止执行。
- 任一预算耗尽都进入降级回答或 `failed`，禁止无限反思。

## 4. Validator 与 Eval Gate

### 4.1 Step Validator

Step Validator 是运行内硬门禁，优先使用确定性规则：

- schema、类型、单位、范围和必填字段。
- Tool/SQL 状态与结果摘要一致性。
- 引用是否存在、是否位于授权范围。
- 计划约束是否满足。
- 是否把拒绝、超时或未知结果伪装成成功。

硬校验失败不能由 LLM Judge 覆盖。

### 4.2 Final Eval Gate

Final Eval Gate 的输入必须包含：用户目标、约束、候选答案、已验证事实、引用、工具结果摘要、失败信息、计划完成情况和版本信息。

评测维度：

| 维度 | 检查内容 | 默认性质 |
|---|---|---|
| safety | 医疗营养风险、越权、敏感内容和危险建议 | 硬门禁 |
| groundedness | 陈述是否由工具结果、业务数据或引用支撑 | 硬门禁 |
| action_integrity | 是否虚构已执行、已保存或已审批 | 硬门禁 |
| constraint_satisfaction | 是否满足预算、人数、时间、禁忌等约束 | 任务相关时硬门禁 |
| completeness | 是否覆盖用户的主要请求 | 质量评分 |
| citation_quality | 引用是否相关、可追溯且未越权 | RAG 任务硬门禁 |
| consistency | 答案内部以及与执行轨迹是否一致 | 硬门禁 |
| clarity | 表达是否清楚、不过度冗长 | 质量评分 |

### 4.3 Eval 输出契约

```json
{
  "verdict": "pass",
  "scores": {
    "groundedness": 0.96,
    "completeness": 0.90,
    "constraint_satisfaction": 1.0,
    "clarity": 0.88
  },
  "hard_failures": [],
  "issues": [],
  "action": "deliver",
  "target_node": null,
  "reason_codes": [],
  "evaluator_version": "final-eval-v1",
  "rubric_version": "foodmate-rubric-v1"
}
```

`verdict` 只允许 `pass/revise/replan/degrade/reject`；`action` 只允许 `deliver/rewrite_answer/replan/retrieve_more/request_user/request_review/fail_safe`。Workflow 必须校验 `target_node` 与动作的固定映射。

### 4.4 判定方式

1. 先执行确定性规则；任何硬失败直接生成非通过结论。
2. 再按任务类型执行 LLM-as-judge 或专用评分器。
3. Judge 只能引用输入证据，不得补充新事实。
4. Eval 服务异常时，高风险任务 fail closed；普通只读问答可输出带明确限制的降级答案。
5. Eval 结果、Prompt/rubric 版本、分数和退回动作必须进入轨迹和指标。

## 5. 完整退回规则

![Eval Gate 与退回规则](./资源/Eval退回规则.svg)

| 发现位置 | 条件 | 目标节点 | 上限 | 终止策略 |
|---|---|---|---|---|
| Router | 缺必要参数 | Clarification | 每轮一次聚合追问 | `waiting_user` |
| Router | 低置信且无法安全降级 | Clarification | 1 | `waiting_user` |
| Step Validator | 结构或可修正参数错误 | 当前执行节点 | `MAX_STEP_RETRIES`，默认 2 | 降级或 `failed` |
| Java Policy | 需要用户确认 | Human Approval | 每个 proposal 1 次 | 拒绝则回 Reflector |
| Java Policy | 权限拒绝 | Reflector | 0 | 替代方案或透明失败 |
| Tool/SQL | retryable 技术失败 | 当前执行节点 | 由 Java 策略限制 | 降级或 `failed` |
| Tool/SQL | non-retryable/unknown | Reflector | 0 | 禁止自动重放副作用 |
| Reflector | 计划仍可修复 | Planner | `MAX_REPLANS`，默认 1 | 降级或 `failed` |
| Final Eval | 仅表达、遗漏或格式问题 | Composer | `MAX_ANSWER_REWRITES`，默认 1 | 降级或 `failed` |
| Final Eval | 证据不足但可检索 | Retrieval/Planner | 计入 `MAX_REPLANS` 和总预算 | 降级回答 |
| Final Eval | 缺用户专属信息 | Clarification | 1 | `waiting_user` |
| Final Eval | 安全、越权或虚构执行 | Fail-safe Composer | 0 | 安全拒绝或 `failed` |
| 任意节点 | 取消、deadline 或预算耗尽 | Terminal Arbiter | 0 | `cancelled/failed` |

退回时必须携带机器可读 `reason_code`、原节点、目标节点、剩余预算和相关证据 ID。不得只用自然语言让模型自行判断跳转。

## 6. Human Approval

- Java Policy 产生 `allow/deny/require_approval`，LLM 无权自报已批准。
- 审批请求必须冻结工具名、版本、规范化参数摘要、风险说明、幂等键和有效期。
- 用户批准后 Java 再校验当前身份、Run、版本、deadline 和参数摘要。
- 参数变化、审批过期或 dispatch 被替代时必须重新审批。
- 拒绝或超时作为观察结果返回 Reflector，不应被伪装成系统失败。
- `waiting_user` 的恢复协议必须区分“补充任务参数”和“批准既定动作”。

## 7. Final Output 与完成条件

只有同时满足以下条件，Workflow 才能建议 `run.completed`：

- 所有必需计划步骤已经成功、明确跳过或透明降级。
- 没有未决审批、未确认副作用或状态未知的 invocation。
- Step Validator 没有未解决硬错误。
- Final Eval Gate 返回 `pass + deliver`。
- Java 接受该事件且当前状态允许进入 `completed`。

生产环境在 Eval 通过前不得发送候选答案正文，只发送路由、规划、检索、工具、校验和预算等业务进度事件。Composer 在服务端缓冲候选答案，Final Eval 通过后才产生 `run.answer_stream`；Eval 失败时只发送重写后的合格答案或安全降级答案。

## 8. 在线 Eval 与离线 Eval

- 在线 Eval Gate：服务单次运行，给出交付或有限退回决定。
- 离线 Regression Eval：使用冻结数据集比较模型、Prompt、工具和 rubric 版本，不修改线上 Run。
- 线上抽样审计：异步复评已交付结果，只用于告警、数据集沉淀和版本回滚，不回写历史答案。
- 发布门禁至少比较任务成功率、硬失败率、引用正确率、虚构执行率、平均循环次数、延迟和成本。

## 9. Java 与 Python 职责边界

| 决策 | Python | Java |
|---|---|---|
| 意图、计划、下一候选动作 | 建议 | 记录必要投影 |
| Workflow 节点和循环预算 | 执行并 checkpoint | 下发全局限制、裁决终态 |
| 工具/SQL proposal | 生成 | 授权、执行、幂等、审计 |
| 人工审批 | 等待结果 | 创建、校验和固化审批 |
| 结果质量 | Validator/Eval | 校验业务硬规则与状态迁移 |
| 最终答案 | 生成候选 | 接受事件、持久化并 SSE |
| AgentRun 状态 | 报告建议阶段 | 唯一权威 |

## 10. 实现门禁

- 编排图所有节点和边必须有单元测试。
- 每条退回边必须测试预算耗尽和终止行为。
- Eval schema、rubric、阈值和版本必须可追溯。
- 固定回归集必须覆盖直接回答、RAG、读工具、写工具、审批拒绝、缺参、超时、取消、证据不足和 Prompt Injection。
- 完整实现前，文档与 UI 必须明确标注当前仍是确定性 stub，不能把目标架构描述成已上线能力。

## 11. Clarification、审批与父子 Run

### 11.1 普通信息补充

用户补充缺失参数时创建新的 continuation Run，不恢复旧 Run：

```text
Run A -> waiting_user
用户发送补充 Message
-> 同一事务创建 Run B
-> Run B.parent_run_id = Run A
-> Run A.superseded_by_run_id = Run B
-> Run A: waiting_user -> superseded
```

- `parent_run_id/superseded_by_run_id` 必须属于同一用户和 Session。
- 一个旧 Run 最多由一个有效 continuation Run 接续。
- Run B 读取 clarification question、结构化 `unresolved_slots`、会话摘要和必要消息，不继承旧工具审批。
- Run B 使用新的预算、超时、Prompt 和模型路由快照。
- `superseded` 是终态，不占用 Session active Run 或 Redis permit；旧 dispatch 的迟到事件全部拒绝。

### 11.2 工具与预算确认

工具审批和预算追加绑定原 Run，因此恢复原 Run 并创建新的 dispatch attempt：

- 工具确认绑定 `run_id/tool_version/proposal_digest/idempotency_key/expiry`。
- 预算确认绑定 `run_id/budget_extension_id/additional_tokens/additional_cost/confirmation_digest`。
- 参数、成本区间或摘要变化后原确认失效。
- 当前不支持长期工具预授权；同一 Run 可以一次确认一组已冻结操作。

## 12. 并发准入与队列

![Agent 生产治理图](./资源/Agent生产治理图.svg)

### 12.1 两类并发限制

- PostgreSQL 保证同一 Session 最多一个 active Run，并保持消息顺序。
- Redis 协调每用户最多活跃 Session 数和 Runtime 全局 active Run 数。
- 用户可以创建多个历史 Session；用户限制只针对同时执行的 Session。
- 当前采用单实例仍使用 Redis 协调，不把进程内 semaphore 当作业务并发真值。
- Python 仍需有界 worker pool 保护进程资源，但 worker pool 不替代 Redis 准入。

默认配置：每用户最多 2 个活跃 Session、全局最多 20 个 active Run、全局队列最多 100 个 Run。具体值由环境变量提供。

### 12.2 排队顺序

| 优先级 | 类型 |
|---|---|
| P0 | 已确认的工具、预算或审批恢复 |
| P1 | continuation Run |
| P2 | 普通新 AgentRun |
| P3 | 摘要、离线 Eval 等后台任务 |

- 同级按 `queued_at` FIFO，同一 Session 严格有序。
- priority burst 和 aging 防止 P2 永久饥饿。
- queue timeout 从首次入队计算，优先级提升不能重置。
- PostgreSQL 保存权威排队事实，Redis 负责协调调度与 permit。

### 12.3 Redis 故障

- Redis 不可用时，新 Agent 请求返回 HTTP 503 和 `RUNTIME_COORDINATION_UNAVAILABLE`。
- 不降级到进程内并发计数；登录、历史会话和消息查询等普通接口继续可用。
- 已准入 Run 在 lease 明确有效时可以继续；无法续租且接近到期时停止创建新调用并安全收敛。
- Redis 恢复后执行 PostgreSQL active Run、队列、permit 和 checkpoint 对账。
- 前端显示“系统暂时无法处理 Agent 请求”，不暴露 Redis 等内部实现。

后端返回 HTTP 503、`Retry-After` 和统一错误体：

```json
{
  "code": "RUNTIME_COORDINATION_UNAVAILABLE",
  "message": "Agent 服务暂时不可用，请稍后重试",
  "request_id": "req_xxx",
  "trace_id": "trace_xxx",
  "retryable": true,
  "retry_after_seconds": 10
}
```

只有服务端明确 Run 尚未创建时，前端才自动重试一次；已有 `run_id` 时改为查询状态，写操作、审批和预算确认永不自动重放。用户手动重试复用 `client_request_id`，由 Java 幂等返回原 Run 或安全创建新 Run。

## 13. 超时模型

超时分为四类，并在 Run 开始时固化到 `TimeoutSnapshot`：

| 类型 | 默认值 | 行为 |
|---|---:|---|
| queue timeout | 30 秒 | `failed + RUNTIME_QUEUE_TIMEOUT` |
| execution timeout | 120 秒 | 停止新调用，返回可信部分结果或失败 |
| node timeout | 30 秒 | 按节点策略重试、降级或跳过可选节点 |
| waiting user timeout | 86400 秒 | 补充 Run 过期；审批与预算确认失效 |

- waiting_user 时间不消耗 execution timeout。
- Java、Python、模型和工具统一传递绝对 deadline，不能各自重置完整倒计时。
- 恢复 Run 继续使用原 TimeoutSnapshot。

## 14. Token、成本与降级

### 14.1 双阈值策略

| 使用比例 | 动作 |
|---:|---|
| `< 70%` | 正常执行 |
| `>= 70%` | 停止非必要 Reflection、降低可选检索和扩展子任务 |
| `>= 85%` | 禁止重规划/重写、低风险节点切经济模型、只完成必要子任务 |
| `>= 100%` | 禁止新调用，返回可信部分结果或请求预算确认 |

- Token 与估算成本任一先触线，使用更严格等级。
- 安全规则、权限检查、写操作确认和确定性 Eval 不能因预算跳过。
- LLM Eval 预算不足时，低风险任务安全降级；高风险任务 fail closed。
- Budget Manager 在每个模型、RAG、Tool、SQL 和 Reflection 前执行确定性检查。

### 14.2 用户追加预算

当前不建设账户余额、租户配额或供应商余额查询。有效追加额度取用户本次授权与 Runtime 单次追加上限的较小值。

- 达到 70% 只做非阻塞提示；达到 85% 显示节省模式；达到 100% 暂停并显示预算确认卡片。
- 卡片展示已用 Token、估算成本、未完成步骤、预计新增区间和已有可信结果。
- 用户确认后生成新的 `BudgetSnapshot revision` 和 dispatch attempt。
- 可以多次追加，但每次都重新确认、记录累计成本并生成审计记录。

### 14.3 模型路由

- Agent 只声明 capability、quality tier 和结构化输出要求。
- 确定性 Model Routing Policy 从 high/standard/economy/eval 别名和 fallback 列表选择模型。
- fallback 模型必须通过对应能力与 Eval 基线，不允许模型自行选择供应商。
- 安全、权限或 schema 错误禁止通过换模型重试。

## 15. Eval 分级与无人审核策略

### 15.1 分级执行

- 所有任务执行确定性硬规则。
- 简单低风险任务执行结构检查，并按比例抽样 LLM Judge。
- RAG、复杂规划、分析和高风险任务强制执行 LLM Judge。
- Judge 使用独立模型或至少独立 Prompt/rubric，输出结构化分数、原因码、证据和动作。

### 15.2 当前没有人工审核人员

当前 `FOODMATE_AGENT_HUMAN_REVIEW_ENABLED=false`，不新增 `waiting_review`：

```text
request_review
-> 不交付高风险候选答案
-> Fail-safe Composer 生成安全降级回答
-> 建议咨询医生或注册营养师
-> 保存原因码和证据摘要供离线审计
-> completed(result_type=safety_degraded)
```

只有安全回答本身失败时才进入 `failed + EVAL_SAFE_RESPONSE_FAILED`。未来必须先具备真实审核人员、权限、队列、SLA 和审计，并通过 ADR 后才能启用在线人工复核。

## 16. Context Builder 与摘要

上下文按以下优先级装配：当前消息、上一 Run 的追问、`unresolved_slots`、Session 摘要、最近消息、授权记忆、授权数据与引用。

- 最多保留最近 8 条原始消息；第 9 条有效消息写入后增量更新摘要并继续保留最新 8 条。
- 摘要保存版本、覆盖消息区间、来源数量、Prompt 版本和摘要 digest。
- 当前消息、系统安全规则和未解决约束不得被裁剪。
- 删除消息后，覆盖它的摘要必须失效并重建。
- Context Builder 记录使用的 message/memory/citation ID，不在 Trace 复制完整内容。

## 17. Checkpoint 与 LangGraph

Python 内部 Workflow 使用 LangGraph 承载节点图、条件边、中断和恢复；Java AgentRun 状态机仍为业务权威。

- checkpoint 使用独立 Redis namespace、AOF、TTL、大小限制和应用层加密。
- 简单直接回答不强制 checkpoint；规划完成、工具结果确认、进入等待和 Eval 前后保存安全恢复点。
- 恢复前与 Java 对账 Run 终态、active dispatch、事件序号和 Tool/SQL invocation。
- checkpoint 不保存完整会话、Chain-of-Thought、凭据或可从 Java 重取的业务真值。
- LangGraph action 只能走白名单边，框架事件必须转换成 FoodMate RunEvent。

## 18. 可观测性与反馈

### 18.1 Trace

默认保存结构化元数据、摘要、digest 和脱敏结果，不保存完整 Prompt、原始模型响应或 Chain-of-Thought。Trace 至少关联 run、parent run、dispatch、节点、模型、工具、预算、超时、Eval、错误和延迟。

### 18.2 用户反馈

- 前端提供有帮助/没帮助、原因多选和可选说明。
- 反馈关联 message、run、Eval、模型路由、Prompt 和 rubric 版本。
- 反馈不直接修改记忆、Prompt 或模型策略；先脱敏、审核后进入离线 Eval 候选集。
- “虚构执行”“安全或隐私问题”触发高优先级审计。

## 19. 工具风险与确认

| 类型 | 示例 | 确认策略 |
|---|---|---|
| READ_ONLY | 查询、检索、计算 | 不确认，但鉴权和审计 |
| REVERSIBLE_WRITE | 保存饮食记录、计划、偏好 | 当前每次确认，可对同一 Run 的冻结操作批量确认 |
| IRREVERSIBLE_WRITE | 删除、对外发送、支付、敏感导出 | 每次强制确认，禁止预授权 |
| HIGH_RISK_ADVICE | 医疗营养高风险内容 | 走安全 Eval，不作为工具确认 |

工具注册必须声明 effect、risk、confirmation、idempotent、retryable、timeout、scope 和 schema version。当前不提供长期预授权。

## 20. 取消语义

- 用户取消后立即关闭新 invocation admission，不再调用 Composer 或 LLM Eval。
- 已提交 Java 的 Tool/SQL 必须等待明确状态或标记 unknown，不能假定回滚。
- 前端通过确定性模板展示已完成、未完成和状态未知步骤。
- Final Eval 已通过且答案已固化时，Java 的首次合法终态裁决仍可能是 completed。
- 取消已发生的 Token 和成本继续计入用量。

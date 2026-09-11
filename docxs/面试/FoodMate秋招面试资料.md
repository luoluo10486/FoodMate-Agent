# FoodMate 秋招面试资料

> 更新时间：2026-09-11。本文只整理当前仓库中已经实现、已经有本地业务证据或可以从代码直接复核的内容。性能压测、长稳、完整依赖故障矩阵、备份恢复、Kubernetes、staging/production 和发布回滚不作为已完成事实描述。

## 1. 先记住三句话

1. FoodMate 是一个面向饮食记录、营养分析和备餐规划的任务型 Agent；它可以理解自然语言，但不让模型直接写业务数据库。
2. Java 控制面负责认证、用户范围、业务事务、Tool Gateway、确认、幂等和审计；Python Runtime 负责路由、规划、RAG、模型调用和回答编排。
3. 两个运行时通过 PostgreSQL Outbox、RocketMQ、Redis Inbox/Checkpoint 和 SSE 协作，写操作必须走 `Proposal -> Confirm -> Execute`，最终结果可以按 `run_id/request_id/proposal_id/audit_id` 对账。

面试中不要把“模型生成了 JSON”说成“业务已经执行”。模型只提出候选，Java 的授权和确定性校验才是业务事实来源。

## 2. 项目介绍话术

### 2.1 30 秒版本

我做的是 FoodMate，一个饮食记录和备餐规划 Agent。用户可以用自然语言记录饮食、生成餐食计划、查询营养数据和检索公共营养知识。架构上采用 Java 控制面加 Python Agent Runtime：Python 负责编排和模型调用，Java 负责用户权限、工具执行、写确认、幂等、事务和审计；两者通过 RocketMQ 异步通信，结果通过 SSE 返回。项目的核心设计是把“模型建议”和“业务副作用”隔离，重复消息或重复确认不会重复写入。

### 2.2 2 分钟版本

FoodMate 的入口是 Java API。Java 创建 `Session` 和 `AgentRun`，把用户消息、用户范围、记忆上下文和公共知识范围组装成 `V1RunCommand`，通过 Outbox 投递给 Python Runtime。Python 经过 Router、Planner、RAG、模型节点和 Composer 生成结构化结果；如果需要写业务数据，它只能提交带有 `run_id`、`proposal_id`、参数摘要和幂等键的 Proposal。

Java 收到 Proposal 后，先检查工具注册表、用户和 Session 归属、参数 schema、风险等级、确认引用、revision 和幂等键，再由 Tool Gateway 调用具体业务服务。用户没有确认时不落业务数据；用户拒绝、确认过期、参数摘要变化或旧 Proposal 被替代时，都不会产生副作用。业务写入、成功审计和 Outbox 在同一事务内完成，失败路径用独立事务记录失败审计。

FoodMate 还实现了公共知识库 RAG：管理员上传 PDF/DOCX/Markdown/TXT 后，Java 记录批次和索引任务，Python 负责安全解析、切分和 Embedding，Milvus 保存向量，Java 消费索引结果并维护权威状态。只有 `published + indexed + 当前版本 + 未删除` 的公共文档可以检索。当前正式 WHO 资料已经完成一次真实 Embedding、Milvus、Java 回写、发布投影和 Chat 引用闭环。

### 2.3 5 分钟版本

可以按下面顺序展开：

1. **为什么拆成 Java 和 Python。** Java 侧已有用户、事务和数据权限，需要作为确定性控制面；Python 侧适合模型路由、RAG 和有界 Agent Workflow。拆分后 Python 即使被模型输出影响，也没有业务数据库凭据。
2. **一次普通请求怎么走。** 前端创建消息，Java 创建 Run 并发命令；Python 读取授权上下文，执行 Router/Planner/Model/RAG/Composer，把事件和结果通过 RocketMQ 回传；Java 持久化事件并通过 SSE 投影给前端。
3. **一次写请求怎么走。** 模型识别出 `food_log_writer` 或 `meal_plan.save_plan`，只产生 Proposal；Java 生成确认摘要，前端展示确认卡；Confirm 后 Java 再做一次参数摘要和权限校验，在事务中执行写入、审计和结果 Outbox。
4. **如何处理重复。** Outbox、Inbox、Proposal、业务写入和 SSE 各自有幂等边界。相同幂等键和相同参数返回既有事实；相同键但参数变化返回冲突；重复事件按事件 ID/哈希去重；重复工具执行复用既有 Result。
5. **RAG 如何避免越权。** Java 固定注入 `tenant_id=0` 和 `public_published`；Python 只接受受控范围，不信任前端或模型传入的 ACL。向量检索和关键词检索都过滤公共、已发布、已索引、当前版本和未删除，Java 在 SSE 输出引用前再校验一次。
6. **当前边界。** 业务闭环已优先完成，但没有把本机单次真实调用包装成生产级容量或可靠性证明。长稳、生产压测、完整重启矩阵、ACK 丢失、组合故障、备份恢复和 Kubernetes 仍然后置。

## 3. 架构总览

### 3.1 运行链路

```text
浏览器 / React
    -> Java REST / SSE / Auth
    -> Application 用例、AgentRun、事务、权限、Outbox
    -> PostgreSQL Outbox
    -> RocketMQ
    -> Python Agent Runtime
       -> Router / Planner / Context / RAG / Model / Composer
       -> Redis Checkpoint / Inbox / Event Outbox
    -> RocketMQ
    -> Java Event / Proposal / Result Consumer
    -> Tool Gateway / SQL Guard / 业务服务
    -> PostgreSQL、Redis、MinIO、Milvus
    -> Java SSE 投影 -> 浏览器
```

### 3.2 组件职责

| 组件 | 负责什么 | 明确不负责什么 |
|---|---|---|
| `foodmate-ui` | 输入、聊天状态、确认卡、工具轨迹、引用和 SSE 展示 | 不决定业务状态，不直接写数据库 |
| `foodmate-api` | REST/SSE 参数转换、认证、响应包装 | 不写 SQL，不编排多步 Agent |
| `foodmate-application` | 用例、事务、业务状态、权限、Proposal/Confirm/Execute | 不持有 Prompt 编排和具体外部客户端 |
| `foodmate-infra` | PostgreSQL、Redis、RocketMQ、MinIO、Milvus、模型适配器 | 不改变 application 的业务授权规则 |
| `foodmate-shared` | 跨模块契约、错误码、ID 和 Runtime DTO | 不依赖具体数据库或业务实现 |
| `agent-runtime` | Router、Planner、RAG、模型、上下文、回答和 Proposal 生成 | 不直连 FoodMate PostgreSQL，不自行确认或执行工具 |
| PostgreSQL | 用户、Session、Run、业务表、审计、Outbox/Inbox 权威事实 | 不承担 Python Workflow 游标 |
| Redis | admission、lease、checkpoint、Inbox/Outbox 技术状态；stub RAG 索引 | 不作为饮食记录或权限真值 |
| RocketMQ | Java/Python 至少一次传输 | 不提供业务幂等，不替代事务 |
| MinIO | 知识库原始文件私有对象存储 | 不向前端暴露对象地址 |
| Milvus | RAG 向量和固定 metadata 检索 | 不决定文档发布和用户权限 |

### 3.3 最重要的权威边界

| 问题 | 权威来源 |
|---|---|
| 用户是谁、能访问谁的数据 | Java 认证、Session/Run 归属和业务查询条件 |
| 饮食记录是否写入 | Java Tool Gateway 和业务事务 |
| Proposal 是否可以执行 | Java 确认引用、参数摘要、权限、revision 和幂等校验 |
| 文档能否被 RAG 检索 | Java `knowledge_documents` 的版本/状态 + 可见性投影 |
| 模型是否成功 | Provider 返回的结构化 usage/结果和 Runtime 状态；不能由回答文本自证 |
| Agent 当前执行到哪里 | Java AgentRun 是对外业务状态，Python checkpoint 是内部恢复状态 |
| SSE 是否可以展示终态 | Java 已持久化并通过授权校验的事件 |

## 4. AgentRun 生命周期

### 4.1 Java 的业务状态和 Python 的节点状态分离

Java 保存前端和业务需要的粗粒度状态，例如 `queued`、`running`、`waiting_user`、`completed`、`failed`、`cancelled` 和 `superseded`。它负责状态迁移、终态保护、取消、SSE 和审计。

Python checkpoint 保存节点、计划版本、循环次数、已完成步骤、待处理 Proposal 和事件游标。checkpoint 只能帮助恢复，不能把某个节点游标当作业务写入成功，也不能绕过 Java Tool Gateway。

### 4.2 一次普通 Chat 的事件顺序

```text
run.accepted
  -> run.routed
  -> run.checkpoint_saved
  -> run.tool_started / run.retrieval_started（按需）
  -> run.tool_finished / run.retrieval_finished（按需）
  -> run.model_usage
  -> run.eval_decided
  -> run.answer_stream
  -> run.completed
```

缺参数时会进入 `run.clarification_requested` 和 `waiting_user`；取消、超时、模型失败或工具拒绝会有明确错误码。正常回答、拒绝、失败和安全降级不能共用“成功回答”文案。

### 4.3 固定 Workflow 为什么不让模型自由调用函数

模型只输出结构化 `route`、`plan`、`next_action`、`candidate_answer` 和 `eval_verdict`。Workflow 对 schema、枚举、必填字段、预算和动作白名单做确定性校验，然后决定下一条边。这样可以限制循环次数、工具类型和高风险动作，避免模型通过文本伪造调用权限。

## 5. Tool Gateway 与写确认

### 5.1 Proposal -> Confirm -> Execute

```text
Python/模型生成 Proposal
    -> Java 校验工具注册、版本、schema、用户/Run 归属
    -> 生成参数摘要和确认引用
    -> 前端展示待确认内容
    -> 用户 Confirm 或 Reject
    -> Java 再校验确认未过期、摘要未变化、revision 未变化
    -> Tool Gateway 执行确定性业务服务
    -> 业务写入 + 成功审计 + Result Outbox 同事务提交
    -> Python/Java 消费 Result
    -> run.completed / SSE
```

### 5.2 关键状态

| 状态 | 含义 | 是否允许业务副作用 |
|---|---|---:|
| `proposed` | 已生成候选，等待处理 | 否 |
| `confirmed` | 用户确认且确认引用有效 | 还需执行时再次校验 |
| `executed` | Java 已完成幂等业务执行 | 已产生一次 |
| `rejected` | 用户拒绝或策略拒绝 | 否 |
| `failed` | 执行失败或校验失败 | 以事务结果为准，失败审计保留 |
| `superseded` | 被新 Proposal/continuation 替代 | 否 |

### 5.3 `food_log_writer` 和 `meal_plan.save_plan`

`food_log_writer` 支持饮食记录的 create/update/delete/restore。它要求 `confirmation_ref`、`run_id`、用户归属、参数摘要、幂等键和 revision；营养匹配由 Java 业务服务完成，不能由模型直接填写营养真值。

`meal_plan.save_plan` 的候选必须先通过 `plan_validator`，校验人数、天数、餐次、预算、忌口和过敏源等约束，再进入确认。计划保存和购物清单生成由 Java 执行，规划页不会因为模型返回了计划 JSON 就直接保存。

### 5.4 幂等的三种结果

1. **同键同参数：** 返回已存在的 Proposal/Result 或既有业务结果，不再执行副作用。
2. **同键参数变化：** 返回幂等冲突，不能把同一个键当成新请求执行。
3. **不同键但同一业务 revision：** 由业务 CAS 或唯一约束裁决，旧 revision 失败，不能覆盖更新后的数据。

## 6. 统一审计与事务

### 6.1 记录什么

统一审计只保存操作者、目标、action、result、稳定错误码、安全参数摘要和关联 ID，例如 `request_id`、`trace_id`、`run_id`、`session_id`、`proposal_id`、`approval_request_id` 和 `idempotency_key`。

### 6.2 不记录什么

密码、Token、API Key、Prompt、完整回答、饮食备注、原始业务请求、对象存储地址、预签名 URL 和完整敏感结果都不能进入统一业务审计。SQL 有专用查询审计，协议拒绝和 Outbox/Inbox 技术状态也保留各自的技术事实，避免业务审计重复膨胀。

### 6.3 成功和失败的事务边界

- 成功业务写入、成功业务审计和对应 Outbox 事实在同一个事务中提交。
- 审计写入失败时，业务事务失败关闭，不能出现“业务成功但没有审计”。
- 业务事务回滚后，失败状态通过独立事务记录 `failed` 审计，至少保留稳定错误码和关联 ID。
- 所有 ID 使用 `IdGenerator`，不使用 `MAX(id)+1`。

## 7. SSE、重连和终态

### 7.1 为什么 SSE 只做投影

SSE 是前端读取通道，不是业务真值。Java 先从持久化事件和授权范围读取，再投影为浏览器事件；前端断线后使用 `Last-Event-ID` 请求缺失事件。事件以 `sse_event_id`/序号去重，终态只能接受一次。

### 7.2 前端要处理的状态

聊天页至少区分运行中、追问、工具轨迹、等待确认、拒绝、失败、取消、降级、引用和完成。不能把连接断开、模型失败、工具拒绝或 Eval 拒绝显示成普通回答成功。

### 7.3 面试回答模板

如果被问“断线会不会丢回答”，回答：

> 不把浏览器内存当真值。Java 会持久化 Run 事件和终态，前端重连时携带 `Last-Event-ID`，服务端按事件序号回放；前端按 `sse_event_id` 去重，`run.completed` 只展示一个。这个设计解决的是业务回读和重复展示，不等于已经完成生产网络故障矩阵。

## 8. 公共知识库 RAG

### 8.1 它和营养目录不是同一类数据

这是面试中很容易混淆的点：

- **营养目录**是结构化业务事实。食材、每 100 克营养值、单位换算和复合菜组成放在 PostgreSQL；向量只能做候选召回，最终由 Java 回源精确匹配和计算。
- **公共知识库**是文档证据。WHO 等资料经过解析、章节切分、Embedding 和向量检索，返回标题、版本、章节、chunk ID 和安全片段，供回答引用。
- **结构化记忆**是用户偏好事实。它不是文档向量，也不是饮食记录，Java 需要校验来源、冲突、确认状态、TTL 和删除状态后才能注入 Agent Context。

### 8.2 导入链路

```text
管理员上传批次
  -> Java 校验 PDF/DOCX/Markdown/TXT、大小、来源、PII 和幂等键
  -> MinIO 私有对象
  -> knowledge index Outbox
  -> RocketMQ foodmate-knowledge-index-v1
  -> Python 受限解析和 K2 切分
  -> Embedding
  -> Redis stub 或 Milvus local
  -> index-result topic
  -> Java 回写 item/document/job 权威状态
  -> 管理员显式发布
  -> visibility topic
  -> Redis/Milvus metadata 投影
```

消息只携带 ID、版本、模式、尝试次数、摘要和 trace/request ID，不携带原文、存储凭据、预签名 URL 或 API Key。

### 8.3 K2 切分策略

当前 Markdown 文档采用结构感知的段落切分：保留完整标题路径，优先按段落合并，超过目标长度时按句末边界拆分，再使用有限重叠保持上下文连续。默认目标约 `700` 字符，硬上限 `1000` 字符，重叠 `80` 字符；chunk 记录顺序、section path、文档版本、固定 ACL metadata 和稳定 `embedding_id`。

PDF、DOCX、Markdown 和 TXT 的共同原则是先得到安全纯文本，再使用同一套 chunk 契约；不执行宏、脚本、外链或嵌入对象。表格、OCR 和网页抓取不属于当前范围。

### 8.4 双检索模式

| 模式 | 后端 | 用途 |
|---|---|---|
| `local-stub` | Redis 隔离前缀、确定性向量/关键词 | 默认离线业务测试，不读取真实 API Key，不依赖 Milvus |
| `local` | OpenAI-compatible Embedding + Milvus | 真实本地闭环，缺少 endpoint、Key、预算或 Milvus 时失败关闭 |

当前正式 WHO 资料的真实证据：9 份资料、49 个活动 chunk、`Qwen/Qwen3-Embedding-0.6B`、3,088 个供应商 token、Milvus 目标集合 49 个实体，并通过一次真实 Chat AgentRun 返回 4 条引用。这个数字是一次本地业务证据，不是容量承诺。

### 8.5 检索和引用约束

Java 固定给 Runtime 注入 `tenant_id=0`、`knowledge_scope=public_published`。Python 不能从用户文本、前端参数、模型输出或任意 metadata 扩大范围。向量路径和关键词路径都必须过滤：

```text
tenant_id = 0
visibility = public_published
document_status = published
index_status = indexed
version = current_version
deleted = false
```

检索最多召回 12 个候选，重排最多 6 个，最终引用最多 4 条，每个文档最多 2 条。无命中或证据不足时不注入知识上下文，也不伪造引用。Java 在 SSE 投影前再校验一次文档可见性，避免下线与输出之间的竞态泄漏。

## 9. SQL Agent

### 9.1 只读链路

```text
用户自然语言查询
  -> Python Router / SQL Planner
  -> 结构化只读 SQL Proposal
  -> Java Tool Gateway
  -> schema 白名单
  -> 当前用户范围和软删除条件
  -> JSqlParser AST Guard
  -> 只读、LIMIT、超时、参数绑定
  -> PostgreSQL
  -> 脱敏结果 + SQL 专用审计
  -> Python Composer
  -> run.completed / SSE
```

### 9.2 为什么不能只靠 Prompt 禁止写 SQL

Prompt 是软约束，模型可能输出 `DELETE`、跨用户条件、系统表或未授权字段。Java 必须在执行前解析 AST，拒绝非 SELECT、未知表、未知字段、系统表、缺少用户范围、超过 LIMIT 或不安全表达式。参数使用绑定值，不能把用户输入直接拼接到 SQL 字符串。

### 9.3 当前业务覆盖

已经覆盖今日热量、近 7 天蛋白质、指定食材出现次数、餐食计划执行完成度和购物清单缺项；缺少时间范围、食材歧义、字段不支持和空结果时要澄清或解释原因。SQL Agent 不负责保存计划或写饮食记录，写操作仍走 Proposal/Confirm/Execute。

## 10. 结构化记忆治理

### 10.1 三层上下文

1. 最近有效原始消息：解决当前追问和上下文连续性。
2. Session 摘要：保存 goals、constraints、decisions、open_questions 和来源消息 ID，不复制完整回答。
3. 长期记忆：只保存跨会话仍有价值的偏好、习惯和交互规则。

饮食记录、餐食计划、购物清单、营养目标、过敏和医疗事实属于领域或高影响事实，不由普通记忆候选替代。

### 10.2 候选处理

Python 只生成短结构化候选，例如：

```json
{
  "memory_type": "budget_habit",
  "memory_key": "daily_budget",
  "memory_value": {"amount": "80 元"},
  "source_message_ids": ["message-1"],
  "confidence": 0.92
}
```

Java 负责白名单、用户归属、来源、敏感词、JSON 对象、置信度、冲突和写入。相同 `memory_type + memory_key + JSON` 幂等去重；不同值进入 `conflict`，未确认前不进入下一次 Context；确认一个值后，同 key 其他活动冲突值被拒绝。

自然表达现在支持偏好、弱否定、预算上限/范围、简单烹饪能力和回答语言。今天、这周、今晚等一次性请求不会生成长期候选；助手消息不能成为候选来源。修改、确认和删除会使 Session Summary 失效。

### 10.3 为什么不用 LLM 自动总结长期记忆

长期记忆属于跨会话持久事实，误写的代价比一次回答错误更高。当前选择可解释规则和 Java 白名单，牺牲一部分自然语言覆盖，换取来源可追溯、可测试、可删除和可冲突处理。以后如果引入模型抽取，也只能作为候选，不改变 Java 的最终裁决边界。

## 11. 真实问题复盘

### 11.1 旧计划直接保存

问题：规划页过去可以直接调用 `createMealPlan`，模型生成的结构和用户是否同意没有清晰边界。

修复：规划页改为创建会话并发送结构化约束，由 Agent 生成候选，先过 `plan_validator`，再进入确认卡和 `meal_plan.save_plan`。面试强调：生成候选和执行写入是两个不同阶段。

### 11.2 复合菜营养快照

问题：复合菜由基础食材和用量组成，如果只在读取时动态计算，基础食材变化会让历史饮食记录的营养值漂移。

修复：复合菜记录时保存当时的营养快照，记录按食用份量计算；复合菜组成和原子营养目录分开，避免把菜谱当成一种基础食材。

### 11.3 中文食材、生熟和单位歧义

问题：“煮熟鸡胸肉”“一片”“一杯”不能简单按字符串相等或猜测单位处理；生鸡胸和熟鸡胸也不能混合。

修复：烹饪前缀最长匹配，候选查询保留生熟和食物形态；`克、份、个、枚、片、杯、大勺` 通过共享 normalizer 处理。不能安全匹配时进入 pending confirmation，不偷偷填营养值。

### 11.4 旧索引版本仍可见

问题：文档新版本索引成功不代表旧向量已经不可见；如果只更新 PostgreSQL，不同步向量 metadata，旧版本可能被检索。

修复：文档发布、下线、删除、恢复都产生可重放 visibility 事实；Redis stub 和 Milvus 都更新固定 metadata；查询过滤当前版本和发布状态，Java 输出引用前再做可见性检查。

### 11.5 重复 Proposal

问题：RocketMQ 是至少一次传输，网络重试或消费者重启可能让同一 Proposal 到达两次。

修复：以 Proposal/幂等键和参数摘要确定请求身份；相同键相同摘要返回既有 Result，相同键不同摘要拒绝；业务写入还通过 revision/CAS 和唯一约束兜底。不能把 MQ 的“恰好一次”当作业务事实。

### 11.6 正式资料状态与离线测试冲突

问题：真实 WHO 资料完成向量索引后，旧 R5 测试仍写死“未构建向量”，导致离线资料测试失败。

修复：将“索引状态”从“是否可以做离线主题/切分测试”中分离，允许待授权和已完成真实索引两种合法状态；付费调用门禁仍由显式配置控制。

## 12. 六条现场演示脚本

演示前原则：使用开发管理员和随机命名空间，不把 API Key、密码、完整 Prompt 或完整供应商响应展示到录屏；普通测试默认 deterministic，真实演示才显式开启付费模式。

### 演示一：普通 Chat 与运行轨迹

输入：`帮我根据今天的饮食记录总结一下蛋白质摄入情况。`

观察点：

- Router/Planner 识别为 nutrition 或 analysis。
- 工具轨迹显示真实工具名、状态和耗时。
- SSE 最终出现唯一 `run.completed`。
- 回答说明时间范围和统计口径，不把空结果编造成数字。

证据位置：`foodmate-ui/src/pages/ChatPage`、`agent-runtime/agent_core.py`、`docxs/功能实现说明/M1-4-Python-Agent-Runtime实现逻辑.md`。

### 演示二：饮食记录写入确认

输入：`晚餐吃了 150 克煮熟鸡胸肉。`

观察点：

1. 真实 Chat 或 deterministic Runtime 生成 `food_log_writer` Proposal。
2. 前端显示食物、份量、营养匹配状态和确认按钮。
3. 未点击确认前，数据库没有新增业务记录。
4. 点击确认后，Java 执行一次，审计和 SSE 终态可回读。
5. 重复确认返回既有结果，不重复写入。

证据位置：`foodmate-application/src/main/java/com/foodmate/application`、`foodmate-ui/src/pages/ChatPage`、`script/local/real-food-log-writer-e2e.ps1`、执行记录中 `food_log_writer` 的 HTTP/RocketMQ 回归条目。

### 演示三：Agent 生成餐食计划

输入：`给我做一个 3 天、每天 80 元以内、清淡、不要香菜的晚餐计划。`

观察点：

- 规划页不会直接保存。
- Agent 生成结构化候选并调用 `plan_validator`。
- 约束错误在确认前展示。
- 用户确认后才保存计划并生成购物清单。
- 拒绝或重复确认没有额外计划。

证据位置：`foodmate-ui/src/pages/PlanningPage`、`agent-runtime/agent_core.py`、`agent-runtime/tests/test_plan_validator.py`、`docxs/项目/M1-5核心饮食业务与写确认实施方案.md`。

### 演示四：SQL Agent 只读分析

输入：`统计我最近 7 天每天的蛋白质摄入。`

观察点：

- Python 生成结构化只读 SQL Proposal。
- Java 应用当前用户范围、软删除条件、schema 白名单和 LIMIT。
- SQL 审计保存安全摘要，不向用户展示任意原始 SQL。
- 尝试 `DELETE`、跨用户查询、系统表或未知字段时稳定拒绝。

证据位置：`agent-runtime/sql_planner.py`、`foodmate-application/src/main/java/com/foodmate/application/sql`、`foodmate-infra/src/main/java/com/foodmate/infrastructure/persistence`、`agent-runtime/tests/test_sql_planner.py`。

### 演示五：RAG 引用

输入：`WHO 对减少盐摄入有什么建议？`

观察点：

- 真实模式下从已发布公共资料检索，不返回 MinIO 地址或对象键。
- `run.completed.citations` 包含标题、版本、章节、chunk ID 和安全片段。
- 最多展示 4 条引用，每个文档最多 2 条。
- 下线文档后同一问题不再返回该文档引用。
- 无命中时回答证据不足，不伪造来源。

证据位置：`agent-runtime/knowledge_rag.py`、`agent-runtime/knowledge_worker.py`、`foodmate-application/src/main/java/com/foodmate/application/knowledge`、`script/data/knowledge/public/manifest.json`、执行记录 D174。

### 演示六：记忆冲突和临时要求

输入一：`平时我喜欢吃鱼。`

观察点：生成 `preference/diet_style` 候选，Java 校验后进入记忆流程。

输入二：`这周晚餐吃什么？`

观察点：这是当前任务，不生成长期记忆。

输入三：`我更倾向于清淡饮食。`

观察点：同 key 的不同值进入冲突，未确认前不注入 Context；确认后旧冲突值被拒绝，摘要失效。

证据位置：`agent-runtime/agent_core.py`、`agent-runtime/tests/test_memory_context.py`、`foodmate-application/src/main/java/com/foodmate/application/conversation/service/impl/MemoryCandidateServiceImpl.java`。

## 13. 面试高频追问与回答

### 13.1 架构与边界

**1. 为什么不让 Python 直接访问 PostgreSQL？**

Python 的输出受模型和 Prompt 影响，直接给数据库凭据会把自然语言不确定性带入业务写入和权限边界。Java 作为控制面统一做用户归属、事务、schema、幂等和审计，Python 只能通过协议提交候选。

**2. 为什么 Java 和 Python 不合成一个服务？**

当前项目中 Java 已经承载认证、事务和业务数据，Python 侧更适合模型 SDK、RAG 和 Workflow。拆分的重点不是语言偏好，而是把不确定的推理边界和确定的业务授权边界隔离。

**3. RocketMQ 在这里解决什么问题？**

它把 Java 请求和 Python 执行解耦，并允许通过 Outbox、Consumer 和状态记录观察异步阶段。它不保证业务恰好一次，所以应用层仍必须实现 Inbox、幂等和状态机。

**4. Redis 和 PostgreSQL 如何分工？**

PostgreSQL 保存业务事实、审计和消息权威状态；Redis 保存 admission、lease、checkpoint、Inbox/Outbox 技术状态和 stub 索引。Redis 丢失时不能把业务记录当成不存在，也不能以 checkpoint 推断业务写入成功。

**5. AgentRun 和 checkpoint 有什么区别？**

AgentRun 是 Java 面向用户的业务执行实体，负责状态、归属、审计和终态；checkpoint 是 Python 内部恢复游标，保存节点和循环进度。两者通过命令和事件关联，但不能互相替代。

**6. 为什么需要 Outbox？**

业务事务提交和消息发送是两个资源，直接先写数据库再发 MQ 可能在进程崩溃时留下未发送消息。Outbox 让消息事实先跟业务事务一起提交，再由 Relay 负责发布和重试。

**7. 为什么不宣称 exactly-once？**

网络、Broker 和消费者都可能重复投递；系统能做到的是至少一次传输加业务幂等。对外承诺的是“同一业务键的副作用最多一次”，不是底层消息天然只到达一次。

**8. 模型失败和业务失败有什么区别？**

模型失败是 provider 超时、结构化输出不合规或 Eval 拒绝；业务失败是权限、参数、revision、数据库或工具规则失败。两者都要进入可观测终态，但错误码、重试边界和用户提示不同。

**9. 为什么要有固定 Workflow？**

固定 Workflow 把可执行动作限制在有限节点和预算内，模型只能填结构化决策，不能动态跳任意函数。这样更容易测试追问、工具拒绝、模型失败、重试和安全降级。

**10. 如何避免无限反思和模型循环？**

Runtime 使用 `MAX_TOTAL_STEPS`、`MAX_MODEL_CALLS`、重试、重规划和答案重写预算；每次 Run 保存不可变预算快照。预算耗尽后进入降级或失败，不追加无限调用。

### 13.2 写入、事务与幂等

**11. 为什么写入前必须用户确认？**

饮食记录和计划保存属于用户可见副作用，模型识别可能有歧义。确认卡把“模型建议”变成“用户明确授权”，同时让用户看到参数、营养匹配状态和风险提示。

**12. 用户点击两次确认会怎样？**

服务端按确认引用、Proposal 状态和幂等键裁决。第一次成功后第二次只能复用结果或返回已执行，不会再次插入饮食记录或计划。

**13. 同一个幂等键换了参数怎么办？**

返回参数摘要冲突，拒绝执行。否则客户端重试时可能把一个业务键误当成两个不同动作，破坏审计和对账。

**14. 如果业务写入成功但发送 Result 失败呢？**

业务写入、审计和 Result Outbox 在同一事务中，Relay 后续可以继续发布；消费者重复收到 Result 也按 Inbox/结果幂等处理。前端暂时看不到结果不代表数据库业务事实不存在，查询和 SSE 回读可以恢复。

**15. 如果审计写入失败呢？**

按失败关闭处理，业务事务回滚；失败事实由独立事务记录，至少保留稳定错误码和关联 ID。不能为了用户看到成功而静默跳过审计。

**16. `revision` 解决什么问题？**

它防止用户基于旧页面内容更新或删除新版本记录。服务端使用 CAS 比对 revision，变化后返回冲突，要求前端刷新，而不是覆盖最新数据。

**17. Proposal 的 `superseded` 有什么意义？**

它表示旧候选已被新候选或 continuation 替代，旧确认即使晚到也不能执行。这样可以把“等待用户期间产生的新事实”纳入状态机，而不是让旧请求竞争写入。

**18. 失败重试会不会重复营养计算？**

重试复用业务幂等键和已有事实，Java 先查询执行结果；营养计算和业务写入都由确定性服务处理。重复 Proposal 不应重新创建一条业务记录。

**19. Java 为什么还要在 Confirm 后再校验一次？**

展示确认卡和真正执行之间可能发生权限、版本、数据或 Proposal 状态变化。Confirm 不是永久授权，执行前必须再次检查用户归属、摘要、有效期、revision 和状态。

**20. 失败审计为什么用独立事务？**

如果失败审计和业务事务共用事务，业务回滚会把失败事实一起回滚。独立事务可以保留“失败发生过”的证据，同时不保存敏感原文。

### 13.3 SSE 与异步恢复

**21. SSE 断线如何恢复？**

前端保存最后一个 `sse_event_id`，重连时发送 `Last-Event-ID`；Java 从持久化事件按序号回放，前端去重。终态事件只展示一次。

**22. SSE 是不是消息队列的替代品？**

不是。RocketMQ 负责运行时之间的异步传输，SSE 只负责把 Java 已授权的事件投影给浏览器；SSE 断线不能影响业务事务和 MQ 消费。

**23. 如果前端已经显示回答但没有收到 `run.completed` 呢？**

回答分片只是中间事件，前端不能把它当作完成。重连后按最后事件回放，只有持久化的唯一终态才能关闭运行状态并展示最终引用/结果。

**24. 如何防止重复终态？**

Java 对终态迁移和事件 ID 做幂等保护；同一 Run 进入终态后拒绝非法回退或第二个冲突终态。前端还按事件 ID 去重，形成服务端和客户端两层防护。

**25. 为什么需要把引用放进 `run.completed`？**

引用是终态回答的一部分，放在终态 payload 能让历史会话恢复时重新展示，不依赖浏览器当时是否收到了某个中间事件。引用仍需经过 Java 可见性校验。

### 13.4 RAG 与数据质量

**26. 结构化营养目录为什么不直接全部当文档切块？**

营养值和单位换算需要精确数值、版本和计算规则，向量相似度不能作为营养事实。目录用 PostgreSQL 做权威回源，向量只用于候选召回；公共知识文档才以 chunk 和引用为主。

**27. 为什么 chunk 要保存 section path？**

标题路径能保留上下文，并且让引用可以解释“这段内容来自哪一章”。只存连续文本会丢失章节语义，用户也难以复核来源。

**28. chunk 为什么需要 overlap？**

跨段落或跨句的定义可能被硬切断，有限重叠可以保留边界语义。重叠必须有上限，避免索引膨胀和重复引用。

**29. Milvus 里为什么要保存 metadata？**

向量相似度只解决相关性，不解决租户、发布状态、文档版本和删除状态。metadata 是检索过滤所需的投影，但最终权限仍由 Java 权威状态和二次校验决定。

**30. 文档上传后为什么不立即可检索？**

上传、解析、索引和发布是不同状态。只有索引完成且管理员显式发布后才进入公共可见范围，避免半成品、未审阅或索引错误的文档出现在用户回答中。

**31. 真实 Embedding 和 stub 的区别是什么？**

stub 用确定性向量和关键词索引验证协议、ACL、状态和引用规则，不产生外部费用；local 使用 OpenAI-compatible Embedding 和 Milvus 验证真实语义检索。两者通过模式配置严格隔离，local 缺配置时失败关闭，不自动降级 stub。

**32. 如何避免旧版本向量泄漏？**

文档状态、当前版本和 Milvus/Redis metadata 同步由可重放 visibility 任务维护；检索固定过滤当前版本，Java 输出引用前再查一次。下线或删除不是只改 UI，而是改变 Java 权威状态并投影到索引。

**33. 引用为什么限制数量？**

限制候选、rerank 和最终引用可以控制上下文成本，避免单个文档或重复 chunk 主导回答。当前最多召回 12、重排 6、最终 4，每文档最多 2 条，并在无证据时明确返回证据不足。

**34. RAG 能不能回答医疗诊断？**

不能。系统公共知识可以提供一般营养资料，但不把检索结果包装为诊断、治疗、处方或个体医疗建议；高风险内容需要安全降级并提示专业人员。

### 13.5 SQL Agent 与安全

**35. 为什么需要 AST Guard？**

字符串黑名单容易绕过，AST 可以判断语句类型、表、列、函数和嵌套结构。Java 在执行前拒绝写操作、未知 schema、系统表、越权条件和不安全表达式。

**36. 当前用户过滤应该由模型生成吗？**

不应该。用户范围由 Java 根据认证身份和允许的查询模板注入，模型只提供查询意图和有限结构。否则模型可能遗漏条件或被用户文本诱导查询其他用户数据。

**37. SQL 参数为什么必须绑定？**

绑定参数可以避免把自然语言输入直接拼进 SQL，减少注入和类型问题。即使 AST 通过，参数范围、日期窗口、LIMIT 和超时仍由 Java 校验。

**38. SQL Agent 如何处理无数据？**

它返回明确的数据为空原因和统计口径，不把空数组编造成“0”或趋势结论。时间范围缺失、食材名称歧义和不支持字段会先进入澄清。

**39. SQL Agent 能不能直接执行保存计划？**

不能。SQL Agent 是只读分析能力；计划保存和饮食写入是高风险业务动作，仍必须经过独立 Proposal、用户确认和 Java Tool Gateway。

**40. 如何审计 SQL 又避免泄露数据？**

记录查询意图、schema、参数摘要、行数、耗时、错误码和关联 ID，按专用 SQL 审计保存；不把完整敏感结果、Token 或未脱敏 Prompt 放进通用业务审计。

### 13.6 记忆、测试和工程取舍

**41. 为什么把记忆做成结构化 key/value？**

结构化 key 可以去重、冲突、确认、TTL、删除和按意图白名单过滤；保存整句话会造成重复和无法判断范围。原始来源只保留消息 ID，便于追溯和来源抑制。

**42. “这周晚餐吃什么”为什么不能成为记忆？**

它是当前任务请求，不是跨会话稳定偏好。规则层过滤今天、这周、今晚等一次性时间词，Java 白名单和确认边界再做第二道防线。

**43. 冲突记忆何时生效？**

不同值进入 `conflict`，未经过用户确认不能进入 Context。用户确认后保留选择值并拒绝同 key 其他冲突，摘要失效，下一次 Run 重新读取授权结果。

**44. 为什么不把过敏事实当普通记忆？**

过敏属于高影响健康事实，误提取或误修改会有安全后果。当前代码拒绝医疗、高风险健康和营养目标进入普通长期记忆；领域约束应有专门字段和更高的确认门槛。

**45. 普通 pytest 为什么不能默认调用真实服务？**

普通测试应该可重复、离线、无费用；`conftest.py` 会把默认模型路由到 deterministic。真实 Chat/Embedding 必须显式开启，并带预算和 fail-closed 约束。

**46. 你如何证明不是只写了 mock？**

分别保留三类证据：纯业务单元测试证明状态和边界，Docker/本地 HTTP 或 RocketMQ 回归证明跨进程协议，D174 记录证明真实 Embedding、Milvus、Java 回写和一次真实 Chat 引用。每类证据的范围写在执行记录中，不混淆。

**47. 当前测试为什么不包含性能压测？**

当前项目目标是先完成可复现的业务闭环，性能数字受本机 Docker、供应商和数据量影响，不能替代生产容量结论。压测、长稳和多实例容量被单独后置，避免用一次本机数字伪造 SLO。

**48. 如果让你下一步做生产化，你会先做什么？**

先补充可重复的依赖故障矩阵和消息语义证据，再做容量模型、数据库备份恢复、密钥轮换、监控告警和发布回滚；同时保留当前业务幂等和审计边界。不会先把 Kubernetes YAML 当成生产可靠性本身。

## 14. 代码与证据索引

| 主题 | 首选代码/文档路径 | 面试用途 |
|---|---|---|
| Java 模块边界 | `foodmate-application/src/main/java`、`foodmate-infra/src/main/java`、`foodmate-api/src/main/java` | 解释控制器、用例和适配器分层 |
| Agent 主编排 | `agent-runtime/agent_core.py` | 解释 Router、Planner、Context、Composer、Proposal |
| Python Runtime HTTP/MQ | `agent-runtime/runtime_server.py`、`agent-runtime/mq_runtime.py` | 解释命令接收、事件和消费者 |
| RAG 检索 | `agent-runtime/knowledge_rag.py`、`agent-runtime/knowledge_worker.py` | 解释 chunk、stub、Embedding、Milvus 和引用 |
| SQL Planner | `agent-runtime/sql_planner.py`、`foodmate-application/src/main/java/com/foodmate/application/sql` | 解释只读 Proposal 与 Java Guard |
| 记忆候选 | `agent-runtime/agent_core.py`、`agent-runtime/tests/test_memory_context.py` | 解释自然表达、临时过滤和候选来源 |
| Java 记忆裁决 | `foodmate-application/src/main/java/com/foodmate/application/conversation/service/impl/MemoryCandidateServiceImpl.java` | 解释白名单、冲突、确认和删除 |
| 写确认 | `foodmate-application/src/main/java/com/foodmate/application/approval`、`foodmate-application/src/main/java/com/foodmate/application/food` | 解释 Proposal/Confirm/Execute |
| AgentRun 事件 | `foodmate-application/src/main/java/com/foodmate/application/runtime`、`foodmate-api/src/main/java` | 解释状态机、SSE 和终态保护 |
| 知识库 Java 控制面 | `foodmate-application/src/main/java/com/foodmate/application/knowledge`、`foodmate-infra/src/main/java/com/foodmate/infrastructure/persistence/knowledge` | 解释索引结果回写和可见性 |
| 前端聊天 | `foodmate-ui/src/pages/ChatPage`、`foodmate-ui/src/services/agentService.ts` | 解释真实 SSE、确认、轨迹和引用 |
| 前端规划 | `foodmate-ui/src/pages/PlanningPage` | 解释生成候选后确认保存 |
| 数据库迁移 | `script/sql/FoodMate/migrations`、`script/sql/FoodMate/validation` | 解释版本、索引和状态约束 |
| 真实 RAG 证据 | `script/data/knowledge/public/manifest.json`、`script/sql/FoodMate/EXECUTION_RECORD.md` D174 | 解释真实 Embedding/Milvus/Chat 证据边界 |
| 业务状态路线 | `docxs/项目/路线图.md`、`README.md` | 解释当前完成和后置范围 |

## 15. 面试前检查清单

- 能在 30 秒内说清 Java 是权威控制面，Python 不能直接写库。
- 能画出 Java Outbox -> RocketMQ -> Python -> Result/Event -> Java -> SSE。
- 能解释为什么 Proposal 不等于执行，Confirm 不等于永远有效。
- 能举出同键同参数、同键不同参数和 revision 冲突三种幂等结果。
- 能区分 PostgreSQL 业务真值、Redis 技术状态和 Milvus 检索索引。
- 能说明结构化营养目录、公共文档 RAG 和用户记忆的三种数据边界。
- 能现场演示一次“先产生候选、用户确认后才写入”的流程。
- 能现场演示一次 SQL 拒绝，而不是只演示成功查询。
- 能说清 SSE `Last-Event-ID` 是业务回放机制，不是 MQ exactly-once。
- 能主动说明当前未完成的生产级内容，不用“高可用”“实时”“零重复”做没有证据的绝对承诺。

## 16. 简历表述建议

可以写：

> 设计并实现 Java 控制面 + Python Agent Runtime 的饮食 Agent：通过 PostgreSQL Outbox/RocketMQ/Redis Checkpoint 完成跨运行时编排；以 Java Tool Gateway 统一权限、Proposal-Confirm-Execute、幂等和审计，支持饮食记录与餐食计划安全写入；基于 OpenAI-compatible Embedding + Milvus 实现版本化公共知识库 RAG，并通过 SSE 输出受 ACL 校验的引用。

面试时补充：

> 当前已完成本地业务正确性闭环和一次真实云模型/真实 Embedding 验证；性能压测、长稳、完整依赖故障矩阵、备份恢复和生产发布治理是明确后置项。

不要写：

- “保证 exactly-once”。
- “已达到生产级高可用”。
- “模型可以直接操作数据库”。
- “RAG 可以提供医疗诊断”。
- “一次本机测试结果就是系统吞吐 SLO”。

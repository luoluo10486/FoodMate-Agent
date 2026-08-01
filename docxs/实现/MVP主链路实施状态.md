# FoodMate MVP 主链路实施状态

更新时间：2026-08-01

> 模板提示：后续 AI 阅读本文档时，必须按功能点拆分为独立小节；只记录已实现和已验证的事实，不得把真实模型、RAG、工具调用或饮食业务写入提前写成已完成。

## 1. 当前结论

- M1-2 已完成真实认证、会话、消息持久化和前端真实 API 接入。
- M1-3 已完成 Java -> Python 确定性 stub -> Java -> SSE 的最小真实闭环。
- M1-4 已完成 RocketMQ/Redis 基础传输、模型适配、预算、LangGraph 白名单图、Eval Gate、Proposal/Result 回注、结构化摘要和恢复入口；本地真实浏览器闭环与 Python 重启后的跨进程 checkpoint 恢复已验证。生产级容量、价格审计、真实云长时间稳定性和生产 Eval 治理仍未完成。

## 2. 已完成主链路

用户注册或登录。

用户创建会话并发送消息。

Java 在同一事务中创建 AgentRun、dispatch 和 dispatch outbox。

Java 使用 PostgreSQL Dispatch Outbox 将 RunCommand 发布到 RocketMQ command topic；Python 通过 Redis Inbox 幂等消费。

Python 确定性 stub 产生 run.accepted、run.routed、两段 run.answer_stream、run.completed，并经 Event Outbox 发布到 RocketMQ event topic。

Java RocketMQ consumer 校验事件身份、摘要、顺序和状态，再写入 PostgreSQL 事件 Inbox、AgentRun 投影和 SSE Outbox。

前端用 agent_run_id 订阅 SSE，展示分段文本、完成、失败或取消。

## 3. 已验证的小点

### 3.1 主链路

- 真实 PostgreSQL 下已验证注册、创建会话、发送消息、创建 AgentRun、Java/Python 回调和 SSE。
- 成功 run 的状态为 completed，事件 inbox 有 5 条事件，SSE outbox 有 5 条事件。

### 3.2 取消

- 已验证事件顺序为 run.accepted、run.cancel_acknowledged、run.cancelled。
- 取消后 AgentRun 为 cancelled，取消记录状态为 resolved。

### 3.3 SSE 恢复和越权

- Last-Event-ID 按持久化 stream_seq 恢复，不重复推送已消费事件。
- 用户 B 查询、订阅或取消用户 A 的 run 均返回 HTTP 403。

### 3.4 自动化验证

- 历史 M1-3 验证曾记录 Python pytest、Java 全模块测试和前端 typecheck 通过；具体旧计数只对应当时提交，不作为当前测试总数。
- M1-4 基础设施阶段另有 RocketMQ 真实往返、Redis/PostgreSQL 状态、continuation `superseded` 和 MQ transport E2E 记录；依赖当前是否在线必须现场检查。

## 4. 当前架构边界

- Java 是用户、授权、业务数据、AgentRun 状态和 SSE 的唯一权威。
- Python 不持有业务数据库凭据，不直接写业务表。
- 默认 Python 运行 `deterministic:local`；显式配置云 tier 时支持真实 OpenAI-compatible 模型调用，但默认不会联网。
- Java 当前采用 6 个 Maven 模块；未来能力先按包组织，避免预建空模块。

## 5. 未完成项与下一步

### 5.1 M1-4

- Python 基础版本、依赖、配置、健康检查、结构化日志、模型适配器、预算、checkpoint 和 pytest 门禁已建立；默认仍使用 deterministic stub。
- LangGraph 白名单图、独立 Eval、正文延迟发布、模型用量事件、Redis 并发与队列基线已实现并有测试证据。
- 普通缺参 continuation 与 `superseded`、预算追加、恢复入口和 Eval 后交付已由 Java、Python 与前端共同落地。
- 仍需在生产目标环境完成真实云长时间重复稳定性、长压容量结论、多实例业务流量、故障恢复指标和正式价格/账单审计。
- 已实现最近 8 条消息、结构化摘要、摘要 CAS、计划型/临时型记忆 TTL 与过期过滤；按意图精细检索、删除防再生、派生摘要失效和完整 Java 恢复对账仍未实现。
- 当前无人审核，`request_review` 只能安全降级，不建设 `waiting_review`。
- 继续禁止 Python 直接访问业务数据库或绕过 Java 授权。

上述未完成项属于生产收尾与后续 Agent 能力目标；本地代码和测试已具备可用闭环，但不代表 M1-4 已达到生产发布门槛。

### 5.2 后续阶段

- 饮食记录、营养分析、餐食计划、写确认和 Java 工具控制面属于 M1-5。
- 审计、可观测性、部署、恢复演练属于 M1-6。
- RAG、知识库、SQL Guard 和运营治理属于 M2。

## 6. 2026-08-01 收尾复核

### 已验证

- Java 恢复入口、浏览器真实闭环、Python 重启后的 checkpoint 恢复、RocketMQ 传输和最终 SSE 已完成本地真实验证。
- Eval Gate 已进入正式运行路径；Python `56 passed, 1 skipped`，前端 Vitest `4 passed`、typecheck/build 通过。
- Redis 准入并发 `6 passed`；30 秒长压 P50/P95/P99 已采集，结果仅作为本机 Docker 基线。

### 仍待生产收尾

- 生产级长压与容量结论、队列防饥饿、多实例 Agent 业务流量、进程级故障恢复、真实云长时间稳定性和正式价格/账单审计。
- 生产 Eval 还需要固定版本、人工校准样本、统一指标存储和告警；本地 Eval 测试不等同生产质量结论。

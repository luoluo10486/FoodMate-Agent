# FoodMate MVP 主链路实施状态

更新时间：2026-07-25

> 模板提示：后续 AI 阅读本文档时，必须按功能点拆分为独立小节；只记录已实现和已验证的事实，不得把真实模型、RAG、工具调用或饮食业务写入提前写成已完成。

## 1. 当前结论

- M1-2 已完成真实认证、会话、消息持久化和前端真实 API 接入。
- M1-3 已完成 Java -> Python 确定性 stub -> Java -> SSE 的最小真实闭环。
- 当前下一阶段是 M1-4：在不改变 Java 业务数据权威边界的前提下，建设受控的 Python 模型运行能力。

## 2. 已完成主链路

用户注册或登录。

用户创建会话并发送消息。

Java 在同一事务中创建 AgentRun、dispatch 和 dispatch outbox。

Java 使用 Service JWT 调用 Python V1 dispatch。

Python 确定性 stub 回传 run.accepted、run.routed、两段 run.answer_stream、run.completed。

Java 校验事件身份、摘要、顺序和状态，再写入事件 inbox、AgentRun 投影和 SSE outbox。

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

- Python pytest：3 个测试通过。
- Java：Shared 10、Gateway 4、Infrastructure 15、Application 5、API 27、Bootstrap 21 个测试通过；依赖环境的 5 个本地 E2E 按条件跳过。
- 前端 typecheck 通过。

## 4. 当前架构边界

- Java 是用户、授权、业务数据、AgentRun 状态和 SSE 的唯一权威。
- Python 不持有业务数据库凭据，不直接写业务表。
- 当前 Python 只运行确定性 stub，不产生真实模型回答。
- Java 当前采用 6 个 Maven 模块；未来能力先按包组织，避免预建空模块。

## 5. 未完成项与下一步

### 5.1 M1-4

- 固定 Python 版本、依赖锁、配置加载、健康检查、结构化日志和 pytest 门禁。
- 建立模型供应商适配器、Router、Planner、Execution Engine、超时和降级策略。
- 建立 Prompt 版本、离线样例、回归评测和模型用量事件。
- 继续禁止 Python 直接访问业务数据库或绕过 Java 授权。

### 5.2 后续阶段

- 饮食记录、营养分析、餐食计划、写确认和 Java 工具控制面属于 M1-5。
- 审计、可观测性、部署、恢复演练属于 M1-6。
- RAG、知识库、SQL Guard 和运营治理属于 M2。

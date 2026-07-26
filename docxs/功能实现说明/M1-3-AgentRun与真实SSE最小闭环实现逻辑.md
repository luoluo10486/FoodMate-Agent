# M1-3 AgentRun 与真实 SSE 最小闭环实现逻辑

> 本文档用于记录 M1-3 的已完成实现逻辑。
> 后续 AI 阅读本文档时，必须按功能点拆分为独立小节，不能把多个功能点写成一大段；不得把真实大模型、RAG、工具调用或业务写入提前写成 M1-3 已完成。
> 后续演进说明（2026-07-26）：本文只记录已经验证的 M1-3 HTTP Runtime stub 实现。M1-4 将目标异步主通道改为 RocketMQ，但尚未完成代码；不得修改本文把目标设计伪装成 M1-3 实现事实。

## 1. 文档信息

| 项目 | 内容 |
|---|---|
| 功能名称 | AgentRun、跨服务事件与真实 SSE 最小闭环 |
| 功能编号/阶段 | M1-3 |
| 文档版本 | v1.0 |
| 实现日期 | 2026-07-25 |
| 适用环境 | local / dev |
| 实现状态 | 已验收 |
| 关联方案或需求 | M1-3 AgentRun 与真实 SSE 最小闭环实施方案 |

## 2. 功能概述

### 2.1 功能目标

- 用户发送消息后，后端创建 AgentRun，并把运行命令派发给 Python Runtime。
- Python Runtime 使用确定性 stub 生成固定顺序事件。
- Java 接收运行事件，写入事件入箱和 SSE outbox，再通过 SSE 推送给前端。
- 取消、恢复和越权校验都在真实数据库和真实接口上验证。

### 2.2 适用范围

- AgentRun 创建、查询和取消。
- Java -> Python dispatch、Python -> Java event 回调。
- SSE 推送、断点续传和事件去重。
- 用户归属校验、取消记录收敛、事件投影。

### 2.3 不包含范围

- 真实大模型回答生成。
- RAG、工具调用、SQL 调用、饮食写入。
- 复杂业务编排和多轮模型推理质量。

## 3. 接口清单

| 接口地址 | HTTP 方法 | 接口名称 | 是否需要登录 | 是否需要 CSRF | 允许角色 | 幂等性 |
|---|---|---|---|---|---|---|
| /api/agent-runs/{runId} | GET | 查询运行状态 | 是 | 否 | user | 是 |
| /api/agent-runs/{runId}/cancel | POST | 取消运行 | 是 | 是 | user | 否 |
| /api/agent-runs/{runId}/stream | GET | SSE 订阅/恢复 | 是 | 否 | user | 是 |
| /internal/runtime/runs:dispatch | POST | Java 派发运行命令 | 内部 | 是 | control-plane | 是 |
| /internal/runtime/runs:cancel | POST | Java 派发取消命令 | 内部 | 是 | control-plane | 是 |
| /foodmate/internal/v1/agent-events | POST | Python 回传事件 | 内部 | 是 | runtime | 是 |

## 4. 功能点实现

### 4.1 AgentRun 创建

- 代码入口是 AgentRunCommandService.java 和 ChatController.java。
- 用户发送消息后，后端先在事务里写入消息，再创建 AgentRun 和 dispatch 记录。
- 创建成功后返回 agent_run_id，前端用它建立状态查询和 SSE 订阅。
- 本阶段的 AgentRun 只承载最小闭环，不包含真实模型调用细节。

### 4.2 Java dispatch 到 Python

- 代码入口是 RuntimeGatewayController.dispatch、RuntimeDispatchPublisher 和 V1HttpRuntimeClient。
- Java 按 V1 RunCommand 结构签发 Service JWT，调用 Python /foodmate/internal/v1/runs。
- publisher 负责重试、租约和 deadline 收敛，避免重复派发。
- Python 返回固定事件流，不直接写业务数据库。

### 4.3 Python 确定性 stub

- 代码入口是 agent-runtime/runtime_server.py 和 agent-runtime/tests/test_runtime_server.py。
- stub 固定输出 run.accepted、run.routed、两段 run.answer_stream、run.completed。
- 取消时先返回 run.cancel_acknowledged，再返回 run.cancelled。
- stub 只验证系统链路，不验证模型质量。

### 4.4 Java 事件入箱和状态投影

- 代码入口是 V1RuntimeEventService.java。
- Java 接收 Python 回调后先做 JWT、契约版本、事件序列和摘要校验。
- 合法事件写入 runtime_event_inbox_v2，再投影到 agent_runs 和 SSE outbox。
- 失败事件会被拒绝，不推进运行状态。

### 4.5 SSE outbox 与断点续传

- 代码入口是 V1RunStreamController.java 和 agent_run_sse_outbox。
- SSE 按持久化 stream_seq 推送，不直接依赖内存游标。
- Last-Event-ID 会被解析成真实 cursor，断线后可以继续发送后续事件。
- 前端收到 sse_event_id 后去重，避免重复消费。

### 4.6 取消链路

- 代码入口是 RuntimeCancellationService.java 和 V1AgentRunController.cancel。
- 用户发起取消后，Java 写入取消记录并提升 cancellation epoch。
- Java 再调用 Python cancel 接口，Python 只补发取消确认和终态事件。
- 取消完成后，agent_run_cancellations 收敛为 resolved，agent_runs 进入 cancelled。

### 4.7 越权和归属校验

- V1AgentRunController.status、cancel 和 V1RunStreamController.stream 都先校验 run 归属。
- 用户只能访问自己的 run，不能查询、订阅或取消别人的 run。
- 越权统一返回 403，不泄漏对方运行是否存在。

## 5. 涉及的数据库表

- agent_runs
- agent_run_dispatches
- runtime_dispatch_outbox
- runtime_event_inbox_v2
- runtime_event_rejections
- agent_run_cancellations
- agent_run_sse_outbox

## 6. 状态与一致性

| 对象 | 初始状态 | 状态变化 | 触发条件 | 失败/重试规则 |
|---|---|---|---|---|
| agent_runs.status | queued | routed -> completed/failed/cancelled | dispatch 或事件回调 | 重复事件不重复推进 |
| agent_run_cancellations.status | requested | acknowledged -> resolved | Python 回传取消事件 | 重试保持同一取消标识 |
| agent_run_sse_outbox.status | pending | sent | SSE publisher 成功发送 | 断线后按 stream_seq 恢复 |

## 7. 验收记录

| 验收项 | 验证方式/命令 | 结果 | 验证日期 | 证据位置 |
|---|---|---|---|---|
| 主链路真实联调 | 用户注册、创建会话、发送消息、SSE 观察 | 通过 | 2026-07-25 | PostgreSQL 记录和 SSE 结果 |
| 取消链路 | 运行中取消并观察事件顺序 | 通过 | 2026-07-25 | PostgreSQL 记录和 SSE 结果 |
| 断点续传 | Last-Event-ID 恢复 | 通过 | 2026-07-25 | SSE 恢复验证结果 |
| 跨用户越权 | 用户 B 访问用户 A 的 run | 通过 | 2026-07-25 | HTTP 403 验证结果 |
| 单元测试 | Python pytest -q、Java mvn test | 通过 | 2026-07-25 | 测试输出 |

## 8. 已知限制和后续工作

- 目前只实现最小真实闭环，不接真实大模型。
- RAG、工具调用、SQL/业务写入和附件能力留给后续阶段。
- runtime_event_inbox_v2 与旧表的统一迁移属于后续兼容收尾。

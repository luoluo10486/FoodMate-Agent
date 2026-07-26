# ADR-0005：RocketMQ 异步主通道

- Status: Accepted
- Date: 2026-07-26

## 背景

FoodMate 的 Java 控制面与 Python Agent Runtime 需要可靠传递 RunCommand、RunEvent、Tool/SQL Proposal 和 Result。M1-3 使用 PostgreSQL Outbox + HTTP 完成确定性 stub 最小闭环，但目标架构还需要削峰、消费组、重试、死信和后台任务解耦。Redis 已用于业务准入和 Python 技术状态，不能同时承担跨运行时消息总线职责。

## 决策

正式目标采用 RocketMQ 普通消息作为异步跨运行时主通道，不使用 RocketMQ 事务消息。Java 使用 PostgreSQL Transactional Outbox/Inbox；Python 本地阶段使用启用 AOF 的 Redis 保存 Inbox、Event/Proposal Outbox 和 LangGraph checkpoint。双方均按至少一次投递设计，以稳定消息 ID、canonical digest、Inbox、状态机和 fencing 吸收重复。

本地开发只部署单 NameServer + 单 Broker。Topic、consumer group、Repository 和配置保留未来扩展位，但本阶段不设计生产集群、TLS 或 ACL。

## 消息边界

| Topic | 方向 | 消息 |
|---|---|---|
| `foodmate.agent.command.v1` | Java -> Python | RunCommand、CancelCommand |
| `foodmate.agent.event.v1` | Python -> Java | RunEvent、RuntimeError |
| `foodmate.agent.proposal.v1` | Python -> Java | ToolProposal、SqlProposal |
| `foodmate.agent.result.v1` | Java -> Python | ToolResult、SqlResult |
| `foodmate.knowledge.command.v1` | Java -> Worker | 文档解析、索引和删除命令 |
| `foodmate.audit.event.v1` | 业务服务 -> 审计消费者 | 脱敏审计与用量事件 |
| `foodmate.notification.command.v1` | Java -> Worker | 通知和异步导出结果 |

Agent 消息使用 `run_id` 作为局部顺序键；不同 Run 可并行。RocketMQ 顺序不能替代 `dispatch_id/attempt/event_seq`、数据库 inbox、gap 校验或终态裁决。

## 准入与传输分工

1. Java 在 PostgreSQL 保存 AgentRun 和权威排队事实。
2. Redis 执行 P0-P3 优先调度、用户/全局 permit、lease、aging 和防饥饿。
3. 获得 permit 后，Java 在事务中固化 dispatch 和 RunCommand Outbox。
4. Outbox Relay 收到 Broker 持久化确认后将消息标记 `published`。
5. Python 持久化 Inbox 后才接受执行；Redis 不可用时停止消费。
6. Python 将 checkpoint 与 Event/Proposal Outbox 通过 Lua/CAS 原子写入，再由 Relay 发布。
7. Java 消费后在 PostgreSQL 事务中完成 Inbox、AgentRun 投影、审计和 SSE Outbox，提交后 ACK。

RocketMQ 负责跨服务运输，Redis 负责业务调度，PostgreSQL 保存业务真值。MQ 不可用时不得自动退回 HTTP 发送业务消息。

## 控制命令

浏览器的取消、工具审批和预算追加继续调用 Java HTTP API。Java落库后通过 RocketMQ 发送 CancelCommand 或新 dispatch RunCommand。直连 Python 的 HTTP 取消只能作为可选 wake-up，不是权威取消通道。

## Outbox 与 Inbox

- 不使用 RocketMQ 事务消息，避免与数据库 Outbox 形成两套事务协调机制。
- Outbox `published` 只表示 Broker 已确认，不表示消费者业务处理完成。
- 消费端必须在本地事务或原子技术状态提交后 ACK。
- 数据提交后、ACK 前崩溃允许重投，由 Inbox 幂等吸收。
- DLQ 只表示消息处理耗尽重试，不能自动把 AgentRun 判为失败；Java Reconciler 对账后裁决。
- DLQ 重放保持原 `message_id/run_id/dispatch_id/attempt/request_hash`。

## 回答分片

候选答案通过 Final Eval 后，Python 按可配置时间或大小切分 `run.answer_stream`，默认 150ms 或 2048 字节满足其一即形成分片。不得逐 Token 发布 MQ 消息。每个分片仍具有稳定 `event_id/event_seq/request_hash`。

## SQL Agent 边界

Python 只读取脱敏、版本化 Schema Catalog 并生成 SqlProposal。Java SQL Guard 使用只读业务数据库账号执行 AST、白名单、用户/租户过滤、限行、超时、脱敏和审计。Python 不连接 FoodMate 业务数据库。

## 替代方案

1. 继续 HTTP 主链路：实现简单，但缺少统一异步削峰、消费组和 DLQ。
2. Redis 同时承担调度与消息总线：组件更少，但业务优先级、可靠运输和 checkpoint 容易形成耦合状态机。
3. RocketMQ 事务消息：仍不能消除消费端幂等，并与现有 PostgreSQL Outbox 重复。
4. Python 直接连接业务库：便于 SQL 生成后执行，但会绕过 Java 权限、审计和 SQL Guard，拒绝采用。

## 后果

需要新增本地 RocketMQ Compose 服务、Topic 初始化、Java/Python producer/consumer、Outbox Relay、Inbox、DLQ Reconciler、监控和故障注入测试。M1-3 HTTP 链路保留为开发/契约测试适配器，不作为目标正式主通道。

## 重新评估条件

只有在真实吞吐、运维成本或部署约束证明 RocketMQ 不适合时才重新评估。任何替换必须保持业务 envelope、Outbox/Inbox、幂等、顺序键、fencing 和 Java 权威边界。

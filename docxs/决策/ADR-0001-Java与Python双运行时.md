# ADR-0001：Java 与 Python 双运行时

- Status: Accepted
- Date: 2026-07-11

## 背景

FoodMate 的业务控制面需要权威状态、审计、授权、Tool/SQL 执行和数据库事务；Agent 推理需要编排、RAG、模型调用、Prompt、评测和可重建 checkpoint。当前已具备 Java/PostgreSQL、RocketMQ、Python/Redis 确定性 stub 的双运行时基础闭环；真实模型、LangGraph、Eval 和完整记忆治理仍是目标设计，不是代码完成证明。

## 决策

采用 Java 控制面与独立 Python Agent Runtime 的双运行时架构。跨运行时消息使用已批准的版本化 envelope；目标正式异步主通道为 RocketMQ，本地/契约测试保留 HTTP/JSON 适配器。Java 发送 RunCommand、CancelCommand 和 Tool/SQL Result；Python回传 RunEvent、RuntimeError 和 Tool/SQL Proposal。所有消息以 `run_id` 关联，并携带契约版本、request/trace 标识、RFC 3339 UTC 时间和幂等摘要。传输、Outbox/Inbox 和 Topic 规则以 ADR-0005 为准。

## 原因

Java 更适合维护事务边界、业务数据、授权和审计，Python 更适合承载快速演进的推理与 RAG 技术栈。传输无关的结构化契约使两端可独立部署、验证和演进，同时避免 Python 通过共享业务数据库绕过控制面。

## 替代方案

1. 单一 Java 运行时：简化部署，但会把推理/RAG 生态和业务控制面耦合在同一演进节奏。
2. 单一 Python 运行时：推理开发方便，但会削弱 Java 已选业务事务、审计和安全边界。
3. Python 直接访问业务数据库或执行工具：集成快捷，但会绕过授权、审计、幂等与 SQL Guard，风险不可接受。

## 后果

需要维护 V1 Schema、MQ Topic/consumer group、HTTP Service JWT 测试适配、超时、重试、DLQ、错误映射、观测和兼容测试。Java 必须先持久化 dispatch/outbox 并以 inbox 接受事件；Python 必须从 Redis 技术状态恢复，不能自行裁决业务终态。RocketMQ 基础链路已经落地并完成本地往返验证，Tool/SQL Proposal、真实模型和生产级故障门禁仍需后续实现。

## 约束

Java 是业务数据、AgentRun、事件接受结果、取消裁决、工具/SQL 调用、模型用量和审计的唯一权威。Python 只保存可丢弃或可重建的技术状态，不持有业务数据库凭据。Tool/SQL 只能由 Python 提议、Java 授权和执行；V1 契约字段、幂等规则和错误码不得被本 ADR 改写。

## 重新评估条件

仅在业务控制面、推理边界、合规要求或运行成本发生实质变化，且通过兼容性、故障注入、性能和安全评审证明另一种边界更优时重评。任何重评必须先更新 ADR、契约、迁移策略和测试矩阵，不能由单侧实现隐式改变。

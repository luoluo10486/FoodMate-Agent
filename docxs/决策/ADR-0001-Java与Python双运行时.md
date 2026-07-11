# ADR-0001：Java 与 Python 双运行时

- Status: Accepted
- Date: 2026-07-11

## 背景

FoodMate 的业务控制面需要权威状态、审计、授权、Tool/SQL 执行和数据库事务；Agent 推理需要编排、RAG、模型调用、Prompt、评测和可重建 checkpoint。当前仓库只有 Java 基础骨架，Python Runtime 尚不存在。双运行时内部契约 V1 和 V2 迁移设计是目标设计，不是代码完成证明。

## 决策

采用 Java 控制面与独立 Python Agent Runtime 的双运行时架构。二者只通过已批准的 V1 内部 HTTPS/JSON 契约通信：Java 发送 RunCommand 和 CancelCommand；Python 回传 RunEvent、RuntimeError，并向 Java 提交 ToolProposal/SqlProposal。所有消息以 `run_id` 关联，使用短期 Service JWT、契约版本、request/trace 标识、RFC 3339 UTC 时间和幂等摘要。

## 原因

Java 更适合维护事务边界、业务数据、授权和审计，Python 更适合承载快速演进的推理与 RAG 技术栈。明确的 HTTP/JSON 契约使两端可独立部署、验证和演进，同时避免 Python 通过共享数据库绕过业务控制面。

## 替代方案

1. 单一 Java 运行时：简化部署，但会把推理/RAG 生态和业务控制面耦合在同一演进节奏。
2. 单一 Python 运行时：推理开发方便，但会削弱 Java 已选业务事务、审计和安全边界。
3. Python 直接访问业务数据库或执行工具：集成快捷，但会绕过授权、审计、幂等与 SQL Guard，风险不可接受。

## 后果

需要维护 V1 Schema、双向 Service JWT、超时、重试、错误映射、观测和兼容测试。Java 必须先持久化 dispatch 并以 inbox 接受事件；Python 必须可从技术 checkpoint 恢复，不能自行裁决业务终态。当前没有 Runtime Client 或 Python 工程，任何实现和部署能力仍需单独落地与验证。

## 约束

Java 是业务数据、AgentRun、事件接受结果、取消裁决、工具/SQL 调用、模型用量和审计的唯一权威。Python 只保存可丢弃或可重建的技术状态，不持有业务数据库凭据。Tool/SQL 只能由 Python 提议、Java 授权和执行；V1 契约字段、幂等规则和错误码不得被本 ADR 改写。

## 重新评估条件

仅在业务控制面、推理边界、合规要求或运行成本发生实质变化，且通过兼容性、故障注入、性能和安全评审证明另一种边界更优时重评。任何重评必须先更新 ADR、契约、迁移策略和测试矩阵，不能由单侧实现隐式改变。

# ADR-0003：工具与 SQL 安全边界

- Status: Accepted
- Date: 2026-07-11

## 背景

Agent 可以建议写入饮食记录、检索知识或查询数据，但这些操作具有用户授权、数据隔离、副作用和审计要求。当前 `foodmate-tool` 与 `foodmate-sql-agent` 只有 marker 类，尚无 ToolPolicy、执行器、SQL AST Guard 或审计链路；现有 YAML 开关不能证明它们可执行。

## 决策

Python Runtime 只能发送 ToolProposal 或 SqlProposal，不能直接调用业务工具、数据库或 SQL。Java 控制面验证 Service JWT、运行上下文、scope、确认要求、幂等键、参数 Schema、deadline 与审计策略后，才执行工具或 SQL 并返回 ToolResult/SqlResult。SQL 必须是只读，并通过 AST 校验、数据源与 schema/表/列白名单、参数化、限行和超时控制。

## 原因

将提议与执行分离，使用户授权、租户范围、审计、幂等和数据访问约束在唯一权威面强制执行。它也使 Python 的模型输出不能直接变成数据库副作用，降低 prompt injection、越权查询和重复执行风险。

## 替代方案

1. Python 直接执行工具或 SQL：实现短，但无法可靠复用 Java 权限、审计与事务边界。
2. 仅靠模型 Prompt 约束 SQL：无法抵御恶意或错误输出，也没有数据库级验证。
3. 完全禁止 Tool/SQL：风险低，但无法支持需要受控行动和数据分析的产品场景。

## 后果

Java 需要实现工具注册/version、Policy、确认、参数校验、执行、结果脱敏、审计和幂等。SQL 需要 AST、只读限制、白名单、参数绑定、最大行数和 timeout 测试。Python 必须处理被拒绝、超时、重放和失败结果，而不是自行重试副作用。当前这些组件都尚未实现。

## 约束

Tool/SQL proposal 必须携带 `run_id`、`dispatch_id`、`attempt`、`invocation_id`、`idempotency_key`、`deadline_at` 和 V1 request hash。相同逻辑调用不得重复执行；摘要冲突必须明确拒绝。Java 不得把运行时 HTTP 接受当作业务执行成功，Python 不得获得业务数据库凭据或绕过 Java 审计。

## 重新评估条件

仅在引入可信沙箱、受监管的数据访问平台、全新工具类别或新的合规要求时重评。重评需要威胁建模、最小权限设计、审计保留策略、恶意 SQL/越权/超时/重复调用测试和回滚方案；在这些证据齐备前，保持 Java 执行边界不变。

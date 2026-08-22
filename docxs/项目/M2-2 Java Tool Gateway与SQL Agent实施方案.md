# M2-2 Java Tool Gateway 与 SQL Agent 实施方案

更新时间：2026-08-22
状态：计划已确认，等待实施
上位计划：[M2剩余功能执行计划.md](M2剩余功能执行计划.md)

## 1. 目标

在现有 `ToolGatewayServiceImpl`、Proposal/Result 消息链路、Approval、`sql_query_audits` 和业务写服务之上，建立统一工具治理与只读 SQL Agent。Python 只能提出工具/SQL proposal；Java 掌握身份、scope、Policy、确认、SQL Guard、数据源凭据、执行和审计。

## 2. 工具范围

首期统一注册七个工具：

| 工具 | 所有者 | 风险 | 确认 | 说明 |
|---|---|---|---|---|
| `calculator` | Java | low | 否 | 安全数值表达式与单位计算 |
| `time_parser` | Java/Python 纯规则 | low | 否 | 服务端时间与时区归一化 |
| `knowledge_search` | Python 检索，Java 授权 | low | 否 | 固定公共知识 scope |
| `database_query` | Java | medium | 否 | 只读、用户范围内的数据查询 |
| `food_log_writer` | Java | high | 是 | 复用现有饮食记录用例 |
| `plan_validator` | Java | low | 否 | 规则化计划约束校验 |
| `meal_plan.save_plan` | Java | high | 是 | 复用现有计划保存用例 |

Registry 保存版本、输入/输出 JSON Schema、required scopes、risk、approval policy、timeout、retryable、idempotent、execution owner、protocol、contract version 和状态。运行时不能信任 proposal 自报的风险或确认策略。

## 3. 统一执行流程

```text
Python proposal
 -> Java validates envelope/run/dispatch
 -> Registry resolves exact tool version
 -> input schema validation
 -> Java derives user/session/scopes
 -> ToolPolicy checks status/risk/untrusted source
 -> Approval when required
 -> idempotent executor
 -> result schema validation
 -> ToolCall + operation audit
 -> Result message to Python
```

任何阶段失败都返回稳定 `TOOL_*` 错误码。业务拒绝、确认拒绝、执行失败和基础设施失败必须区分；失败不能被 Composer 解释为成功。

## 4. SQL 可见范围

首期只公开逻辑数据集，不向 Python暴露表账号：

- 当前用户饮食记录明细与 today/7d/30d 汇总。
- 当前用户餐食计划、计划状态与购物清单。
- 公共 approved 营养目录和单位换算。
- `tenant_id=0` 的已发布公共知识文档元数据，不含原文和对象键。

禁止发布 users/auth/session token、operation audit 原文、模型 Prompt/回答、密钥、系统配置和其他用户记录。

## 5. Schema Catalog

Java application 提供授权 Catalog DTO，字段至少包含 datasource/view、业务含义、允许字段、类型、可过滤/聚合/排序能力、敏感等级、必需用户过滤和最大时间范围。infra 从 PostgreSQL 权威配置读取；Python 每个 Run 只收到当前请求需要的最小 Catalog 快照。

Catalog 版本进入 proposal/request hash。版本变化后旧 proposal 不直接执行，需重新规划或返回契约冲突。

## 6. SQL Planner 双模式

### 6.1 local-stub

只支持批准意图模板，例如摄入汇总、趋势、餐次分布、计划查询和公共营养查询。Planner 输出结构化 QueryPlan，再由 Java 构造或严格校验 SQL。用户输入不能直接成为 SQL 片段。

### 6.2 local

复用 OpenAI-compatible ModelService structured output。输出和 stub 使用同一 QueryPlan/SqlProposal DTO。缺少模型配置、超时或响应不符合 Schema 时失败关闭，不自动执行 stub 查询。

## 7. Java SQL Guard

使用成熟 SQL parser/AST 实现，不以正则作为唯一安全边界：

1. 只允许单条 `SELECT` 或 `WITH ... SELECT`。
2. 拒绝 INSERT/UPDATE/DELETE/MERGE/COPY/CALL/DDL、锁、临时表、多语句和注释穿透。
3. 表、字段、函数、join、group/order 都必须在 Catalog 白名单中。
4. 强制当前 user_id/tenant 范围；不接受模型声称“已过滤”作为证据。
5. 强制 LIMIT、statement timeout、最大返回行数和字节数。
6. 结果按字段策略脱敏，并拒绝不在输出 Schema 的列。
7. SQL 和拒绝原因写 `sql_query_audits`；凭据和连接串永不写审计。

建议执行账号使用真正只读数据库角色；若当前本地环境尚未建立该账号，Java Guard 仍必须完整，账号隔离作为单独功能提交，不用性能验证替代。

## 8. Agent 闭环

分析问题经过 Router -> time_parser -> SQL Planner -> sql_proposal -> Java Guard/Executor -> tool result -> Composer。Composer 必须显示时间范围、数据覆盖和统计口径；空结果只能输出空态，不得生成虚构趋势。

## 9. API 与管理能力

- 管理端查询 Registry、版本和状态。
- admin 可启停普通工具；superadmin 才能启停 high-risk 工具。
- 状态变更使用 revision、幂等键、二次确认和统一审计。
- SQL audit 查询只展示摘要、状态、行数、耗时和 trace；普通管理员不查看完整 SQL 中可能存在的业务值。

## 10. 业务测试

重点覆盖：Registry 版本/状态、JSON Schema、scope、确认、幂等、失败审计；SQL AST 攻击样例、跨用户、敏感字段、无 LIMIT、超时；stub/local planner 契约；最近 7 天摄入和无记录业务路径。

不包含压力、并发容量、数据库故障、MQ 重启或生产攻击演练。

## 11. 提交顺序

1. `feat(tools): establish authoritative tool registry`
2. `feat(tools): unify policy execution and audit pipeline`
3. `feat(sql): publish authorized schema catalog`
4. `feat(sql): enforce ast read-only guard and user scope`
5. `feat(sql-agent): add deterministic local planner`
6. `feat(sql-agent): add model-backed structured planner`
7. `feat(tools): connect authorized database query execution`
8. `feat(agent): close read-only nutrition analysis workflow`

每个提交必须包含该切片必要的业务测试，并保持已有写确认回归通过。

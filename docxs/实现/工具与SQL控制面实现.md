# FoodMate 工具与 SQL 控制面实现蓝图

版本：v1.0 目标实现蓝图

维护基线：2026-07-11

对应契约：[双运行时内部契约 V1](../契约/双运行时内部契约V1.md)

对应数据设计：[V2 双运行时迁移设计](../数据/V2双运行时迁移设计.md)

工程边界：[Java 控制面工程设计](../架构/Java控制面工程设计.md)

当前事实：[当前实现审计与完善计划](./当前实现审计与完善计划.md)

文档定位：本文是 Java Tool 与 SQL 权威执行面的目标实现蓝图，不是完成证明。当前 `foodmate-tool` 和旧 `foodmate-sql-agent` 都只有 marker，`foodmate-infra` 尚无 SQL Guard、只读执行器或 Tool/SQL invocation 持久化。V1 表也未完整持久化工具契约字段，V2 SQL 尚未创建。本文不创建代码或迁移，不新增 ToolProposal、ToolResult、SqlProposal、SqlResult wire 字段、状态、digest 或错误码；新增的执行 fencing/lease 是 Java/V2 持久化目标，必须通过后续增量迁移落地。

## 1. 所有权与模块边界

所有注册工具的 `execution_owner` 固定为 `java`。Python/LLM 只能提交 Proposal，不能启用工具、扩大 scope、确认写操作、获取 JDBC 凭据或直接执行副作用。外部系统调用也必须经过 Java Adapter。

| 模块 | 目标包 | 职责 |
|---|---|---|
| `foodmate-api` | `com.foodmate.api.internal.tool` | Tool/SQL Proposal 入站和 Service JWT scope 校验 |
| `foodmate-tool` | `com.foodmate.tool.registry` | Tool Registry、版本和 schema 快照 |
| `foodmate-tool` | `com.foodmate.tool.policy` | scope、风险、确认和启停裁决 |
| `foodmate-tool` | `com.foodmate.tool.executor` | 幂等门禁、deadline、Adapter 调用和 ToolResult |
| `foodmate-tool` | `com.foodmate.tool.adapter` | calculator、time parser、knowledge search、food log writer 等确定性适配器 |
| `foodmate-infra` | `com.foodmate.sqlaccess.catalog` | 授权后的 Schema Catalog |
| `foodmate-infra` | `com.foodmate.sqlaccess.guard` | SQL AST、schema、敏感字段、LIMIT 与只读校验 |
| `foodmate-infra` | `com.foodmate.sqlaccess.tenantfilter` | 可信租户/用户过滤注入 |
| `foodmate-infra` | `com.foodmate.sqlaccess.executor` | 参数绑定、只读执行、deadline 和限行 |
| `foodmate-infra` | `com.foodmate.sqlaccess.audit` | SQL 审计、脱敏结果与重放 |

旧 `foodmate-sql-agent` 不再生成 SQL；Planner 迁往 Python，Java Guard/执行归入上述 SQL access 组件。不得在旧模块和 `foodmate-infra` 各保留一套 Guard。

## 2. 工具注册表

每个有效工具版本必须提供既有目标字段：`name/display_name/description/version/category/risk_level/execution_owner/protocol/contract_version/required_scopes/approval_policy/timeout_ms/retryable/idempotent` 以及 input/output schema。固定约束：

- `execution_owner=java`，其他值启动/发布校验失败。
- `contract_version=v1` 与 ToolProposal `tool_version` 一起选择不可变 schema 快照。
- Java 注册表是启停、scope、确认、deadline、retryable/idempotent 的权威；Proposal `policy` 只是 Python 使用的快照，不能覆盖 Java 决策。
- 当前 V1 `tool_registries` 未完整包含这些列。实施时只能使用已版本化的 `tool_schema_versions.schema_json` 或经评审的后续追加迁移补齐，不能声称 V1 已有字段，也不能回改 V1。

Registry 返回 Python 的快照必须来自同一已发布版本，避免 schema 与 policy 来自不同修订。停用工具仍保留历史 ToolCall 和 Result 重放能力。

## 3. 身份与 scope 派生

ToolProposal 入口要求 Python Service JWT `scope=tool:propose`；SqlProposal 入口要求 `scope=sql:propose`。Service JWT 只证明调用服务，不授予最终用户权限。

Java 根据 `run_id` 依次读取 AgentRun、session、用户、租户/角色和当前授权上下文，形成 `EffectiveScope`。必须校验 Proposal 的 `dispatch_id/attempt` 属于该 Run 且仍可创建 invocation。可信 scope 只来自 Java 数据和权限规则：

```text
service identity
  + run_id -> AgentRun -> session/user/tenant
  + current role and resource ownership
  + Java tool registry required_scopes
  = EffectiveScope / allow-deny decision
```

Proposal 的 `context.session_id` 必须与 Java 反查结果一致；`policy.required_scopes` 和 `approval_policy` 只用于检测 Python 快照是否陈旧或被篡改，不能扩大 EffectiveScope。取消或终态 Run 不允许创建新 invocation。

## 4. 取消 fencing 与 invocation 单执行者

当前 V2 设计没有 `cancellation_epoch`、执行租约或 owner token；下面是后续 V2 增量迁移的持久化目标，不是现有列。Tool 和 SQL 创建必须与取消接受竞争同一 `agent_runs` 行锁；若实现选择 CAS，也必须使用同一 `cancellation_epoch`，不能一条路径锁行、另一条路径无条件更新。

推荐目标字段：

| 目标对象 | 目标字段/状态 | 约束 |
|---|---|---|
| `agent_runs` 或等价 Run fencing 记录 | `cancellation_epoch` | 取消接受在行锁内递增；初始值为 0 |
| `tool_calls`、`sql_query_audits` | `invocation_epoch` | 创建时复制当时的 `cancellation_epoch` |
| `tool_calls`、`sql_query_audits` | `execution_state` | `pending/leased/succeeded/failed/unknown` |
| `tool_calls`、`sql_query_audits` | `owner_token`/`lease_until` | CAS 领取和崩溃接管；不进入 Tool/SQL wire envelope |

两条关键竞态必须成立：

```text
cancel transaction: lock agent_runs row -> verify cancel idempotency -> increment cancellation_epoch -> commit
invocation transaction: lock same agent_runs row -> reject terminal/cancelled -> copy current epoch -> insert pending placeholder -> commit
```

若 invocation 先拿到锁，取消随后提交会递增 epoch；invocation 在进入 Adapter 前、以及提交业务副作用前必须重新锁定/比较 `invocation_epoch == current cancellation_epoch` 且 Run 仍可执行，失败则返回 cancelled/`RUN_CANCELLED`，不产生新副作用。若取消先提交，后续 proposal 直接拒绝。不能仅在 proposal 入站时检查一次。

一个 invocation 同时只能有一个 executor。创建 `pending` 占位后，executor 使用 `UPDATE ... WHERE execution_state='pending'` 的 CAS 写入随机 `owner_token` 和 `lease_until`，领取成功者才可执行。并发命中 `pending` 或 `leased` 时不再执行第二次，返回 202/`Retry-After` 或由内部调用等待原 owner 的稳定结果；结果为 `succeeded/failed` 时直接重放。

lease 过期只允许对声明 `retryable=true` 且 `idempotent=true` 的安全工具接管，并且接管前再次通过 cancellation epoch、deadline 和 policy 校验。外部副作用在 owner 崩溃后无法确认时必须进入 `unknown`，禁止自动重放；Adapter 必须使用 provider idempotency key，或转人工/运维对账。`unknown` 不是 `failed`，不能被 V1 `tool_calls.status` 或 `sql_query_audits.status` 反向猜测。

## 4. ToolProposal 固定执行顺序

```text
validate Service JWT, v1 headers and ToolProposal schema
recompute V1 request_hash over the exact ToolProposal digest field set
load and verify run/dispatch/attempt/session
derive user/tenant/role/scopes from Java authority
resolve exact tool_name + tool_version registry snapshot
assert execution_owner == java and tool is active
create pending idempotency placeholder before adapter execution
evaluate ToolPolicy: allow / deny / require_approval
validate confirmation when required
validate input against the registered schema
check deadline and derive adapter timeout
CAS claim pending -> leased with owner_token and lease_until
recheck cancellation_epoch before adapter and before committing side effects
execute adapter, with business write inside Application/Domain transaction
persist execution_state/result_status/result_hash/full ToolResult audit and release lease
return ToolResult or stable replay
```

摘要字段集固定为 `schema_version/run_id/dispatch_id/attempt/invocation_id/idempotency_key/deadline_at/tool_name/tool_version/input/context/policy`，完整对象按 RFC 8785 JCS + SHA-256。Java 独立重算；同 `invocation_id` 或 `(run_id,idempotency_key)` 命中且 hash 相同返回原结果，任一键命中且 hash 不同返回 `TOOL_IDEMPOTENCY_CONFLICT`。

ToolResult 的结果摘要字段集固定为 `schema_version/run_id/dispatch_id/attempt/invocation_id/status/data/error/meta`，仅排除 `meta.replayed`。摘要不包含自身、`request_id` 或 `trace_id`。

任何 Adapter 执行前必须成功建立 pending 幂等占位并完成单执行者 CAS 领取。重放从 `output_json/result_status/result_hash` 返回完整 ToolResult，只允许把响应 `meta.replayed` 改为 true；它不参与 result digest。不得重做业务写入。

## 5. ToolPolicy、确认、schema 与 deadline

`ToolPolicyChecker` 至少按以下顺序裁决：工具存在且启用 -> 版本匹配 -> `execution_owner=java` -> Run 可执行 -> EffectiveScope 覆盖 registry `required_scopes` -> 风险/白名单 -> untrusted content 隔离 -> approval policy。结果只允许 `allow/deny/require_approval`，写入 V2 `policy_decision`。

- deny 返回 ToolResult `status=denied`、错误码 `TOOL_POLICY_DENIED`。
- 缺少或无效确认返回 `status=confirmation_required`、错误码 `TOOL_CONFIRMATION_REQUIRED`。
- 需要确认时 `context.confirmation_ref` 必填；Java 验证其用户、Run、工具版本、输入摘要、有效期和未撤销状态，不信任一个非空字符串即代表确认。
- input 必须按已注册版本 schema 完整校验；失败返回 `TOOL_SCHEMA_INVALID`，不进入 Adapter。
- 实际 adapter timeout 取 registry `timeout_ms` 与 `deadline_at-now` 的较小值。请求到达已过期返回 `RUNTIME_DEADLINE_EXCEEDED`，执行超时返回 ToolResult `timeout`/`TOOL_TIMEOUT`。

只有 registry 同时声明 `retryable=true` 和 `idempotent=true` 的工具，才允许在相同 invocation/idempotency key、摘要和 deadline 内重试。客户端或 Adapter 不得为重试生成新逻辑 invocation。

## 6. 业务事务与 Adapter

写工具如 `food_log_writer` 必须调用 `foodmate-application`/Domain 用例，在单一业务事务内完成业务表更新、ToolCall 结果和操作审计。Adapter 不直接依赖 API，不绕过 Repository 权限规则。

本地数据库副作用与 ToolCall 可在同一 PostgreSQL 事务提交。外部系统副作用不能伪装成数据库原子事务：必须向外部传递稳定 idempotency key，先有本地 invocation 占位，再记录确定结果；响应不确定时保持可对账状态，不盲目重试非幂等动作。

ToolResult 状态只允许 `success/failed/denied/confirmation_required/timeout/cancelled`，并严格映射：

| ToolResult | V1 `tool_calls.status` | V2 `result_status` | V1 `error_code` |
|---|---|---|---|
| `success` | `success` | `success` | null |
| `failed` | `failed` | `failed` | `TOOL_FAILED` 或具体既有错误码 |
| `denied` | `failed` | `denied` | `TOOL_POLICY_DENIED` |
| `confirmation_required` | `pending` | `confirmation_required` | `TOOL_CONFIRMATION_REQUIRED` |
| `timeout` | `timeout` | `timeout` | `TOOL_TIMEOUT` |
| `cancelled` | `cancelled` | `cancelled` | `RUN_CANCELLED` 或既有取消原因 |

`output_json` 保存完整 ToolResult，`result_hash` 按契约字段集计算。同 invocation 不同 result hash 返回 `TOOL_RESULT_CONFLICT`，原结果不得覆盖。

## 7. SqlProposal 固定执行顺序

```text
validate Service JWT, v1 headers and complete SqlProposal
recompute V1 request_hash over the exact SqlProposal digest field set
verify run/dispatch/attempt and derive trusted user/tenant/scopes
create pending SQL audit idempotency placeholder
resolve datasource_id and exact catalog_version
parse exactly one SQL statement into AST
allow only SELECT or WITH ... SELECT
authorize every table/column/function against the catalog
deny sensitive fields without scope
inject trusted tenant/user and is_deleted filters in AST
enforce max_rows/LIMIT and expected_shape
bind proposal parameters plus Java-derived filter parameters
CAS claim pending -> leased with owner_token and lease_until
recheck cancellation_epoch before executor and before any committed result/audit side effect
execute through readonly role/connection with statement timeout
redact and cap rows
persist execution_state/result_status/result_hash/result_json and audit; release lease
return SqlResult or stable replay
```

摘要字段集固定为 `schema_version/run_id/dispatch_id/attempt/invocation_id/idempotency_key/deadline_at/datasource_id/catalog_version/question/sql/parameters/expected_shape/policy`。同 `invocation_id` 或 `(run_id,idempotency_key)` 同 hash 重放原结果且不再次执行 SQL；不同 hash 返回 `SQL_IDEMPOTENCY_CONFLICT`。

SqlResult 的结果摘要字段集固定为 `schema_version/run_id/dispatch_id/attempt/invocation_id/status/rows/error/meta`，仅排除 `meta.replayed`。摘要不包含自身、`request_id` 或 `trace_id`。

## 8. SQL AST Guard

Guard 必须使用 SQL parser/AST，不使用正则或字符串拼接判断安全性。至少拒绝：

- 多语句、分号后附加语句和解析残留 token。
- `INSERT/UPDATE/DELETE/MERGE/DDL/CALL/COPY` 及任何非 `SELECT`、非 `WITH ... SELECT` 根节点。
- 未授权 datasource、catalog、schema、表、列、函数、子查询或 CTE 引用。
- 绕过只读的锁语句、数据修改 CTE、危险函数和供应商特有写语义。
- 未授权敏感字段，以及通过别名、表达式、通配符或嵌套查询间接读取敏感字段。
- 超出 Java policy 的 `max_rows`，或与 `expected_shape` 不一致的列形状。

Parser 无法完整理解的 SQL 一律拒绝，不能降级为直接执行。Guard 必须对注释、大小写、quoted identifier、CTE、UNION、嵌套子查询和函数调用做反例测试。

## 9. 租户过滤、参数绑定与只读执行

Python 不能提供可信 `user_id/tenant_id`。`TenantFilterInjector` 从 EffectiveScope 生成过滤谓词，并在 AST 每个可见业务表作用域注入必须条件，包括目标表要求的 `user_id/tenant_id` 和 `is_deleted=false`。已有同名条件也必须校验其值来源；不能因为 SQL 自带过滤就跳过 Java 注入。

所有用户值、时间范围、租户/用户 ID 和分页参数使用 PreparedStatement 绑定。不得把参数拼入 SQL 文本，不得接受 Proposal 中伪造的可信过滤参数。最终执行 SQL、参数类型和 catalog version 进入脱敏审计。

执行层同时使用：只读数据库账号、read-only transaction/connection、statement timeout、最大行数、受限 fetch size。实际 timeout 取 SQL policy 与剩余 `deadline_at` 的较小值。即使 Guard 已通过，数据库只读权限仍是最后一道强制边界。

## 10. 脱敏、审计与状态映射

SqlResult 成功时 `rows` 必须经过字段级脱敏并受 `max_rows` 限制；`meta.truncated` 准确反映截断。失败时 `rows=[]`，error 不得包含凭据、完整敏感 SQL 结果或数据库内部堆栈。

每次 proposal 包括拒绝和超时都只保留一条 `sql_query_audits` 事实，记录 run/session/datasource、question、经治理 SQL 或其脱敏表示、catalog version、row count、latency、trace、reject reason、`request_hash/result_hash` 和完整脱敏 `result_json`。

| SqlResult | V1 `sql_query_audits.status` | V2 `result_status` | V1 `reject_reason` |
|---|---|---|---|
| `success` | `executed` | `success` | null |
| `rejected` | `rejected` | `rejected` | 对应 `SQL_READONLY_VIOLATION`、`SQL_SCHEMA_DENIED` 或 `SQL_SENSITIVE_FIELD_DENIED` |
| `failed` | `rejected` | `failed` | `SQL_EXECUTION_FAILED` |
| `timeout` | `rejected` | `timeout` | `SQL_TIMEOUT` |
| `cancelled` | `rejected` | `cancelled` | `RUN_CANCELLED` 或既有取消原因 |

`result_json` 保存完整脱敏 SqlResult。重放只允许修改不参与摘要的 `meta.replayed`。同 invocation 不同 result hash 返回 `SQL_RESULT_CONFLICT`，不得覆盖原结果或再次执行 SQL。SQL 读取若 owner 崩溃且结果未知，只有在执行器/数据源能证明幂等且仍通过 Guard 时才允许 lease 接管；不可证明时记录 `unknown` 并人工对账。

## 11. 测试矩阵

| 场景 | 关键断言 |
|---|---|
| Registry 校验 | 每个注册工具 `execution_owner=java`；字段和 schema 完整 |
| scope 派生 | Python 自报 scope/user/tenant 不能扩大 Java 权限 |
| Policy allow/deny/approval | 三种决策、错误码和 V2 `policy_decision` 正确 |
| 确认 | 缺失、过期、撤销、跨用户、输入 digest 变化均拒绝 |
| Tool schema | 未知字段、缺字段、类型/枚举错误返回 `TOOL_SCHEMA_INVALID` |
| Tool 幂等 | 两个唯一键任一命中同 hash 只执行一次；不同 hash 冲突 |
| Tool deadline/重试 | 过期不进 Adapter；仅幂等且 retryable 工具可有界重试 |
| 取消 fencing | 取消接受与 invocation 创建串行化在同一 AgentRun 锁/epoch；取消提交后禁止新 invocation；执行前和副作用提交前再次校验 |
| invocation 单执行者 | pending/leased CAS 只允许一个 owner；并发返回 202/Retry-After 或稳定等待结果 |
| lease 接管 | 过期 lease 仅安全幂等工具可接管；外部副作用 unknown 不自动重放，provider key/人工对账生效 |
| 写工具事务 | 业务写、ToolCall 和审计一起提交或回滚 |
| Tool 状态映射 | 六种 Result 到 V1/V2/完整 JSON 逐项一致 |
| SQL AST | 多语句、DML/DDL、修改 CTE、注释绕过、锁语句全部拒绝 |
| Catalog/sensitive field | 未授权表列、`*`、别名/子查询间接访问均拒绝 |
| 租户过滤 | 每个作用域注入可信用户/租户与 `is_deleted=false`，无跨租户结果 |
| 参数绑定 | 恶意字符串只作为值，不改变 AST；无字符串拼接 |
| 只读执行 | 数据库只读角色和 transaction 双重阻止写入 |
| LIMIT/timeout | 行数被限制，`truncated`、statement timeout 和错误码正确 |
| SQL 幂等 | 重放不再次执行；请求/结果摘要冲突不覆盖原审计 |
| SQL invocation lease | 并发 proposal 只有一个 executor；lease 崩溃场景不重复执行不可证明幂等的副作用 |
| SQL 状态映射 | 五种 Result 到 V1/V2/脱敏 JSON 逐项一致 |
| 脱敏审计 | JWT、凭据、敏感列和数据库堆栈不出现在响应或日志 |

SQL Guard、事务、唯一约束和只读账号测试必须在真实 PostgreSQL Testcontainers 中运行；mock 解析器或 SQL 文本断言不足以验收。

## 12. 验收命令

以下是后续实现后的目标命令；当前 marker 和 V1 文本测试不能证明控制面已实现。

```powershell
.\mvnw.cmd -pl foodmate-tool,foodmate-infra,foodmate-application,foodmate-api -am test
.\mvnw.cmd -pl foodmate-bootstrap -am test
.\mvnw.cmd clean verify
```

验收报告必须记录测试数、失败数、PostgreSQL/Testcontainers/SQL parser 版本、Adapter 执行计数，并逐项给出 Tool/SQL 幂等、状态映射、恶意 SQL、租户隔离、只读执行、deadline、脱敏和审计结果。

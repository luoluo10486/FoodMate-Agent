# FoodMate Java 运行时客户端实现蓝图

版本：v1.0 目标实现蓝图

维护基线：2026-07-11

对应契约：[双运行时内部契约 V1](../契约/双运行时内部契约V1.md)

对应数据设计：[V2 双运行时迁移设计](../数据/V2双运行时迁移设计.md)

工程边界：[Java 控制面工程设计](../架构/Java控制面工程设计.md)

当前事实：[当前实现审计与完善计划](./当前实现审计与完善计划.md)

文档定位：本文是 `foodmate-gateway-client` 的目标实现蓝图，不是完成证明。当前模块只有 `com.foodmate.gateway.GatewayClientModule` marker 类，尚无 Runtime HTTP Client、Service JWT、内部 DTO、重试或契约测试。本文不新增消息类型、字段、摘要算法、状态或错误码；这些内容以双运行时内部契约 V1 为唯一依据。

## 1. 模块边界与目标包

`foodmate-gateway-client` 只负责 Java 到 Python Runtime 的单次出站通信，不迁移 `AgentRun` 状态，不直接访问数据库，也不承载前端 SSE。持久化 dispatch outbox、lease/CAS publisher 和 AgentRun 收敛属于 Application/Worker/Infra；publisher 从 outbox 读取已经固化的 RunCommand 后调用本客户端。目标根包收敛为 `com.foodmate.agentclient`；现有 `com.foodmate.gateway` marker 仅表示当前骨架事实，实施时可保留为模块标记，但不得在两个根包各实现一套客户端。

```text
com.foodmate.agentclient
  client/
    AgentRuntimeClient                 # Application 使用的端口
    HttpAgentRuntimeClient             # HTTP 实现
    RuntimeStreamReader                # 已建立内部流的控制帧读取
  command/
    RunCommand                         # 严格映射契约 V1
    CancelCommand                      # 严格映射契约 V1
  auth/
    ServiceJwtIssuer                   # 签发 Java -> Python JWT
    ServiceJwtKeyProvider              # 按 kid 读取轮换密钥
  config/
    AgentRuntimeProperties             # 强类型配置与启动校验
    AgentRuntimeClientConfiguration    # HTTP client/codec/clock 装配
  digest/
    CanonicalRequestDigest             # RFC 8785 JCS + SHA-256
  header/
    RuntimeHeaderFactory               # Authorization/version/request/trace
  error/
    RuntimeError                       # 契约运行内错误
    PreRunProtocolError                # 运行前协议错误
    RuntimeClientException             # Java 内部异常外壳
    RuntimeErrorMapper                 # HTTP/流错误映射
  observation/
    RuntimeClientObservation           # 指标与脱敏日志
```

Application 侧目标接口只暴露契约已有操作：

```java
interface AgentRuntimeClient {
    void dispatchRun(RunCommand command);
    void cancelRun(CancelCommand command);
    RuntimeHealth health();
}
```

dispatch/cancel 的 2xx 只表示同步方法正常返回，不定义临时结果类型、轮询 token 或跨运行时第九类消息。Python 的业务进度仍只能通过契约 `RunEvent` 回传。`resumeRun` 若后续保留，只能组装新的或既有 `RunCommand`，不能定义另一套 Resume envelope。

## 2. 配置键

目标配置前缀固定为 `foodmate.agent-runtime`，取代当前尚未收敛的 model gateway 配置语义。Secret 只允许通过环境变量或 Secret 挂载提供，不写入仓库。

| 配置键 | 必填 | 目标语义 |
|---|---:|---|
| `base-url` | 是 | Python Runtime 内部 HTTPS 根地址 |
| `contract-version` | 是 | 固定 `v1`，同时写入 Header 和 body |
| `connect-timeout-ms` | 是 | TCP/TLS 建连上限 |
| `read-timeout-ms` | 是 | 单次响应或已建立流相邻控制帧的无数据上限 |
| `run-timeout-ms` | 是 | Java 允许一次 dispatch 的最大总运行时间，用于计算 `deadline_at` |
| `retry.max-transport-attempts` | 是 | 同一逻辑请求的最大传输尝试数，不递增业务 `attempt` |
| `retry.initial-backoff-ms` | 是 | 可重试传输错误初始退避 |
| `retry.max-backoff-ms` | 是 | 退避上限，始终受 `deadline_at` 限制 |
| `jwt.issuer` | 是 | 固定 `foodmate-control-plane` |
| `jwt.subject` | 是 | 固定 `foodmate-control-plane` |
| `jwt.audience` | 是 | 固定 `foodmate-agent-runtime` |
| `jwt.key-id` | 是 | JWT Header `kid` |
| `jwt.private-key-location` | 是 | 签名私钥的 Secret 引用 |
| `jwt.ttl-seconds` | 是 | 不超过 300 秒 |
| `jwt.clock-skew-seconds` | 是 | 仅用于时钟偏差校验，不延长业务 deadline |

启动校验必须拒绝：非 `v1` 契约、非 HTTPS 的 `prod` 地址、JWT TTL 大于 300 秒、空 `kid`、缺失私钥、任一 timeout 小于等于 0、`read-timeout-ms` 或重试退避可能越过 `run-timeout-ms`。`local-stub` 可显式使用 HTTP stub，但不能复用生产 JWT 私钥。

## 3. Service JWT 与 Header

每次出站请求签发短期 JWT，不使用长期静态 bearer token。Java 到 Python 的 claims 固定为：

- `iss=sub=foodmate-control-plane`
- `aud=foodmate-agent-runtime`
- dispatch 使用 `scope=agent:run`，取消使用 `scope=agent:cancel`；同一 token 可按既有契约同时含二者，但不附加用户 scope
- `iat/nbf/exp/jti` 全部存在，`exp - iat <= 300s`
- Header 含允许算法和非空 `kid`

HTTP Header 固定写入 `Authorization: Bearer <service-jwt>`、`X-Contract-Version: v1`、`X-Request-Id` 和 W3C `traceparent`。body 的 `request_id/trace_id/schema_version` 必须与 Header/Trace 上下文一致。Service JWT 只证明 Java 服务身份；`authorized_context` 的用户、会话和工具范围必须由 Java Application 从权威数据组装，JWT 不替代业务授权。

日志与指标禁止记录 JWT、私钥、完整 Prompt、原始请求体或未脱敏错误详情。可记录 `run_id/dispatch_id/attempt/request_id/trace_id`、HTTP 状态、契约错误码、耗时和传输尝试数。

## 4. HTTP 与流式边界

使用已批准路径：

| 方法与路径 | 请求 | 成功语义 |
|---|---|---|
| `POST /foodmate/internal/v1/runs` | `RunCommand` | 接受或按同一 `dispatch_id` 幂等返回 |
| `POST /foodmate/internal/v1/runs/{run_id}/cancel` | `CancelCommand` | 接受取消；最终结果由有序 `RunEvent` 确认 |
| `GET /foodmate/internal/health/live` | 无 | 进程存活 |
| `GET /foodmate/internal/health/ready` | 无 | Runtime 可接收工作 |

`runtime_options.stream_answer=true` 只要求 Python 产生 `run.answer_stream` RunEvent；Java 不为此另造流消息。若 HTTP 连接已经升级为内部流，`RuntimeStreamReader` 只接受契约规定的 `event: runtime.error` + `RuntimeError` JSON 控制帧。PreRunProtocolError 必须在成功响应头之前作为非 2xx `application/json` 返回，已建立流不得发送 PreRunProtocolError。所有业务 RunEvent 仍进入 Java 的事件接入端点并走 inbox 事务。

Jackson 边界要求：未知顶层字段默认拒绝；ID 按字符串；时间必须是带 `Z` 的 RFC 3339 UTC；枚举、必填字段及互斥字段严格校验。序列化前后必须保持契约语义，不把 `agent_run_id` 写入消息。

## 5. 三层 timeout

1. 连接 timeout：只覆盖 DNS/TCP/TLS 建连。触发后若仍在 `deadline_at` 前，可按传输重试规则重试。
2. 读取 timeout：覆盖普通响应读取或流相邻帧等待。它是连接故障，不直接把 AgentRun 改为失败；调用方先关闭连接并按同一幂等标识重试或交给运行超时收敛。
3. 总运行 timeout：Application 创建 dispatch 时取 `min(业务上限, now + run-timeout-ms)` 形成绝对 `deadline_at`。超过该时间不得创建新副作用，Java 以同一取消意图发送 `CancelCommand(reason=deadline_exceeded)`，最终状态仍由 Java 状态机裁决。

任何单次 connect/read/backoff 都必须裁剪到剩余 deadline。HTTP 客户端 timeout 不能替代 `deadline_at`，也不能因重试重新计算更晚的 deadline。

## 6. Dispatch、取消与重试边界

### 6.1 Dispatch 幂等

Application 必须在同一业务事务中持久化 AgentRun、V2 dispatch 和该 dispatch 唯一的 `runtime_dispatch_outbox`。outbox 固化通过 V1 Schema 的完整 RunCommand `payload_json` 及 `request_hash/schema_version/run_id/dispatch_id/attempt/deadline_at/fencing_epoch`；事务提交前不得发送网络请求，提交后不得从 AgentRun、消息、当前 Prompt/工具配置或其他可变状态重建命令。

相同 `run_id + attempt` 固定复用 `dispatch_id`。publisher 的每次网络尝试发送 outbox 中完全相同的 RunCommand body，包含不变的 `request_id/trace_id`；只重新签发短期 Service JWT 等 HTTP 传输 Header。`request_hash` 严格按契约字段集执行 RFC 8785 JCS + SHA-256，输出 `sha256:<64 lowercase hex>`，并且必须与 outbox、dispatch 和 payload 独立重算结果一致。

该发送前置依赖 V2 阶段 1 正式定义的 single-active partial unique、`active_epoch/fencing_token` 和 Run `admission_epoch`；阶段 4 主链路启用前必须完成对应 PostgreSQL Testcontainers 并发 fixture。客户端不得在缺少这些持久化约束时用进程内判断替代。

客户端不负责重新派发或选择 active dispatch。publisher 在每次领取和实际发送前都要确认 deadline、active dispatch、admission/active epoch 和 fence；不满足时不调用客户端并将 outbox 合法收敛为 `expired`。Application 在同一 Run 上创建新 attempt 前，必须先将旧 dispatch 仲裁为 `superseded`，使旧 outbox 不再可发送，再为新 dispatch 建立新的 `dispatch_id/attempt/active_epoch/outbox`。收到旧 dispatch 的回调由事件接入层按 active epoch 审计并返回 `RUNTIME_STATE_CONFLICT`，不能靠客户端重试把旧 attempt 重新激活。

| 类型 | 幂等键 | V1 摘要字段集 |
|---|---|---|
| RunCommand | `dispatch_id` | `schema_version/run_id/dispatch_id/attempt/deadline_at/message/authorized_context/runtime_options` |
| CancelCommand | `cancel_id` | `schema_version/run_id/dispatch_id/attempt/cancel_id/deadline_at/reason/requested_at` |
| RuntimeError | `(run_id,error_id)` | `schema_version/run_id/error_id/related_event_id/related_event_seq/error` |

摘要不包含自身、`request_id`、`trace_id`、HTTP Header 或接收时间；嵌套对象必须完整参与 JCS，不能挑选子字段。

```text
scan pending rows due now and leased rows with expired lease
CAS one row to leased with a fresh owner_token and lease_until
load immutable payload_json and verify dispatch/hash/deadline/epoch/fence
if expired or superseded: CAS expired, clear lease, converge Run/audit; do not POST
POST the exact persisted RunCommand with a fresh Service JWT
if authenticated/version-valid accepted or already-accepted 2xx:
  CAS delivered by owner_token and set delivered_at
else if transport outcome is uncertain or retryable before deadline:
  CAS pending by owner_token, increment send_attempts and set next_attempt_at
else if RUNTIME_DISPATCH_IDEMPOTENCY_CONFLICT:
  CAS failed by owner_token; do not create a new dispatch
never mutate payload_json and never increment business attempt inside publisher/client
```

Python 只有在按 `dispatch_id/request_hash` 持久化接受后才返回 2xx；同 ID 同 hash 返回原接受结果。Java 只有验证身份、版本、HTTP 状态和 runs endpoint 成功语义后才标 `delivered`。2xx 不新增接受响应 wire 类型；非 2xx 继续只解析既有 RuntimeError/PreRunProtocolError。发送后 ACK 前崩溃产生的是同一 envelope 重放，Python 不得二次执行。

publisher 启动时必须恢复扫描，且多实例依赖数据库 lease/CAS 单领取；未过期 owner 不得被抢占，过期接管必须更换 owner token。重新派发不是网络重试：只有 Application 做出重新派发决策后，才创建新 `dispatch_id` 且 `attempt=previous+1`。客户端和 publisher 都不得自动执行该动作。

### 6.2 取消

相同取消意图固定复用 `cancel_id`、摘要字段和 `request_hash`。客户端收到 HTTP 接受只表示 Python 接收取消请求，不得直接把 AgentRun 写成 `cancelled`。`run.cancel_acknowledged` 或 `run.cancelled` 必须进入事件事务，由 Java 在没有更早合法终态时裁决。取消的网络重试不得创建新 `cancel_id`。

### 6.3 允许与禁止重试

允许在剩余 deadline 和最大传输次数内重试：建连失败、连接重置、读取 timeout、HTTP 503 `RUNTIME_UNAVAILABLE`，以及明确 `retryable=true` 的运行内错误。dispatch/cancel 重试必须复用业务幂等标识和 canonical digest。

禁止自动重试：401/403/426/400、全部幂等冲突、`RUNTIME_STATE_CONFLICT`、`RUN_CANCELLED`、`retryable=false`，以及 deadline 已过期。客户端不得自动重试 Tool/SQL/模型调用，也不得把 500 一律视为可重试；`RUNTIME_INTERNAL_ERROR` 只按 envelope 的 `retryable` 处理。

## 7. 错误映射

| 输入 | Java 处理 |
|---|---|
| 认证、版本、JSON 解析或可信 `run_id` 前 schema 错误 | 解析 `PreRunProtocolError`；不创建/更新 AgentRun，不写 `runtime_error_inbox` |
| 已验证真实 `run_id` 后的非 2xx RuntimeError | 校验 `(run_id,error_id)`、摘要和关联事件字段，交 Application 记录 `runtime_error_inbox` |
| 已建立流的 `runtime.error` | 与 HTTP RuntimeError 相同去重；不得按 RunEvent 分配 `event_seq` |
| 非契约 HTML、空 body 或无法解析响应 | 本地映射为传输/协议异常，保留 HTTP 状态和脱敏 body digest，不伪造 RuntimeError |
| 网络异常 | 本地传输异常；由重试边界和总 deadline 决定后续动作 |

契约错误码必须原样保留，包括 `RUNTIME_AUTH_INVALID`、`RUNTIME_AUTH_FORBIDDEN`、`RUNTIME_VERSION_UNSUPPORTED`、`RUNTIME_CONTRACT_INVALID`、`PRE_RUN_IDEMPOTENCY_CONFLICT`、`RUNTIME_DEADLINE_EXCEEDED`、dispatch/cancel/error 幂等冲突、事件错误、`RUNTIME_UNAVAILABLE`、`RUNTIME_INTERNAL_ERROR` 和 `RUN_CANCELLED`。不得重新发明语义相同的客户端错误码。

## 8. 测试矩阵

优先使用 MockWebServer 做字节级请求、连接与 timeout 测试；若项目统一采用 WireMock，则只保留一套主测试框架，避免重复维护。两者都必须验证真实 JSON、Header 和故障行为。

| 场景 | 关键断言 |
|---|---|
| RunCommand 成功 | 路径、UTF-8 JSON、四个固定 Header、全部必填字段正确 |
| canonical digest | Java 重算与契约 fixture 一致；变化 `request_id/trace_id` 不改变 hash，变化摘要字段必改变 hash |
| 重复 dispatch | 两次网络请求复用 `dispatch_id/attempt/request_hash`，业务执行计数为 1 |
| dispatch hash 冲突 | 409 映射 `RUNTIME_DISPATCH_IDEMPOTENCY_CONFLICT`，不重试 |
| 事务原子性 | AgentRun/dispatch/dispatch outbox 同事务提交或回滚；网络发送发生在事务外 |
| 提交后发送前崩溃 | 启动扫描重新领取 pending，发送持久化原 payload |
| 发送后 ACK 前崩溃 | lease 恢复后重发完全相同 RunCommand，Python 返回原接受结果，执行计数仍为 1 |
| lease owner 崩溃/多实例竞争 | 仅过期 lease 可换 owner；同一时刻只有一个 CAS 领取成功 |
| deadline/旧 dispatch | 过期或 superseded outbox 不调用 HTTP，标 expired 并合法收敛 Run/审计 |
| 显式重新派发 | 旧 dispatch 先 superseded；新 `dispatch_id/attempt/outbox`，旧 outbox 永不发送 |
| 取消重试 | 复用 `cancel_id/request_hash`，HTTP 接受不直接写终态 |
| JWT | claims、scope、TTL、`kid`、轮换兼容和过期 token 反例 |
| 版本不兼容 | 426 映射 `RUNTIME_VERSION_UNSUPPORTED`，不降级 |
| connect/read/run timeout | 分别触发；backoff 不越过绝对 `deadline_at` |
| 503/连接重置 | 有界重试；每次新 `request_id`，业务幂等字段稳定 |
| 401/403/400/409 | 不自动重试 |
| PreRunProtocolError | 不含 `run_id`，进入 pre-run 分支 |
| RuntimeError HTTP/stream | 真实 `run_id/error_id/request_hash` 保留，流控制帧不当作 RunEvent |
| 未知顶层字段/数值 ID/非 UTC 时间 | DTO 明确拒绝 |
| 日志脱敏 | 不出现 Authorization、私钥、原始 body 或 Prompt |

## 9. 验收命令

以下是后续代码实现完成后的目标验收命令；当前 marker 基线不能据此声明客户端已实现。

```powershell
.\mvnw.cmd -pl foodmate-gateway-client -am test
.\mvnw.cmd -pl foodmate-bootstrap -am test
.\mvnw.cmd dependency:tree -pl foodmate-gateway-client
```

验收报告必须记录测试数、失败数、使用的 MockWebServer/WireMock 版本以及 connect/read/run timeout 的真实耗时断言。仅编译 marker 类不满足本蓝图。

# FoodMate MVP 主链路实施状态

更新时间：2026-07-18

本文记录代码和自动化验证已经证明的事实，并覆盖路线图、旧实施蓝图中关于“尚未创建 Python Runtime”“尚未有认证/API/SSE”的历史描述。目标架构、完整 V1 wire 契约和 V2 持久化设计仍以各自的 ADR、契约和迁移设计文档为准；本文不将未实现目标标为完成。

## 已完成主链路

```text
登录/注册
  -> Java 用户、会话、消息和 AgentRun
  -> RuntimeGatewayService dispatch
  -> Python agent-runtime
  -> Java runtime event inbox
  -> AgentRun 状态投影和助手消息
  -> SSE 推送或断线重放
  -> 前端 real mode 展示和取消
```

Java 是业务数据与授权的唯一权威：Python 不持有业务数据库凭据，不能直接写业务表。运行状态映射为 `DISPATCHED -> queued`、`RUNNING -> executing`、`SUCCEEDED -> completed`、`FAILED -> failed`、`CANCELED -> cancelled`。

## 当前代码事实

| 能力 | 位置 | 状态 |
|---|---|---|
| 注册、登录、PBKDF2 密码哈希、服务端认证会话 | `foodmate-application/.../UserAccountService.java` | 已实现；会话 ID 和 CSRF Token 仅保存哈希 |
| 会话、消息和 AgentRun 创建 | `SessionController`、`ChatController` | 已实现 |
| Run 查询、事件查询、SSE、取消 | `ChatController`、`RunStreamController` | 已实现；run 必须属于当前用户 |
| dispatch/cancel 幂等、超时、事件去重/乱序/缺口/状态校验 | `RuntimeGatewayService` | 已实现；JDBC 存在时使用 `runtime_*` 表 |
| Java 到 Python HTTP 客户端 | `foodmate-gateway-client` | 已实现；传递 `X-Runtime-Token` 与 `X-Contract-Version` |
| Python Runtime 工程、健康检查、dispatch/cancel | `agent-runtime/` | 已实现最小 V1 Runtime；`/health/live`、`/health/ready` |
| Python 回调事件 | `agent-runtime/runtime_server.py` | 已实现；回调 Java `/internal/runtime/runs:events` |
| 前端真实接入 | `frontend/src/services` | `VITE_AGENT_MODE=real` 时使用登录、Cookie 会话、CSRF 请求头、SSE 与取消；默认仍保留 mock |

## 本地运行

先启动 Java：

```powershell
.\mvnw.cmd -pl foodmate-bootstrap -am package
& java -jar '.\foodmate-bootstrap\target\foodmate-bootstrap-0.1.0-SNAPSHOT.jar' '--spring.profiles.active=local-stub' '--foodmate.runtime.agent-base-url=http://127.0.0.1:9000'
```

再启动 Python：

```powershell
cd agent-runtime
python runtime_server.py
```

前端真实模式需要设置 `VITE_AGENT_MODE=real`；其余时候默认使用 mock，以支持无后端的原型开发。生产或跨服务环境必须配置 Java/Python 各自的 Ed25519 私钥、对方 X.509 公钥与 `kid`，并保持 `RUNTIME_CONTRACT_VERSION` 与 `FOODMATE_CONTRACT_VERSION` 一致。

## 已验证

- `./mvnw.cmd -q verify`：通过。
- `frontend`：`npm run typecheck` 通过。
- `agent-runtime`：`python -m unittest discover -s tests` 通过，覆盖有序生命周期和取消前置场景。
- 既有实际联调已覆盖：`Java Chat -> Python Runtime -> RunEvent -> Java EventInbox -> AgentRun SUCCEEDED`，并验证用户消息与助手消息关联同一 `agent_run_id`。

## 仍未完成或未验证

- 未启动 Docker/PostgreSQL，因此 Flyway、JDBC 事务、重启后 inbox 去重和持久化 SSE 重放没有在真实数据库中验证。
- 当前运行时内存状态仅用于 local-stub；目标中的 dispatch outbox、lease/CAS/fencing、事务型 SSE outbox 尚未实现。
- 已实现双向短期 Ed25519 Service JWT 和 `kid`；尚未完成密钥轮换、JWKS、HTTPS/mTLS 部署以及跨语言端到端密钥联调。
- `RunCommand`/`RunEvent` 尚未扩展到目标 V1 中的 `request_id`、`trace_id`、授权上下文、ToolProposal、SqlProposal 和错误 envelope。
- RAG、ToolRegistry/Policy、SQL Guard、食品日志、分析、餐食计划、知识库、模型治理均是后续阶段，未实现。

## 下一优先级

1. 用 PostgreSQL/Testcontainers 落地并验证 dispatch/event/SSE 的事务 outbox、重启恢复和并发 lease。
2. 完成 Service JWT 密钥轮换、JWKS、HTTPS/mTLS 部署和 V1 完整 envelope 契约测试。
3. 建设 Java Tool Gateway/Policy，再让 Python Router、Planner 和 ExecutionEngine 通过受控工具实现业务场景。

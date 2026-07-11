# FoodMate Python 智能体运行时设计

版本：v1.0 目标设计

维护基线：2026-07-11

对应架构：[架构总览](./架构总览.md)、[Java 控制面工程设计](./Java控制面工程设计.md)

对应契约：[双运行时内部契约 V1](../契约/双运行时内部契约V1.md)、[智能体行为与工具协议](../契约/智能体行为与工具协议.md)

文档定位：本文定义 Python Agent Runtime 的目标工程边界和实现结构。当前仓库没有 `agent-runtime/`、`pyproject.toml`、Python 项目虚拟环境或 pytest 基线；下述目录、接口和组件均为后续实现依据，不代表当前实现。

## 1. 运行时定位

FoodMate 只有两个受控运行时：

- Java 业务控制面是业务数据、`AgentRun`、审计、工具注册与执行、SQL Guard 与 SQL 执行的唯一权威。
- Python Agent Runtime 只负责 Agent 推理与编排，包括 Router、Planner、Execution、RAG、Model、Composer 和 Evaluation。
- Python 不面向前端，不签发用户身份，不判断最终业务权限，不直接执行业务工具或 SQL。
- Python 不持有 PostgreSQL 等业务库的读写凭据；需要业务数据、授权知识范围、工具结果或 SQL 结果时，必须调用 Java 内部接口。

跨运行时契约统一使用 `run_id`。它在 Java 数据库中映射为 `agent_runs.agent_run_id`；`agent_run_id` 只作为现有 V1 数据库列名或外部业务 API 资源字段存在，不进入 Python 内部契约模型。

## 2. 目标目录

```text
agent-runtime/                         # 目标目录，当前尚不存在
  pyproject.toml
  src/foodmate_agent/
    main.py                            # FastAPI app 与生命周期
    api/
      dependencies.py                 # Service JWT、Trace、deadline
      exception_handlers.py           # RuntimeError 映射
      routes/
        runs.py                        # RunCommand
        cancellations.py              # CancelCommand
        health.py                      # live/ready
      schemas/                         # Pydantic v2 契约模型
        common.py
        run.py
        tool.py
        sql.py
        error.py
    orchestrator/
      router.py
      planner.py
      execution.py
      composer.py
      checkpoint.py
      state.py
    rag/
      query_understanding.py
      retrieval.py
      rerank.py
      citations.py
    model/
      gateway.py
      routing.py
      structured_output.py
      usage.py
    evaluation/
      evaluators.py
      datasets.py
      regression.py
    clients/
      java_control_plane.py
      tool_gateway.py
      sql_gateway.py
    prompts/
      loader.py
      manifest.py
    observability/
      logging.py
      tracing.py
      metrics.py
    config/
      settings.py
  prompts/
    system/
    router/
    planner/
    query-understanding/
    tool/
    sql-agent/
    validator/
    composer/
  tests/
    unit/
    contract/
    integration/
    evaluation/
```

目录只表达目标职责。首次创建工程时应保持单一 Python 部署单元，不预先拆分 RAG、模型或评测微服务。

## 3. FastAPI 与 Pydantic 边界

### 3.1 内部 HTTP 入口

| 方法 | 路径 | 请求模型 | 成功语义 |
|---|---|---|---|
| `POST` | `/foodmate/internal/v1/runs` | `RunCommand` | 接受或幂等返回同一 `dispatch_id` |
| `POST` | `/foodmate/internal/v1/runs/{run_id}/cancel` | `CancelCommand` | 接受取消，随后以有序 `RunEvent` 确认 |
| `GET` | `/foodmate/internal/health/live` | 无 | 进程和事件循环存活 |
| `GET` | `/foodmate/internal/health/ready` | 无 | 配置、Prompt、模型适配和 Java 回调客户端可用 |

Python 通过 Java 的内部事件入口回传 `RunEvent`，并通过 Java Tool/SQL Gateway 提交 `ToolProposal` 或 `SqlProposal`、接收 `ToolResult` 或 `SqlResult`。具体字段以[双运行时内部契约 V1](../契约/双运行时内部契约V1.md)为唯一依据。

### 3.2 Pydantic v2 约束

- 所有跨运行时 DTO 使用 Pydantic v2 `BaseModel`，`extra="forbid"`；V1 不静默接收未知字段。
- 字段名固定为 `snake_case`，时间固定为带时区 RFC 3339 UTC，ID 在 JSON 中均为字符串。
- 命令、事件、Proposal、Result 和 RuntimeError 按内部契约规定的字段集执行 RFC 8785 JCS + SHA-256；`request_id/trace_id` 是传输追踪字段，不进入幂等摘要。
- `schema_version` 必须精确为受支持版本；不兼容时返回 `RUNTIME_VERSION_UNSUPPORTED`。
- `deadline_at` 在入站校验后立即比较当前 UTC 时间，过期命令不得启动新模型、工具或 SQL 调用。
- API 层只做认证、契约校验、deadline 和幂等入口，不承载规划或模型逻辑。
- OpenAPI 仅作为生成和联调产物；Java/Python 双端必须用同一组 JSON Schema golden fixtures 做契约测试。

### 3.3 服务身份

FastAPI dependency 必须校验短期 Service JWT 的签名、`iss`、`aud`、`exp`、`nbf` 和 service scope。Java 到 Python 固定 `iss=foodmate-control-plane`、`aud=foodmate-agent-runtime`；Python 回调 Java 使用相反方向。用户身份、租户和 scope 由 Java 按 `run_id` 派生，Python 请求体中的同名字段不能成为授权依据。

## 4. 编排组件

| 组件 | 输入 | 输出 | 不允许做 |
|---|---|---|---|
| Router | 用户消息、授权上下文 | 意图、置信度、RAG/工具需求、缺失槽位 | 直接执行工具或写业务状态 |
| Planner | 目标、约束、Router 结果 | 最少必要步骤、依赖和终止条件 | 绕过确认或 Java Policy |
| Execution | 计划、checkpoint、外部结果 | 有序节点推进和 `RunEvent` | 直接访问业务库或把 proposal 当成功结果 |
| RAG | 授权知识范围、查询 | 改写、召回、rerank、引用 | 扩大 ACL 或把检索文本当系统指令 |
| Model | Prompt、结构化 schema | 模型输出、usage、latency、`model_call_id` | 保存业务真值或直接产生副作用 |
| Composer | 已校验事实、引用、执行结果 | 结构化最终回答 | 暴露内部推理或伪造失败结果 |
| Evaluation | 数据集、轨迹、最终回答 | 离线指标和回归结果 | 改写线上业务状态 |

Execution 采用 Plan-Act-Observe-Reflect：每一步有明确输入、输出和终止条件。模型输出、RAG 内容和第三方文本均为不可信输入；工具与 SQL 只能形成 proposal，实际授权、执行和审计由 Java 完成。

## 5. 状态、事件与恢复

### 5.1 运行状态

Python 维护的是单次 dispatch 的技术执行状态，Java 维护 `AgentRun` 权威状态。Python 可以建议或报告阶段，但不能覆盖 Java 已接受的终态。V1 不存在 `cancelling`；取消确认后由 Java 决定是否把数据库状态推进为 `cancelled`。

### 5.2 Checkpoint

- checkpoint key 固定包含 `run_id`、`dispatch_id`、`attempt` 和节点名。
- checkpoint 至少保存计划版本、已完成节点、待处理 proposal、最后已发 `event_seq`、未确认事件的 `event_id/request_hash`、Prompt 版本和模型调用引用。
- checkpoint 只能保存恢复编排所需的技术状态，不保存业务库凭据，不替代 `agent_runs`、`tool_calls`、`sql_query_audits` 或审计表。
- 恢复前必须向 Java 对账当前终态、取消请求和已完成 invocation；不能仅凭本地 checkpoint 重放副作用。
- checkpoint 存储实现可后选，但配置必须与业务数据源完全分离，并设置保留期、加密和清理策略。

### 5.3 事件发送

每个 `dispatch_id` 的 `event_seq` 从 1 开始严格递增 1。Python 在发送前持久化或可靠记录事件标识和 canonical request hash；重试重放保持原 `event_id/event_seq/occurred_at/event_type/payload/request_hash`。序号只能由单一 dispatch writer 分配，多个节点并发完成时先汇入有序事件出口。

## 6. Tool、SQL 与模型调用

### 6.1 Tool

Python 只生成 `ToolProposal`。Java 根据 `run_id` 恢复可信用户上下文，校验工具版本、schema、scope、确认、deadline 和 `idempotency_key` 后执行。Python 必须把 `TOOL_POLICY_DENIED`、`TOOL_CONFIRMATION_REQUIRED` 和失败结果作为观察值继续编排，不能伪装为成功。

### 6.2 SQL

Python 只负责查询理解、授权 catalog 选择建议和只读 SQL proposal。Java 重新执行 Schema 授权、AST Guard、敏感字段、用户/租户过滤、LIMIT、超时、执行和审计。Python 代码、环境变量和 Secret 模板中均禁止出现 JDBC URL、业务 PostgreSQL 用户名或密码。

### 6.3 Model

每次逻辑模型调用生成稳定且全局唯一的 `model_call_id`；每次供应商 attempt 生成唯一 `provider_attempt_id`，供应商返回的单次请求标识保存为 `provider_request_id`。供应商重试保持同一 `model_call_id`，但使用新的 `provider_attempt_id/provider_request_id`。

每个 attempt 结束后，Execution 通过标准 `RunEvent(event_type="run.model_usage")` 回传完整 usage、latency、cost、状态和三类模型 ID。Java 先按事件幂等，再按 `model_call_id` 写唯一逻辑调用父记录、按 `provider_attempt_id` 写 attempt 子记录并聚合父记录。RunEvent envelope 的 `request_id` 只追踪 HTTP 传输，不能作为模型用量唯一键。该规则是 V2 目标设计，不表示当前 Python 工程或 V2 表已经存在。

## 7. Prompt 版本

- Prompt 文件不得写死在 Python 常量或放入 Java 工程。
- 每份 Prompt manifest 必须包含 `name`、`version`、`owner`、内容摘要哈希和兼容的输出 schema 版本。
- 一次 Run 在 dispatch 开始时解析并固定 Prompt 版本；恢复执行沿用原版本，除非 Java 明确发起新 dispatch。
- Prompt 发布采用不可变版本；回滚通过切换激活版本完成，不覆盖旧文件。
- `RunEvent` 的规划、模型和最终结果 payload 应能关联实际 Prompt 版本，便于回放和评测。

## 8. 配置与 Secret

目标配置仅允许包含：

- Runtime 监听地址、worker/并发和超时。
- Java 控制面 URL、Service JWT 签名/验证材料和 scope。
- 模型、Embedding、Rerank 供应商凭据。
- Milvus/对象存储的受限技术访问配置（仅在授权架构落地后启用）。
- checkpoint、Prompt、日志、Trace、指标和评测配置。

明确禁止：

- 业务 PostgreSQL/MySQL JDBC/DSN、业务库用户名或密码。
- 可绕过 Java Tool Gateway 的业务服务凭据。
- 用户 Access/Refresh Token 或长期 Service JWT。
- 在日志、checkpoint 或错误详情中输出 Secret、完整 Prompt 敏感上下文或未脱敏工具结果。

启动时执行配置 denylist 校验；发现 `DB_PASSWORD`、业务 JDBC URL 或约定的业务数据源键时 readiness 失败并阻止接收 Run。

## 9. 健康检查

`live` 只验证进程、事件循环和基本线程池，不探测外部依赖。`ready` 验证：

- 配置和 Service JWT key set 可加载。
- Prompt manifest、版本和摘要完整。
- Java 控制面回调地址可解析，契约版本受支持。
- 必需模型适配器已配置；可选供应商失败时按降级策略报告。
- checkpoint backend 可用且 schema 兼容。
- 未检测到业务库凭据。

健康响应不得泄露 Secret、供应商 token 或内部网络凭据。依赖退化通过组件状态和错误码表达，不把 `live` 与 `ready` 混用。

## 10. 日志、Trace 与指标

- 入口读取 `X-Request-Id` 和 W3C `traceparent`；日志统一携带 `request_id`、`trace_id`、`run_id`、`dispatch_id`、`attempt`，涉及调用时再带 `invocation_id`、`model_call_id` 或 `provider_attempt_id`。
- 日志使用结构化 JSON，禁止把完整用户输入、Prompt、工具输出和 SQL 结果默认写入 INFO。
- Trace span 至少覆盖 Router、Planner、Execution 节点、RAG、模型、Java Tool/SQL 往返、checkpoint 和事件发送。
- 指标至少包含运行接受/拒绝、节点耗时、模型调用、proposal、取消延迟、事件重放、序号缺口和 Java 回调失败。
- `event_seq` 是业务协议顺序，不替代 Trace span 顺序；`trace_id` 也不是幂等键。

## 11. 失败与取消

- 契约、认证、deadline 失败在启动编排前返回 `RuntimeError`。
- 编排中的可报告失败使用 `run.failed` 事件，payload 引用统一错误码；已发送终态后不得再发另一个终态。
- 收到 `CancelCommand` 后停止创建新的模型、Tool 或 SQL invocation，尝试取消可中断任务，并按当前 dispatch 序列发 `run.cancel_acknowledged`。
- 已提交 Java 的 invocation 不能靠 Python 本地取消假定回滚；必须等待 Java 返回或对账。
- Java 是终态竞争的裁决者；Python 收到 Java 已终态响应后停止执行并清理 checkpoint。

## 12. 测试与完成定义

Python 工程后续实现时至少需要：

1. Pydantic 单元测试：必填字段、枚举、未知字段、UTC 时间和 deadline。
2. 双端契约测试：八类消息 golden JSON 在 Java DTO 与 Pydantic 双向通过，并验证每类 canonical digest、稳定重放与同 ID 不同 hash 冲突。
3. 编排测试：追问、工具拒绝、SQL 拒绝、模型失败、重试、取消和终态竞争。
4. 事件测试：重复、乱序、缺口、断线重放、`event_seq` 单调性和 `run.model_usage` 多 attempt 幂等。
5. 安全测试：Service JWT issuer/audience/scope、Prompt Injection、日志脱敏和业务库凭据 denylist。
6. 恢复测试：checkpoint 恢复前与 Java 对账，不重复 Tool/SQL 副作用。
7. Evaluation 回归：固定数据集、Prompt 版本、结构化输出和引用准确性。

完成标准不是目录存在，而是 FastAPI/pytest 可在项目虚拟环境中启动和通过上述测试，并与 Java stub 完成 dispatch、事件、proposal、取消和错误闭环。

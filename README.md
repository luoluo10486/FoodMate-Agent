# FoodMate

FoodMate 是面向饮食记录、营养分析与备餐规划的任务型 Agent 产品。它不提供医疗诊断、治疗、处方或紧急健康决策；涉及高风险健康判断时，系统应安全降级并提示用户咨询医生或注册营养师。

## 项目架构

![FoodMate 当前架构](./docxs/架构/图/FoodMate当前架构.svg)

核心边界：

- `foodmate-ui` 负责用户界面、认证交互和 SSE 展示。
- Java 控制面是用户、授权、业务数据、工具执行和审计的唯一权威。
- Python `agent-runtime` 负责受控 Agent 编排、模型适配与结果组装，不能直连 FoodMate 业务数据库。
- PostgreSQL 是业务真值；Redis 保存协调、租约和 checkpoint 等技术状态；RocketMQ 提供至少一次异步传输。

![FoodMate Agent 运行闭环](./docxs/架构/图/FoodMateAgent运行闭环.svg)

完整的边界、状态机、预算、Eval 与退回规则见：[架构总览](./docxs/架构/架构总览.md)、[Agent 运行架构](./docxs/架构/Agent运行架构.md)、[ADR-0005](./docxs/决策/ADR-0005-RocketMQ异步主通道.md)。

## 当前真实状态（2026-08-01）

以下仅记录已经运行验证的事实；“已实现”不等于已经完成完整生产闭环。

| 范围 | 已验证事实 |
|---|---|
| 本地基础设施 | Docker 中 PostgreSQL、Redis、MinIO、RocketMQ NameServer/Broker/Proxy 均可 healthy。 |
| 真实持久化 | PostgreSQL E2E 已验证注册、登录、Cookie/CSRF、会话创建、消息持久化和读取。 |
| 异步传输 | Java PostgreSQL Outbox -> RocketMQ -> Consumer 的真实 E2E 已验证 envelope、`request_hash`、`dispatch_id` 与 `run_id`。 |
| Tool/SQL 闭环 | Proposal -> Java Tool Gateway -> 只读 SQL / 审计 -> Result 的真实 E2E 已验证；SQL 失败会记录 `SQL_EXECUTION_FAILED`，重复 Proposal 不重复执行。 |
| 真实云模型与 Eval | SiliconFlow 单次 composer 与独立 Eval 已验证；Eval 拒绝会安全降级，模型调用记录包含 Token、价格版本和成本。完整 Runtime 长时间稳定重复验证仍未完成，默认仍是 `deterministic:local`。 |
| 恢复 | 已验证 Python Runtime 重启后保留 checkpoint，前端恢复入口经 Java PostgreSQL Inbox 对账，创建新的 `dispatch_id + attempt`，再次经 RocketMQ 完成并返回最终 SSE。 |
| 本地长压与多实例 | 30 秒专用 Redis logical DB 准入压测通过：`active_max=20`、`operations=268`、`P50=5.227ms`、`P95=121.163ms`、`P99=121.733ms`、0 次协调错误；两个独立 Java JVM 已共享 PostgreSQL/Redis 跑通跨实例业务读写。 |
| 回归 | `mvn -pl foodmate-bootstrap -am test` 已通过。 |

当前不能宣称完成的内容：

- Python Runtime 的生产 RAG 与更完整 Tool/SQL 业务编排闭环。
- 生产资源上的长时间压测、P95/P99 容量结论、跨节点故障切换以及持续业务 Agent 流量验证。
- 供应商正式价格表核准、账单抽样对账、人工 Eval 校准样本和成本异常告警等生产治理。

## 本地启动

### 1. 启动基础设施

先参考 [`docker/.env.example`](./docker/.env.example) 补齐本地根目录 `.env`，尤其是 MinIO 管理员凭据；不要把真实云模型密钥提交到仓库。

```powershell
docker compose --env-file .env -f docker/compose.yml up -d
docker compose --env-file .env -f docker/compose.yml ps
```

`rocketmq-namesrv` 与 `rocketmq-broker` 分别是名称服务和消息存储/投递节点；`rocketmq-proxy` 是 Python RocketMQ 5.x gRPC 客户端使用的协议代理，不是额外的 Broker。

### 2. 启动 Java 控制面

```powershell
.\mvnw.cmd -pl foodmate-bootstrap -am package
& java -jar '.\foodmate-bootstrap\target\foodmate-bootstrap-0.1.0-SNAPSHOT.jar' '--spring.profiles.active=local'
```

`local` 连接本地真实 PostgreSQL 等基础设施；`local-stub` 只用于不依赖真实基础设施的兼容/开发场景。

```powershell
Invoke-WebRequest http://localhost:8080/actuator/health
```

### 3. 启动前端

```powershell
cd foodmate-ui
npm install
npm run dev
```

### 4. 运行已使用的验证命令

```powershell
mvn -pl foodmate-bootstrap -am '-Dfoodmate.local-e2e=true' '-Dtest=LocalPostgresE2ETest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -pl foodmate-bootstrap -am '-Dfoodmate.local-mq-e2e=true' '-Dtest=M14RocketMqTransportE2ETest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -pl foodmate-bootstrap -am '-Dfoodmate.local-mq-e2e=true' '-Dtest=M14ProposalResultE2ETest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -pl foodmate-bootstrap -am '-Dfoodmate.local-e2e=true' '-Dtest=M14RuntimeCheckpointRecoveryE2ETest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -pl foodmate-bootstrap -am test
```

## 文档

[文档索引](./docxs/文档索引.md) 是唯一导航入口。发生冲突时，以实际代码、迁移和测试事实优先；内部 Java/Python 消息以[双运行时内部契约 V1](./docxs/契约/双运行时内部契约V1.md)为准。

## 2026-08-01 质量门与恢复验证补充

- Python Runtime 现在在 `run.answer_stream` 之前发布独立的 `run.eval_decided`，携带 `result/reason/score/evaluator_version`；Java 将其作为非终态事件写入 Inbox 和 SSE Outbox。
- Eval 本地质量门、Golden 回归、Judge schema/provider fail-closed、安全降级、质量指标 P95/P99 和前端测试/typecheck 已有本机验证证据；本轮 Python 为 `56 passed, 1 skipped`，这不等于生产人工校准或统一指标系统已完成。
- V12 事件 `attempt` 迁移已在本地 PostgreSQL 幂等执行；新环境仍须按人工迁移记录执行 `script/sql/FoodMate/migration/V12__m1_4_event_attempt_compatibility.sql`。

## M1-4 收尾边界

已完成本地真实闭环、跨进程 checkpoint 恢复、RocketMQ Proposal/Result、Eval Gate 和 Redis 准入基线。当前仍不能宣称生产完成：生产资源长压与容量结论、队列防饥饿、多实例业务流量、进程级故障恢复、真实云模型长时间重复稳定性、正式价格核准/账单对账，以及人工校准驱动的生产 Eval 指标告警仍需在目标环境执行。

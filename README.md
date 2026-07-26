# FoodMate

FoodMate 是面向餐饮、营养、饮食记录、摄入分析和备餐规划的任务型 Agent 产品。

## 当前真实状态

截至 2026-07-26，M1-2 与 M1-3 最小真实闭环已完成：前端已接入真实认证、会话、消息、AgentRun SSE、取消和续传；Java 已实现权威 AgentRun、dispatch/outbox、事件 inbox 和 SSE outbox；Python `agent-runtime/` 已实现确定性 stub、Service JWT、dispatch/cancel 和事件回调。真实模型、LangGraph、Eval、预算治理和 RocketMQ 正式异步主通道尚未实现。

| 范围 | 当前事实 |
|---|---|
| 前端构建 | `npm run build` 通过 |
| 前端 lint | `npm run lint` 因 35 个 warning 与零 warning 门槛失败 |
| 前端测试 | `npm run test` 因没有测试文件失败 |
| Java 验证 | `./mvnw.cmd clean verify` 通过 |
| Python Runtime | `.venv` 中 pytest 实测 3 passed；只证明 M1-3 确定性 stub |
| RocketMQ | 目标架构与 M1-4 方案已确认；Compose、Topic 和代码尚未实现 |

完整文档入口、权威优先级和更新条件见 [文档索引](./docxs/文档索引.md)。

## 启动

前端：

```bash
cd foodmate-ui
npm install
npm run dev
```

Java：

```powershell
.\mvnw.cmd -pl foodmate-bootstrap -am package
& java -jar '.\foodmate-bootstrap\target\foodmate-bootstrap-0.1.0-SNAPSHOT.jar' '--spring.profiles.active=local-stub'
```

启动后在另一个 PowerShell 中检查：

```powershell
Invoke-WebRequest http://localhost:8080/actuator/health
```

以上 JAR 命令已于 2026-07-11 以 `local-stub` profile 实际启动并返回 HTTP 200；在前台运行时按 Ctrl+C 正常停止。

Python Runtime 测试：

```powershell
cd agent-runtime
.\.venv\Scripts\python.exe -m pytest -q
```

RocketMQ 目标设计见 [ADR-0005](./docxs/决策/ADR-0005-RocketMQ异步主通道.md)。当前不要把 HTTP stub 链路描述为 RocketMQ 已接入。

# FoodMate

FoodMate 是面向餐饮、营养、饮食记录、摄入分析和备餐规划的任务型 Agent 产品。

## 当前真实状态

截至 2026-07-11，仓库中的前端是 Phase 1-2 静态和 mock 原型；Java 后端是可编译、可启动的基础骨架，尚未提供真实认证、会话、消息、AgentRun、Runtime Client 或 SSE 业务能力。Python Agent Runtime 尚不存在：仓库没有 `agent-runtime/`、`pyproject.toml` 或项目 Python 运行环境。

| 范围 | 当前事实 |
|---|---|
| 前端构建 | `npm run build` 通过 |
| 前端 lint | `npm run lint` 因 35 个 warning 与零 warning 门槛失败 |
| 前端测试 | `npm run test` 因没有测试文件失败 |
| Java 验证 | `./mvnw.cmd clean verify` 通过 |
| Python Runtime | 尚未创建，不能安装、启动或测试 |

完整文档入口、权威优先级和更新条件见 [文档索引](./docxs/文档索引.md)。

## 启动

前端：

```bash
cd frontend
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

Python Agent Runtime 尚未落地，因此当前没有 Python 安装或启动命令。

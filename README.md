# FoodMate

FoodMate 是一个面向餐饮、营养、饮食记录、摄入分析和备餐规划的任务型 Agent 产品。

本文是项目总入口。先看“当前基线”确认项目真实进度，再按“优先阅读”了解产品和工程边界，最后从 `待办任务TODO.md` 领取一个任务。

## 当前基线

截至 2026-07-11，项目状态以仓库代码和测试为准。目标架构已经调整为 **Java 业务控制面 + Python Agent 智能执行面**，但 Python 工程尚未创建，因此必须区分“当前代码”与“目标架构”：

| 范围 | 状态 | 事实依据 |
|---|---|---|
| 前端 Phase 1-2 | 已完成 | `frontend/` 已包含页面、共享组件、mock 数据和 Agent 事件回放 |
| 后端 B3 | 已完成 | 13 个 Maven 模块、Java 21、Spring Boot、统一响应/错误/Trace、软删除基础约束 |
| 后端 B4-1 | 已完成 | `foodmate-infra/src/main/resources/db/migration/V1__init_core_schema.sql`、回滚脚本及迁移测试 |
| Java Agent 旧骨架 | 待迁移 | `foodmate-orchestrator`、`foodmate-rag`、`foodmate-sql-agent`、`foodmate-model` 目前只有占位代码，不再作为 Agent 实现目标 |
| Python Agent Runtime | 未开始 | 尚无 `agent-runtime/`、`pyproject.toml` 或 Python 依赖环境 |
| 后端 API / 认证 | 未开始 | 当前没有真实认证、会话、消息或 Agent SSE 控制器 |
| 当前下一步 | B4-2 | 建立用户、认证、个人资料、会话和消息域的 PO / Mapper |

当前阶段不是“前端已完成、后端从 Phase 3 开始”，而是前端 Phase 1-2 和 Java 后端 B3、B4-1 已完成，正在进入业务持久化和双运行时迁移准备。进入 Agent Phase 6 前，必须先建立 Python Runtime、Java Runtime Client 和版本化内部契约。

目标运行链路：

```text
Frontend -> Java API / Auth / AgentRun / SSE
         -> Python Agent Runtime
         -> Java Tool Gateway / SQL Guard / Business Services
         -> PostgreSQL / external systems
```

- Java 是认证、业务事务、业务数据、`AgentRun`、工具策略和审计的唯一权威。
- Python 负责 Agent 编排、Prompt、模型调用、RAG、SQL proposal、结构化输出和评测。
- Python 不直接写业务数据库，也不能绕过 Java 执行工具或 SQL。

## 快速开始

前端：

```bash
cd frontend
npm install
npm run dev
```

前端生产构建和测试：

```bash
cd frontend
npm run build
npm run lint
npm run test
```

后端：

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd -pl foodmate-bootstrap spring-boot:run -Dspring-boot.run.profiles=local-stub
```

健康检查：`http://localhost:8080/actuator/health`

Python Agent Runtime 尚未落地，因此当前没有可执行的 Python 安装或启动命令。工程创建后必须在此补充项目环境、依赖安装、测试、启动端口和健康检查，不能使用系统 Python 冒充项目环境。

## 优先阅读

请先阅读：

1. [需求文档 PRD](./docs/需求文档PRD.md)
2. [UI 设计文档](./docs/UI设计文档.md)
3. [前端架构文档](./docs/前端架构文档.md)
4. [架构文档](./docs/架构文档.md)
5. [待办任务 TODO](./待办任务TODO.md)

这些文档回答：

- 我们要做什么。
- 产品服务谁。
- MVP 包含什么。
- 页面应该长什么样。
- 核心工程边界是什么。
- 下一步应该先实现什么。

## 详细参考文档

原有长文档保留为详细资料：

1. [FoodMate-产品需求文档.md](./FoodMate-产品需求文档.md)
2. [FoodMate-系统设计与技术方案.md](./FoodMate-系统设计与技术方案.md)
3. [FoodMate-智能体行为与工具协议.md](./FoodMate-智能体行为与工具协议.md)
4. [FoodMate-接口与数据规范.md](./FoodMate-接口与数据规范.md)
5. [FoodMate-Java工程骨架与模块设计.md](./FoodMate-Java工程骨架与模块设计.md)（Java 控制面、跨语言边界及旧模块迁移）
6. [FoodMate-数据库设计与接口工具清单.md](./FoodMate-数据库设计与接口工具清单.md)
7. [FoodMate-实现附录.md](./FoodMate-实现附录.md)

## 文档分层与职责

文档分为三层：

1. `README.md` 和 `docs/`：日常开发入口，说明当前范围、实现状态和执行顺序。
2. 根目录 `FoodMate-*.md`：详细设计基线，保存完整产品、架构、接口、数据库和协议决策。
3. `待办任务TODO.md`：唯一执行路线，记录任务边界和验收标准。

当入口文档与代码实现不一致时，按以下优先级处理：已批准的目标架构与安全边界 > TODO 迁移状态 > 迁移脚本/测试和实际业务代码 > 详细设计 > 示例。旧 Java Agent 占位模块只代表历史代码状态，不能覆盖本次双运行时架构决策。

| 文件 | 职责 |
|---|---|
| `README.md` | 项目总入口：当前基线、启动方式、文档地图和下一步 |
| `docs/需求文档PRD.md` | 产品范围入口：定位、用户、MVP、流程、成本边界、验收标准 |
| `docs/UI设计文档.md` | UI 入口：页面、组件、状态、视觉规范、响应式规则 |
| `docs/前端架构文档.md` | 前端入口：React/Vite/TypeScript/Arco、目录、路由、mock 边界 |
| `docs/架构文档.md` | 工程入口：运行主链路、模块职责、Agent/RAG/Tool 边界和当前阶段 |
| `待办任务TODO.md` | 唯一执行路线图：任务范围、允许改动范围和验收标准 |

| `FoodMate-产品需求文档.md` | 完整产品需求参考 |
| `FoodMate-系统设计与技术方案.md` | 完整系统设计和技术方案参考 |
| `FoodMate-智能体行为与工具协议.md` | Agent 行为、Prompt 和工具协议参考 |
| `FoodMate-接口与数据规范.md` | API、DTO、SSE、状态机和接口语义 |
| `FoodMate-Java工程骨架与模块设计.md` | Java 业务控制面、Python Runtime 边界、内部契约和旧模块迁移参考 |
| `FoodMate-数据库设计与接口工具清单.md` | 数据库、Milvus、工具注册和实现级接口参考 |
| `FoodMate-实现附录.md` | 详细样例、模板、页面状态和实现说明 |

## 推荐工作流

1. 从 `待办任务TODO.md` 选择一个任务。
2. 阅读对应的 `docs/` 入口文档。
3. 阅读入口文档引用的详细来源文档。
4. 只实现该 TODO 允许的范围。
5. 完成后按验收标准检查，再进入下一个任务。

下一项：`B4-2`。业务持久化可以继续推进；进入 Agent 主链路前，先完成双运行时迁移准备任务，避免继续向旧 Java Agent 模块增加实现。

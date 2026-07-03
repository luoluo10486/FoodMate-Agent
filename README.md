# FoodMate

FoodMate 是一个面向餐饮、营养、饮食记录、摄入分析和备餐规划的任务型 Agent 产品。

当前项目已完成工作流文档入口、前端静态 UI 原型和前端 mock 交互阶段。本阶段目标是把产品范围、UI 方向、架构边界和实现顺序整理清楚，并让人类开发者和 AI Coding Agent 都能一次只执行一个明确任务。

## 当前阶段

- 产品、架构、接口、Agent 协议和工程设计的详细资料已经存在。
- `docs/` 提供日常开发优先阅读的入口文档。
- `待办任务TODO.md` 是执行路线图。
- Phase 1 前端静态 UI 原型与 Phase 2 前端 mock 交互已在 `frontend/` 中落地。
- 后端 B3 工程骨架与通用能力已落地：Java 21、Spring Boot 3、13 个 Maven 模块、Maven Wrapper、`local-stub` 启动、统一响应/错误/Trace、MyBatis-Plus 基础约束。
- 后端 B4-1 已新增 Flyway 首版核心表迁移脚本；当前仍未实现认证 API 或 Agent 运行时。

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
5. [FoodMate-Java工程骨架与模块设计.md](./FoodMate-Java工程骨架与模块设计.md)
6. [FoodMate-数据库设计与接口工具清单.md](./FoodMate-数据库设计与接口工具清单.md)
7. [FoodMate-实现附录.md](./FoodMate-实现附录.md)

## 文档职责

| 文件 | 职责 |
|---|---|
| `docs/需求文档PRD.md` | 产品范围入口：定位、用户、MVP、流程、成本边界、验收标准 |
| `docs/UI设计文档.md` | UI 入口：页面、组件、状态、视觉规范、响应式规则 |
| `docs/前端架构文档.md` | 前端入口：React/Vite/TypeScript/Arco、目录结构、Phase 1-2 mock 交互边界 |
| `docs/架构文档.md` | 工程入口：运行主链路、模块职责、Agent/RAG/Tool 边界 |
| `待办任务TODO.md` | 执行路线图：分阶段任务、允许改动范围和验收标准 |
| `FoodMate-产品需求文档.md` | 完整产品需求参考 |
| `FoodMate-系统设计与技术方案.md` | 完整系统设计和技术方案参考 |
| `FoodMate-智能体行为与工具协议.md` | Agent 行为、Prompt 和工具协议参考 |
| `FoodMate-接口与数据规范.md` | API、DTO、SSE、状态机和接口语义 |
| `FoodMate-Java工程骨架与模块设计.md` | Java 模块化单体和包边界参考 |
| `FoodMate-数据库设计与接口工具清单.md` | 数据库、Milvus、工具注册和实现级接口参考 |
| `FoodMate-实现附录.md` | 详细样例、模板、页面状态和实现说明 |

## 推荐工作流

1. 从 `待办任务TODO.md` 选择一个任务。
2. 阅读对应的 `docs/` 入口文档。
3. 阅读入口文档引用的详细来源文档。
4. 只实现该 TODO 允许的范围。
5. 完成后按验收标准检查，再进入下一个任务。

## 下一步开发目标

Phase 1 静态 UI 原型和 Phase 2 Mock 交互已完成前端闭环，前端技术栈为 React + Vite + TypeScript + Arco Design。后端 B3 骨架与 B4-1 数据库首版迁移脚本已完成，下一步推荐进入持久化 PO / Mapper 与认证基础。

已完成：

- `F1-1. 创建前端项目骨架`
- `F1-2. 实现首页静态原型`
- `F1-3. 实现会话页静态原型`
- `F1-4. 实现分析页静态原型`
- `F1-5. 实现规划页静态原型`
- `F1-6. 抽取共享 UI 组件`
- `F2-1. 添加假 Agent 事件回放`
- `F2-2. 添加 mock 追问流程`
- `F2-3. 添加 mock 确认流程`
- `F2-4. 添加个人资料页 mock`
- `F2-5. 添加管理后台 mock 入口`
- `B3-1. 创建 Maven 模块化单体骨架`
- `B3-2. 建立 shared 通用契约`
- `B3-3. 建立配置与环境文件`
- `B3-4. 建立统一异常与参数校验`
- `B3-5. 建立审计与软删除基础约束`
- `B4-1. 建立数据库迁移体系`

之后继续：

- `B4-2. 建立用户、认证、个人资料与会话域表的 PO / Mapper`
- 后续再进入后端认证、个人资料、会话与 Agent 主链路。

## 前端本地运行

前端项目位于 `frontend/`：

```bash
cd frontend
npm install
npm run dev
```

生产构建检查：

```bash
cd frontend
npm run build
```

## 后端本地运行

后端使用仓库内 Maven Wrapper，不依赖全局 Maven：

```bash
.\mvnw.cmd clean verify
```

启动无外部依赖的 stub 环境：

```bash
.\mvnw.cmd -pl foodmate-bootstrap spring-boot:run -Dspring-boot.run.profiles=local-stub
```

健康检查：

```bash
curl http://localhost:8080/actuator/health
```

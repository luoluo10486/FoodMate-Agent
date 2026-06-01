# CookHero 技术选型

版本：v1.0  
对应总设计：[CookHero-总体设计.md](./CookHero-总体设计.md)  
对应产品文档：[CookHero-产品需求文档.md](./CookHero-产品需求文档.md)  
对应接口文档：[CookHero-接口规范.md](./CookHero-接口规范.md)  
对应提示词与工具协议：[CookHero-提示词与工具协议.md](./CookHero-提示词与工具协议.md)

---

## 1. 选型目标

这份文档不是简单列一个“技术栈清单”，而是给出一套适合 Agent + RAG + 业务工具编排的工程化选型方案。目标是满足以下要求：

1. 适合快速落地 MVP
2. 适合后续扩展复杂 Agent 能力
3. 适合做检索、工具调用、异步任务和状态管理
4. 适合做前后端分离和长期维护
5. 适合有清晰工程边界和可观测性

---

## 2. 选型原则

### 2.1 原则一：优先工程稳定性

Agent 系统最怕“能跑但不好维护”。因此选型要优先考虑：

- 类型系统清晰
- 生态成熟
- 调试方便
- 社区成熟
- 可观测性强

### 2.2 原则二：优先任务编排能力

这个项目不是普通网页应用，而是一个会做任务的 Agent 系统，所以技术栈必须天然适合：

- 任务状态管理
- 工具调用
- 异步执行
- 流式输出
- 结构化数据处理

### 2.3 原则三：优先长期可维护

不能只看“开发快”，还要看：

- 后续是否容易扩展新工具
- 是否容易替换模型供应商
- 是否容易做评测和回放
- 是否容易增加新场景

---

## 3. 推荐总体栈

### 3.1 推荐方案概览

这是我建议的默认方案：

- **前端**：React + Vite + TypeScript
- **后端 API**：Java 21 + Spring Boot 3 + Spring WebFlux
- **Agent 编排**：自研状态机 + Spring AI / LangChain4j
- **RAG 检索**：PostgreSQL + pgvector
- **缓存**：Redis
- **消息队列**：RocketMQ
- **对象存储**：MinIO 或 S3 兼容存储
- **异步任务**：RocketMQ Starter + `@Async` + Quartz
- **鉴权**：Spring Security + JWT + Refresh Token
- **监控**：OpenTelemetry + Prometheus + Grafana
- **日志**：Logback + 结构化日志 + Loki / ELK
- **错误追踪**：Sentry

### 3.2 为什么是这个组合

#### 前端选 React + Vite + TypeScript

原因：

- 与截图中的本地开发体验一致，适合快速迭代
- Vite 启动快，适合交互密集型产品
- React 组件生态成熟，适合复杂页面
- TypeScript 适合长期维护

#### 后端选 Java + Spring Boot

原因：

- Java 更适合长期工程化和分层维护
- Spring Boot 的生态适合业务系统、鉴权、审计和接口治理
- Spring WebFlux 适合 SSE 和流式响应
- Spring AI / LangChain4j 适合接入模型、工具调用和 RAG 能力
- DTO、校验、事务和依赖注入更适合稳定后端治理

#### 检索选 PostgreSQL + pgvector

原因：

- MVP 阶段不必引入过多基础设施
- 关系型数据和向量数据可以放在一套主库里
- 易部署、易备份、易联调
- 对中小规模知识库非常合适

#### Redis 作为缓存和队列

原因：

- 会话状态、短期记忆、任务队列都能用
- 响应快
- 成本低
- 部署简单

---

## 4. 前端技术选型

### 4.1 核心框架

| 技术 | 选择 | 作用 |
|---|---|---|
| React | 推荐 | UI 构建 |
| Vite | 推荐 | 构建与开发服务器 |
| TypeScript | 强烈推荐 | 类型约束与长期维护 |

### 4.2 状态与数据请求

| 技术 | 选择 | 作用 |
|---|---|---|
| TanStack Query | 推荐 | 请求缓存、分页、刷新 |
| Zustand | 推荐 | UI 状态、会话状态 |
| Zod | 推荐 | 表单与 schema 校验 |

### 4.3 UI 组件

| 技术 | 选择 | 作用 |
|---|---|---|
| Tailwind CSS | 推荐 | 快速做布局和样式 |
| Radix UI / shadcn/ui | 推荐 | 高可用基础组件 |
| Framer Motion | 可选 | 动效 |
| Recharts / ECharts | 推荐 | 数据可视化 |

### 4.4 前端能力要求

前端必须支持：

- 流式消息渲染
- 会话列表与会话恢复
- 工具状态展示
- 引用片段展示
- 结构化结果卡片
- 异步任务状态展示

### 4.5 前端不建议的选择

- 纯 jQuery
- 组件和状态无分层的大型单文件方案
- 过早引入过重的状态管理框架

---

## 5. 后端技术选型

### 5.1 Web API

| 技术 | 选择 | 作用 |
|---|---|---|
| Spring Boot 3 | 强烈推荐 | 对外 API、业务编排、依赖注入 |
| Spring WebFlux | 推荐 | 流式响应、SSE、非阻塞接口 |
| Spring Validation | 推荐 | 请求参数校验 |
| Jackson | 推荐 | JSON 序列化与反序列化 |
| Lombok | 可选 | 简化 DTO / Entity 代码 |
| JDK 21 | 推荐 | 长期支持版本，适合生产 |

### 5.2 业务框架

建议将后端分成以下逻辑模块：

- API 层
- Agent 编排层
- 工具层
- 检索层
- 数据访问层
- Worker 任务层

### 5.3 为什么不优先选重型单体框架

如果一开始上强约束重框架，可能会带来：

- 改动成本高
- 流式和异步处理不够灵活
- Agent 编排实现被框架绑死

Spring Boot 更适合：

- 快速定义接口
- 明确分层
- 更容易做权限、审计、事务和领域建模

---

## 6. Agent 编排技术选型

### 6.1 推荐：自研状态机 + 可选工作流框架

推荐主策略：

- 以自研状态机为核心
- 将 Agent 任务执行拆成可控步骤
- 对复杂编排场景可引入 Spring AI 的对话/工具封装，或在必要时引入 LangChain4j 做补充

### 6.2 为什么不完全依赖黑盒框架

Agent 项目一旦进入生产，会非常关注：

- 每一步为什么执行
- 中间状态是什么
- 哪个工具失败了
- 何时追问
- 如何回放

如果完全依赖黑盒框架，调试会困难很多。

### 6.3 推荐能力

Agent 编排层至少要支持：

- Router
- Planner
- Retriever
- Tool Executor
- Validator
- Answer Composer
- Retry Policy
- Stop Policy

---

## 7. 数据与存储选型

### 7.1 主数据库：PostgreSQL

用途：

- 用户
- 会话
- 消息
- 任务
- 工具调用
- 日志
- 计划
- 分析结果

优势：

- 事务可靠
- SQL 灵活
- 生态成熟
- 便于做统计与回放

### 7.2 向量能力：pgvector

用途：

- 文档切片 embedding
- RAG 召回
- 语义检索

优势：

- 维护成本低
- 和主库一体化
- 足够支持中小规模知识库

### 7.3 缓存与短期状态：Redis

用途：

- 会话临时态
- 运行中的任务状态
- 热点检索结果
- 限流
- 幂等控制

### 7.4 文件存储：MinIO / S3

用途：

- 上传文档
- 图片附件
- 导出报告
- 备份文件

---

## 8. 任务与异步执行选型

### 8.1 异步队列：RocketMQ + Redis + Spring Worker

适合承载：

- 长时间任务
- 大批量文档解析
- 计划生成
- 报告生成
- 批量分析
- 文档切分与索引

### 8.2 为什么需要队列

因为 Agent 场景经常存在：

- 单轮耗时不可控
- 任务依赖多个工具
- 需要后台执行和前端轮询/订阅
- 需要事务外的异步恢复能力

队列可把“请求线程”和“执行线程”解耦。

---

## 9. 检索技术选型

### 9.1 MVP 方案

推荐优先使用：

- PostgreSQL 全文检索
- pgvector 语义召回
- 简单 rerank

### 9.2 进阶方案

当知识库增大后，可升级为：

- OpenSearch / Elasticsearch 做关键词召回
- 向量库独立部署
- 专门的 reranker 服务

### 9.3 选型建议

MVP 不建议一开始就把检索系统拆得很重，因为会增加：

- 运维复杂度
- 联调成本
- 数据同步成本

---

## 10. 模型与调用方式选型

### 10.1 模型接入方式

建议设计为模型无关层：

- 支持 OpenAI-Compatible API
- 支持多模型提供商切换
- 支持不同场景选择不同模型
- 通过 Spring AI 统一模型适配层，避免业务代码直接依赖具体厂商 SDK

### 10.2 模型能力要求

推荐至少支持：

- 工具调用
- 结构化输出
- 流式输出
- 上下文窗口较大

### 10.3 模型使用策略

- Router 可用较便宜模型
- Planner 可用中等能力模型
- 最终回答可用高质量模型
- 纯计算和查询尽量交给工具

---

## 11. 可观测性选型

### 11.1 日志

推荐结构化日志：

- request_id
- session_id
- run_id
- tool_name
- latency
- error_code

### 11.2 指标

推荐监控：

- 请求延迟
- 工具调用成功率
- 检索命中率
- Agent 完成率
- 追问率
- 超时率

### 11.3 链路追踪

推荐使用：

- OpenTelemetry
- Prometheus
- Grafana

### 11.4 错误追踪

推荐使用：

- Sentry

---

## 12. 鉴权与安全选型

### 12.1 推荐方案

- JWT Access Token
- Refresh Token
- API 级鉴权
- 角色权限控制

### 12.2 管控点

- 用户只能访问自己的会话
- 写操作必须确认
- 管理员能力单独授权
- 关键接口记录审计日志

---

## 13. 部署选型

### 13.1 本地开发

推荐使用：

- Docker Compose
- 前端本地 dev server
- 后端本地启动
- PostgreSQL + Redis + MinIO 容器化

### 13.2 生产部署

推荐容器化部署：

- Web 服务
- API 服务
- Worker 服务
- PostgreSQL
- Redis
- 对象存储

### 13.3 未来扩展

规模上来后可迁移到：

- Kubernetes
- 独立向量服务
- 独立检索服务
- 独立分析服务

---

## 14. 推荐工程结构

### 14.1 推荐仓库结构

```text
/
  apps/
    web/
    api/
    worker/
  packages/
    shared/
    schemas/
    ui/
  docs/
  infra/
```

### 14.2 目录职责

- `apps/web`：前端界面
- `apps/api`：业务 API 与 Agent 编排入口
- `apps/worker`：异步任务
- `packages/shared`：通用工具、常量、类型
- `packages/schemas`：请求响应 schema
- `docs`：设计与接口文档
- `infra`：部署与环境文件

---

## 15. 备选方案与取舍

### 15.1 纯 Node.js 全栈

优点：

- 语言统一
- 前后端共享类型方便

缺点：

- 业务治理、事务、权限和领域建模通常不如 Java/Spring 自然
- 对复杂后端分层的约束力弱一些

### 15.2 Java + Spring Boot 全栈

优点：

- 工程化强，适合长期维护
- 事务、校验、鉴权、审计、分层更自然
- SSE、WebFlux、批处理、任务调度都能统一治理
- 便于和企业级中台、权限系统、日志系统集成

缺点：

- 原型验证速度略慢于纯脚本式实现
- 需要更规范的包结构和接口设计

### 15.3 推荐结论

对于这个项目，推荐：

- 前端：React + Vite + TypeScript
- 后端：Java 21 + Spring Boot 3 + Spring WebFlux

这是目前平衡“前端开发效率”和“工程化后端治理能力”的最佳折中。

---

## 16. 最终推荐结论

如果只给一个最实用的推荐答案：

- **前端**：React + Vite + TypeScript + Tailwind + shadcn/ui
- **后端**：Java 21 + Spring Boot 3 + Spring WebFlux + Spring Security
- **Agent**：自研状态机编排 + Spring AI / LangChain4j
- **检索**：PostgreSQL + pgvector
- **缓存/队列**：Redis + RocketMQ
- **存储**：MinIO / S3
- **监控**：OpenTelemetry + Prometheus + Grafana + Sentry

这套组合最适合先把 CookHero 这个垂直 Agent 产品做成稳定、可维护、可扩展的工程系统。

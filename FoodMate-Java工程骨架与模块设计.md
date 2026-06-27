# FoodMate Java工程骨架与模块设计

版本：v1.0  
对应总设计：[FoodMate-系统设计与技术方案.md](./FoodMate-系统设计与技术方案.md)  
对应接口文档：[FoodMate-接口与数据规范.md](./FoodMate-接口与数据规范.md)  
对应行为协议：[FoodMate-智能体行为与工具协议.md](./FoodMate-智能体行为与工具协议.md)

文档定位：本文件是 FoodMate 的工程搭建蓝图，只回答“项目怎么搭、模块怎么拆、边界怎么守、未来怎么拆服务”。产品边界、工具优先级、查询主路径和模型治理总原则，仍以总设计文档为准。

---

## 0. 先说结论

FoodMate 当前采用 **模块化单体**，而不是直接做成运行时微服务。

这样做的原因不是“规模小”，而是当前系统的核心复杂度主要集中在：

- Agent 编排
- 查询理解
- RAG 检索
- SQL Agent
- 工具调用
- 模型接入与模型治理

这些能力还在快速演化，如果一开始就拆成多个独立服务，会带来下面的问题：

- 领域边界会频繁变化
- 接口协议会反复改
- 调试链路变长
- 部署、联调、排障成本明显上升

所以当前阶段的推荐形态是：

- **代码结构按模块化单体设计**
- **文档提前定义未来拆服务边界**
- **运行时先单进程部署，后续再按边界拆分**

---

## 1. 工程定位与演进策略

### 1.1 当前目标

第一阶段的工程目标不是“平台化”，而是先把下面 6 条主链路跑稳：

1. 会话问答链路
2. RAG 检索链路
3. SQL Agent 查询链路
4. 工具调用链路
5. 记忆写入链路
6. 模型调用链路

### 1.2 为什么不直接微服务

当前不直接微服务化，主要基于以下判断：

- `orchestrator`、`rag`、`sql-agent` 三个域现在耦合度高，但边界还在演化
- P0 工具数量有限，独立拆服务只会增加 RPC 和运维成本
- 模型网关虽然未来可能独立，但第一阶段可以先以内嵌网关模块落地
- 当前系统更需要高效验证主链路，而不是提前为分布式复杂度买单

### 1.3 什么时候才拆服务

只有满足下面任一条件，才建议从模块化单体拆到微服务：

- 单次发布经常被不同模块相互阻塞
- `worker` 的异步负载和主 API 竞争资源
- `rag` 或 `sql-agent` 需要独立扩容
- 模型网关出现多租户、多配额、多供应商策略治理需求
- 团队已经拆成至少两个稳定协作小组，需要独立发布节奏

### 1.4 拆分顺序

FoodMate 推荐的拆分顺序如下：

1. `worker`
2. `model-gateway`
3. `rag/knowledge`
4. `tool + sql-agent`
5. `orchestrator`
6. `api/bff`

这个顺序的原则是：

- 先拆异步和高资源模块
- 再拆有明确治理边界的模块
- 最后才拆编排主链路

---

## 2. Maven 多模块工程结构

### 2.1 根工程

根工程建议使用 Maven 聚合工程，`packaging` 固定为 `pom`。

根工程职责：

- 管理依赖版本
- 管理插件版本
- 管理 Java 版本与编码
- 管理统一构建规则
- 聚合所有业务模块

建议的顶层结构：

```text
foodmate/
  ├── pom.xml
  ├── README.md
  ├── docs/
  ├── prompts/
  ├── scripts/
  ├── sql/
  ├── foodmate-bootstrap/
  ├── foodmate-api/
  ├── foodmate-application/
  ├── foodmate-domain/
  ├── foodmate-orchestrator/
  ├── foodmate-rag/
  ├── foodmate-tool/
  ├── foodmate-sql-agent/
  ├── foodmate-model/
  ├── foodmate-gateway-client/
  ├── foodmate-worker/
  ├── foodmate-infra/
  └── foodmate-shared/
```

### 2.2 模块清单与角色

| 模块 | 类型 | 主要职责 | 是否可独立拆服务 |
|---|---|---|---|
| `foodmate-bootstrap` | 启动模块 | Spring Boot 启动、配置装配、Bean 装配 | 否 |
| `foodmate-api` | 接入层 | HTTP API、SSE、鉴权、请求响应协议 | 是 |
| `foodmate-application` | 应用层 | 用例编排、事务协调、DTO 组装 | 否 |
| `foodmate-domain` | 领域层 | 实体、聚合、领域规则、领域服务接口 | 否 |
| `foodmate-orchestrator` | 编排层 | Router、Planner、Executor、Validator、Composer | 是 |
| `foodmate-rag` | 能力层 | 查询理解、检索、重排、引用组装 | 是 |
| `foodmate-tool` | 能力层 | 工具注册、工具执行、工具适配器 | 是 |
| `foodmate-sql-agent` | 能力层 | Schema Catalog、SQL Planner、SQL Guard、MCP | 是 |
| `foodmate-model` | 模型层 | ModelService、Prompt 装配、结构化输出适配 | 否 |
| `foodmate-gateway-client` | 模型层 | 模型网关客户端、统一请求头、协议转换 | 是 |
| `foodmate-worker` | 异步层 | 文档解析、索引构建、摘要、批任务 | 是 |
| `foodmate-infra` | 基础设施层 | PostgreSQL、Redis、Milvus、RocketMQ、MinIO 集成 | 部分可拆 |
| `foodmate-shared` | 共享模块 | 公共枚举、错误码、通用 DTO、Trace 上下文 | 否 |

### 2.3 模块依赖方向

允许的依赖方向固定如下：

```text
foodmate-bootstrap
  -> foodmate-api
  -> foodmate-application
  -> foodmate-orchestrator
  -> foodmate-rag
  -> foodmate-tool
  -> foodmate-sql-agent
  -> foodmate-model
  -> foodmate-gateway-client
  -> foodmate-worker
  -> foodmate-infra
  -> foodmate-shared

foodmate-api
  -> foodmate-application
  -> foodmate-shared

foodmate-application
  -> foodmate-domain
  -> foodmate-orchestrator
  -> foodmate-rag
  -> foodmate-tool
  -> foodmate-sql-agent
  -> foodmate-model
  -> foodmate-shared

foodmate-orchestrator
  -> foodmate-domain
  -> foodmate-rag
  -> foodmate-tool
  -> foodmate-sql-agent
  -> foodmate-model
  -> foodmate-shared

foodmate-rag
  -> foodmate-domain
  -> foodmate-model
  -> foodmate-infra
  -> foodmate-shared

foodmate-tool
  -> foodmate-domain
  -> foodmate-infra
  -> foodmate-shared

foodmate-sql-agent
  -> foodmate-domain
  -> foodmate-model
  -> foodmate-infra
  -> foodmate-shared

foodmate-model
  -> foodmate-gateway-client
  -> foodmate-shared

foodmate-gateway-client
  -> foodmate-shared

foodmate-worker
  -> foodmate-application
  -> foodmate-rag
  -> foodmate-tool
  -> foodmate-sql-agent
  -> foodmate-model
  -> foodmate-infra
  -> foodmate-shared

foodmate-infra
  -> foodmate-shared
```

### 2.4 明确禁止的依赖

下面这些依赖一律禁止：

- `api -> infra`
- `api -> model`
- `api -> rag`
- `domain -> application`
- `domain -> infra`
- `tool -> api`
- `rag -> api`
- `sql-agent -> api`
- `shared -> 任何业务模块`

解释：

- `api` 不应直连数据库、Milvus、模型 SDK
- `domain` 必须保持纯领域语义，不依赖框架实现
- `shared` 只放稳定公共对象，不能反向侵入业务

---

## 3. Java 包结构建议

### 3.1 根包

根包统一使用：

```text
com.foodmate
```

### 3.1.1 ID 约束

FoodMate 的主键方案统一为 Snowflake BIGINT，工程实现必须遵循：

- Java 实体内部可以使用 `Long`
- API DTO、SSE 事件、外部集成 DTO 一律把 ID 序列化为字符串
- 前端禁止把 Snowflake ID 当数值参与计算
- 新增表和新增 DTO 不允许再引入 UUID 口径

### 3.2 顶层包结构

```text
com.foodmate
  ├── api
  ├── application
  ├── domain
  ├── infrastructure
  ├── orchestrator
  ├── rag
  ├── sqlagent
  ├── tool
  ├── model
  ├── gateway
  ├── worker
  ├── security
  └── shared
```

### 3.3 包路径模板

#### `api`

```text
com.foodmate.api
  ├── controller
  ├── sse
  ├── request
  ├── response
  ├── assembler
  └── advice
```

职责：

- 处理 HTTP / SSE 入站协议
- 参数校验
- 身份上下文解析
- 响应结构封装
- 异常转标准错误码

#### `application`

```text
com.foodmate.application
  ├── service
  ├── command
  ├── query
  ├── dto
  ├── assembler
  └── facade
```

职责：

- 承接 API 发起的用例
- 调用编排层与领域层
- 控制事务边界
- 组装 DTO

#### `domain`

```text
com.foodmate.domain
  ├── session
  ├── message
  ├── agent
  ├── memory
  ├── foodlog
  ├── mealplan
  ├── knowledge
  ├── datasource
  ├── tool
  ├── model
  └── common
```

职责：

- 实体和聚合定义
- 领域规则
- 领域服务接口
- 仓储接口

#### `orchestrator`

```text
com.foodmate.orchestrator
  ├── router
  ├── planner
  ├── executor
  ├── validator
  ├── composer
  ├── policy
  └── runtime
```

职责：

- 任务路由
- 多步计划生成
- 工具与 RAG 调用编排
- 输出校验
- 最终答复拼装

#### `rag`

```text
com.foodmate.rag
  ├── understanding
  ├── retriever
  ├── rerank
  ├── citation
  ├── memory
  └── acl
```

职责：

- 查询理解
- Hybrid 检索
- 候选重排
- 引用组装
- ACL 过滤

#### `sqlagent`

```text
com.foodmate.sqlagent
  ├── router
  ├── catalog
  ├── planner
  ├── guard
  ├── mcp
  ├── audit
  └── executor
```

职责：

- 数据源识别
- Schema Catalog 解析
- SQL 生成
- SQL 安全校验
- MCP 调用
- 审计记录

#### `tool`

```text
com.foodmate.tool
  ├── registry
  ├── executor
  ├── contract
  ├── calculator
  ├── timeparser
  ├── knowledgesearch
  ├── databasequery
  ├── foodlogwriter
  └── planvalidator
```

职责：

- 维护工具注册表
- 维护输入输出 schema
- 调用具体适配器
- 输出统一 ToolResult

#### `model`

```text
com.foodmate.model
  ├── service
  ├── prompt
  ├── contract
  ├── request
  ├── response
  └── metrics
```

职责：

- `ModelService` 对外统一接口
- Prompt 装配
- 模型请求响应标准化
- usage、cost、latency 统计

#### `gateway`

```text
com.foodmate.gateway
  ├── client
  ├── config
  ├── header
  └── converter
```

职责：

- 模型网关 HTTP 客户端
- Header 注入
- Trace 透传
- 协议转换

#### `worker`

```text
com.foodmate.worker
  ├── job
  ├── handler
  ├── mq
  ├── schedule
  └── listener
```

职责：

- 消费 RocketMQ 消息
- 执行异步任务
- 处理索引构建、摘要、批量任务

#### `infrastructure`

```text
com.foodmate.infrastructure
  ├── persistence
  ├── redis
  ├── milvus
  ├── mq
  ├── minio
  ├── mcp
  ├── external
  └── config
```

职责：

- 具体技术接入
- Repository 实现
- Milvus、RocketMQ、MinIO、MCP 客户端封装

### 3.4 六类核心角色职责边界

| 角色 | 允许做什么 | 不允许做什么 |
|---|---|---|
| `Controller` | 入参校验、鉴权、SSE 输出、错误映射 | 直接访问 DB、直接调模型 |
| `Application Service` | 用例编排、事务控制、DTO 组装 | 写复杂多轮推理逻辑 |
| `Domain Service` | 领域规则、聚合协调 | 依赖框架实现 |
| `Repository` | 持久化和查询 | 承担业务决策 |
| `Tool Adapter` | 确定性执行 | 负责路由和最终答复 |
| `Worker Handler` | 异步任务处理 | 直接承接用户同步请求 |

---

## 4. 核心模块职责与对外接口

### 4.1 `foodmate-model`

#### 对外公开接口

- `chat(...)`
- `chatStream(...)`
- `structuredOutput(...)`
- `toolCall(...)`
- `embed(...)`
- `rerank(...)`

#### 统一返回对象字段

- `request_id`
- `trace_id`
- `provider`
- `model_name`
- `content`
- `usage`
- `latency_ms`
- `cost`
- `status`
- `error`

#### 边界规则

- 业务层只依赖 `ModelService`
- 不允许任何业务模块直接依赖供应商 SDK
- 模型路由、配额、鉴权不在 `ModelService` 内实现业务规则，只通过 `ModelGatewayClient` 转发

### 4.2 `foodmate-rag`

#### 对外公开接口

- `QueryUnderstandingService`
- `KnowledgeSearchService`
- `CitationAssembler`
- `MemoryRecallService`

#### 负责内容

- 查询理解
- ACL 过滤构建
- BM25 / dense / hybrid 检索
- Rerank
- 引用片段组装

#### 不负责内容

- 最终答复生成
- 数据库事务写入
- SQL 生成

### 4.3 `foodmate-tool`

#### 对外公开接口

- `ToolRegistry`
- `ToolExecutor`
- `ToolResult`

#### P0 工具固定为

- `calculator`
- `time_parser`
- `knowledge_search`
- `database_query`
- `food_log_writer`
- `plan_validator`

### 4.4 `foodmate-sql-agent`

#### 对外公开接口

- `SqlAgentService`
- `SchemaCatalogResolver`
- `SqlGuardService`
- `McpQueryExecutor`

#### 固定主链路

`Query Router -> Query Understanding -> Schema Catalog -> SQL Planner -> SQL Guard -> MCP -> Readonly SQL Executor`

#### 不允许行为

- 直接执行写 SQL
- 绕过 `SQL Guard`
- 绕过审计落库

### 4.5 `foodmate-orchestrator`

#### 核心内部组件

- `IntentRouter`
- `TaskPlanner`
- `ExecutionEngine`
- `ResultValidator`
- `AnswerComposer`

#### 核心职责

- 决定要不要检索
- 决定要不要调用工具
- 决定是否追问
- 维护多步任务状态

#### 不承担职责

- 不直接实现工具逻辑
- 不直接操作数据库
- 不直接发 HTTP 给模型供应商

---

## 5. 关键运行链路

### 5.1 会话问答链路

| 步骤 | 组件 | 输入 | 输出 |
|---|---|---|---|
| 1 | `api` | 用户消息 | `ChatRequest` |
| 2 | `application` | 请求 DTO | `AgentCommand` |
| 3 | `orchestrator.router` | 用户意图、上下文 | `IntentDecision` |
| 4 | `orchestrator.planner` | 意图、约束 | `ExecutionPlan` |
| 5 | `orchestrator.executor` | 执行计划 | 检索结果 / 工具结果 |
| 6 | `orchestrator.composer` | 证据、结果 | 最终答复 |
| 7 | `application` | 答复 | 会话持久化 |
| 8 | `api.sse` | 结果事件 | SSE 输出 |

### 5.2 RAG 检索链路

1. `orchestrator` 判断问题属于知识问答或混合场景  
2. 调用 `QueryUnderstandingService`  
3. 生成 `keyword_query`、`semantic_query`、`filters`、`acl_filter`  
4. `rag.retriever` 调用 Milvus 执行 BM25 + dense hybrid search  
5. `rag.rerank` 调用 rerank 模型重排  
6. `rag.citation` 组装引用片段  
7. 结果返回 `orchestrator`

### 5.3 SQL Agent 查询链路

1. `database_query` 作为唯一对外入口被调用  
2. `sqlagent.router` 判断目标数据源域  
3. `sqlagent.catalog` 读取 `schema_catalogs`  
4. `sqlagent.planner` 生成候选 SQL  
5. `sqlagent.guard` 做危险 SQL 拦截  
6. `sqlagent.audit` 写入审计记录  
7. `sqlagent.mcp` 通过 MCP 调用只读执行器  
8. 返回结构化结果给 `tool.executor`

### 5.4 工具调用链路

1. `orchestrator` 输出工具调用决策  
2. `tool.registry` 校验工具是否已注册  
3. `tool.executor` 校验输入 schema  
4. 具体 Tool Adapter 执行  
5. `ToolResult` 标准化输出  
6. `application` / `orchestrator` 写入 `tool_calls`

### 5.5 记忆写入链路

1. `orchestrator` 或 `rag` 提交候选记忆  
2. `MemoryService` 判断是短期记忆还是长期记忆  
3. 长期记忆检查显式性、置信度、冲突  
4. 持久化到 `user_memories`  
5. 会话摘要更新到 `session_summaries`

### 5.6 模型调用链路

1. 业务模块调用 `ModelService`  
2. `ModelService` 装配 Prompt、模型参数、结构化输出约束  
3. `ModelGatewayClient` 发送请求到模型网关  
4. 模型网关完成路由、鉴权、限流、配额、审计  
5. 供应商响应返回  
6. `ModelService` 统一封装响应并记录 usage / latency / cost  

---

## 6. 配置与环境设计

### 6.1 配置文件分层

固定使用：

- `application.yml`
- `application-local.yml`
- `application-dev.yml`
- `application-prod.yml`

### 6.2 建议配置树

```yaml
spring:
  application:
    name: foodmate
  datasource:
    url: jdbc:postgresql://localhost:5432/foodmate
    username: foodmate
    password: ${DB_PASSWORD}
  data:
    redis:
      host: localhost
      port: 6379

foodmate:
  model:
    default-chat-model: qwen-plus
    default-embed-model: text-embedding-v4
    timeout-ms: 15000
  gateway:
    base-url: http://model-gateway.internal
    api-key: ${MODEL_GATEWAY_KEY}
  rag:
    top-k: 12
    rerank-top-n: 5
    collection: foodmate_knowledge
  milvus:
    host: localhost
    port: 19530
    token: ${MILVUS_TOKEN}
  mcp:
    read-timeout-ms: 5000
  sql-agent:
    readonly-enforced: true
    max-rows: 500
  tool:
    enabled:
      calculator: true
      database-query: true
  audit:
    enabled: true
```

### 6.3 配置职责

| 配置域 | 作用 |
|---|---|
| `foodmate.model` | 模型默认参数 |
| `foodmate.gateway` | 模型网关连接与鉴权 |
| `foodmate.rag` | 检索参数 |
| `foodmate.milvus` | Milvus 接入 |
| `foodmate.mcp` | MCP 调用超时与重试 |
| `foodmate.sql-agent` | SQL Agent 只读策略和阈值 |
| `foodmate.tool` | 工具开关 |
| `foodmate.audit` | 审计与落库控制 |

---

## 7. 工程治理约束

### 7.1 Trace 规范

所有请求必须具备：

- `request_id`
- `trace_id`
- `session_id`
- `agent_run_id`

日志打印最少包含：

- 请求入口
- 模型调用
- 工具调用
- SQL Agent 调用
- 错误码
- 耗时

### 7.2 错误码分层

错误码前缀固定如下：

- `API_`：接口层错误
- `APP_`：应用层错误
- `RAG_`：检索错误
- `TOOL_`：工具错误
- `SQL_`：SQL Agent 错误
- `MODEL_`：模型调用错误
- `AUTH_`：权限错误

### 7.3 审计字段

需要审计的对象至少包含：

- `agent_runs`
- `tool_calls`
- `sql_query_audits`
- `model_usage_logs`

统一审计字段：

- `request_id`
- `trace_id`
- `session_id`
- `operator_id`
- `source`
- `status`
- `error_code`
- `created_at`

### 7.4 幂等、重试、超时

固定规则：

- 写接口必须支持幂等键
- 模型调用默认开启超时
- 工具调用只有幂等工具允许重试
- SQL Agent 失败可重试，但不得跳过 `SQL Guard`
- Worker 任务重试次数由 RocketMQ 统一控制

### 7.4.1 Repository 与软删除约束

固定规则：

- 所有 Repository 默认查询必须附带 `is_deleted = false`
- 只有显式的管理接口查询才允许绕过软删除条件
- 业务代码禁止手写“全量查”来跳过软删除
- `DELETE` 操作统一走软删除更新，不允许默认物理删除
- 恢复操作必须记录 `operator_id`、`request_id`、`trace_id`

推荐抽象：

- `SoftDeleteRepositorySupport`
- `SoftDeleteQuerySpec`
- `RestoreCommandHandler`

### 7.5 Prompt 工程化目录

```text
prompts/
  ├── system/
  ├── router/
  ├── planner/
  ├── query-understanding/
  ├── tool/
  ├── sql-agent/
  ├── validator/
  └── composer/
```

Prompt 加载规则：

- Prompt 不写死在 Java 代码常量里
- 每个 Prompt 带 `name`、`version`、`owner`
- 变更必须可回滚

---

## 8. 模块化单体到微服务的映射

### 8.1 目标映射

| 当前模块 | 未来服务 | 拆分时机 |
|---|---|---|
| `foodmate-api` | `api-bff` | 前后端迭代频率明显分离时 |
| `foodmate-orchestrator` | `agent-orchestrator` | 编排逻辑需要独立扩容时 |
| `foodmate-rag` | `knowledge-service` | 检索负载和索引任务持续增长时 |
| `foodmate-tool` + `foodmate-sql-agent` | `tool-service` | 工具数量和数据源种类显著增长时 |
| `foodmate-worker` | `worker-service` | 异步任务压缩主实例资源时 |
| `foodmate-gateway-client` | `model-gateway` | 需要独立治理模型路由和配额时 |

### 8.2 拆前条件

每次拆服务之前必须满足：

- 边界已经稳定 2 个迭代以上
- 接口已经标准化
- 日志、指标、审计链路完整
- 调用量足够支撑拆分收益

### 8.3 拆后边界

拆分后固定原则：

- `api-bff` 不直接访问 Milvus 和模型供应商
- `agent-orchestrator` 不直接持有供应商 SDK
- `knowledge-service` 只负责查询理解、检索、引用
- `tool-service` 只暴露工具执行能力
- `model-gateway` 只负责路由与治理，不承接业务语义

---

## 9. 第一版实施顺序

建议按下面顺序真正搭工程：

1. 创建根 `pom.xml` 与 13 个模块
2. 先把 `bootstrap`、`api`、`application`、`shared` 跑通
3. 搭 `model`、`gateway-client`、`orchestrator` 主链
4. 搭 `rag` 与 `tool` 的 P0 能力
5. 搭 `sql-agent` 的只读链路
6. 搭 `infra` 中的 PostgreSQL、Redis、Milvus、RocketMQ、MinIO 接入
7. 最后补 `worker`

如果第一版只做 MVP，可以优先交付：

- `foodmate-bootstrap`
- `foodmate-api`
- `foodmate-application`
- `foodmate-orchestrator`
- `foodmate-rag`
- `foodmate-tool`
- `foodmate-model`
- `foodmate-gateway-client`
- `foodmate-infra`
- `foodmate-shared`

`foodmate-sql-agent` 和 `foodmate-worker` 可以在第二阶段增强。

---

## 10. 最终落地原则

这份工程骨架文档最终只服务一件事：  
**让 FoodMate 的 Java 工程一开始就按正确边界落地，先以模块化单体快速交付，再平滑演进到服务化，而不是反复推倒重来。**

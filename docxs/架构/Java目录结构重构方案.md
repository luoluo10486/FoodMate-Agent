# FoodMate Java 目录结构重构方案（最终版）

版本：v2.1
维护基线：2026-08-02
适用基线：当前工作树（包含尚未提交的 nutrition、admin detail、knowledge ingestion 代码）
对应总设计：[Java 控制面工程设计](./Java控制面工程设计.md)
对应架构总览：[架构总览](./架构总览.md)
权威优先级：实际代码、模块依赖、契约和 ADR 高于本文；本文是实施指南，不替代架构约束。

---

## 0. 最终结论

FoodMate 采用“多模块横向分层、模块内部按业务领域分包”的结构：

```text
api -> application -> shared
infra -> application/shared
bootstrap -> api/application/infra/shared
```

其中：

- `foodmate-api` 只负责 HTTP/SSE 参数转换、鉴权和响应包装。
- `foodmate-application` 负责用例编排、事务和业务流程，定义入站用例接口以及必要的出站边界。
- `foodmate-infra` 负责数据库、消息、缓存、对象存储和外部客户端的具体实现。
- `foodmate-shared` 只放跨模块共享的契约、错误码、基础类型和小型工具。
- `foodmate-bootstrap` 负责启动、配置、Bean 装配和运行时实现选择。

本次重构的最终规则如下：

1. application 的业务用例统一采用 `XxxService` + `XxxServiceImpl` 双件套，但不要求每个普通协作者都机械增加 `Impl`。
2. `XxxService` 是入站用例接口；`XxxRepository` 是持久化出站接口；`XxxPort` 是外部能力出站接口；三者不能混用。
3. infra 通过 `XxxAdapter`、`XxxClient`、`XxxPublisher`、`XxxConsumer`、`XxxStorage` 等职责名称表达具体技术实现。
4. Mapper 只负责 SQL 映射，Po 只负责持久化模型，API DTO、application 结果和领域对象不得跨边界复用。
5. `Domain` 表示业务模型和纯业务规则，不是 `Repository` 或 `Port` 的另一种命名；只有出现真实领域行为时才建立独立领域模块。
6. 先完成依赖反转和类型边界，再进行大规模重命名；第一阶段不改变数据库表、SQL 语义和公开 HTTP/SSE 字段。
7. API 请求和响应使用明确的 `Request/Response` DTO；业务接口禁止以 `Map` 作为前端请求体或响应体，动态 SQL 行、消息 payload 等内部动态结构单独例外。

本方案不是单纯的目录移动。任何包路径变化都必须同步处理 Java 包名、Spring 扫描、模块依赖、测试、ArchUnit 和配置类。

## 1. 当前项目事实

### 1.1 Maven 模块

根 [pom.xml](../../pom.xml) 当前声明 6 个模块：

```text
foodmate-shared
foodmate-gateway-client
foodmate-infra
foodmate-application
foodmate-api
foodmate-bootstrap
```

`foodmate-domain`、`foodmate-model`、`foodmate-orchestrator`、`foodmate-rag`、`foodmate-sql-agent`、`foodmate-tool`、`foodmate-worker` 当前没有形成可独立验收的 Maven 模块，不应仅为了目录完整而加入根 POM。

短期保留 `foodmate-gateway-client`，先完成其中接口、协议、HTTP/MQ 实现的拆分；只有当业务代码和运行时配置不再依赖该模块时，才评估删除或收缩该模块。

### 1.2 当前业务领域

目标结构必须覆盖当前工作树中的以下业务：

| 领域 | 当前职责 | 改造重点 |
|---|---|---|
| `account` | 用户认证、个人资料、隐私、后台管理、后台详情 | Service/Impl、账户持久化端口、后台查询边界 |
| `conversation` | Session、Message、摘要和记忆 | 从 account/runtime 中拆出用例和持久化边界 |
| `knowledge` | 知识库、文档、分块、异步摄入任务 | 文档存储、摄入流程和外部能力端口 |
| `nutrition` | 饮食日志、营养分析、膳食计划、购物清单 | 区分 CRUD 用例和真实营养业务规则 |
| `runtime` | AgentRun、Dispatch、Event、预算、取消、恢复、Outbox、DLQ、Proposal、Tool Gateway | 拆分 service、processor、messaging、recovery 和出站端口 |

### 1.3 当前结构问题

| # | 问题 | 当前表现 | 优先级 |
|---|---|---|---|
| 1 | application 用例接口和实现未统一分离 | `UserAccountService`、`RuntimeGatewayService` 等仍可能是具体类 | 高 |
| 2 | Store 与 Mapper 直接耦合 | 多个 infra Mapper 直接继承 application 的 `XxxStore` | 最高 |
| 3 | application 直接感知基础设施 | 使用 gateway-client、MinIO、Redis、RocketMQ 或 MyBatis 类型 | 最高 |
| 4 | `Store` 名称掩盖真实职责 | 持久化、缓存、消息、运行时客户端和流程协作者混用 | 高 |
| 5 | gateway-client 职责过多 | Runtime HTTP 客户端、MQ 客户端、消费者容器和消息契约混在一个模块 | 高 |
| 6 | runtime 内部职责集中 | command、event、recovery、messaging、tool、memory 与业务服务平铺 | 中 |
| 7 | API DTO 边界不稳定 | controller 内部 record、Store record 或动态 Map 可能跨层传播 | 中 |
| 8 | 空目录与候选模块容易被误认为正式架构 | 多个 `foodmate-*` 目录没有进入根 POM | 低 |

### 1.4 第一阶段必须保持的行为

- 数据库表结构、字段语义、SQL 查询条件和软删除行为保持不变。
- 已有 HTTP/SSE 路径、请求字段、响应字段和错误码保持兼容。
- `local-stub` 只能在明确的 Profile 下生效，生产环境不能静默降级到 stub。
- RocketMQ 的 ACK、RETRY、REJECT 语义保持不变。
- Runtime 的取消、恢复、预算、Outbox、DLQ 和幂等行为保持不变。
- nutrition、admin detail、knowledge ingestion 等当前工作树代码必须纳入迁移，不得因目录重构遗漏。

## 2. 目标模块边界

### 2.1 稳定模块

```text
FoodMate
├── foodmate-shared          # 跨模块契约、错误码、基础类型、小型工具
├── foodmate-gateway-client  # 迁移期间保留；最终仅保留必要的传输协议或被拆除
├── foodmate-infra           # 持久化、消息、缓存、对象存储、外部客户端适配器
├── foodmate-application     # 用例、事务、领域协作和出站边界
├── foodmate-api             # HTTP/SSE、鉴权和响应包装
└── foodmate-bootstrap       # 启动、配置和运行时装配
```

### 2.2 依赖规则

```text
api -> application -> shared
infra -> application/shared
bootstrap -> api/application/infra/shared
```

必须满足：

- `application` 不依赖 `infra`、`gateway-client` 的具体实现、MyBatis、MinIO、Redis、RocketMQ 或其他基础设施 SDK。
- `api` 不依赖 `infra`，不直接使用 Po、Mapper、客户端和消息 SDK。
- `shared` 不依赖任何业务模块、application、api、infra 或具体基础设施。
- `infra` 可以依赖 application 中定义的 `Repository/Port` 接口，但不能反向要求 application 依赖 infra 实现。
- `bootstrap` 是运行时实现选择、配置和 Bean 装配的唯一入口，不承载业务流程。
- `gateway-client` 处于迁移期时不得新增业务用例依赖；跨模块协议优先迁入 shared，具体 HTTP/MQ 实现迁入 infra。

### 2.3 可选的 foodmate-domain

当前不为 `foodmate-domain` 创建空模块。满足以下条件后才建立：

- 至少存在一个稳定的聚合或领域状态机。
- 存在实体不变量、跨实体规则、领域事件或复杂策略。
- 该领域模型需要被多个 application 用例共享。
- 可以明确 domain 与 application、infra 的依赖方向。

建立后建议依赖关系为：

```text
foodmate-domain -> foodmate-shared
foodmate-application -> foodmate-domain/shared
```

domain 只放实体、聚合、值对象、领域事件、状态机、不变量以及无 I/O 的 `DomainService`、`Policy`、`Specification`、`Factory`。不放 Controller、`XxxServiceImpl`、Repository、Mapper、Po、Request、Response、Spring Bean 或外部客户端。

## 3. 目标目录结构

### 3.1 foodmate-application

```text
com.foodmate.application
├── account/
│   ├── service/
│   │   ├── UserAccountService
│   │   ├── AdminDashboardService
│   │   ├── AdminDetailService
│   │   ├── AdminManagementService
│   │   └── PersonalDataService
│   ├── service/impl/
│   │   ├── UserAccountServiceImpl
│   │   ├── AdminDashboardServiceImpl
│   │   ├── AdminDetailServiceImpl
│   │   ├── AdminManagementServiceImpl
│   │   └── PersonalDataServiceImpl
│   └── port/out/
│       ├── UserAccountRepository
│       ├── AdminDashboardRepository
│       └── ObjectStoragePort
├── conversation/
│   ├── service/
│   ├── service/impl/
│   └── port/out/
│       ├── SessionRepository
│       ├── MessageRepository
│       ├── ConversationSummaryRepository
│       └── ConversationMemoryPort
├── knowledge/
│   ├── service/
│   ├── service/impl/
│   └── port/out/
│       ├── KnowledgeRepository
│       ├── KnowledgeIngestionPort
│       └── ObjectStoragePort
├── nutrition/
│   ├── service/
│   ├── service/impl/
│   └── port/out/
│       ├── NutritionRepository
│       └── NutritionAnalysisPort
└── runtime/
    ├── service/
    ├── service/impl/
    ├── processor/
    ├── recovery/
    ├── admission/
    ├── command/
    ├── event/
    ├── messaging/
    └── port/out/
        ├── RuntimeClientPort
        ├── MessagePublisherPort
        ├── MessageConsumerPort
        ├── AdmissionPort
        └── RuntimeRepository
```

目录只是承载职责，不要求每个领域一开始就创建全部子目录。没有实际类时不创建空包。

### 3.2 foodmate-infra

```text
com.foodmate.infrastructure
├── persistence/
│   ├── account/
│   │   ├── po/
│   │   ├── mapper/
│   │   └── adapter/
│   ├── conversation/
│   │   ├── po/
│   │   ├── mapper/
│   │   └── adapter/
│   ├── knowledge/
│   │   ├── po/
│   │   ├── mapper/
│   │   └── adapter/
│   ├── nutrition/
│   │   ├── po/
│   │   ├── mapper/
│   │   └── adapter/
│   └── runtime/
│       ├── po/
│       ├── mapper/
│       └── adapter/
├── client/
│   └── runtime/
├── messaging/
│   └── rocketmq/
├── storage/
│   └── minio/
├── cache/
│   └── redis/
├── audit/
└── config/
```

规则：

1. `Mapper` 只负责 MyBatis SQL 和持久化行映射，不能继承 application 的 `Store/Repository` 接口。
2. `Po` 只存在于 infra；application 使用领域对象、命令、结果或专用 DTO。
3. `Adapter` 实现 application 的 `Repository/Port`，负责调用 Mapper、客户端或 SDK，以及完成类型转换。
4. `Client` 表示外部客户端，`Publisher`/`Consumer` 表示消息职责，`Storage`/`Cache` 表示存储和缓存职责；这些是 infra 实现名称，不直接暴露给 API。
5. `@MapperScan`、事务、SQL 注解、Flyway、软删除和 local-stub 配置集中在 infra 或 bootstrap。

### 3.3 foodmate-api

```text
com.foodmate.api
├── controller/
│   ├── account/
│   ├── conversation/
│   ├── knowledge/
│   ├── nutrition/
│   └── runtime/
├── request/
│   ├── account/
│   ├── conversation/
│   ├── knowledge/
│   ├── nutrition/
│   └── runtime/
├── response/
│   ├── account/
│   ├── conversation/
│   ├── knowledge/
│   ├── nutrition/
│   └── runtime/
├── sse/
├── advice/
└── filter/
```

Controller 只做参数校验、鉴权上下文提取、调用 `XxxService` 和响应包装，不写 SQL、事务编排或外部客户端调用。

API DTO 规则：

- `api/request` 放前端输入对象，例如 `LoginRequest`、`SessionRequest`、`RuntimeProposalRequest`。
- `api/response` 放前端输出对象，例如 `AuthResponse`、`SessionResponse`、`ChatRunResponse`、`AdminDashboardResponse`。
- `AuthResponse`、`SessionResponse`、`ChatRunResponse` 等已有响应名称保留，只从 Controller 内嵌 `record` 迁移为独立类型。
- Controller 不再声明业务 Request/Response 内嵌 `record`，也不使用 `@RequestBody Map<String, Object>`。
- 普通业务结果使用明确的 Response record；后台统计、导出任务、状态操作等不能返回 `Map.of(...)`。
- `Map` 只允许留在确实动态的内部结构，例如动态 SQL 查询行、Runtime 消息 payload、Redis Hash 或错误扩展详情；这些结构不能直接作为普通前端 DTO 使用。

当前前端 JSON 由 Spring MVC + Jackson 绑定到 `@RequestBody XxxRequest`，路径、查询参数、请求头、Cookie 和 multipart 文件分别使用 `@PathVariable`、`@RequestParam`、`@RequestHeader`、`@CookieValue` 和 `@RequestPart`。字段校验由 Request DTO 上的 Jakarta Validation 注解完成。

### 3.4 foodmate-shared

```text
com.foodmate.shared
├── account/
│   ├── UserRole
│   ├── UserStatus
│   ├── SessionStatus
│   ├── SessionMode
│   ├── MessageRole
│   ├── ToolStatus
│   ├── KnowledgeDocumentStatus
│   └── RestorableResourceType
├── api/
├── error/
├── runtime/
└── trace/
```

跨模块共享的稳定业务枚举放在 `shared`，保留对外 JSON 和持久化使用的小写 code；API Request 可以直接绑定这些枚举，application 在调用 infra 端口时再转换为数据库或消息协议值。运行时状态、事件类型和 Redis/SQL 内部状态只在所属模块维护，不创建一个跨业务复用的通用 `Status`。

允许放置：

- 跨模块 HTTP/SSE 契约和 Runtime wire contract。
- 错误码、稳定异常标识、ID、分页、trace 类型。
- 被多个模块共同使用的小型序列化工具和不可变基础类型。

禁止放置：

- 用户、营养、知识、会话或 AgentRun 的业务 Service、Repository、Po。
- API 专用 Request/Response。
- Spring、MyBatis、MinIO、Redis、RocketMQ 或其他业务模块依赖。

### 3.5 foodmate-bootstrap

```text
com.foodmate.bootstrap
├── FoodMateApplication
├── config/
│   ├── CoreConfiguration
│   ├── MinioConfiguration
│   ├── RedisConfiguration
│   ├── RuntimeClientConfiguration
│   ├── RuntimeRocketMqConfiguration
│   └── SchedulingConfiguration
└── test/
    ├── architecture/
    ├── e2e/
    └── persistence/
```

bootstrap 只负责启动和装配。HTTP/MQ/Local stub 的实现选择集中在配置类中，不在 bootstrap 中实现业务逻辑、SQL 或消息处理流程。

## 4. 命名和职责规范

### 4.1 Service/Impl 双件套

| 类型 | 推荐命名 | 所在位置 | 责任 |
|---|---|---|---|
| application 入站用例接口 | `XxxService` | `application/<feature>/service` | 描述系统对外提供的用例能力 |
| application 用例实现 | `XxxServiceImpl` | `application/<feature>/service/impl` | 事务、鉴权后的校验、流程编排、调用出站边界 |
| 领域服务 | `XxxDomainService` | domain 或 feature/domain | 纯业务规则，无 Spring、无 I/O |
| 普通协作者 | `XxxProcessor`、`XxxReconciler`、`XxxHandler` | application 对应职责包 | 单一流程协作，不为形式统一而创建 Impl |

规则：

- API 和其他调用方只依赖 `XxxService`，不依赖 `XxxServiceImpl`。
- `XxxServiceImpl` 使用构造函数注入，不通过静态状态或隐式获取 Bean。
- `XxxServiceImpl` 不直接引用 Mapper、Po、MyBatis、MinIO、Redis、RocketMQ 或外部 HTTP SDK。
- 不把 MyBatis-Plus 的 `ServiceImpl<Mapper, Po>` 作为 application 服务基类。
- 一个 Service 只承载一个清晰的用例边界；查询和写入可以拆成不同 Service，不为了数量统一强行合并。

### 4.2 Repository、Port 和其他边界名称

`Port` 是模块边界接口，不是网络端口。它用于表达调用方需要另一层提供的能力。命名应根据方向和职责选择：

| 类型 | 推荐命名 | 定义位置 | 实现位置 | 适用范围 |
|---|---|---|---|---|
| 持久化出站接口 | `XxxRepository` | application | infra adapter | 聚合、实体或稳定数据集合的读写 |
| 外部客户端端口 | `XxxClientPort` 或 `XxxPort` | application | infra client/adapter | Runtime、HTTP、模型、第三方服务 |
| 对象存储端口 | `ObjectStoragePort` | application | infra storage | MinIO、S3、文件存储 |
| 缓存端口 | `XxxCachePort` | application | infra cache | Redis、缓存失效和读取 |
| 消息发布端口 | `MessagePublisherPort` | application | infra publisher | MQ 发布、Outbox 投递 |
| 消息消费端口 | `MessageHandler` 或 `MessageConsumerPort` | application/shared | infra consumer | 传输无关的消息处理契约 |
| 具体外部客户端 | `RuntimeClient`、`EmbeddingClient` | infra | infra | 对接具体 SDK 或 HTTP |
| 具体消息实现 | `RocketMqMessagePublisher`、`RocketMqConsumer` | infra | infra | RocketMQ 传输层 |
| 适配器 | `XxxAdapter`、`XxxRepositoryAdapter` | infra | infra | 将端口调用转换为技术实现 |
| SQL 接口 | `XxxMapper` | infra | infra | MyBatis SQL 映射 |
| 持久化模型 | `XxxPo` | infra | infra | 数据库行和持久化字段 |

以下规则必须执行：

1. 只有真正代表持久化边界时才使用 `Repository`。
2. 消息、缓存、对象存储、Runtime 客户端不要为了统一而命名为 `Repository`。
3. application 如果需要依赖 `Client`、`Publisher`、`Consumer` 或 `Storage`，应在 application 定义对应端口，具体实现留在 infra。
4. 不为普通内部方法创建无意义的 Port；有多实现、测试替身、跨模块契约或技术隔离价值时才创建。
5. 新代码禁止继续使用含义不明的 `Store` 作为通用出站接口。迁移期间保留的旧 `Store` 必须登记归属和最终目标名称。

### 4.3 Domain 的边界

`Domain` 不是接口后缀，而是业务模型和业务规则的归属：

| 领域内容 | 推荐类型 | 说明 |
|---|---|---|
| 业务身份和值 | `XxxId`、`XxxStatus`、`XxxValue` | 不可变值和业务语义 |
| 聚合和实体 | `Xxx`、`XxxAggregate`、`XxxEntity` | 状态、不变量、生命周期 |
| 领域规则 | `XxxPolicy`、`XxxSpecification`、`XxxDomainService` | 不依赖数据库、MQ、缓存或 HTTP |
| 领域事件 | `XxxOccurred`、`XxxCompleted` | 已发生的业务事实，不等同于 MQ DTO |
| 对象创建 | `XxxFactory` | 负责创建满足不变量的领域对象 |

领域对象不负责事务、重试、网络调用、数据库查询、消息 ACK 或 Spring Bean 装配。`XxxDomainService` 与 `XxxServiceImpl` 必须分开：前者执行纯业务规则，后者编排 application 用例。

## 5. 各业务模块的改造方案

### 5.1 account

目标结构：

```text
application/account/
├── service/
│   ├── UserAccountService
│   ├── AdminDashboardService
│   ├── AdminDetailService
│   ├── AdminManagementService
│   └── PersonalDataService
├── service/impl/
└── port/out/
    ├── UserAccountRepository
    ├── AdminDashboardRepository
    ├── AdminDetailRepository
    └── PersonalDataRepository
```

改造要求：

- `UserAccountService`、`PersonalDataService`、`AdminDashboardService`、`AdminManagementService` 和 `AdminDetailService` 拆为接口和 `Impl`。
- `AdminDetail` 必须归入 account 的后台管理能力，不单独创建顶层模块。
- 用户认证、个人资料、后台查询使用各自的 application 结果，不直接返回 Po 或 Mapper record。
- infra 的 account Mapper 只继承 MyBatis-Plus 的持久化基类，application 端口由 adapter 实现。
- 用户头像、文件或隐私相关外部能力通过 `ObjectStoragePort`、`PrivacyPort` 等职责接口访问。

### 5.2 conversation

conversation 负责 Session、Message、摘要和记忆，不能因为历史代码位于 account 或 runtime 就继续混放。

建议拆分为：

```text
application/conversation/
├── service/
│   ├── ConversationService
│   ├── ConversationMessageService
│   ├── ConversationSummaryService
│   └── ConversationMemoryService
├── service/impl/
└── port/out/
    ├── SessionRepository
    ├── MessageRepository
    ├── ConversationSummaryRepository
    └── ConversationMemoryPort
```

改造要求：

- Session、Message 的持久化接口归 conversation，不再挂在 `UserAccountStore` 下。
- `MemoryCandidateService`、`SessionSummaryService` 按真实事务和用例边界迁入 conversation；测试和出站端口一起迁移。
- 记忆若由 Redis、向量库或其他外部系统提供，应使用职责明确的 Port，不要用 `MemoryRepository` 掩盖外部能力。
- conversation 的摘要结果、记忆候选和 API 响应分别建模，不复用数据库 Po。

### 5.3 knowledge

knowledge 负责知识库、文档、分块和异步摄入流程：

```text
application/knowledge/
├── service/
│   ├── KnowledgeBaseService
│   ├── KnowledgeDocumentService
│   ├── KnowledgeChunkService
│   └── KnowledgeIngestionService
├── service/impl/
└── port/out/
    ├── KnowledgeRepository
    ├── KnowledgeIngestionPort
    └── ObjectStoragePort
```

改造要求：

- 文档、知识库、分块的数据库访问使用对应 Repository，不把 `Mapper` 直接暴露给 application。
- 文件上传和下载通过 `ObjectStoragePort`，MinIO 实现放在 infra。
- 摄入任务的调度、解析、分块、增强、索引等步骤按处理职责拆分；只有跨模块或需要替身的能力才定义接口。
- ingestion 的状态、结果和节点上下文使用 application/domain 类型，不直接使用 MQ 或数据库结构。
- ingestion 的异步消息 DTO 与领域事件区分，消息重试和任务状态更新保持现有语义。

### 5.4 nutrition

nutrition 负责饮食记录、营养分析、膳食计划和购物清单：

```text
application/nutrition/
├── service/
│   ├── NutritionLogService
│   ├── NutritionAnalysisService
│   ├── MealPlanService
│   └── ShoppingListService
├── service/impl/
└── port/out/
    ├── NutritionRepository
    ├── NutritionAnalysisPort
    └── FoodCatalogPort
```

改造要求：

- `NutritionStore` 先判断其实际职责，再决定拆成 `NutritionRepository`、`NutritionAnalysisPort` 或其他职责接口。
- 记录保存、分析计算、计划生成、购物清单编排不要集中到一个超大 Service。
- 只有营养约束、摄入计算、计划规则等形成稳定不变量后，才提取 `NutritionDomainService`、Policy 或 Value Object。
- 外部营养数据、模型分析或食品目录通过 Port 访问，具体客户端放 infra。
- nutrition 的 API Request/Response 不跨到 infra，也不以数据库 Po 直接返回。

### 5.5 runtime

runtime 是当前最需要内部拆分的模块，目标结构如下：

```text
application/runtime/
├── service/
│   ├── AgentRunCommandService
│   ├── RuntimeGatewayService
│   ├── RuntimeRecoveryService
│   ├── RuntimeCancellationService
│   ├── RuntimeDlqService
│   └── BudgetExtensionService
├── service/impl/
├── processor/
│   ├── RuntimeEventMessageProcessor
│   └── RuntimeProposalMessageProcessor
├── admission/
├── recovery/
├── command/
├── event/
├── messaging/
└── port/out/
    ├── RuntimeClientPort
    ├── MessagePublisherPort
    ├── MessageConsumerPort
    ├── AdmissionPort
    └── RuntimeRepository
```

改造要求：

- 用例类拆成 `XxxService` 和 `XxxServiceImpl`；`Processor`、`Publisher`、`Reconciler` 保持职责类，不机械创建 Impl。
- `AgentRunCommandStore`、`CancellationStore`、`BudgetExtensionStore`、`AdmissionReconciliationStore`、`RuntimeRecoveryStore`、`SessionSummaryStore`、`ProtocolAuditStore` 逐一判断是否为持久化边界，确认后改为对应 Repository。
- `DispatchOutbox`、`ProposalInbox`、`Dlq` 属于可靠消息和持久化流程，应使用 `OutboxRepository`、`InboxRepository`、`DeadLetterRepository` 或更具体的职责名称，不笼统命名为普通 Repository。
- `RuntimeGatewayStore`、`ToolGatewayStore` 如果实际代表外部调用，应改为 `RuntimeClientPort`、`ToolGatewayPort` 等外部能力端口；如果只读写数据库，则改为对应 Repository。
- event、command、recovery、messaging 的 DTO、持久化模型和 application 结果分开定义。
- Runtime 客户端、RocketMQ consumer/publisher、Admission 实现放在 infra，application 只依赖端口和传输无关的处理契约。

### 5.6 gateway-client

当前 `foodmate-gateway-client` 同时包含 Runtime HTTP 客户端、RocketMQ 客户端、消费者容器、消息处理契约和配置对象，必须拆分职责：

```text
application/runtime/port/out/
├── RuntimeClientPort
├── MessagePublisherPort
└── MessageConsumerPort

shared/runtime/
├── RunCommand
├── RunEvent
├── MqConsumeDecision
└── 其他跨模块传输契约

infra/client/runtime/
├── V1HttpRuntimeClient
├── V1RocketMqRuntimeClient
└── UnavailableRuntimeClient

infra/messaging/rocketmq/
├── RocketMqConsumerContainer
├── RocketMqMessagePublisher
└── RocketMqConsumer
```

拆分规则：

- application 只依赖 `RuntimeClientPort` 和消息处理契约，不依赖 `GatewayClient`、RocketMQ 设置类或传输容器。
- shared 只保留确实被多个模块使用的稳定协议，不把客户端实现放入 shared。
- infra 负责把 RocketMQ 元数据转换成传输无关的处理上下文，并把处理结果转换为 ACK、RETRY 或 REJECT。
- 当全量引用迁出后，删除或收缩 `foodmate-gateway-client`；删除前必须通过编译、ArchUnit、启动和消息集成测试。

## 6. Store、Mapper 和 Repository 的迁移规则

### 6.1 Store 分类

不一次性按名称批量重命名。每个 `Store` 必须根据实际实现和调用方向分类：

| 实际职责 | 目标接口 | infra 实现 |
|---|---|---|
| 数据库聚合或稳定数据集合读写 | `XxxRepository` | `XxxRepositoryAdapter` |
| 外部 Runtime/HTTP/模型能力 | `XxxPort` 或 `XxxClientPort` | `XxxClient` / `XxxAdapter` |
| Redis 或其他缓存 | `XxxCachePort` | `RedisXxxCache` |
| 对象存储 | `ObjectStoragePort` | `MinioObjectStorageAdapter` |
| MQ 发布 | `MessagePublisherPort` | `RocketMqMessagePublisher` |
| MQ 消费处理 | `MessageHandler` / `MessageConsumerPort` | `RocketMqConsumer` |
| Outbox、Inbox、DLQ 等可靠消息数据 | `OutboxRepository`、`InboxRepository`、`DeadLetterRepository` | 对应 adapter |
| 纯业务流程协作者 | `XxxProcessor`、`XxxReconciler`、`XxxPolicy` | application/domain 具体类 |

### 6.2 迁移前后示例

当前模式：

```text
application/account/UserAccountStore
        ▲
infra/persistence/account/UserAccountMapper extends UserAccountStore
```

目标模式：

```text
application/account/port/out/UserAccountRepository
        ▲
infra/persistence/account/adapter/UserAccountRepositoryAdapter
        │
infra/persistence/account/mapper/UserAccountMapper
        │
infra/persistence/account/po/UserPo
```

目标规则：

1. Mapper 不再实现或继承 application 端口。
2. Adapter 实现 application 端口，并集中完成 Po、持久化行、领域对象和 application 结果的转换。
3. Local stub 必须实现同一个 application 端口，且只在 local-stub Profile 下装配。
4. 旧 `Store` 在迁移期间可以作为临时兼容接口，但不得新增依赖；每个旧接口必须记录目标名称和删除条件。
5. 不使用 `Map<String,Object>` 作为默认跨层结构；确实是动态内容时才允许在明确边界使用。

## 7. 迁移顺序

| 步骤 | 内容 | 主要范围 | 风险 |
|---|---|---|---|
| S0 | 冻结基线：盘点类、包、POM、依赖、测试、ArchUnit、local-stub、Mapper、SQL 和公开 DTO | 全项目 | 低 |
| S1 | 建立目标包目录并迁移纯路径，不改变行为；同步修改包名、扫描配置和测试 | account、conversation、knowledge、nutrition | 中 |
| S2 | 将 application 用例拆为 `XxxService` + `XxxServiceImpl`；API 和测试替身只依赖接口 | 各业务 Service | 中 |
| S3 | 按职责将 Store 分类为 Repository、Port、Cache、Processor、MessageHandler 等，定义 application 出站边界 | account、conversation、runtime | 高 |
| S4 | Mapper 与 application 端口解耦，引入 Adapter、Po 和边界映射；迁移 local-stub | infra 全部持久化 | 高 |
| S5 | 拆分 gateway-client，将 HTTP/MQ 实现迁入 infra，协议迁入 shared，删除 application SDK 依赖 | runtime、gateway-client | 最高 |
| S6 | API Request/Response 按业务归档，消除 Po、Store record 和默认 Map 跨边界传播 | api/application | 高 |
| S7 | 完成 runtime 内部 command、event、recovery、messaging、tool、admission 分组 | runtime | 高 |
| S8 | 根据真实业务不变量提取 domain；清理空目录，确认是否删除或收缩 gateway-client | domain、全项目 | 高 |

### 7.1 推荐实施顺序

1. `account`：边界清晰、可先验证 Service/Impl 和 Repository Adapter 模式。
2. `conversation`：同步拆出 Session/Message，避免 account 和 runtime 继续承载会话职责。
3. `knowledge`：同时验证 ObjectStoragePort、异步摄入和任务状态边界。
4. `nutrition`：先完成 application/infra 分层，再判断是否需要真实领域模型。
5. `runtime`：最后处理，按 command、event、recovery、messaging、admission、tool 分组，避免一次性迁移全部类。

每个业务模块完成一个阶段后独立提交和验证，不把所有重命名、依赖调整和行为变更合并为一次大迁移。

## 8. 验收标准

### 8.1 模块依赖

- `application` 不依赖 infra 包、gateway-client 具体实现、MyBatis、MinIO、Redis、RocketMQ 或外部 HTTP SDK。
- `api` 不依赖 infra、Po、Mapper、客户端、消息 SDK 或 `service.impl`。
- `api` 只依赖 `XxxService` 用例接口以及自身 Request/Response/SSE 类型。
- `infra` 可以实现 application 端口，但 application 不引用 infra 实现。
- `shared` 不依赖业务模块和基础设施。
- `domain`（如果建立）不依赖 Spring、api、infra、Mapper、Po 或外部客户端。
- bootstrap 是唯一负责运行时实现选择和 Bean 装配的模块。

### 8.2 命名和结构

- 新增 application 用例遵循 `XxxService` + `XxxServiceImpl`。
- 新增代码不使用含义不明的 `XxxStore` 作为通用出站接口。
- Mapper 不继承 application 的 `Store/Repository`；Po 不跨出 infra。
- `Repository` 只用于持久化边界；外部能力使用 `Port` 或具体职责名称。
- API 业务请求和响应不使用 `Map`；新增 `@RequestBody Map` 或 `ApiResponse<Map<...>>` 视为结构验收失败。
- `DomainService` 不包含 I/O、事务、Spring Bean 或外部 SDK。
- account、conversation、knowledge、nutrition、runtime 均有清晰的 service、port、adapter 归属。
- 不创建没有实际类、规则或依赖边界支撑的空包和空模块。

### 8.3 行为和契约

- 第一阶段数据库表结构、SQL 语义、软删除、公开 HTTP/SSE 字段和错误码不变。
- Runtime 的恢复、取消、预算、Outbox、DLQ、幂等和消息确认语义不变。
- local-stub 不会在生产 Profile 下生效。
- 对外 DTO 的字段、校验、序列化和错误响应有兼容性测试。
- Store 到 Repository/Port 的迁移有明确的 adapter 和类型转换，不通过强制类型转换绕过边界。

### 8.4 测试和验证

- 每个阶段运行受影响模块测试。
- 提交前运行 `mvnw.cmd verify`。
- 更新并运行 ArchUnit，验证模块依赖、禁止 application 使用基础设施 SDK、禁止 API 使用 infra 和 Po/Mapper。
- application 单元测试覆盖 `XxxServiceImpl`，测试替身实现 application 端口。
- API 测试只验证 Controller 到 Service 接口的调用、鉴权和 DTO 转换。
- infra 运行 Mapper、Repository Adapter、Flyway、软删除、local-stub 和数据库集成测试。
- gateway-client 拆分后运行 HTTP、MQ、ACK/RETRY/REJECT 和 Runtime 恢复类集成测试。
- bootstrap 运行生产配置矩阵、local-stub Profile、启动装配和 E2E 测试。

## 9. 完成定义

本次重构完成的标志不是目录看起来整齐，而是以下条件同时满足：

1. application、api、infra、shared、bootstrap 的依赖方向稳定且有 ArchUnit 保护。
2. application 用例均可通过 `XxxService` 接口调用，具体实现集中在 `service/impl`。
3. Store 已完成职责分类；新增代码不再以 Store 作为模糊的通用边界。
4. Mapper、Po、Adapter、Repository/Port 和 API DTO 的边界清晰，类型不跨层泄漏。
5. Runtime、knowledge、nutrition、account、conversation 的业务职责归属明确。
6. gateway-client 不再承载混合业务、传输和基础设施职责；无引用时完成删除或收缩。
7. 真实领域规则才进入 domain；没有真实领域模型时不保留空 domain 模块。
8. 所有阶段性测试、`mvnw.cmd verify` 和架构检查通过，且既有业务行为保持兼容。

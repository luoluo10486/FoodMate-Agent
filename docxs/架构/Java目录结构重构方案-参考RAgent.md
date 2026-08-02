# FoodMate Java 目录结构重构方案（参考 RAgent）

版本：v1.1
维护基线：2026-08-01
适用基线：当前工作树（包含尚未提交的 nutrition、admin detail、knowledge ingestion 代码）
参考项目：[RAgent](D:\develop\RAgent\ragent)（com.nageoffer.ai.ragent）
对应总设计：[Java 控制面工程设计](./Java控制面工程设计.md)
对应架构总览：[架构总览](./架构总览.md)
权威优先级：实际代码、模块依赖、契约和 ADR 高于本文；本文是实施指南，不替代架构约束。

---

## 0. 结论与范围

FoodMate 继续采用多模块横向分层：

```text
api -> application -> shared
infra -> application/shared
bootstrap -> api/application/infra/shared
```

其中 `application` 定义用例和出站端口，`infra` 实现数据库、消息、对象存储、缓存和 Runtime 客户端适配器，`api` 只负责 HTTP/SSE 边界，`bootstrap` 负责 Bean 装配和启动配置。

本次重构借鉴 RAgent 的领域分包思想，但不照搬其单体结构：

1. application、api、infra 各自按领域分包，但不同层不混放职责。
2. 端口、持久化 Mapper、数据库适配器、API DTO 使用不同命名和类型，避免一套类型跨越所有边界。
3. 先处理真实存在的领域和依赖，不创建没有代码和规则支撑的空包、空模块。
4. 目录重构、依赖反转、持久化适配器、DTO/领域建模分阶段实施，不能全部标记为“低风险目录调整”。

本次第一阶段不修改数据库表和 SQL 语义。Mapper 解耦、对象映射、DTO 改造和领域建模属于后续阶段，必须单独验证。

## 1. RAgent 可借鉴与不可照搬的部分

### 1.1 可借鉴的原则

| 原则 | RAgent 做法 | FoodMate 采用方式 |
|---|---|---|
| 领域自包含 | 领域包内组织 controller、service、dao 等内容 | 每层按领域分包；application 端口与用例同域，infra 适配器与持久化同域 |
| 命名统一 | DO、VO、Request、Mapper 等术语稳定 | 使用 `Request/Response`、`Po/Mapper`、`Repository/Port`，按职责区分，不强行只保留一个后缀 |
| 横切能力独立 | framework 承载 cache、mq、trace 等能力 | shared 只放跨模块契约、错误码、基础类型和小型工具；具体 SDK 适配器放 infra |
| 领域包优先 | 用包承载领域，不为每个能力建立模块 | RAG、SQL Agent、Tool、Worker 暂不建 Maven 模块，待出现真实边界后再决定 |

### 1.2 不照搬的部分

- 不采用单体单模块，保留 api/application/infra/shared/bootstrap 的职责边界。
- 不引入 `Service/Impl` 双件套，application 用例类直接作为实现类。
- 不把 controller 放进 application，controller 继续归 api。
- 不把所有出站接口都叫 `Repository`，消息、缓存、对象存储和外部客户端使用更准确的 `Port` 或职责名称。

## 2. 当前项目事实与真正需要解决的问题

### 2.1 Maven 模块现状

根 [pom.xml](../../pom.xml) 当前已经声明 6 个 Maven 模块：

```text
foodmate-shared
foodmate-gateway-client
foodmate-infra
foodmate-application
foodmate-api
foodmate-bootstrap
```

`foodmate-domain`、`foodmate-model`、`foodmate-orchestrator`、`foodmate-rag`、`foodmate-sql-agent`、`foodmate-tool`、`foodmate-worker` 当前是空目录，不在根 POM 的 `<modules>` 中，不应在验收标准中按 Maven 模块统计。

因此本方案不再使用“13 个 Maven 模块收敛到 6 个”的表述。目标是清理空目录、完成依赖反转，并根据是否形成真实领域模型决定是否增加非空的 `foodmate-domain` 模块。

### 2.2 当前 Java 包现状

当前 application 已经按以下领域分包：

```text
com.foodmate.application.account
com.foodmate.application.knowledge
com.foodmate.application.nutrition
com.foodmate.application.runtime
```

`runtime` 下已有 17 个用例、处理器和协调器类，application 根包没有散落的业务类。真正的问题是 runtime 内部同时承载 command、event、recovery、messaging、tool、memory 等多个职责，需要做内部职责分组，而不是把根包类迁入 runtime。

当前工作树还包含以下领域，目标结构不能遗漏：

- `account`：用户认证、个人资料、隐私、后台管理和后台详情。
- `conversation`：当前 Session/Message 的持久化已存在，但 application 相关能力仍部分位于 `UserAccountService`；摘要和记忆目前位于 runtime。
- `knowledge`：知识文档和异步摄入任务。
- `nutrition`：饮食日志、营养分析、膳食计划和购物清单。
- `runtime`：AgentRun、Dispatch、Event、预算、取消、恢复、Outbox、DLQ、Proposal 和 Tool Gateway。

### 2.3 当前问题清单

| # | 问题 | 当前证据 | 优先级 |
|---|---|---|---|
| 1 | runtime 内部职责过于集中 | 17 个类和 14 个持久化端口都在 runtime 下 | 中 |
| 2 | application 直接依赖基础设施 SDK | application 使用 gateway-client、MinIO、RocketMQ、Redis | 最高 |
| 3 | gateway-client 同时包含端口、HTTP/MQ 实现和消息回调契约 | `GatewayClient`、`V1RuntimeClient`、`RocketMqConsumerContainer`、`MqMessageHandler` 混在同一模块 | 最高 |
| 4 | Store 与 Mapper 直接继承 | 当前工作树扫描到 21 个 Store、25 个 Mapper、3 个 Repository | 高 |
| 5 | application 输出类型跨边界泄漏 | Store 内部 record、Service 内部 record、`Map<String,Object>` 被 API 或跨用例使用 | 高 |
| 6 | application 与 infra 的领域归属尚未完全对齐 | infra 有 conversation 持久化包，但 Session/Message 端口仍集中在 `UserAccountStore` | 中 |
| 7 | api controller 和请求/响应 DTO 仍较平 | controller 目录包含 18 个 Java 文件，多个 DTO 作为 controller 内部 record | 中 |
| 8 | 空目录容易被误认为模块 | 多个 `foodmate-*` 目录没有 Java 文件，也没有加入根 POM | 低 |

## 3. 目标模块结构

### 3.1 模块层

稳定模块继续保留：

```text
FoodMate
├── foodmate-bootstrap      # 启动、配置和运行时装配
├── foodmate-api            # HTTP/SSE 边界
├── foodmate-application    # 用例、事务和出站端口
├── foodmate-infra          # 持久化、消息、缓存、对象存储和外部客户端适配器
└── foodmate-shared         # 跨模块契约、错误码、基础类型和小型工具
```

候选模块：

```text
foodmate-domain             # 只有形成真实领域对象、状态机或不变量后才加入根 POM
```

最终是否为 5 个还是 6 个 Maven 模块，不以数量为目标。若 `foodmate-domain` 仍为空，应保持不建模块；若建立，则必须有独立依赖规则和实际领域代码。

`foodmate-gateway-client` 的最终处理方式不是整体搬迁，而是拆分后删除：

- 传输无关的出站端口和消息处理契约进入 application 或 shared。
- HTTP、RocketMQ、JWT 具体实现进入 infra 的 `client`、`messaging` 或 `security`。
- bootstrap 继续负责选择 HTTP/MQ 实现并装配 Bean。

### 3.2 foodmate-application

建议结构如下：

```text
com.foodmate.application
├── account/
│   ├── port/                 # UserAccount、AdminDashboard、AdminDetail、Privacy 等出站端口
│   ├── UserAccountService
│   ├── AdminDashboardService
│   ├── AdminDetailService
│   ├── AdminManagementService
│   └── PersonalDataService
├── conversation/
│   ├── port/                 # Session、Message、Summary、Memory 相关端口
│   ├── session/
│   ├── message/
│   ├── summary/
│   └── memory/
├── knowledge/
│   ├── port/                 # KnowledgeIngestion、ObjectStorage 等端口
│   └── KnowledgeIngestionService
├── nutrition/
│   ├── port/                 # Nutrition 持久化端口
│   └── NutritionService
└── runtime/
    ├── port/                 # RuntimeClient、MQ、Admission 等出站端口
    ├── command/
    ├── admission/
    ├── event/
    ├── recovery/
    ├── messaging/            # Proposal、DLQ、Outbox、消息处理器
    └── tool/
```

迁移时可以先只移动包，不必一次建立全部子目录。只有当一个子目录包含一组稳定的协作类时才创建，禁止为每个类创建空包。

具体归属：

- `NutritionService/NutritionStore` 必须纳入 nutrition。
- `AdminDetailService/AdminDetailStore` 必须纳入 account 的 admin 能力。
- `MemoryCandidateService` 和 `SessionSummaryService` 可迁入 conversation，但相应端口和测试要一起迁移。
- `ProposalInbox`、`DispatchOutbox`、`Dlq`、`ProtocolAudit` 属于 runtime messaging/可靠性设施，不应因为名称中包含 inbox 或 audit 就放入 conversation。
- 当前 `UserAccountService` 中的 Session/Message 用例是否拆出，需要以用例边界和事务边界为准，不建议只移动接口文件而保留业务实现混在 account。

application 不应直接依赖以下类型：

```text
com.foodmate.gateway
io.minio
org.apache.rocketmq
org.springframework.data.redis
org.apache.ibatis
com.baomidou.mybatisplus
```

### 3.3 foodmate-api

建议按业务前缀组织 controller：

```text
com.foodmate.api
├── controller/
│   ├── auth/
│   ├── user/
│   ├── admin/
│   ├── conversation/
│   ├── knowledge/
│   ├── nutrition/
│   └── runtime/
├── dto/
│   ├── auth/request + response
│   ├── user/request + response
│   ├── admin/request + response
│   ├── conversation/request + response
│   ├── knowledge/request + response
│   ├── nutrition/request + response
│   └── runtime/request + response
├── sse/
├── filter/
├── advice/
└── security/
```

Request/Response 不建议全部放进一个全局平面 `request/`、`vo/` 目录，否则不同领域会出现同名 DTO 和跨领域复用。只被一个 controller 使用、且不影响边界的简单 record 可以暂时保留在 controller 内部。

API 必须把 application result 转换成 API response，不能直接暴露 `Store` record、`Service` 内部 record 或持久化模型。Nutrition、Knowledge ingestion 是当前最明显的 DTO 边界改造对象。

`SseEventEnvelope`、`SseTraceContext`、Filter 和全局异常处理继续保留为 API 横切能力。`ServiceJwt` 如果同时被 API、bootstrap 和客户端实现使用，应迁入 shared 的 `security` 小型工具包，不能让 API 依赖 infra。

### 3.4 foodmate-infra

```text
com.foodmate.infrastructure
├── config/
│   ├── mybatis/
│   ├── storage/
│   └── messaging/
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
│   ├── nutrition/
│   └── runtime/
├── storage/
│   └── minio/
├── messaging/
│   └── rocketmq/
├── client/
│   └── runtime/
└── audit/
```

规则：

1. `Mapper` 只负责 MyBatis SQL 和持久化行映射，不再 `extends` application 端口。
2. `Adapter/Repository` 实现 application 端口，负责调用 Mapper、组装查询结果和完成边界转换。
3. 不要把所有适配器命名为 `JdbcRepository`，当前项目实际使用 MyBatis/MyBatis-Plus；可使用 `XxxRepositoryAdapter` 或 `XxxMybatisRepository`。
4. `BasePo`、`UserPo`、`SessionPo` 等持久化模型只能留在 infra，不能作为 API 或 application 的返回类型。
5. `LocalStubPersistenceConfig` 要随端口迁移，继续只在 `local-stub` Profile 下生效，不能让生产环境静默降级到 stub。
6. `@MapperScan`、事务边界、SQL 注解、Flyway 脚本和软删除行为必须保持等价。

### 3.5 foodmate-gateway-client 的拆分目标

当前模块混合了接口、实现和 MQ 传输层，建议按以下方式拆分：

```text
application/runtime/port/
    RuntimeClientPort
    MessageHandler
    MessagePublisherPort

shared/runtime/ 或 shared/security/
    RunCommand / RunEvent 等跨模块协议
    MqConsumeDecision（若被多个模块使用）
    ServiceJwt（若作为跨边界小型工具）

infra/client/runtime/
    HttpRuntimeClient
    V1HttpRuntimeClient
    UnavailableRuntimeClient

infra/messaging/rocketmq/
    RocketMqRuntimeClient
    RocketMqConsumer
    RocketMqSettings
```

application 的消息处理器可以实现 application 定义的 `MessageHandler`，infra 的 RocketMQ consumer 负责把 RocketMQ 元数据翻译成传输无关的 context，并在处理完成后决定 ACK、RETRY 或 REJECT。

### 3.6 foodmate-shared

当前 `api/error/id/json/page/runtime/trace` 的分包方向基本合理。继续遵守：

- 可以放跨模块 HTTP/SSE 契约、Runtime wire contract、错误码、ID、分页、trace 和小型序列化工具。
- 不放用户、营养、知识、AgentRun 的业务服务、Repository、Po 或 API 专用 DTO。
- 不依赖 api、application、domain、infra 或具体第三方基础设施客户端。

### 3.7 foodmate-bootstrap

bootstrap 只负责启动和装配：

```text
com.foodmate.bootstrap
├── FoodMateApplication
├── config/
│   ├── CoreConfiguration
│   ├── MinioConfiguration
│   ├── RuntimeClientConfiguration
│   ├── RuntimeRocketMqConfiguration
│   └── SchedulingConfiguration
└── test/
    ├── architecture/
    ├── e2e/
    └── persistence/
```

bootstrap 可以依赖所有运行模块，但不应承载业务用例、SQL、MQ 消息处理逻辑或外部客户端实现。HTTP/MQ/Local stub 的选择应集中在配置类中。

### 3.8 foodmate-domain（候选）

只有在出现真实的领域行为后才创建该模块：

```text
com.foodmate.domain
├── agent/
├── conversation/
├── user/
├── knowledge/
└── nutrition/
```

domain 只放实体、值对象、领域事件、状态机和不变量，不放 Service、Controller、Repository、Mapper、Po、Request、Response 或 Spring Bean。不要为了“目录完整”创建空领域包。

## 4. 命名规范

| 职责 | 目标命名 | 说明 |
|---|---|---|
| application 出站端口 | `XxxPort` | 外部客户端、对象存储、缓存、消息发布和消息处理等能力 |
| 聚合持久化端口 | `XxxRepository` | 只有确实围绕一个聚合或稳定数据集合时使用 |
| 数据库实体 | `XxxPo` | 仅存在于 infra |
| MyBatis SQL 接口 | `XxxMapper` | 仅存在于 infra，不继承 application 端口 |
| 端口实现 | `XxxRepositoryAdapter` / `XxxMybatisRepository` | 负责 Mapper 调用和类型转换，不使用不准确的 Jdbc 后缀 |
| API 请求 | `XxxRequest` | 按 API 领域归属 |
| API 响应 | `XxxResponse` | 优先于含义不明确的全局 `VO` |
| application 结果 | `XxxResult` / `XxxView` | 不直接复用 API Response 或 Po |
| 领域对象 | `Xxx`、`XxxId`、`XxxStatus` | 放入 domain，只有存在真实领域模块时使用 |

Store 不需要一次性全部重命名。先按职责分类，再进行重命名，避免 `DlqRepository`、`ToolGatewayRepository` 等名称掩盖真实边界。

## 5. 迁移步骤

| 步骤 | 内容 | 类型 | 风险 |
|---|---|---|---|
| S0 | 盘点当前工作树：类、包、模块、POM 依赖、ArchUnit、测试、local-stub 和数据库 Mapper；冻结本文基线 | 文档/清单 | 低 |
| S1 | 清理未纳入 Git 的空目录；不修改根 POM 的模块数量，不删除仍有文件或职责的模块 | 目录 | 低 |
| S2 | application、api、infra 做纯包路径调整，补齐 account、knowledge、nutrition、runtime 的实际领域；暂时保留现有 Store、Mapper 和 API 返回结构 | 目录/导包 | 中 |
| S3 | 定义 application 端口，拆分 gateway-client；将 MinIO、Redis、RocketMQ 和 Runtime 客户端实现移入 infra；删除 application 对 SDK 的直接依赖 | 依赖/代码 | 高 |
| S4 | Mapper 与 application 端口解耦，增加必要的 RepositoryAdapter，完成 Po、持久化行、application result 的映射；同步迁移 local stub | 代码/行为 | 高 |
| S5 | API Request/Response 按领域归档，application 返回 `Result/View`，逐步消除 Store record 和 `Map<String,Object>` 跨边界泄漏 | API 契约/代码 | 高 |
| S6 | 只有有真实领域模型时才创建 foodmate-domain；随后删除已拆空的 gateway-client，并更新根 POM、启动配置和测试 | 模块/代码 | 高 |

S1-S2 不是只改目录：包名变化会影响测试、Spring 扫描和 ArchUnit。S3-S6 必须按模块逐步提交，不能把所有重命名和行为调整合并为一次大迁移。

## 6. 各模块验收标准

### 6.1 依赖边界

- application 不依赖 `com.foodmate.infrastructure..`、`com.foodmate.gateway..`、MyBatis、MinIO、RocketMQ、Redis SDK。
- api 不依赖 infra、Po、Mapper 或外部基础设施客户端。
- shared 不依赖业务模块和基础设施。
- domain（如果建立）不依赖 Spring、api、infra、Mapper、Po 或外部客户端。
- bootstrap 是唯一负责运行时实现选择和 Bean 装配的模块。

### 6.2 结构和类型

- application 根包没有业务类。
- 领域包不创建空目录，且每个端口或适配器有明确所属领域。
- Mapper 不继承 application 端口。
- Po 不跨出 infra；API 不直接暴露 Store record。
- `nutrition`、`AdminDetail`、knowledge ingestion 均有明确的 application、api、infra 归属。
- `Map<String,Object>` 只允许出现在确实是动态结构的边界，不能作为默认数据传输类型。

### 6.3 测试和运行验证

- 每次阶段性迁移执行 `mvnw.cmd verify`。
- 更新并运行 ArchUnit 模块依赖测试，补充对 gateway 和第三方 SDK 的禁止规则。
- application 单元测试随包迁移，测试替身改用 application 端口，不依赖 gateway-client 实现类。
- infra 运行 Mapper/Repository、Flyway、软删除和数据库集成测试。
- bootstrap 运行生产配置矩阵、local-stub Profile、HTTP/MQ 传输和恢复类 E2E 测试。
- API DTO 迁移增加 JSON 字段、校验和错误响应兼容性测试。
- 第一阶段不得改变现有数据库表结构和公开 HTTP/SSE 字段；如字段变化，必须单独记录兼容策略。

## 7. 最终结构对照

| 维度 | RAgent | FoodMate 目标 |
|---|---|---|
| 应用形态 | 单体 + 领域包 | 多模块 + 各层内领域包 |
| 领域承载 | bootstrap 内领域包 | application/api/infra 按层对齐，domain 作为候选模块 |
| 横切能力 | framework | shared 契约 + infra 适配器 |
| 持久化 | Mapper 直用 | application 端口 + infra Mapper + 必要的 Adapter |
| 外部客户端 | 领域内直接使用 | application 只依赖 Port，infra 提供 HTTP/MQ/MinIO/Redis 实现 |
| 模块数量 | 固定 4 个主要模块 | 数量不是目标；稳定模块 5 个，domain 仅在非空且有边界时加入 |

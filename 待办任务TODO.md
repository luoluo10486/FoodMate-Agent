# FoodMate 待办任务 TODO

本文是 FoodMate 的工程执行清单，面向人类开发者和 AI Coding Agent。  
执行原则：一次只做一个任务；每个任务必须有明确目标、允许改动范围、实现要点和验收标准。

详细来源：

- `docs/需求文档PRD.md`
- `docs/UI设计文档.md`
- `docs/架构文档.md`
- `FoodMate-接口与数据规范.md`
- `FoodMate-数据库设计与接口工具清单.md`
- `FoodMate-Java工程骨架与模块设计.md`
- `FoodMate-智能体行为与工具协议.md`
- `FoodMate-系统设计与技术方案.md`
- `FoodMate-实现附录.md`

## 0. 工作规则

- 每次开发优先只领取一个编号任务，例如 `B2-1`。
- 实现前必须阅读该任务列出的参考文档。
- 不允许跳过验收标准直接进入下一项。
- 不允许在无需求的情况下删除或重写原有 7 份长文档。
- 后端实现优先保持模块化单体，不急着拆微服务。
- API、SSE、DTO、状态机、错误码以 `FoodMate-接口与数据规范.md` 为详细准则。
- 表结构、工具注册、SQL Agent、Milvus、审计字段以 `FoodMate-数据库设计与接口工具清单.md` 为详细准则。
- Java 模块、包结构、依赖方向以 `FoodMate-Java工程骨架与模块设计.md` 为详细准则。

## 0.1 范围保留决策

以下能力虽然不一定在第一轮全部实现，但属于 FoodMate 的正式目标，不从文档和计划中删除：

- Milvus、MinIO、RocketMQ 等基础设施接入。
- SQL Agent、MCP、Schema Catalog 和只读 SQL Guard。
- ModelService、模型调用日志、模型路由与成本治理。
- UserMemory、SessionSummary、Prompt 工程化和回归样例集。
- Admin 查询、审计、Trace、软删除和恢复能力。
- P1/P2 工具，例如 `nutrition_lookup`、`meal_plan_generator`、`shopping_list_generator`、`summary_generator`、`data_exporter`。

执行原则：保留完整目标，但按 Phase 顺序逐步落地；未到对应 Phase 时只允许做必要占位、接口契约或 mock，不提前实现完整复杂能力。

## Phase 0：工作流文档与项目入口

### D0-1. 建立中文 docs 工作流入口

目标：

- 让 `docs/` 成为理解产品、UI 和架构范围的第一入口。

允许改动：

- `docs/需求文档PRD.md`
- `docs/UI设计文档.md`
- `docs/架构文档.md`
- `README.md`
- `待办任务TODO.md`

实现要点：

- PRD 文档说明产品定位、目标用户、MVP 范围、暂不做、核心流程、成本边界和验收标准。
- UI 文档说明首页、会话页、分析页、规划页、核心组件、页面状态和视觉规则。
- 架构文档说明模块化单体、Agent、RAG、Tool、SQL Agent、ModelService、Data Service 的边界。
- README 改成项目入口，不只是旧文档索引。

验收标准：

- 新工程师只读 README 和 docs 三份入口文档，就能判断项目目标和第一周任务。
- 原有 7 份长文档仍然保留并可从 README 访问。
- 新文档不和原接口、状态机、工具清单冲突。

### D0-2. 补充 UI 原型图

目标：

- 让 `docs/UI设计文档.md` 不只描述页面，还能直观看到页面结构。

允许改动：

- `docs/UI设计文档.md`
- `docs/assets/prototypes/`

实现要点：

- 生成首页、会话页、分析页、规划页、个人资料页、管理后台 6 张低保真原型图。
- 原型图只表达页面骨架、信息层级、状态区域和关键操作。
- 不把低保真原型当最终高保真视觉稿。

验收标准：

- UI 文档中能直接预览 6 张原型图。
- 首页和会话页原型能指导 Phase 1 静态页面实现。
- 个人资料页和管理后台原型能指导后续 mock 页面与权限入口实现。
- 原型图路径使用相对路径，文档链接不失效。

## Phase 1：前端静态 UI 原型

### F1-1. 创建前端项目骨架

目标：

- 创建第一版前端应用，用于实现静态 UI 原型。

参考文档：

- `docs/UI设计文档.md`
- `docs/前端架构文档.md`
- `FoodMate-实现附录.md`

允许改动：

- 新增前端应用目录。
- 新增前端构建配置、依赖声明和启动脚本。
- 更新 README，加入前端启动命令。

实现要点：

- 锁定使用 React + Vite + TypeScript + Arco Design。
- Arco Design 参与整体视觉风格，但不直接套 Arco Pro 后台模板。
- 样式使用 Arco 主题能力、FoodMate design tokens 和 CSS Modules。
- 第一版不依赖真实后端。
- 使用 mock 数据驱动页面。
- 建立基础目录：页面、布局、组件、features、mock 数据、类型、样式 token。
- 第一版不引入大型状态库，Phase 2 优先用 React state / reducer 管理 mock event replay。

验收标准：

- 前端应用可本地启动。
- 首页可以渲染。
- 控制台无明显错误。
- 移动端和桌面端基础布局可见。
- `docs/前端架构文档.md` 中的目录、路由、组件库和样式策略已落实到工程骨架。

### F1-2. 实现首页静态原型

目标：

- 按 UI 文档和首页原型图实现首页。

允许改动：

- 首页页面文件。
- 首页相关组件和 mock 数据。
- 前端样式文件。

实现要点：

- 顶部栏：FoodMate、模块入口、全局状态、用户入口。
- 左侧栏：新建会话、搜索、置顶会话、最近会话。
- 主区域：当前 Agent 模式、常用任务卡片、推荐问题。
- 底部输入区：自然语言输入、附件入口、发送按钮、工具状态。
- 覆盖空态、加载中、错误态。
- Arco `Button`、`Input`、`Card`、`Tag`、`Skeleton` 可作为基础控件。
- 首页任务卡和推荐问题必须保留 FoodMate 自定义视觉，不直接使用默认后台卡片堆叠。
- 页面进入和任务卡 hover/focus 可以使用轻量 CSS 动效。

验收标准：

- 页面结构和 `docs/assets/prototypes/首页原型.svg` 一致。
- 快捷任务卡片和推荐问题来自 mock 数据。
- 页面宽度小于 768px 时不横向溢出。
- 首屏不需要整体滚动即可看到 Tools/Agents 和 Composer。
- 减少动态偏好开启时，任务卡动效可关闭。

### F1-3. 实现会话页静态原型

目标：

- 实现 Agent 主执行工作区。

允许改动：

- 会话页页面文件。
- 会话消息、工具轨迹、引用、结果卡、追问卡、确认卡组件。
- mock 数据。

实现要点：

- 消息流展示用户消息和助手回答。
- Agent 状态条展示 routing、planning、retrieving、executing、validating、composing。
- 工具轨迹展示工具名、状态、耗时、错误和可展开摘要。
- 引用区域展示来源标题、片段、分数或 metadata。
- 追问卡用于缺少关键参数。
- 确认卡用于写入饮食记录、保存计划、修改偏好、删除数据。
- 底部输入区在执行中展示停止按钮。
- Arco `Progress`、`Tag`、`Drawer`、`Tooltip`、`Modal`、`Message` 可用于状态、详情和反馈。
- ToolTraceItem、CitationBlock、ClarificationCard、ConfirmationCard 必须按 FoodMate Agent 语义自定义封装。
- Agent 状态推进、工具轨迹展开、追问卡和确认卡出现可以使用轻量 CSS 动效。

验收标准：

- 页面结构和 `docs/assets/prototypes/会话页原型.svg` 一致。
- 覆盖执行中、追问中、完成、失败、取消状态。
- mock 流式更新时输入区高度不跳动。
- 工具轨迹和引用详情可通过抽屉或展开区域查看。
- 停止、失败、取消反馈不依赖浏览器 alert。

### F1-4. 实现分析页静态原型

目标：

- 实现摄入分析页的静态展示。

允许改动：

- 分析页页面文件。
- 指标卡、趋势图占位、异常提示组件。
- mock 数据。

实现要点：

- 时间范围选择。
- 汇总指标：热量、蛋白质、脂肪、碳水、餐次数。
- 趋势图区域。
- 目标对比。
- 异常点和缺失数据提示。
- Arco `Card`、`Tag`、`Skeleton`、`Tabs` 或轻量选择控件可用于指标、状态和时间范围。
- 图表第一版可用 SVG/CSS 占位，但数据结构必须能替换为真实图表库。
- 异常提示需要有进入动效或高亮反馈，但不能干扰扫读。

验收标准：

- 页面结构和 `docs/assets/prototypes/分析页原型.svg` 一致。
- 静态数据能表达趋势、目标和异常。
- 图表区域即使使用占位，也要保留后续接真实图表的数据结构。
- Tools（2/6）对应 `time_parser` 和 `database_query` 的 mock 工具轨迹口径。

### F1-5. 实现规划页静态原型

目标：

- 实现餐食计划和购物清单页面的静态展示。

允许改动：

- 规划页页面文件。
- 计划表、购物清单、约束摘要、校验结果组件。
- mock 数据。

实现要点：

- 约束摘要：人数、天数、预算、目标、忌口。
- 多日菜单表。
- 按类别分组的购物清单。
- 预算估算。
- 计划校验结果。
- 确认保存动作。
- Arco `Table` 可用于计划表，也可以基于 FoodMate 视觉封装为 `MealPlanTable`。
- Arco `Tag`、`Progress`、`Modal` 可用于校验状态、预算进度和保存确认。
- 购物清单和校验结果必须使用 FoodMate 自定义卡片，突出“可执行计划”而不是普通表格。

验收标准：

- 页面结构和 `docs/assets/prototypes/规划页原型.svg` 一致。
- 计划表在移动端可用。
- 保存动作先进入确认态，不直接假装保存。
- Tools（3/6）对应 `knowledge_search`、`database_query`、`plan_validator` 的 mock 工具轨迹口径。

### F1-6. 抽取共享 UI 组件

目标：

- 为后续接入真实数据打好组件边界。

允许改动：

- 前端组件目录。
- 本地样式或 design token。

必须组件：

- `SidebarSessionList`
- `TaskCard`
- `Composer`
- `AgentStatusStrip`
- `ToolTraceItem`
- `ResultCard`
- `CitationBlock`
- `ClarificationCard`
- `ConfirmationCard`
- `MetricCard`
- `MealPlanTable`
- `ShoppingList`
- `EmptyState`
- `ErrorState`

组件分层：

- 基础控件可基于 Arco 封装，例如按钮、输入框、弹窗、抽屉、Tooltip、Tag、Progress、Skeleton。
- 业务组件必须按 FoodMate Agent 语义自定义，例如 Composer、AgentStatusStrip、ToolTraceItem、ResultCard、CitationBlock、ClarificationCard、ConfirmationCard。
- 页面组件只组合业务组件和 mock 数据，不直接写复杂事件回放逻辑。

验收标准：

- 组件接收 props，不和单个页面硬绑定。
- 组件状态至少覆盖 normal、loading、disabled、error。
- 组件命名和 UI 文档一致。
- 组件使用 Arco 时必须保留 FoodMate design tokens 和单屏工作台布局。

## Phase 2：前端 Mock 交互

Phase 2 统一约定：

- 不调用真实后端。
- 使用本地 mock event sequence 模拟 `FoodMate-接口与数据规范.md` 中的 `run.*` SSE 事件。
- 状态管理优先使用 React state / reducer。
- 如果事件回放跨页面共享明显变复杂，后续再评估轻量状态库，不提前引入大型状态方案。

### F2-1. 添加假 Agent 事件回放

目标：

- 在没有后端时模拟 Agent 运行过程。

允许改动：

- 前端 mock event 工具。
- 会话页状态管理。

实现要点：

- 点击快捷任务或发送消息后，模拟事件顺序：`run.created`、`run.routed`、`run.planned`、`run.retrieval_started`、`run.retrieval_finished`、`run.tool_started`、`run.tool_finished`、`run.answer_stream`、`run.completed`。
- 失败场景模拟 `run.failed`。
- 取消场景模拟 `run.cancelled`。
- 工具轨迹随事件逐步出现。
- 回答内容模拟逐步输出。
- 停止按钮把状态切换为 cancelled。
- mock event replay 封装为 hook 或 feature 层工具，不写死在页面组件中。

验收标准：

- 会话页能完整播放一轮 mock 任务。
- 停止后不继续追加答案。
- failed 和 cancelled 状态有明确 UI。
- Agent 状态、工具轨迹和答案流同步更新。
- mock 事件名与接口文档的 `run.*` 事件一致。

### F2-2. 添加 mock 追问流程

目标：

- 验证缺参时的交互。

允许改动：

- 前端 mock 状态。
- 追问卡组件。

实现要点：

- 输入“给我做一周备餐计划”时展示预算、忌口、目标三个追问。
- 追问通过 `ClarificationCard` 展示缺失字段。
- 用户补充后继续 mock 执行。
- 追问回答要保留在消息流中。
- 状态从 `waiting_user` 回到 `planning` 后继续播放 mock event sequence。

验收标准：

- 追问问题短、明确、可回答。
- 补充答案后状态从 waiting_user 回到 planning。
- 追问卡可使用 Arco `Button`、`Input`、`Tag`，但卡片结构保持 FoodMate 自定义。

### F2-3. 添加 mock 确认流程

目标：

- 验证写入前确认的体验。

允许改动：

- 前端 mock 状态。
- 确认卡组件。

实现要点：

- 输入“帮我记录今天午餐”时展示饮食记录确认卡。
- 确认卡展示写入时间、餐型、食物、份量、估算营养。
- 支持确认、取消、修改。
- 可使用 Arco `Modal` 承载高风险确认，也可使用页面内 FoodMate `ConfirmationCard`。
- 确认后只模拟成功反馈，不调用真实写入接口。
- 修改后回到可编辑状态，取消后进入可重试状态。

验收标准：

- 未确认前不显示“已保存”。
- 取消后保留用户输入和可重试入口。
- 确认、取消、修改、重试都有明确反馈。
- 不调用真实后端，不产生真实数据写入。

### F2-4. 添加个人资料页 mock

目标：

- 为真实登录后的个人资料管理准备页面和状态边界。

允许改动：

- 前端路由。
- 个人资料页面。
- 头像上传 mock 组件。
- auth/profile mock 数据。

实现要点：

- 新增 `/profile`。
- 展示昵称、头像、邮箱、营养目标、忌口、过敏原、单位偏好和修改密码入口。
- 头像上传只做 mock 预览和状态反馈，不上传真实文件。
- 用户不能修改自己的 role/status。
- 顶部用户菜单加入个人资料入口。

验收标准：

- `/profile` 可访问并使用 mock 数据渲染。
- 保存资料只展示 mock 反馈，不调用真实后端。
- 头像替换、删除和失败状态有明确 UI。

### F2-5. 添加管理后台 mock 入口

目标：

- 为 admin/operator 的治理视图准备前端页面骨架。

允许改动：

- 前端路由。
- 管理后台页面。
- 管理后台 mock 数据。
- 用户菜单权限展示。

实现要点：

- 新增 `/admin`。
- 管理后台使用独立布局，不复用 Agent 工作台左侧会话栏。
- 新增 `/admin/users`、`/admin/runs`、`/admin/tools`、`/admin/usage`、`/admin/knowledge`、`/admin/deleted` 等子路由。
- 页面包含概览、用户管理、Agent 运行、工具调用、模型用量、知识库、软删除资源等视图入口，左侧菜单点击后必须真实跳转。
- 普通用户不显示管理入口。
- operator 仅展示只读操作，admin 展示高风险操作入口和二次确认占位。
- 当前阶段不接真实管理接口。

验收标准：

- `/admin` 可以展示 mock 管理概览。
- `/admin/users`、`/admin/runs`、`/admin/tools`、`/admin/usage`、`/admin/knowledge`、`/admin/deleted` 可以展示对应 mock 子页面。
- 普通用户访问时展示 403 或无权限空态。
- 管理列表支持分页和基础筛选的 UI 占位。

## Phase 3：后端工程骨架与通用能力

### B3-1. 创建 Maven 模块化单体骨架

目标：

- 建立 Java/Spring Boot 后端基础工程。

参考文档：

- `FoodMate-Java工程骨架与模块设计.md`
- `docs/架构文档.md`

允许改动：

- 根 `pom.xml`。
- 后端模块目录。
- 基础 Spring Boot 启动类。
- 初始配置文件。

建议模块：

- `foodmate-bootstrap`
- `foodmate-api`
- `foodmate-application`
- `foodmate-domain`
- `foodmate-orchestrator`
- `foodmate-rag`
- `foodmate-tool`
- `foodmate-sql-agent`
- `foodmate-model`
- `foodmate-gateway-client`
- `foodmate-infra`
- `foodmate-worker`
- `foodmate-shared`

实现要点：

- Java 21。
- Spring Boot 3。
- 第一阶段运行时仍是模块化单体。
- 依赖方向遵循工程骨架文档，不让 `api` 直接依赖 `infra`、`model`、`rag`。

验收标准：

- Maven 项目可以本地构建。
- `foodmate-bootstrap` 能启动最小 Spring Boot 应用。
- 模块依赖方向和文档一致。

### B3-2. 建立 shared 通用契约

目标：

- 建立全局 ID、响应、错误码、分页、Trace 基础对象。

允许改动：

- `foodmate-shared`
- 少量 API 响应封装。

必须实现：

- Snowflake ID 生成接口或占位实现。
- `ApiResponse<T>` 成功/失败结构。
- `ErrorCode` 错误码分层。
- `PageRequest`、`PageResult`。
- `RequestContext` 或 `TraceContext`。
- ID 对外序列化为字符串的约定。

验收标准：

- API DTO 对外 ID 为字符串。
- 错误响应包含 code、message、details、requestId、traceId。
- 单元测试覆盖 ID 序列化和错误响应封装。

### B3-3. 建立配置与环境文件

目标：

- 准备本地、开发、生产配置结构。

允许改动：

- `application.yml`
- `application-local.yml`
- `application-dev.yml`
- `application-prod.yml`

必须配置占位：

- PostgreSQL。
- Redis。
- Milvus。
- MinIO。
- RocketMQ。
- Model gateway。
- RAG topK/rerank 参数。
- SQL Agent 只读策略。
- Tool 开关。
- Audit 开关。

验收标准：

- 本地配置不提交真实密钥。
- 应用可在 local profile 下启动。
- 缺少外部依赖时能以 mock/stub 模式启动。

### B3-4. 建立统一异常与参数校验

目标：

- 让 API 层错误格式统一。

允许改动：

- `foodmate-api`
- `foodmate-shared`

必须实现：

- 全局异常处理器。
- 参数校验错误映射。
- 业务异常映射。
- 权限异常占位。
- 工具、RAG、SQL、模型错误码前缀。

验收标准：

- 参数缺失返回统一错误结构。
- 未知异常不会直接暴露堆栈给前端。
- 测试覆盖至少 3 类错误：参数错误、业务错误、内部错误。

### B3-5. 建立审计与软删除基础约束

目标：

- 为所有后续业务对象建立删除、恢复和审计口径。

允许改动：

- `foodmate-domain`
- `foodmate-infra`
- `foodmate-shared`

必须实现：

- `is_deleted`、`deleted_at`、`deleted_by` 通用字段约定。
- Repository 默认查询排除软删除数据。
- `SoftDeleteRepositorySupport` 或等价抽象。
- 恢复操作的基础接口约定。
- 审计字段：operatorId、requestId、traceId、targetType、targetId、action、createdAt。

验收标准：

- 默认查询不会返回软删除数据。
- 删除和恢复都能记录操作者与 trace。
- 测试覆盖软删除、恢复、默认查询过滤。

## Phase 4：数据库与持久化

### B4-1. 建立数据库迁移体系

目标：

- 让表结构可版本化、可回滚、可审查。

允许改动：

- 数据库迁移目录。
- `foodmate-infra` 数据库配置。

实现要点：

- 可选择 Flyway 或 Liquibase。
- 第一版 PostgreSQL 为事务主库。
- 不使用 pgvector 作为主向量库。
- 所有主键为 `BIGINT`，由应用层 Snowflake 生成。

验收标准：

- 本地可以执行迁移。
- 迁移脚本有版本号。
- 主表包含软删除字段。

### B4-2. 建立用户、认证、个人资料与会话域表

目标：

- 支撑真实登录、角色权限、个人资料、头像资产、会话和消息基础链路。

必须表：

- `users`
- `user_profiles`
- `auth_refresh_tokens`
- `user_avatar_assets`
- `sessions`
- `messages`

字段来源：

- `FoodMate-数据库设计与接口工具清单.md` 第 1.2 节。

验收标准：

- 所有表主键使用带业务语义的 `{业务对象}_id`，例如 `users.user_id`、`user_profiles.profile_id`、`sessions.session_id`、`messages.message_id`，不统一使用泛化 `id`。
- `users` 包含 username、email、passwordHash、role、avatarUrl、status、lastLoginAt、passwordUpdatedAt、lockedUntil。
- `user_profiles` 包含展示名、身高、体重、活动水平、营养目标、忌口、过敏原和单位偏好。
- `auth_refresh_tokens` 保存 tokenHash、用户、设备、过期、撤销和轮换信息，不保存明文 token。
- `user_avatar_assets` 保存 MinIO/S3 对象 key、URL、mime、size、宽高和状态，不保存图片二进制。
- 用户状态统一为 active/disabled/locked，角色统一为 user/admin/operator。
- `sessions(user_id, last_message_at, is_deleted)` 或等价主路径索引存在。
- `messages(session_id, sequence_no, is_deleted)` 或等价主路径索引存在。

### B4-3. 建立 Agent 运行域表

目标：

- 支撑 AgentRun、ToolCall、执行轨迹和回放。

必须表：

- `agent_runs`
- `tool_calls`

字段来源：

- `FoodMate-数据库设计与接口工具清单.md` 第 1.3 节。

验收标准：

- `agent_runs` 包含 session、userMessage、intent、status、planJson、resultJson、errorCode、traceId。
- `tool_calls` 包含 run、toolName、version、inputJson、outputJson、status、latencyMs、errorCode、traceId。
- 可按 session 查询运行记录。
- 可按 run 查询工具调用记录。

### B4-4. 建立业务域表

目标：

- 支撑饮食记录、餐食计划和后续分析。

必须表：

- `food_logs`
- `meal_plans`
- 后续可增加 `analysis_reports`
- 后续可增加 `shopping_lists`

字段来源：

- `FoodMate-数据库设计与接口工具清单.md` 第 1.4 节。
- `FoodMate-接口与数据规范.md` 第 5.5、5.6、5.7 节。

验收标准：

- `food_logs` 支持 user、session、mealTime、mealType、itemsJson、nutritionJson、source。
- `meal_plans` 支持 user、session、days、budget、constraintsJson、planJson、validationJson、status。
- 删除与恢复遵循软删除规则。

### B4-5. 建立记忆与摘要域表

目标：

- 支撑长期偏好、短期摘要和上下文管理。

必须表：

- `user_memories`
- `session_summaries`

验收标准：

- 记忆包含 type、key、value、confidence、source、scope、expiresAt。
- 会话摘要包含 summaryText 和 keyConstraints。
- 长期记忆写入预留用户确认能力。

### B4-6. 建立知识库域表

目标：

- 支撑知识文档、chunk、Milvus 可见性同步。

必须表：

- `knowledge_documents`
- `knowledge_chunks`

验收标准：

- 文档支持 sourceType、title、status、version、storageKey、metadataJson。
- chunk 支持 documentId、chunkNo、chunkText、sectionPath、tags、embeddingId、metadataJson。
- 删除文档后，后续异步任务可下线 Milvus metadata。

### B4-7. 建立 SQL Agent 与工具注册表

目标：

- 支撑结构化数据查询、工具治理和审计。

必须表：

- `data_sources`
- `schema_catalogs`
- `sql_query_audits`
- `tool_registries`
- `tool_schema_versions`

验收标准：

- 数据源标记 readonly。
- schema catalog 记录表、字段、描述、敏感字段、示例 SQL。
- SQL audit 记录原始问题、解析问题、SQL、状态、拒绝原因、行数、耗时、traceId。
- 工具注册表记录 name、displayName、category、riskLevel、status、currentVersion。
- 工具 schema 版本记录 inputSchema、outputSchema、permissions、timeout、retryable、idempotent。

### B4-8. 建立模型治理表

目标：

- 支撑模型调用日志、成本统计和后续路由治理。

必须表：

- `model_usage_logs`
- `model_route_rules`

验收标准：

- usage log 记录 requestId、traceId、scene、providerCode、modelName、usageJson、latencyMs、costAmount、status。
- route rule 支持 tenant、scene、modelType、provider、fallback、maxCost、maxLatency、ruleJson。

## Phase 5：后端 API 主链路

### B5-0. 实现认证、RBAC 与个人资料接口

目标：

- 建立真实登录、刷新、退出、当前用户、注册、个人资料、头像上传和基础授权能力。

参考接口：

- `POST /foodmate/auth/login`
- `POST /foodmate/auth/refresh`
- `POST /foodmate/auth/logout`
- `GET /foodmate/auth/me`
- `POST /foodmate/auth/register`
- `POST /foodmate/auth/password-reset/request`
- `POST /foodmate/auth/password-reset/confirm`
- `GET /foodmate/users/me`
- `PATCH /foodmate/users/me`
- `PATCH /foodmate/users/me/profile`
- `POST /foodmate/users/me/avatar`
- `DELETE /foodmate/users/me/avatar`
- `POST /foodmate/users/me/password/change`

必须实现：

- Spring Security 鉴权入口。
- JWT Access Token 校验。
- HttpOnly Refresh Token 签发、校验、轮换和撤销。
- BCrypt 或 Argon2 密码哈希。
- RBAC：user/admin/operator。
- 登录限流、验证码、幂等键等 Redis 占位能力。
- 头像上传 MinIO/S3 占位或 mock storage adapter。
- 后端从 token 解析 userId/role/status，不信任前端传入身份字段。

验收标准：

- 登录成功返回 Access Token，并通过 Cookie 设置 Refresh Token。
- disabled/locked 用户不能登录。
- refresh 成功会轮换 Refresh Token，logout 后当前 Refresh Token 失效。
- 普通用户只能查看和修改自己的个人资料。
- 用户不能修改自己的 role/status。
- 头像上传限制图片类型、大小和尺寸，并返回 avatarUrl。
- 普通用户访问 admin 接口返回 403。

### B5-1. 实现会话接口

目标：

- 支撑会话创建、查询、更新、归档、删除、恢复。

参考接口：

- `POST /foodmate/agent-sessions`
- `GET /foodmate/agent-sessions`
- `GET /foodmate/agent-sessions/{session_id}`
- `PATCH /foodmate/agent-sessions/{session_id}`
- `POST /foodmate/agent-sessions/{session_id}/archive`
- `DELETE /foodmate/agent-sessions/{session_id}`
- `POST /foodmate/agent-sessions/{session_id}/restore`

必须实现：

- Controller DTO。
- Application service。
- Domain entity。
- Repository。
- 权限占位：用户只能访问自己的会话。
- 软删除和恢复。

验收标准：

- 能创建会话并返回字符串 ID。
- 列表默认不返回软删除会话。
- 归档不等于删除。
- 恢复操作记录审计。

### B5-2. 实现消息接口

目标：

- 支撑消息发送和消息列表查询。

参考接口：

- `POST /foodmate/agent-sessions/{session_id}/messages`
- `GET /foodmate/agent-sessions/{session_id}/messages`

必须实现：

- 用户消息持久化。
- 消息 sequenceNo。
- 创建 AgentRun。
- 返回 messageId 和 agentRunId。
- 分页查询消息。

验收标准：

- 发送消息后能查到消息。
- 发送消息会创建 queued/routed 前的 AgentRun。
- 消息列表按 sequenceNo 稳定排序。

### B5-3. 实现 AgentRun 查询与取消接口

目标：

- 支撑前端查看运行状态、工具调用、引用和最终结果。

参考接口：

- `GET /foodmate/agent-runs/{agent_run_id}`
- `GET /foodmate/agent-runs/{agent_run_id}/events`
- `POST /foodmate/agent-runs/{agent_run_id}/cancel`

必须实现：

- 查询 run 详情。
- 查询 toolCalls。
- 查询 retrieval references 占位。
- 取消 run。
- failed/cancelled 状态处理。

验收标准：

- 运行详情包含 intent、status、plan、toolCalls、finalAnswer、error。
- cancel 后不会继续追加 mock 执行事件。
- 不允许用户访问他人的 run。

### B5-4. 实现 SSE 事件流

目标：

- 让前端通过 SSE 订阅 AgentRun 生命周期。

必须事件：

- `run.created`
- `run.routed`
- `run.clarification_requested`
- `run.planned`
- `run.retrieval_started`
- `run.retrieval_finished`
- `run.tool_started`
- `run.tool_finished`
- `run.answer_stream`
- `run.completed`
- `run.failed`
- `run.cancelled`

实现要点：

- 使用 Spring WebFlux 或可稳定输出 SSE 的实现。
- 每个事件包含 eventType、runId、timestamp、payload。
- 保留 traceId。
- 客户端断开后释放资源。

验收标准：

- 前端可以接收完整 mock run 事件。
- 失败事件包含错误码和用户可读消息。
- 取消后优先输出 `run.cancelled`，并停止继续追加工具或答案事件；兼容旧实现时才允许从 `AgentRun.status=cancelled` 派生展示。

### B5-5. 实现知识库接口

目标：

- 支撑知识检索和文档管理的最小入口。

参考接口：

- `POST /foodmate/knowledge-base/search`
- `GET /foodmate/knowledge-base/documents`
- `POST /foodmate/knowledge-base/documents`
- `DELETE /foodmate/knowledge-base/documents/{document_id}`
- `POST /foodmate/knowledge-base/documents/{document_id}/restore`

第一阶段实现：

- search 可先走 mock 或 PostgreSQL keyword stub。
- documents 支持 metadata 入库。
- 上传文件可先占位，后续接 MinIO。

验收标准：

- search 有命中时返回 hits 和 references。
- search 无命中时不编造引用。
- 文档删除默认软删除。

### B5-6. 实现饮食日志接口

目标：

- 支撑饮食记录的创建、查询、汇总、删除、恢复。

参考接口：

- `POST /foodmate/food-logs`
- `GET /foodmate/food-logs`
- `GET /foodmate/food-logs/summary`
- `DELETE /foodmate/food-logs/{food_log_id}`
- `POST /foodmate/food-logs/{food_log_id}/restore`

必须实现：

- mealTime、items、notes 校验。
- 查询按时间范围过滤。
- summary 聚合 calories、protein、fat、carbs、mealCount。
- 写接口支持幂等键。

验收标准：

- 创建后可查询。
- 默认不返回软删除记录。
- summary 结果包含统计范围和数据来源。

### B5-7. 实现分析报告接口

目标：

- 支撑摄入分析报告的生成和查询。

参考接口：

- `POST /foodmate/nutrition-analysis/reports`
- `GET /foodmate/nutrition-analysis/reports/{report_id}`

第一阶段实现：

- 基于 food_logs 聚合生成简单报告。
- 支持 protein_trend、calorie_summary 两类。
- 结果存入 analysis_reports 或以 resultJson 形式返回。

验收标准：

- 报告包含统计口径、时间范围、总览、分项统计、建议。
- 无数据时返回明确空态，不生成假报告。

### B5-8. 实现餐食计划接口

目标：

- 支撑计划校验、保存、查询、删除、恢复、购物清单生成。

参考接口：

- `POST /foodmate/meal-plans/validate`
- `POST /foodmate/meal-plans`
- `GET /foodmate/meal-plans/{meal_plan_id}`
- `DELETE /foodmate/meal-plans/{meal_plan_id}`
- `POST /foodmate/meal-plans/{meal_plan_id}/restore`
- `POST /foodmate/meal-plans/{meal_plan_id}/shopping-list`

必须实现：

- 校验人数、天数、预算、目标、约束、计划结构。
- 保存计划前要求有效 planJson。
- 购物清单可先基于 planJson 聚合生成。

验收标准：

- 校验接口返回 valid、issues、nutritionSummary、budgetSummary。
- 保存后可查询。
- 删除与恢复遵循软删除。

### B5-9. 实现管理查询接口

目标：

- 支撑管理后台的概览、用户管理、审计、知识库、工具、模型用量和软删除资源治理。

参考接口：

- `GET /foodmate/admin/overview`
- `GET /foodmate/admin/users`
- `GET /foodmate/admin/users/{user_id}`
- `PATCH /foodmate/admin/users/{user_id}/status`
- `POST /foodmate/admin/users/{user_id}/sessions/reset`
- `GET /foodmate/admin/agent-runs`
- `GET /foodmate/admin/tool-calls`
- `GET /foodmate/admin/sql-audits`
- `GET /foodmate/admin/model-usage`
- `GET /foodmate/admin/knowledge-documents`
- `POST /foodmate/admin/knowledge-documents`
- `PATCH /foodmate/admin/knowledge-documents/{document_id}/status`
- `GET /foodmate/admin/tools`
- `PATCH /foodmate/admin/tools/{name}/status`
- `GET /foodmate/admin/deleted-resources`
- `POST /foodmate/admin/deleted-resources/{resource_type}/{resource_id}/restore`

第一阶段实现：

- 可以先做只读查询和少量状态变更占位。
- `operator` 只能访问只读治理信息。
- `admin` 可访问用户状态、工具启停、知识库写入和软删除恢复等高风险操作。

验收标准：

- 管理接口不对普通用户开放。
- 查询结果支持分页。
- 可以按 status、createdAt、traceId、userId 查询。
- 管理写操作必须记录审计。
- admin 能禁用/启用/锁定用户。
- admin 能恢复软删除资源。

## Phase 6：Agent 编排主链路

### B6-1. 实现 Orchestrator 基础接口

目标：

- 建立 Agent 总调度入口。

必须组件：

- `IntentRouter`
- `TaskPlanner`
- `ExecutionEngine`
- `ResultValidator`
- `AnswerComposer`
- `AgentRuntime`

实现要点：

- 第一阶段可以用规则和 mock 模型，不接真实 LLM。
- Orchestrator 只做编排，不直接访问数据库或供应商 SDK。
- 每一步产生可持久化状态。

验收标准：

- 输入一条用户消息可以生成 AgentRun 状态变化。
- 能输出 planJson 和 resultJson。
- 失败时写 errorCode。

### B6-2. 实现 IntentRouter

目标：

- 将用户输入分类为计算、记录、分析、规划、知识问答或混合任务。

意图类型：

- `calculation`
- `record`
- `analysis`
- `planning`
- `knowledge_qna`
- `mixed`

输出字段：

- intent。
- confidence。
- needRag。
- needTools。
- needClarification。
- missingSlots。
- subIntents。

验收标准：

- 至少覆盖 30 条样例输入。
- 低置信度或缺关键字段时进入 waiting_user。
- 混合任务能识别 subIntents。

### B6-3. 实现 TaskPlanner

目标：

- 为复杂任务生成最小可执行步骤。

实现要点：

- 简单任务不硬拆太多步。
- 规划任务先收集约束。
- 每步包含 id、name、goal、tool、expectedOutput、stopCondition。
- 缺参时不进入工具执行。

验收标准：

- “一周备餐计划”会生成 collect_constraints、generate_plan、validate_plan、generate_shopping_list。
- “20 克鸡胸肉多少卡”只生成必要计算步骤。

### B6-4. 实现 ExecutionEngine

目标：

- 根据计划调用 RAG、Tool、SQL Agent 或 ModelService。

实现要点：

- 串行执行有依赖的步骤。
- 无依赖工具可以并行。
- 每次工具调用写 `tool_calls`。
- 工具失败时根据 retryable 决定是否重试。

验收标准：

- 工具执行状态能反映到 SSE。
- 工具失败不会被 AnswerComposer 假装成功。
- 每次工具调用可回放输入输出摘要。

### B6-5. 实现 ResultValidator

目标：

- 统一校验工具结果、计划约束、写入风险和输出结构。

实现要点：

- 数值结果校验单位和范围。
- 计划结果校验预算、人数、天数、营养约束。
- 写入类操作校验确认状态。
- 最终回答校验必备字段。

验收标准：

- 未确认写入会被拦截。
- plan_validator 返回问题时，最终结果必须展示 issues。
- 输出结构不完整时进入 failed 或 fallback。

### B6-6. 实现 AnswerComposer

目标：

- 把执行结果组装成前端可渲染的结构化答案。

输出字段：

- summary。
- answer。
- steps。
- references。
- warnings。
- nextActions。
- structuredPayload。

验收标准：

- 计算类回答包含数值、单位、口径。
- 知识类回答包含引用。
- 规划类回答包含菜单、购物清单、校验结果。
- 错误类回答包含失败原因和下一步建议。

## Phase 7：工具系统

### B7-1. 实现 ToolRegistry 和 ToolExecutor

目标：

- 建立工具统一注册、查询、执行和审计机制。

必须实现：

- `ToolRegistry`
- `ToolExecutor`
- `ToolContract`
- `ToolResult`
- schema 校验。
- 权限校验占位。
- 超时和重试策略。

验收标准：

- 工具执行前校验工具是否启用。
- 工具入参不符合 schema 时返回 `TOOL_SCHEMA_INVALID`。
- 每次执行写入 `tool_calls`。

### B7-2. 注册 P0 工具元数据

目标：

- 将 MVP 必备工具写入注册体系。

必须工具：

- `calculator`
- `time_parser`
- `knowledge_search`
- `database_query`
- `food_log_writer`
- `plan_validator`

验收标准：

- 每个工具都有 name、displayName、description、version、category、riskLevel、permissions、timeoutMs、retryable、idempotent。
- 高风险写工具 `food_log_writer` 标记为需要确认。

### B7-3. 实现 calculator

目标：

- 支撑确定性数值计算、比例计算、总量汇总。

实现要点：

- 不执行任意代码。
- 只支持安全表达式或明确参数计算。
- 返回 result、unit、formula、warnings。

验收标准：

- 20g 鸡胸肉基础热量换算可通过测试。
- 非法表达式被拒绝。

### B7-4. 实现 time_parser

目标：

- 将“今天、昨天、上周、本月、最近 7 天”等表达转成标准时间范围。

实现要点：

- 支持 timezone，默认 `Asia/Shanghai`。
- 返回 start、end、timezone、sourceText。
- 当前日期由服务端注入，方便测试。

验收标准：

- 最近 7 天、昨天、上周、本月测试通过。
- 不明确时间返回 needClarification。

### B7-5. 实现 food_log_writer

目标：

- 受控写入饮食记录。

实现要点：

- 必须校验 mealTime、items、amount、unit。
- 写入前必须确认。
- 支持幂等键。
- 写入后返回 foodLogId 和摘要。

验收标准：

- 未确认写入被拒绝。
- 重复幂等请求不会重复写入。
- 写入记录可在 food_logs 查询到。

### B7-6. 实现 plan_validator

目标：

- 校验餐食计划是否满足预算、人数、营养和约束。

实现要点：

- 输入 plan、budget、people、days、constraints。
- 输出 valid、issues、nutritionSummary、budgetSummary。
- 第一版可先实现规则校验，不接复杂优化器。

验收标准：

- 超预算计划返回 issue。
- 缺少天数或餐次返回 issue。
- 合法计划返回 valid=true。

## Phase 8：RAG 与知识库

### B8-1. 实现 QueryUnderstandingService

目标：

- 把用户问题转为可检索、可过滤、可审计的查询结构。

输出字段：

- originalQuery。
- resolvedQuery。
- keywordQuery。
- semanticQuery。
- entities。
- filters。
- aclFilter。
- confidence。

验收标准：

- “这个要多久”可结合上下文改写为明确 query。
- 无法消歧时返回 needClarification。
- 不凭空添加用户未说过的硬约束。

### B8-2. 实现 KnowledgeSearchService

目标：

- 打通知识检索主入口。

第一阶段：

- 可先使用 mock 或 PostgreSQL 文本检索。

后续阶段：

- 接 Milvus dense + sparse hybrid search。
- 接 rerank。
- 接 ACL filter。

验收标准：

- 有命中时返回 hits。
- 无命中时返回空结果。
- 返回结果包含 title、snippet、score、metadata。

### B8-3. 实现 CitationAssembler

目标：

- 把检索结果转成前端可展示引用。

输出字段：

- documentId。
- chunkId。
- title。
- snippet。
- score。
- metadata。

验收标准：

- AnswerComposer 可以直接消费 references。
- 引用片段不为空。
- 不展示被软删除或无权限的文档。

### B8-4. 建立知识库构建 Worker 占位

目标：

- 为后续文档解析、切块、索引准备异步任务边界。

必须预留：

- 文档上传事件。
- 解析任务。
- chunk 写入。
- embedding 任务。
- Milvus metadata 下线任务。

验收标准：

- Worker 模块能接收 mock job。
- 文档状态可从 uploaded 变为 parsed/indexed 或 failed。

## Phase 9：SQL Agent 与 MCP 数据查询

### B9-1. 实现 Schema Catalog 查询

目标：

- 让 SQL Agent 能读取可用数据源和字段说明。

必须实现：

- `data_sources` 查询。
- `schema_catalogs` 查询。
- 敏感字段标记。
- 数据源 readonly 标记。

验收标准：

- 能按 datasourceId 查询字段目录。
- 敏感字段不会默认暴露给普通查询。

### B9-2. 实现 SQL Planner 占位

目标：

- 根据用户问题和 schema 生成只读 SQL 草稿。

第一阶段：

- 可使用规则或 mock SQL。

后续阶段：

- 接 ModelService structuredOutput。

硬规则：

- 只允许 `SELECT` 或 `WITH ... SELECT`。
- 必须带 LIMIT。
- 必须注入 user/tenant filter。

验收标准：

- 分析最近 7 天蛋白质摄入可生成只读 SQL 草稿。
- 缺时间范围时返回 needClarification。

### B9-3. 实现 SQL Guard

目标：

- 拦截危险 SQL。

必须拦截：

- `INSERT`
- `UPDATE`
- `DELETE`
- `DROP`
- `ALTER`
- `TRUNCATE`
- 多语句。
- 注释穿透。
- 未注册表。
- 敏感字段越权。

验收标准：

- 写 SQL 被拒绝。
- 不带 LIMIT 的查询被拒绝或自动补 LIMIT。
- 拒绝原因写入 sql_query_audits。

### B9-4. 实现 Readonly SQL Executor / MCP 占位

目标：

- 通过受控入口执行只读 SQL。

实现要点：

- 第一版可以使用内部 readonly executor。
- 后续替换为 MCP executor。
- 强制超时和行数限制。

验收标准：

- 只读 SQL 可执行并返回 rows。
- 超时返回结构化错误。
- 每次执行都有 sqlAuditId。

### B9-5. 实现 database_query 工具

目标：

- 将结构化数据查询暴露为 Agent 可调用工具。

链路：

`database_query -> SQL Agent -> Schema Catalog -> SQL Planner -> SQL Guard -> Readonly Executor -> Audit`

验收标准：

- 输入自然语言问题后返回 rows 和 sqlAuditId。
- SQL 被拒绝时返回 `SQL_READONLY_VIOLATION` 或对应错误。
- AgentRun 能记录这次工具调用。

## Phase 10：ModelService 与模型治理

### B10-1. 实现 ModelService 接口

目标：

- 统一业务侧模型调用入口。

必须方法：

- `chat`
- `chatStream`
- `structuredOutput`
- `toolCall`
- `embed`
- `rerank`

第一阶段：

- 可使用 mock provider。

验收标准：

- 业务模块不直接依赖供应商 SDK。
- 每次调用返回 provider、modelName、usage、latencyMs、cost、status。

### B10-2. 实现模型调用日志

目标：

- 记录模型成本、耗时、状态和错误。

必须记录：

- requestId。
- traceId。
- scene。
- providerCode。
- modelName。
- usageJson。
- latencyMs。
- costAmount。
- status。

验收标准：

- mock 调用也能写 model_usage_logs。
- 失败调用记录错误状态。

### B10-3. 实现 Prompt 目录与加载约定

目标：

- 避免 Prompt 散落在 Java 常量里。

建议目录：

- `prompts/system/`
- `prompts/router/`
- `prompts/planner/`
- `prompts/query-understanding/`
- `prompts/sql-agent/`
- `prompts/validator/`
- `prompts/composer/`

验收标准：

- Prompt 文件带 name、version、owner。
- ModelService 或 PromptLoader 可以按名称和版本加载 Prompt。

## Phase 11：业务能力闭环

### B11-1. 打通计算场景

目标：

- 用户问热量或单位换算时，能走 Agent + 工具闭环。

链路：

`Message -> AgentRun -> Router(calculation) -> calculator/time_parser/nutrition_lookup占位 -> Composer -> SSE/Result`

验收标准：

- “20 克鸡胸肉多少卡”能返回数值、单位、口径。
- 工具调用记录可查询。
- 结果不靠模型硬猜。

### B11-2. 打通饮食记录场景

目标：

- 用户可以通过自然语言记录饮食。

链路：

`Router(record) -> 缺参追问 -> 确认卡 -> food_log_writer -> food_logs -> Composer`

验收标准：

- 缺少食物或份量时追问。
- 写入前确认。
- 写入后返回 foodLogId。

### B11-3. 打通摄入分析场景

目标：

- 用户可以分析最近一段时间的摄入。

链路：

`Router(analysis) -> time_parser -> database_query/food_logs summary -> Composer`

验收标准：

- “分析我最近一周蛋白质摄入”返回时间范围、总量、趋势和建议。
- 无记录时返回空态。
- 统计口径清楚。

### B11-4. 打通知识问答场景

目标：

- 用户可以问烹饪或营养知识，并看到引用。

链路：

`Router(knowledge_qna) -> QueryUnderstanding -> knowledge_search -> CitationAssembler -> Composer`

验收标准：

- “西兰花焯水多久”返回结论和引用。
- 检索为空时不编造来源。

### B11-5. 打通备餐规划场景

目标：

- 用户可以生成可执行备餐计划。

链路：

`Router(planning) -> collect_constraints -> meal_plan_generator占位 -> plan_validator -> shopping_list_generator占位 -> Composer`

验收标准：

- 缺预算、忌口、目标时先追问。
- 输出分日菜单、购物清单、预算估算和校验结果。
- 保存计划前需要确认。

## Phase 12：测试、评估与可观测性

### T12-1. 建立后端单元测试基线

目标：

- 覆盖核心纯逻辑和规则。

必须覆盖：

- ID 序列化。
- 参数校验。
- 状态流转。
- 软删除。
- time_parser。
- calculator。
- SQL Guard。
- Tool schema 校验。

验收标准：

- 单元测试可本地运行。
- 新增核心模块必须带测试。

### T12-2. 建立 API 集成测试

目标：

- 验证主要 REST API 行为。

必须覆盖：

- 创建会话。
- 发送消息。
- 查询消息。
- 查询 AgentRun。
- 创建饮食记录。
- 查询饮食汇总。
- 校验计划。
- 软删除与恢复。

验收标准：

- 集成测试可一键执行。
- 错误响应格式一致。

### T12-3. 建立 SSE 测试

目标：

- 验证流式事件完整性。

必须覆盖：

- 正常 completed 流程。
- failed 流程。
- cancelled 流程。
- 客户端断开。

验收标准：

- 事件顺序符合接口文档。
- 每个事件包含 runId、timestamp、payload。

### T12-4. 建立 Agent 回归样例集

目标：

- 让 Agent 行为可以回归评估。

样例分类：

- 计算类 20 条。
- 记录类 20 条。
- 分析类 20 条。
- 规划类 20 条。
- 知识问答 20 条。
- 边界与缺参 20 条。

每条样例包含：

- 输入。
- 期望 intent。
- 是否需要追问。
- 期望工具。
- 期望输出结构。

验收标准：

- Router 可以跑样例集。
- 工具选择可以统计准确率。
- 回归结果可保存。

### T12-5. 建立日志、指标和 Trace

目标：

- 让任务可观测、可排障、可审计。

必须记录：

- requestId。
- traceId。
- sessionId。
- agentRunId。
- intent。
- toolName。
- modelName。
- latencyMs。
- errorCode。

验收标准：

- 单次用户消息可以串起 API、AgentRun、ToolCall、ModelCall、SQLAudit。
- 失败时可以定位到具体工具或模型调用。

## Phase 13：部署与本地开发环境

### O13-1. 建立本地 Docker Compose

目标：

- 准备后端本地依赖。

建议服务：

- PostgreSQL。
- Redis。
- Milvus。
- MinIO。
- RocketMQ。

验收标准：

- 一条命令启动本地依赖。
- README 写清启动和停止方式。
- 不提交真实账号密钥。

### O13-2. 建立初始化脚本

目标：

- 本地环境可以快速初始化。

必须包含：

- 数据库迁移。
- 默认用户或测试用户。
- P0 工具注册种子数据。
- 示例知识文档 metadata。

验收标准：

- 新开发者可以从空库启动项目。
- 初始化后能跑通会话和 mock AgentRun。

### O13-3. 建立后端启动与健康检查

目标：

- 让服务具备基本运行可见性。

必须实现：

- `/actuator/health` 或等价健康检查。
- 数据库连接检查。
- Redis 连接检查。
- Milvus/MinIO/RocketMQ 可选检查。

验收标准：

- 本地启动后健康检查返回可读状态。
- 依赖缺失时错误信息清晰。

## 第一版推荐执行顺序

1. `D0-1` 到 `D0-2`：完成文档和原型入口。
2. `F1-1` 到 `F2-5`：完成前端静态原型、mock 交互、个人资料页和管理后台 mock 入口。
3. `B3-1` 到 `B3-5`：完成后端工程骨架和通用能力。
4. `B4-1` 到 `B4-8`：完成数据库和持久化基础。
5. `B5-0` 到 `B5-4`：完成认证、个人资料、会话、消息、AgentRun、SSE 主链路。
6. `B6-1` 到 `B6-6`：完成 Agent 编排骨架。
7. `B7-1` 到 `B9-5`：完成工具、RAG、SQL Agent 主能力。
8. `B10-1` 到 `B10-3`：完成模型服务和 Prompt 工程化。
9. `B11-1` 到 `B11-5`：打通 5 个业务场景闭环。
10. `T12-1` 到 `O13-3`：补齐测试、可观测性和本地部署。

## MVP 完成定义

MVP 不是“所有文档都写完”，而是以下链路可运行、可演示、可验收：

- 用户能进入首页和会话页。
- 用户能登录或使用测试用户，并能查看个人资料入口。
- 用户能创建会话并发送消息。
- 后端能创建 `Session`、`Message`、`AgentRun`。
- 后端能校验 Access Token，Refresh Token 可撤销。
- 前端能接收 SSE 运行事件。
- 普通用户不能访问管理后台；管理员能查看基础管理概览。
- Router 能识别计算、记录、分析、规划、知识问答。
- calculator、time_parser、knowledge_search、database_query、food_log_writer、plan_validator 至少有可用实现或可替换 stub。
- 饮食记录写入前必须确认。
- 知识问答有命中时展示引用，无命中时不编造。
- 所有工具调用、SQL 查询、模型调用都有 trace 或审计记录。
- README 能说明如何启动、如何验证、下一步做什么。

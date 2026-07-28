# FoodMate 完整功能实施 TODO

## 1. 文档目的

本文定义 FoodMate 从当前工程状态走向可正式交付产品的总待办清单。它明确产品边界、阶段目标、依赖、风险和完成门槛；具体框架、库、表字段和接口细节以实施时评审为准。

本文不替代现有 ADR、外部 API 契约、Java/Python 内部契约和数据库设计。发生冲突时，优先级为：实际代码与测试事实 > ADR/契约 > 本 TODO > 其他设计文档。

## 当前执行状态

| 阶段 | 当前结论 | 说明 |
|---|---|---|
| M0 | 最小可验证基线已完成 | 数据库、真实持久化和安全配置已有实现与验证；下方未勾选项只表示环境隔离、生产复验等强化工作。 |
| M1-1 | 已完成 | 账户、授权与个人数据能力已有真实实现和验收记录。 |
| M1-2 | 已完成 | 真实认证、会话、消息、前端 API 接入和 Cookie/CSRF 已验收。 |
| M1-3 | 最小真实闭环已完成 | Java -> Python 确定性 stub -> Java -> SSE、取消、续传和越权校验已验证。 |
| M1-4 | 实现中 | 已落地受控模型适配、原生 LangGraph 白名单图、Eval/预算、Redis 准入、超时释放、摘要 CAS 和记忆候选；真实云联调、真实 Proposal/Result 往返、故障注入和完整质量门禁仍未完成。 |

## 2. 已确认的产品边界

| 项目 | 当前决策 |
|---|---|
| 产品定位 | 饮食与营养辅助工具，不是医疗诊断、治疗、处方或紧急健康决策系统。 |
| 租户模型 | V1 为单租户正式系统；保留 `tenant_id` 扩展位，但不实现多组织隔离。 |
| 客户端 | V1 以 Web 浏览器为正式入口；原生 App、第三方开放平台和 OAuth2/OIDC 后置。 |
| 数据权威 | Java 是用户、授权、业务数据、工具执行、SQL 执行和审计的唯一权威。 |
| Agent 边界 | Python 只负责 Agent 编排、模型调用、检索编排和 proposal 生成，不直连业务数据库。 |
| 模型调用 | 允许第三方云模型；必须最小化传输、脱敏、可审计且可替换。 |
| 写操作 | 默认需用户确认；明确、低风险且参数完整的创建操作可直接执行；修改、删除、批量和覆盖必须二次确认。 |
| 隐私权利 | V1 包含会话撤销、数据导出、内容软删除、账号注销申请和延迟物理清理。 |
| 数据库变更 | 所有 SQL 均人工执行；Java 不自动执行建表、迁移或回滚。 |

## 3. 当前事实与主要缺口

当前已具备的基础：

- PostgreSQL FoodMate 已执行基线及 V2-V6 追加迁移；账号、会话、消息、continuation、预算基础结构与 MQ 运行时表均已用于真实联调。
- Java 已实现账号、认证会话、会话、消息、AgentRun、dispatch outbox、事件 inbox、取消和 SSE。
- Python agent-runtime 已实现 V1 Service JWT、RocketMQ command/event、Redis Inbox/Outbox 与确定性 stub；M1-3 HTTP 回调仅保留兼容和契约测试用途。
- 前端真实模式已接入认证、会话、消息、AgentRun SSE 和取消；知识库、业务工具和部分运营能力仍未接入。

当前不能宣称完成的部分：

- Python Runtime 尚未形成真实模型、Router、Planner、Tool Proposal 和 RAG 的完整生产链路。
- 业务工具、SQL Guard、知识库、文件存储、后台运营、可观测性和发布流程未形成完整闭环。

## 4. 里程碑与发布门槛

| 里程碑 | 目标 | 必须完成 | 不阻塞项 |
|---|---|---|---|
| M0 工程可信基线 | 让真实环境可重复验证 | 数据库手工脚本、Java 真实连接、测试与配置门禁、密钥边界 | 复杂业务能力 |
| M1 正式核心版 | 用户可安全使用核心饮食助手 | 认证、会话、真实 Agent、饮食记录、分析、计划、确认、审计、前端真实接入、部署与监控 | SQL Agent、完整 RAG、运营后台深度能力 |
| M2 扩展能力版 | 完成受控数据与知识能力 | RAG、文件知识库、Java Tool Gateway、只读 SQL Agent、管理后台、成本治理 | 原生 App、开放平台、多租户 |
| M3 生产强化版 | 可持续运维与扩展 | 压测、灾备、告警、数据生命周期自动化、安全演练、发布回滚演练 | 新功能扩张 |

## 5. M0：工程可信基线

状态说明：M0 的本地最小基线已经通过并有功能实现说明。以下清单将“已验证基础”和“生产强化”分开记录，不能因为仍有生产强化项就把 M0 解释为从未完成，也不能因为本地验证通过就宣称具备生产发布条件。

### M0-1 数据库与本地环境

- [x] 固定 `script/sql/FoodMate/baseline`、`migration`、`rollback` 目录，迁移、校验与回滚脚本按版本管理。
- [x] 建立人工执行、执行前备份和执行后校验流程；Java 各环境关闭 Flyway 自动迁移。
- [x] 已用数据库脚本测试和本地 PostgreSQL E2E 验证核心表、索引、约束及软删除基础语义。
- [x] 清理应用内过时迁移资源，Java 启动不自动执行建表、迁移或回滚。
- [ ] 在独立测试、预生产和生产环境完成数据库隔离、备份恢复及人工执行记录演练；禁止复用生产数据做本地调试。
- [ ] 扩大 PostgreSQL 集成测试，完整覆盖全部中文注释、软删除恢复和每个后续迁移的回滚前置条件。

风险：手工 SQL 容易遗漏执行、执行顺序错误或环境漂移。控制方式：每份脚本必须有校验查询、执行记录、版本号和回滚说明。

### M0-2 Java 真实持久化验证

- [x] 用 `local` profile 启动 Java，验证连接 `FoodMate` 且不自动运行 SQL。
- [x] 跑通注册、登录、登出、Cookie/CSRF、个人资料、会话创建、消息写入与持久化恢复读取。
- [x] 正式路径使用 Repository/JDBC 持久化，内存实现只用于 `local-stub`。
- [ ] 为并发消息序号、唯一用户名/邮箱、会话撤销和幂等写入补充数据库级测试。

风险：内存与数据库双写产生数据不一致；并发请求造成消息序号或幂等冲突。控制方式：单一权威存储、事务、唯一约束和冲突错误映射。

### M0-3 安全与配置基线

- [x] 完成环境变量、Secret 注入、日志脱敏、错误输出和前端环境变量基础边界。
- [x] 验证 Service JWT 签名、`kid`、过期时间、受众和 scope，并建立缺失配置时的启动拒绝门禁。
- [x] 完成 Web 会话 HttpOnly、Secure 配置、SameSite、CSRF 和会话撤销测试。
- [x] 建立开发弱配置与生产启动拒绝的自动化配置矩阵。
- [ ] 使用真实 prod Secret、正式域名和跨源浏览器环境完成生产级复验与 `kid` 轮换演练。

风险：真实密码、私钥、会话 token 或模型输入泄漏。控制方式：Secret 管理、禁止日志输出、启动校验、依赖漏洞扫描和最小权限。

## 6. M1：正式核心版

### M1-1 账户、授权与个人数据

- [x] 完成注册、登录、登出、当前用户、密码变更、密码重置和设备会话管理。
- [x] 完成 `user/admin/operator` RBAC，资源查询校验当前用户归属，operator 保持只读。
- [x] 完成个人资料、营养偏好、过敏原、忌口和单位偏好管理。
- [x] 完成头像 MinIO 私有对象存储、文件类型/大小校验、替换和删除流程。
- [x] 完成数据导出、账号注销申请、立即禁用、会话全部撤销和异步物理清理。

边界：不实现第三方登录、原生 App 登录或开放 API token。

### M1-2 会话、消息与前端真实接入（已完成）

- [x] 统一前端 HTTP Client、错误码、Cookie 认证、CSRF 头、401 刷新与 403 展示策略。
- [x] 用真实 API 替换登录、个人资料、会话列表、消息列表和消息发送 mock。
- [x] 实现会话重命名、归档、软删除、恢复、分页和搜索；限制用户只能访问本人资源。
- [x] 实现消息稳定排序、分页、重试和附件入口的清晰降级状态。
- [x] 完成前端路由守卫、未授权跳转、加载/空态/错误态和网络中断处理。

风险：Cookie 跨域、CSRF、刷新并发和 SSE 断线重连容易造成隐性安全或体验缺陷。控制方式：浏览器 E2E、契约测试和跨浏览器验证。

### M1-3 Java 权威 AgentRun 与 SSE（最小真实闭环已完成）

- [x] 统一 AgentRun 状态机、合法状态转换、失败码与取消语义；超时的生产级收敛留待后续强化。
- [x] 完成 Java dispatch、cancel、事件 inbox、事件去重、缺口/乱序拒绝与终态保护。
- [x] 将事件持久化、AgentRun 更新和 SSE outbox 纳入事务边界。
- [x] 实现 SSE 订阅、断线恢复、事件序号、客户端取消和资源释放。
- [ ] 补齐超时、网络失败、Python 不可用等完整故障注入与浏览器级 E2E；不阻塞最小闭环结论。

边界：Java 不替代 Python 推理；Python 不直接修改 AgentRun 或业务表。

### M1-4 Python Agent Runtime 与模型能力（实现中）

当前已完成的基础闭环：Java PostgreSQL Dispatch Outbox -> RocketMQ command -> Python Redis Inbox -> 确定性 stub -> Redis Event Outbox -> RocketMQ event -> Java PostgreSQL Inbox/AgentRun/SSE Outbox。以下清单只记录尚未完成的 M1-4 Agent 能力，不把这次传输闭环重复列为待办。

M1-4 前置门禁已完成：Python pytest 通过，Java 全模块 Maven 测试通过，Compose 示例配置校验通过；本地单 NameServer、单 Broker、Proxy、四个 Agent Topic 和 Redis/PostgreSQL 依赖均已启动并完成一次真实消息往返。历史过期 Outbox/Run 只保留为故障验证记录，不作为当前闭环成功依据。

- [x] Java PostgreSQL AgentRun/Dispatch/Outbox -> RocketMQ command。
- [x] Python Redis Inbox 幂等消费 -> 确定性 stub -> Redis Event Outbox -> RocketMQ event。
- [x] Java PostgreSQL Event Inbox/AgentRun/SSE Outbox 消费落库，重复消息和 request hash 冲突有自动化测试。
- [x] 本地 RocketMQ Topic/consumer group 初始化、Compose 配置和 Java/Python 基础测试门禁。

- [x] 固定 Python 版本与依赖、配置加载、健康检查、结构化日志和 pytest 基础门禁；真实模型依赖锁随 Agent 能力实现继续收紧。
- [x] 在现有 Compose 中接入本地单 NameServer + 单 Broker，并初始化 command/event/proposal/result 四个 Agent Topic；本阶段不建设生产高可用集群。
- [x] 实现 Java PostgreSQL Outbox Relay、MQ Event Consumer、Inbox 事务和基础 DLQ 对账；Proposal/Result 业务处理随 Tool/SQL 阶段补齐。
- [x] 实现 Python Redis AOF Inbox、Event Outbox 与 Relay；Redis 不可用时停止消费。Proposal Outbox 和 LangGraph checkpoint 原子写入仍属下列 Agent 能力任务。
- [x] 使用 LangGraph 完成 Router、Planner、Execution、Composer、Final Eval Gate 和 Terminal Arbiter 的原生白名单图包装；仍未完成 Reflector 和完整 Step Validator。
- [x] 完成短期记忆 Context Builder：Java 装配最近 8 条有效消息、摘要、长期记忆和来源 ID，Python 执行上下文 Token 裁剪；摘要删除重建仍未完成。
- [x] 完成摘要压缩：第 9 条有效消息写入后增量更新摘要；摘要保存覆盖消息范围、来源数量、Prompt 版本和 digest，并使用版本/CAS 防止并发覆盖。当前为确定性短摘要，摘要模型替换和更正后的自动重建仍需强化。
- [x] 完成摘要失效与重建的最小链路：消息被删除或更正后摘要失效，下一次超过 8 条有效消息时从权威消息重建；摘要缓存和长期缓存联动仍需强化。
- [x] 完成长期记忆候选链路：Python 只产生带来源、类型、置信度、作用域和有效期的候选，Java 校验后写入 `user_memories`，不得把模型推测、一次性参数、审批或医疗判断自动记忆。
- [x] 提供长期记忆查看、更正、删除和冲突确认 API；冲突记忆默认不进入 Agent Context，用户确认后才恢复可用。
- [ ] 删除或更正长期记忆后同步失效所有相关摘要、缓存和历史 Context 引用；当前已完成记忆逻辑删除，摘要关联失效仍需继续接入。
- [ ] 为每次 Context 装配保存可审计来源 ID：`message_id/summary_id/memory_id/citation_id`，但不得保存 Chain-of-Thought 或完整 Prompt。
- [x] 完成 Redis 协调：用户默认最多 2 个 Session 并发、全局默认 20 个 active Run、全局队列默认 100；同 Session 单 active Run 由 PostgreSQL 保证，不创建 Session 级 Redis permit。当前已接入 Lua/ZSET lease，未引入进程内 semaphore。
- [ ] 完成生产级优先队列、permit lease、aging、防饥饿和 Redis 故障关闭；当前已实现有限 priority + FIFO aging 基础和协调不可用 503，仍缺 Redis 故障注入与长期防饥饿验证。
- [x] 完成 queue、execution、node、waiting_user、cancel drain 超时，Run 接受时固化 `TimeoutSnapshot`，取消或超时后可靠释放 permit。当前已实现 queue/execution 扫描和终态释放，node/cancel drain 的独立执行器与 waiting_user 专用 deadline 仍需强化。
- [x] Python 已接入供应商无关的受控模型适配器，支持逻辑层级路由、兼容云端点、超时/限流 fallback、用量采集与失败归因；真实云凭据和联调仍待补充。
- [ ] 完成 Token/成本预算快照、70%/85%/100% 分级降级和用户显式追加预算；每次追加生成新 revision 和 dispatch attempt。
- [ ] 完成 Redis checkpoint 的 AOF、CAS、TTL、加密和 Java 对账；简单问答可不落 checkpoint，复杂、暂停、工具和 Eval 任务保存关键恢复点。
- [ ] 建立确定性硬规则、分级 LLM Judge、Prompt 模板版本、离线 golden 样例、回归评测和安全策略测试；Eval 通过前不得发送候选答案正文。
- [ ] Eval 通过后按可配置 150ms/2048 字节默认阈值切分回答事件，禁止逐 Token 发布 RocketMQ。
- [ ] 当前无人审核时，`request_review` 必须返回安全降级答案并记录原因，不新增虚假的 `waiting_review`。
- [x] 普通缺参补充创建 continuation Run，旧 Run 进入 `superseded`，并完成 V5、Java 事务、SSE 和前端状态映射。
- [x] 工具审批和预算追加恢复原 Run、创建新 `dispatch_id + attempt`，并完成预算确认前端交互与恢复测试。预算追加已接入 Redis 准入；工具审批仍是后续 proposal 能力。
- [ ] 完成结构化 Trace、预算与 Eval 指标、脱敏策略和用户反馈入口；不得保存 Chain-of-Thought、完整 Prompt 或默认原始模型响应。
- [x] 只允许 Python 产生 Tool/SQL Proposal；Java Tool Gateway 不向 Python 暴露 PostgreSQL 业务库凭据。
- [x] Java 已接入独立 Proposal consumer、只读 SQL Guard、审计和 Result producer；`runtime_tool_proposal_inbox` 固化 `proposal_id + request_hash` 幂等事实。
- [x] Python Result consumer 已接入 Redis 幂等 Inbox；Java command RocketMQ 真实传输 E2E 已通过。
- [ ] Python Proposal publisher/Result 业务往返、只读数据库账号、业务链路故障注入和真实云模型仍待完成。

M1-4 的上述治理项均属于最小真实模型闭环的完成门槛，不得把“能调用一次模型”标记为 M1-4 完成。状态、wire 和数据库扩展必须先更新契约与迁移，再进入实现。

风险：模型幻觉、供应商故障、成本失控和敏感数据外发。控制方式：输入最小化、脱敏、预算/频率限制、模型日志摘要、降级回答和人工可追溯。

### M1-5 核心饮食业务与工具确认

- [ ] 实现饮食记录创建、查询、聚合、编辑、删除、恢复与幂等键。
- [ ] 实现基础营养分析报告，明确统计口径、无数据空态和非医疗免责声明。
- [ ] 实现餐食计划校验、保存、查询、修改、删除、恢复和购物清单生成。
- [ ] 建立写操作确认卡片、确认/拒绝/超时语义、审计记录和可重放结果。
- [ ] 提供 `food_log_writer`、计划生成等最小 Java 工具，并由策略层校验用户、参数、风险和幂等。

边界：不把模型输出直接写数据库；不承诺疾病诊断、处方或紧急饮食方案。

### M1-6 审计、可观测性与核心部署

- [ ] 记录认证、写工具调用、模型调用摘要、AgentRun、错误和管理员操作审计。
- [ ] 提供结构化日志、请求 ID、trace ID、健康/就绪检查、核心指标和告警规则。
- [ ] 建立 Java、Python、PostgreSQL、前端的环境配置、镜像构建、发布顺序和回滚说明。
- [ ] 建立备份、恢复演练、最小容量估算、限流和异常降级策略。

发布门槛：核心用户路径可在真实环境重复跑通；关键安全、权限、写确认、数据持久化、取消/超时、浏览器 E2E 和恢复演练均有通过证据。

## 7. M2：扩展能力版

### M2-1 知识库与 RAG

- [ ] 接入对象存储、文档上传、解析、分块、版本、删除、恢复和异步索引任务。
- [ ] 接入向量库与关键词检索，建立 metadata 权限过滤、引用返回、索引失败重试和下线清理。
- [ ] 将 RAG 引用展示接入前端，明确无命中时不编造引用。
- [ ] 为文档类型、恶意文件、版权来源、个人信息和索引成本建立策略。

风险：未授权文档泄露、过时引用、索引任务堆积和存储成本。控制方式：权限元数据、版本化、队列监控、配额和数据保留策略。

### M2-2 Java Tool Gateway 与 SQL Agent

- [ ] 实现 Tool Registry、版本、输入输出 Schema、scope、风险等级、启停、超时、重试和幂等策略。
- [ ] 完成 Proposal -> Policy -> Confirm -> Execute -> Audit 的 Java 受控执行链路。
- [ ] 实现只读 SQL Guard：AST 解析、单语句、只读、schema/字段白名单、敏感字段遮蔽、用户过滤、行数与超时限制。
- [ ] 实现 SQL 审计、结果脱敏、错误分类、攻击样例和越权回归测试。

边界：Python 只能提议，不能访问数据源账号或绕过 Java Policy；SQL Agent 不允许任何写操作。

### M2-3 管理后台与模型治理

- [ ] 用真实接口替换用户、AgentRun、工具、知识库、审计、软删除资源和模型用量页面 mock。
- [ ] 实现分页、筛选、权限、状态变更、审计追踪和高危操作二次确认。
- [ ] 实现模型供应商、模型路由、预算、配额、成本汇总和异常告警。
- [ ] 为管理员/运营人员增加最小权限、操作留痕和导出控制。

## 8. M3：生产强化版

- [ ] 完成接口、SSE、模型调用、数据库和队列的压测与容量基线。
- [ ] 完成依赖漏洞扫描、密钥轮换、权限审计、渗透测试和安全事件预案。
- [ ] 完成数据库备份恢复、跨环境迁移、灾难恢复和发布回滚演练。
- [ ] 完成数据保留/硬删除任务、失败重试、死信处理和定期审计。
- [ ] 完成浏览器兼容性、可访问性、移动 Web 适配和性能优化。

## 9. 跨阶段质量门禁

- [ ] Java：单元测试、PostgreSQL 集成测试、架构依赖测试、API 安全测试全部通过。
- [ ] Python：pytest、契约测试、离线评测、无业务数据库凭据检查全部通过。
- [ ] 前端：lint、typecheck、单元测试、API 契约测试、浏览器 E2E 全部通过。
- [ ] 数据库：每次人工执行脚本均有执行人、环境、版本、校验结果、备份位置和回滚结论。
- [ ] 安全：权限越权、CSRF、会话撤销、敏感日志、模型输入脱敏、Tool/SQL 绕过均有回归用例。
- [ ] 发布：上线前必须完成 Smoke、监控检查、错误预算检查和回滚演练；没有实际证据不得标记完成。

## 10. 明确后置的事项

- 原生 iOS/Android 客户端。
- 对外开放 API、第三方开发者平台、OAuth2/OIDC。
- 多租户 SaaS 的组织、账单、隔离与运营能力。
- 医疗诊断、治疗、处方、紧急建议和医疗器械相关能力。
- 任何绕开 Java 授权层、让 Python 直接读写业务数据库的设计。

## 11. 推荐执行顺序

1. M0-1 至 M0-3 的本地最小可信基线已完成；剩余生产复验和隔离演练与后续发布准备并行推进。
2. M1-1、M1-2，先完成真实账号、会话和前端接入。
3. M1-3、M1-4，完成 Java/Python Agent 可靠主链路。
4. M1-5、M1-6，完成用户能感知的核心业务、审计和部署。
5. M2-1 至 M2-3，逐步扩展知识、工具、SQL 和运营能力。
6. M3，在实际用户量和运维需求出现后强化可靠性与安全性。

每个小项开始前只需补充该项的接口、数据和验收细节；未完成其前置依赖，不应并行推进高层功能。

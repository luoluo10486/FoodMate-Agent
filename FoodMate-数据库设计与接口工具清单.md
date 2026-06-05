# FoodMate 数据库设计与接口工具清单

版本：v1.0  
对应总设计：[FoodMate-系统设计与技术方案.md](./FoodMate-系统设计与技术方案.md)  
对应工程文档：[FoodMate-Java工程骨架与模块设计.md](./FoodMate-Java工程骨架与模块设计.md)  
对应接口文档：[FoodMate-接口与数据规范.md](./FoodMate-接口与数据规范.md)

文档定位：本文件是 FoodMate 的数据与接口落地蓝图，只回答“表怎么建、接口怎么开、工具怎么注册、状态机和错误码怎么定”。产品范围、模型网关主职责、工具优先级和查询主路径，以总设计文档为准。

---

## 0. 存储总览

### 0.1 存储分工

| 组件 | 角色 | 负责内容 |
|---|---|---|
| PostgreSQL | 事务主库 | 用户、会话、消息、AgentRun、ToolCall、FoodLog、MealPlan、审计、配置 |
| Redis | 缓存与短期状态 | 热会话、短期上下文、幂等键、限流计数 |
| Milvus | 知识检索底座 | Dense vector、BM25/sparse、hybrid search、知识片段 metadata |
| MinIO | 文档对象存储 | 原始文档、附件、导入文件、解析中间文件 |

### 0.2 为什么主库选 PostgreSQL

FoodMate 选 `PostgreSQL` 作为事务主库，**不是因为要用 `pgvector`**。  
知识库主检索已经固定由 `Milvus` 承担，所以 PostgreSQL 不承担主向量库角色。

选择 PostgreSQL 的核心原因是：

1. 强事务能力更适合 `Session`、`AgentRun`、`ToolCall`、`FoodLog` 这类状态型数据
2. 复杂关系建模更适合用户、会话、消息、任务、工具调用、审计日志之间的关联
3. `JSONB` 更适合存 `plan_json`、`result_json`、`metadata_json`、`input_schema`、`route_rule`
4. SQL Agent 目标库使用 PostgreSQL 方言更利于复杂查询表达和安全治理
5. 后台统计、审计查询、模型成本分析、任务行为回放更适合 PostgreSQL

### 0.3 为什么不是 MySQL

MySQL 不是不能用，但当前不是推荐主方案，主要原因是：

- JSON 扩展查询与索引体验不如 PostgreSQL 稳定
- SQL Agent 需要较强的复杂查询表达能力
- 审计治理、复杂条件过滤、CTE、窗口函数使用场景更偏 PostgreSQL

结论固定为：

- `PostgreSQL`：事务主库
- `Milvus`：知识检索底座
- `pgvector`：不进入当前主方案

### 0.4 命名约定

数据库设计统一遵循：

- 表名：`snake_case`
- 主键：`id UUID`
- 时间字段：`created_at TIMESTAMPTZ`、`updated_at TIMESTAMPTZ`
- 扩展字段：优先 `JSONB`
- 状态字段：使用 `VARCHAR(32)`，不在第一版引入数据库 enum

---

## 1. PostgreSQL 表设计

### 1.1 通用字段规范

绝大多数业务表建议包含：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `UUID` | 主键，默认 `gen_random_uuid()` |
| `created_at` | `TIMESTAMPTZ` | 创建时间 |
| `updated_at` | `TIMESTAMPTZ` | 更新时间 |
| `created_by` | `UUID` / `VARCHAR(64)` | 创建人或来源系统 |
| `updated_by` | `UUID` / `VARCHAR(64)` | 更新人或来源系统 |

日志型表不要求 `updated_at`，以追加写为主。

### 1.2 会话域

#### `users`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `UUID` | PK | 用户主键 |
| `tenant_id` | `UUID` | IDX | 多租户隔离 |
| `user_no` | `VARCHAR(64)` | UK | 用户业务编号 |
| `nickname` | `VARCHAR(128)` |  | 昵称 |
| `status` | `VARCHAR(32)` | IDX | `active/inactive/locked` |
| `created_at` | `TIMESTAMPTZ` |  | 创建时间 |
| `updated_at` | `TIMESTAMPTZ` |  | 更新时间 |

索引：

- `uk_users_user_no`
- `idx_users_tenant_status`

#### `user_profiles`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `UUID` | PK | 主键 |
| `user_id` | `UUID` | UK, FK | 对应用户 |
| `diet_goal` | `VARCHAR(64)` |  | 增肌/减脂/均衡 |
| `allergens` | `JSONB` |  | 过敏原 |
| `dislikes` | `JSONB` |  | 忌口 |
| `preferred_units` | `JSONB` |  | 单位偏好 |
| `profile_json` | `JSONB` |  | 扩展画像 |
| `updated_at` | `TIMESTAMPTZ` |  | 更新时间 |

#### `sessions`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `UUID` | PK | 会话主键 |
| `tenant_id` | `UUID` | IDX | 租户 |
| `user_id` | `UUID` | IDX, FK | 用户 |
| `title` | `VARCHAR(255)` |  | 会话标题 |
| `mode` | `VARCHAR(32)` | IDX | `agent/chat` |
| `status` | `VARCHAR(32)` | IDX | `active/archived/closed` |
| `last_message_at` | `TIMESTAMPTZ` | IDX | 最近消息时间 |
| `created_at` | `TIMESTAMPTZ` |  | 创建时间 |
| `updated_at` | `TIMESTAMPTZ` |  | 更新时间 |

索引：

- `idx_sessions_user_last_message_at`
- `idx_sessions_tenant_status`

#### `messages`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `UUID` | PK | 消息主键 |
| `session_id` | `UUID` | IDX, FK | 所属会话 |
| `agent_run_id` | `UUID` | IDX | 关联运行 |
| `role` | `VARCHAR(32)` | IDX | `user/assistant/system/tool` |
| `content` | `TEXT` |  | 文本内容 |
| `structured_payload` | `JSONB` |  | 结构化载荷 |
| `sequence_no` | `INT` | IDX | 会话内顺序 |
| `created_at` | `TIMESTAMPTZ` |  | 创建时间 |

索引：

- `idx_messages_session_sequence`
- `idx_messages_session_created_at`

### 1.3 Agent 运行域

#### `agent_runs`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `UUID` | PK | 运行主键 |
| `session_id` | `UUID` | IDX, FK | 所属会话 |
| `user_message_id` | `UUID` | IDX | 触发消息 |
| `intent` | `VARCHAR(64)` | IDX | `record/analysis/planning/knowledge_qna` |
| `status` | `VARCHAR(32)` | IDX | `queued/executing/success/failed/cancelled` |
| `plan_json` | `JSONB` |  | 执行计划 |
| `result_json` | `JSONB` |  | 执行结果 |
| `error_code` | `VARCHAR(64)` |  | 错误码 |
| `trace_id` | `VARCHAR(64)` | IDX | 链路追踪 |
| `created_at` | `TIMESTAMPTZ` |  | 创建时间 |
| `updated_at` | `TIMESTAMPTZ` |  | 更新时间 |

推荐索引：

- `idx_agent_runs_session_created_at`
- `idx_agent_runs_status_created_at`
- `idx_agent_runs_trace_id`

#### `tool_calls`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `UUID` | PK | 主键 |
| `agent_run_id` | `UUID` | IDX, FK | 所属运行 |
| `tool_name` | `VARCHAR(64)` | IDX | 工具名 |
| `tool_version` | `VARCHAR(32)` |  | 工具版本 |
| `input_json` | `JSONB` |  | 入参 |
| `output_json` | `JSONB` |  | 出参 |
| `status` | `VARCHAR(32)` | IDX | `running/success/failed/cancelled` |
| `latency_ms` | `INT` |  | 耗时 |
| `error_code` | `VARCHAR(64)` |  | 错误码 |
| `trace_id` | `VARCHAR(64)` | IDX | 链路追踪 |
| `created_at` | `TIMESTAMPTZ` |  | 创建时间 |

推荐索引：

- `idx_tool_calls_run_created_at`
- `idx_tool_calls_tool_status`
- `idx_tool_calls_trace_id`

### 1.4 业务域

#### `food_logs`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `UUID` | PK | 饮食记录主键 |
| `user_id` | `UUID` | IDX, FK | 用户 |
| `session_id` | `UUID` | IDX | 来源会话 |
| `meal_time` | `TIMESTAMPTZ` | IDX | 用餐时间 |
| `meal_type` | `VARCHAR(32)` | IDX | 早餐/午餐/晚餐/加餐 |
| `items_json` | `JSONB` |  | 食材明细 |
| `nutrition_json` | `JSONB` |  | 汇总营养值 |
| `notes` | `TEXT` |  | 备注 |
| `source` | `VARCHAR(32)` |  | manual/agent/import |
| `created_at` | `TIMESTAMPTZ` |  | 创建时间 |

#### `meal_plans`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `UUID` | PK | 计划主键 |
| `user_id` | `UUID` | IDX | 用户 |
| `session_id` | `UUID` | IDX | 来源会话 |
| `plan_name` | `VARCHAR(128)` |  | 计划名称 |
| `days` | `INT` |  | 覆盖天数 |
| `budget` | `NUMERIC(12,2)` |  | 预算 |
| `constraints_json` | `JSONB` |  | 约束 |
| `plan_json` | `JSONB` |  | 计划详情 |
| `validation_json` | `JSONB` |  | 校验结果 |
| `status` | `VARCHAR(32)` | IDX | draft/validated/saved |
| `created_at` | `TIMESTAMPTZ` |  | 创建时间 |
| `updated_at` | `TIMESTAMPTZ` |  | 更新时间 |

### 1.5 记忆与摘要域

#### `user_memories`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `UUID` | PK | 主键 |
| `user_id` | `UUID` | IDX | 用户 |
| `memory_type` | `VARCHAR(32)` | IDX | preference/constraint/habit |
| `memory_key` | `VARCHAR(64)` | IDX | 键 |
| `memory_value` | `JSONB` |  | 值 |
| `confidence` | `NUMERIC(5,4)` |  | 置信度 |
| `source` | `VARCHAR(32)` |  | 来源 |
| `scope` | `VARCHAR(32)` |  | global/session/task |
| `expires_at` | `TIMESTAMPTZ` |  | 过期时间 |
| `created_at` | `TIMESTAMPTZ` |  | 创建时间 |

#### `session_summaries`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `UUID` | PK | 主键 |
| `session_id` | `UUID` | UK, FK | 会话 |
| `summary_text` | `TEXT` |  | 摘要文本 |
| `key_constraints` | `JSONB` |  | 关键约束 |
| `updated_at` | `TIMESTAMPTZ` |  | 更新时间 |

### 1.6 知识库域

#### `knowledge_documents`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `UUID` | PK | 文档主键 |
| `tenant_id` | `UUID` | IDX | 租户 |
| `source_type` | `VARCHAR(64)` | IDX | cookbook/manual/faq/web |
| `title` | `VARCHAR(255)` |  | 标题 |
| `status` | `VARCHAR(32)` | IDX | uploaded/parsed/indexed/disabled |
| `version` | `VARCHAR(32)` |  | 文档版本 |
| `storage_key` | `VARCHAR(255)` |  | MinIO 对象键 |
| `metadata_json` | `JSONB` |  | 扩展 metadata |
| `created_at` | `TIMESTAMPTZ` |  | 创建时间 |

#### `knowledge_chunks`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `UUID` | PK | Chunk 主键 |
| `document_id` | `UUID` | IDX, FK | 来源文档 |
| `chunk_no` | `INT` |  | 顺序号 |
| `chunk_text` | `TEXT` |  | Chunk 内容 |
| `section_path` | `VARCHAR(255)` |  | 章节路径 |
| `tags` | `JSONB` |  | 标签 |
| `version` | `VARCHAR(32)` |  | chunk 版本 |
| `embedding_id` | `VARCHAR(64)` | IDX | 向量引用 |
| `metadata_json` | `JSONB` |  | ACL、来源、时间等 metadata |
| `created_at` | `TIMESTAMPTZ` |  | 创建时间 |

推荐索引：

- `idx_knowledge_chunks_document_chunk_no`
- `idx_knowledge_chunks_embedding_id`
- `gin_knowledge_chunks_tags`
- `gin_knowledge_chunks_metadata`

### 1.7 数据源与 SQL Agent 域

#### `data_sources`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `UUID` | PK | 数据源主键 |
| `name` | `VARCHAR(128)` | UK | 数据源名 |
| `db_type` | `VARCHAR(32)` | IDX | postgresql/mysql/clickhouse |
| `purpose` | `VARCHAR(128)` |  | 用途 |
| `visibility` | `VARCHAR(32)` | IDX | public/restricted/private |
| `status` | `VARCHAR(32)` | IDX | active/disabled |
| `readonly` | `BOOLEAN` |  | 必须为 true |
| `connection_ref` | `VARCHAR(128)` |  | 连接配置引用 |
| `created_at` | `TIMESTAMPTZ` |  | 创建时间 |

#### `schema_catalogs`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `UUID` | PK | 主键 |
| `datasource_id` | `UUID` | IDX, FK | 数据源 |
| `schema_name` | `VARCHAR(64)` |  | schema |
| `table_name` | `VARCHAR(128)` | IDX | 表名 |
| `field_name` | `VARCHAR(128)` | IDX | 字段名 |
| `field_desc` | `VARCHAR(255)` |  | 字段说明 |
| `data_type` | `VARCHAR(64)` |  | 数据类型 |
| `is_sensitive` | `BOOLEAN` | IDX | 是否敏感 |
| `sample_sql` | `TEXT` |  | 示例 SQL |
| `updated_at` | `TIMESTAMPTZ` |  | 更新时间 |

#### `sql_query_audits`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `UUID` | PK | 审计主键 |
| `session_id` | `UUID` | IDX | 会话 |
| `agent_run_id` | `UUID` | IDX | 运行 |
| `datasource_id` | `UUID` | IDX | 数据源 |
| `original_question` | `TEXT` |  | 原始问题 |
| `resolved_question` | `TEXT` |  | 查询理解结果 |
| `sql_text` | `TEXT` |  | 最终 SQL |
| `status` | `VARCHAR(32)` | IDX | drafted/validated/rejected/executed |
| `reject_reason` | `VARCHAR(255)` |  | 拒绝原因 |
| `row_count` | `INT` |  | 返回行数 |
| `latency_ms` | `INT` |  | 耗时 |
| `trace_id` | `VARCHAR(64)` | IDX | 链路追踪 |
| `created_at` | `TIMESTAMPTZ` |  | 创建时间 |

推荐索引：

- `idx_sql_audits_session_created_at`
- `idx_sql_audits_datasource_status`
- `idx_sql_audits_trace_id`

### 1.8 工具注册域

#### `tool_registries`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `UUID` | PK | 主键 |
| `name` | `VARCHAR(64)` | UK | 工具名 |
| `display_name` | `VARCHAR(128)` |  | 展示名 |
| `description` | `VARCHAR(255)` |  | 描述 |
| `category` | `VARCHAR(32)` | IDX | query/write/generate/validate |
| `risk_level` | `VARCHAR(16)` | IDX | low/medium/high |
| `availability_scope` | `VARCHAR(32)` |  | user/admin/internal |
| `status` | `VARCHAR(32)` | IDX | active/disabled |
| `current_version` | `VARCHAR(32)` |  | 当前版本 |
| `created_at` | `TIMESTAMPTZ` |  | 创建时间 |
| `updated_at` | `TIMESTAMPTZ` |  | 更新时间 |

#### `tool_schema_versions`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `UUID` | PK | 主键 |
| `tool_id` | `UUID` | IDX, FK | 工具 |
| `version` | `VARCHAR(32)` | IDX | 版本 |
| `input_schema` | `JSONB` |  | 入参 schema |
| `output_schema` | `JSONB` |  | 出参 schema |
| `permissions` | `JSONB` |  | 权限 |
| `timeout_ms` | `INT` |  | 超时 |
| `retryable` | `BOOLEAN` |  | 是否可重试 |
| `idempotent` | `BOOLEAN` |  | 是否幂等 |
| `published_at` | `TIMESTAMPTZ` |  | 发布时间 |

### 1.9 模型治理域

#### `model_usage_logs`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `UUID` | PK | 主键 |
| `request_id` | `VARCHAR(64)` | IDX | 请求 ID |
| `trace_id` | `VARCHAR(64)` | IDX | Trace ID |
| `scene` | `VARCHAR(64)` | IDX | 使用场景 |
| `provider_code` | `VARCHAR(64)` | IDX | 供应商 |
| `model_name` | `VARCHAR(128)` |  | 模型名 |
| `usage_json` | `JSONB` |  | token 用量 |
| `latency_ms` | `INT` |  | 耗时 |
| `cost_amount` | `NUMERIC(12,6)` |  | 成本 |
| `status` | `VARCHAR(32)` | IDX | success/failed/fallback |
| `created_at` | `TIMESTAMPTZ` |  | 创建时间 |

#### `model_route_rules`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `UUID` | PK | 主键 |
| `tenant_id` | `UUID` | IDX | 租户 |
| `scene` | `VARCHAR(64)` | IDX | 场景 |
| `model_type` | `VARCHAR(32)` | IDX | chat/embed/rerank |
| `provider_code` | `VARCHAR(64)` |  | 主供应商 |
| `fallback_provider_code` | `VARCHAR(64)` |  | 备供应商 |
| `max_cost` | `NUMERIC(12,6)` |  | 最大成本 |
| `max_latency_ms` | `INT` |  | 最大延迟 |
| `rule_json` | `JSONB` |  | 路由策略 |
| `status` | `VARCHAR(32)` | IDX | active/inactive |
| `created_at` | `TIMESTAMPTZ` |  | 创建时间 |
| `updated_at` | `TIMESTAMPTZ` |  | 更新时间 |

---

## 2. Milvus 知识库结构

### 2.1 Collection 设计

第一版统一使用一个主 collection：

```text
foodmate_knowledge_chunks
```

### 2.2 主字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `chunk_id` | `VARCHAR(64)` | 对应 `knowledge_chunks.id` |
| `document_id` | `VARCHAR(64)` | 对应文档 |
| `tenant_id` | `VARCHAR(64)` | 租户隔离 |
| `dense_vector` | `FLOAT_VECTOR` | 语义向量 |
| `sparse_vector` | `SPARSE_FLOAT_VECTOR` | BM25 稀疏向量 |
| `chunk_text` | `VARCHAR/TEXT` | 文本 |
| `doc_type` | `VARCHAR(64)` | 文档类型 |
| `section_path` | `VARCHAR(255)` | 章节路径 |
| `tags` | `ARRAY/VARCHAR` | 标签 |
| `visibility` | `VARCHAR(32)` | ACL 可见性 |
| `owner_scope` | `VARCHAR(64)` | 所属组织/租户 |
| `version` | `VARCHAR(32)` | chunk 版本 |
| `updated_at` | `INT64` | 更新时间戳 |

### 2.3 ACL metadata

ACL 相关 metadata 固定包含：

- `tenant_id`
- `visibility`
- `owner_scope`
- `role_scope`
- `doc_status`

原则：

- ACL 过滤前置到召回阶段
- 不依赖 rerank 后再做权限兜底

### 2.4 版本与隔离策略

- 同一文档新版本入库时，旧 chunk 不物理覆盖，先标记旧版本失效
- 检索默认只查最新 `active` 版本
- 多租户环境下，检索必须带 `tenant_id`

---

## 3. API 清单

### 3.1 统一约束

- 前缀固定：`/api/v1`
- 鉴权：默认 Bearer Token
- 返回结构：统一 `code/message/data/requestId`
- 写接口需要支持幂等键：`Idempotency-Key`

### 3.2 会话与消息接口

| 方法 | 路径 | 用途 | 鉴权 | 幂等 |
|---|---|---|---|---|
| `POST` | `/api/v1/sessions` | 创建会话 | 用户 | 是 |
| `GET` | `/api/v1/sessions/{id}` | 查询会话 | 用户 | 否 |
| `GET` | `/api/v1/sessions/{id}/messages` | 查询消息列表 | 用户 | 否 |
| `PATCH` | `/api/v1/sessions/{id}` | 修改标题/状态 | 用户 | 是 |
| `POST` | `/api/v1/sessions/{id}/archive` | 归档会话 | 用户 | 是 |

关键请求与响应字段：

- `POST /sessions`
  - 请求：`title`、`mode`
  - 响应：`session_id`、`status`、`created_at`
- `GET /sessions/{id}/messages`
  - 请求：`page`、`page_size`
  - 响应：`items[]`、`next_cursor`

### 3.3 SSE 流式对话接口

| 方法 | 路径 | 用途 | 鉴权 | 幂等 |
|---|---|---|---|---|
| `POST` | `/api/v1/chat/stream` | 发起流式对话 | 用户 | 是 |

请求字段：

- `session_id`
- `message`
- `attachments`
- `client_request_id`

响应事件类型：

- `session.created`
- `message.received`
- `agent.run.started`
- `plan.generated`
- `retrieval.started`
- `retrieval.completed`
- `tool.call.started`
- `tool.call.completed`
- `sql.audit.created`
- `answer.delta`
- `answer.completed`
- `agent.run.failed`

### 3.4 饮食记录接口

| 方法 | 路径 | 用途 | 鉴权 | 幂等 |
|---|---|---|---|---|
| `POST` | `/api/v1/food-logs` | 创建饮食记录 | 用户 | 是 |
| `GET` | `/api/v1/food-logs` | 查询饮食记录 | 用户 | 否 |
| `GET` | `/api/v1/food-logs/{id}` | 查询单条记录 | 用户 | 否 |
| `PATCH` | `/api/v1/food-logs/{id}` | 修改记录 | 用户 | 是 |
| `DELETE` | `/api/v1/food-logs/{id}` | 删除记录 | 用户 | 是 |

关键字段：

- 创建请求：`meal_time`、`meal_type`、`items[]`、`notes`
- 查询条件：`start`、`end`、`meal_type`

### 3.5 计划与校验接口

| 方法 | 路径 | 用途 | 鉴权 | 幂等 |
|---|---|---|---|---|
| `POST` | `/api/v1/meal-plans/validate` | 校验计划 | 用户 | 是 |
| `POST` | `/api/v1/meal-plans` | 保存计划 | 用户 | 是 |
| `GET` | `/api/v1/meal-plans/{id}` | 查询计划 | 用户 | 否 |

校验请求字段：

- `days`
- `people`
- `budget`
- `constraints`
- `plan`

校验响应字段：

- `valid`
- `issues[]`
- `nutrition_summary`
- `budget_summary`

### 3.6 知识库检索接口

| 方法 | 路径 | 用途 | 鉴权 | 幂等 |
|---|---|---|---|---|
| `POST` | `/api/v1/knowledge/search` | 直接检索知识库 | 用户 | 是 |
| `POST` | `/api/v1/knowledge/documents` | 导入文档 | 管理员 | 是 |
| `GET` | `/api/v1/knowledge/documents/{id}` | 查询文档 | 管理员 | 否 |

`/knowledge/search` 请求字段：

- `query`
- `top_k`
- `filters`
- `session_id`

响应字段：

- `hits[]`
- `references[]`
- `trace_id`

### 3.7 数据源与 Schema Catalog 接口

| 方法 | 路径 | 用途 | 鉴权 | 幂等 |
|---|---|---|---|---|
| `GET` | `/api/v1/data-sources` | 查询数据源列表 | 管理员 | 否 |
| `GET` | `/api/v1/data-sources/{id}` | 查询数据源详情 | 管理员 | 否 |
| `GET` | `/api/v1/schema-catalogs` | 查询字段目录 | 管理员 | 否 |
| `POST` | `/api/v1/schema-catalogs/refresh` | 刷新目录 | 管理员 | 是 |

### 3.8 SQL Agent 内部接口

| 方法 | 路径 | 用途 | 鉴权 | 幂等 |
|---|---|---|---|---|
| `POST` | `/internal/sql-agent/query` | 执行结构化查询链路 | 内部 | 是 |
| `POST` | `/internal/sql-agent/validate` | SQL 安全校验 | 内部 | 是 |
| `GET` | `/internal/sql-agent/audits/{id}` | 查询审计记录 | 内部 | 否 |

### 3.9 工具注册与查询接口

| 方法 | 路径 | 用途 | 鉴权 | 幂等 |
|---|---|---|---|---|
| `GET` | `/api/v1/tools` | 查询工具注册表 | 管理员 | 否 |
| `GET` | `/api/v1/tools/{name}` | 查询工具详情 | 管理员 | 否 |
| `POST` | `/api/v1/tools/{name}/versions` | 发布工具 schema | 管理员 | 是 |
| `PATCH` | `/api/v1/tools/{name}/status` | 启停工具 | 管理员 | 是 |

### 3.10 管理后台接口

| 方法 | 路径 | 用途 | 鉴权 | 幂等 |
|---|---|---|---|---|
| `GET` | `/api/v1/admin/agent-runs` | 查询运行记录 | 管理员 | 否 |
| `GET` | `/api/v1/admin/tool-calls` | 查询工具调用 | 管理员 | 否 |
| `GET` | `/api/v1/admin/sql-audits` | 查询 SQL 审计 | 管理员 | 否 |
| `GET` | `/api/v1/admin/model-usage` | 查询模型用量 | 管理员 | 否 |

---

## 4. SQL Agent / MCP 边界

### 4.1 外部入口

对外只有一个工具入口：

```text
database_query
```

固定原则：

- 不直接暴露任意 SQL 执行接口给上层
- 高频稳定查询可做封装，但仍归属于 `database_query` 体系

### 4.2 内部链路

固定内部链路如下：

1. `Query Router`
2. `Query Understanding`
3. `Schema Catalog Resolver`
4. `SQL Planner`
5. `SQL Guard`
6. `MCP Executor`

### 4.3 只读约束

固定只读规则：

- 只允许 `SELECT`
- 禁止 `INSERT/UPDATE/DELETE/TRUNCATE/ALTER/DROP/CREATE/GRANT`
- 禁止多语句拼接
- 禁止注释穿透
- 禁止查询系统表和敏感字段白名单外列
- 默认限制 `LIMIT`

### 4.4 危险 SQL 拦截规则

以下情况直接拦截：

- 存在写关键词
- 不带筛选条件扫描高敏感表
- 不带行数限制的大结果集导出
- 访问未注册数据源
- 查询 `is_sensitive = true` 字段但无权限

### 4.5 SQL 审计落库字段

每次 SQL Agent 执行都必须写入：

- `session_id`
- `agent_run_id`
- `datasource_id`
- `original_question`
- `resolved_question`
- `sql_text`
- `status`
- `reject_reason`
- `row_count`
- `latency_ms`
- `trace_id`

---

## 5. 工具注册表

### 5.1 统一注册字段

所有外部工具必须注册以下字段：

| 字段 | 说明 |
|---|---|
| `name` | 工具名 |
| `display_name` | 展示名 |
| `description` | 工具说明 |
| `version` | 当前版本 |
| `category` | 工具类别 |
| `input_schema` | 输入 schema |
| `output_schema` | 输出 schema |
| `permissions` | 权限要求 |
| `timeout_ms` | 超时 |
| `retryable` | 是否可重试 |
| `idempotent` | 是否幂等 |
| `risk_level` | 风险等级 |
| `availability_scope` | 可用范围 |

### 5.2 权威工具清单

#### P0

- `calculator`
- `time_parser`
- `knowledge_search`
- `database_query`
- `food_log_writer`
- `plan_validator`

#### P1

- `nutrition_lookup`
- `meal_plan_generator`
- `shopping_list_generator`
- `summary_generator`
- `acl_filter_tool`
- `data_exporter`

#### 内部能力模块

- `entity_normalizer`
- `prompt_router`

### 5.3 `database_query` schema 示例

```json
{
  "name": "database_query",
  "display_name": "结构化数据查询",
  "description": "统一处理结构化数据查询，内部走 SQL Agent + MCP + Schema Catalog",
  "version": "1.0.0",
  "category": "query",
  "permissions": ["read:data"],
  "timeout_ms": 8000,
  "retryable": true,
  "idempotent": true,
  "risk_level": "medium",
  "availability_scope": "user",
  "input_schema": {
    "type": "object",
    "required": ["intent", "question"],
    "properties": {
      "intent": { "type": "string" },
      "datasource_hint": { "type": "string" },
      "question": { "type": "string" }
    }
  },
  "output_schema": {
    "type": "object",
    "required": ["rows", "sql_record_id"],
    "properties": {
      "rows": { "type": "array" },
      "sql_record_id": { "type": "string" }
    }
  }
}
```

### 5.4 `food_log_writer` schema 示例

```json
{
  "name": "food_log_writer",
  "display_name": "饮食记录写入",
  "description": "写入用户饮食记录",
  "version": "1.0.0",
  "category": "write",
  "permissions": ["write:food_log"],
  "timeout_ms": 3000,
  "retryable": false,
  "idempotent": true,
  "risk_level": "high",
  "availability_scope": "user",
  "input_schema": {
    "type": "object",
    "required": ["meal_time", "items"],
    "properties": {
      "meal_time": { "type": "string" },
      "meal_type": { "type": "string" },
      "items": { "type": "array" },
      "notes": { "type": "string" }
    }
  },
  "output_schema": {
    "type": "object",
    "required": ["log_id", "status"],
    "properties": {
      "log_id": { "type": "string" },
      "status": { "type": "string" }
    }
  }
}
```

### 5.5 `plan_validator` schema 示例

```json
{
  "name": "plan_validator",
  "display_name": "计划校验器",
  "description": "校验计划是否满足预算、营养和人数约束",
  "version": "1.0.0",
  "category": "validate",
  "permissions": ["read:plan"],
  "timeout_ms": 5000,
  "retryable": true,
  "idempotent": true,
  "risk_level": "low",
  "availability_scope": "user",
  "input_schema": {
    "type": "object",
    "required": ["plan"],
    "properties": {
      "plan": { "type": "object" },
      "budget": { "type": "number" },
      "constraints": { "type": "object" }
    }
  },
  "output_schema": {
    "type": "object",
    "required": ["valid", "issues"],
    "properties": {
      "valid": { "type": "boolean" },
      "issues": { "type": "array" },
      "nutrition_summary": { "type": "object" },
      "budget_summary": { "type": "object" }
    }
  }
}
```

---

## 6. 状态机与错误码

### 6.1 Session 状态

| 状态 | 含义 |
|---|---|
| `active` | 活跃会话 |
| `archived` | 已归档 |
| `closed` | 已关闭 |

### 6.2 AgentRun 状态

| 状态 | 含义 |
|---|---|
| `queued` | 已入队 |
| `executing` | 执行中 |
| `success` | 成功 |
| `failed` | 失败 |
| `cancelled` | 已取消 |

### 6.3 ToolCall 状态

| 状态 | 含义 |
|---|---|
| `running` | 执行中 |
| `success` | 成功 |
| `failed` | 失败 |
| `cancelled` | 取消 |

### 6.4 SQL 审核状态

| 状态 | 含义 |
|---|---|
| `drafted` | 已生成草稿 SQL |
| `validated` | 已通过校验 |
| `rejected` | 被安全规则拒绝 |
| `executed` | 已执行 |

### 6.5 索引任务状态

| 状态 | 含义 |
|---|---|
| `uploaded` | 已上传 |
| `parsing` | 解析中 |
| `parsed` | 已解析 |
| `indexing` | 建索引中 |
| `indexed` | 已建索引 |
| `failed` | 失败 |

### 6.6 错误码分层

| 前缀 | 说明 |
|---|---|
| `REQ_` | 参数错误 |
| `AUTH_` | 权限错误 |
| `TOOL_` | 工具错误 |
| `MODEL_` | 模型错误 |
| `RAG_` | 检索错误 |
| `SQL_` | SQL 安全与执行错误 |
| `UPSTREAM_` | 上游依赖错误 |

建议首批错误码：

- `REQ_INVALID_ARGUMENT`
- `REQ_MISSING_FIELD`
- `AUTH_FORBIDDEN`
- `TOOL_TIMEOUT`
- `TOOL_SCHEMA_INVALID`
- `MODEL_TIMEOUT`
- `MODEL_PROVIDER_UNAVAILABLE`
- `RAG_NOT_FOUND`
- `SQL_READONLY_VIOLATION`
- `SQL_SENSITIVE_FIELD_DENIED`
- `UPSTREAM_MCP_UNAVAILABLE`

---

## 7. 第一版落地顺序

建议数据库和接口按下面顺序落地：

1. 建 `users`、`sessions`、`messages`
2. 建 `agent_runs`、`tool_calls`
3. 建 `food_logs`、`meal_plans`
4. 建 `user_memories`、`session_summaries`
5. 建 `knowledge_documents`、`knowledge_chunks`
6. 建 `data_sources`、`schema_catalogs`、`sql_query_audits`
7. 建 `tool_registries`、`tool_schema_versions`
8. 建 `model_usage_logs`、`model_route_rules`
9. 最后接 Milvus collection 和 MinIO

API 按下面顺序开放：

1. `/sessions`
2. `/chat/stream`
3. `/food-logs`
4. `/meal-plans/validate`
5. `/knowledge/search`
6. `/data-sources` / `/schema-catalogs`
7. `/tools`
8. `/admin/*`

---

## 8. 最终落地原则

这份文档最终要达到的效果只有一个：  
**开发同学拿到它，就可以直接建 PostgreSQL 表、Milvus collection、HTTP API、SSE 事件和工具注册表，而不需要再自己补关键决策。**

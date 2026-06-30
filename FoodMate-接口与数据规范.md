# FoodMate 接口与数据规范

版本：v1.0  
对应总设计：[FoodMate-系统设计与技术方案.md](./FoodMate-系统设计与技术方案.md)  
对应产品文档：[FoodMate-产品需求文档.md](./FoodMate-产品需求文档.md)
文档边界：本文件只定义 API、DTO、状态机、数据库模型、错误码与流式协议；架构职责、工具优先级、查询主路径和模型治理边界以总设计文档为准。

---

## 1. 设计目标

本规范定义 FoodMate Agent 的接口边界、数据结构、状态流转、错误格式和事件协议，目标是让前端、后端、Agent 编排层、工具层、检索层能够独立开发、联调和替换。

### 1.1 工程原则

1. 接口稳定
2. 版本可控
3. 数据结构明确
4. 事件可流式传输
5. 状态可恢复
6. 错误可追踪

---

## 2. 系统分层

### 2.1 逻辑分层

| 层级 | 职责 |
|---|---|
| Web/UI | 展示、输入、状态订阅 |
| API Gateway | 对外业务 API 入口治理，负责鉴权、限流、路由与通用网关控制 |
| Agent Orchestrator | 路由、规划、编排 |
| RAG Service | 检索、重排、引用 |
| Tool Service | 确定性工具执行 |
| Data Service | 会话、消息、日志、统计 |
| Worker | 异步任务、长任务处理 |

### 2.2 通信方式

说明：

- `API Gateway` 属于业务 API 外部入口层
- `Model Gateway` 属于模型接入治理层，不替代对外业务 API 网关

- 同步接口：REST JSON
- 流式接口：SSE 优先，必要时 WebSocket
- 内部任务：异步队列

---

## 3. 通用规范

### 3.1 Base URL

示例：

```text
/foodmate
```

路径命名规则：

- 外部接口统一以 `/foodmate` 开头，不再在路径中使用通用的 `/api/v1`。
- 第一版不设计请求头版本协商；后续如有破坏性升级，单独补充迁移说明。
- 资源名使用能表达业务含义的英文，例如 `agent-sessions`、`agent-runs`、`knowledge-base`、`nutrition-analysis`。

### 3.2 请求头

| Header | 说明 |
|---|---|
| Authorization | Bearer Token |
| X-Request-Id | 请求追踪 ID |
| X-Session-Id | 可选，会话上下文 |
| Idempotency-Key | 幂等键，适用于写接口 |
| Content-Type | `application/json`；头像上传使用 `multipart/form-data` |

### 3.3 统一响应格式

#### 成功响应

```json
{
  "success": true,
  "data": {},
  "meta": {
    "request_id": "req_123",
    "trace_id": "trace_123"
  }
}
```

#### 失败响应

```json
{
  "success": false,
  "error": {
    "code": "INVALID_ARGUMENT",
    "message": "参数不合法",
    "details": {}
  },
  "meta": {
    "request_id": "req_123",
    "trace_id": "trace_123"
  }
}
```

### 3.4 错误码约定

| 错误码 | 语义 |
|---|---|
| UNAUTHORIZED | 未认证 |
| FORBIDDEN | 无权限 |
| NOT_FOUND | 资源不存在 |
| INVALID_ARGUMENT | 参数错误 |
| CONFLICT | 资源冲突 |
| RATE_LIMITED | 被限流 |
| TOOL_FAILED | 工具执行失败 |
| TOOL_POLICY_DENIED | 工具调用被策略层拒绝 |
| RAG_EMPTY | 检索为空 |
| AGENT_TIMEOUT | Agent 超时 |
| INTERNAL_ERROR | 系统异常 |
| AUTH_INVALID_CREDENTIALS | 用户名或密码错误 |
| AUTH_TOKEN_EXPIRED | Access Token 已过期 |
| AUTH_REFRESH_TOKEN_INVALID | Refresh Token 无效、过期或已撤销 |
| AUTH_ACCOUNT_DISABLED | 账号被禁用 |
| AUTH_ACCOUNT_LOCKED | 账号被锁定 |
| AUTH_FORBIDDEN | 已登录但无权限 |
| AUTH_REQUIRED | 未登录 |
| AUTH_REGISTER_DISABLED | 当前环境未开放注册 |
| AUTH_PASSWORD_RESET_INVALID | 密码重置 token 无效或过期 |
| USER_AVATAR_INVALID | 头像文件不合法 |
| USER_PASSWORD_INVALID | 密码校验失败 |

### 3.5 分页规范

```json
{
  "page": 1,
  "page_size": 20,
  "total": 100
}
```

### 3.6 ID 与删除语义

- 所有数据库主键统一使用 Snowflake BIGINT
- 数据库主键字段统一使用 `{业务对象}_id`，例如 `user_id/session_id/message_id`
- 所有 API、SSE、JSON DTO 对外统一按字符串返回 ID，字段名同样使用带业务语义的 `xxx_id`
- 所有默认查询都排除 `is_deleted = true` 的数据
- `DELETE` 接口统一表示软删除
- 恢复操作统一使用 `POST /restore`
- 只有管理接口允许显式传 `include_deleted=true`

### 3.7 认证、个人资料与 RBAC

第一版认证使用 `Spring Security + JWT Access Token + HttpOnly Refresh Token`。

| 方法 | 路径 | 用途 | 鉴权 |
|---|---|---|---|
| `POST` | `/foodmate/auth/login` | 登录并返回 Access Token，Refresh Token 通过 Cookie 返回 | 匿名 |
| `POST` | `/foodmate/auth/refresh` | 使用 Refresh Cookie 轮换 token | 匿名 |
| `POST` | `/foodmate/auth/logout` | 注销当前 Refresh Token | 用户 |
| `GET` | `/foodmate/auth/me` | 查询当前用户、角色、权限和资料摘要 | 用户 |
| `POST` | `/foodmate/auth/register` | 注册普通用户 | 匿名 |
| `POST` | `/foodmate/auth/password-reset/request` | 发起密码找回 | 匿名 |
| `POST` | `/foodmate/auth/password-reset/confirm` | 确认密码重置 | 匿名 |
| `GET` | `/foodmate/users/me` | 查询个人资料 | 用户 |
| `PATCH` | `/foodmate/users/me` | 修改昵称、展示名等基础资料 | 用户 |
| `PATCH` | `/foodmate/users/me/profile` | 修改营养目标、忌口、过敏原、单位偏好 | 用户 |
| `POST` | `/foodmate/users/me/avatar` | 上传或替换头像 | 用户 |
| `DELETE` | `/foodmate/users/me/avatar` | 删除头像 | 用户 |
| `POST` | `/foodmate/users/me/password/change` | 修改密码 | 用户 |

角色固定为：

| 角色 | 权限范围 |
|---|---|
| `user` | 只能访问自己的会话、消息、饮食记录、计划、个人资料和可见知识库内容 |
| `operator` | 可只读访问运营治理信息，例如知识库文档、工具状态、部分运行日志 |
| `admin` | 可访问管理后台全部能力，并执行用户状态、工具启停、知识库写入、软删除恢复等管理操作 |

认证与资料规则：

- Refresh Token 主状态保存在 PostgreSQL，Redis 只做验证码、限流、幂等、短期黑名单和缓存。
- Refresh Token 不放 JSON body，默认通过 `Set-Cookie` 返回。
- Refresh 成功必须轮换 Refresh Token。
- 后端必须从 token 解析 `user_id/role/status`，禁止信任前端传入的身份字段。
- 用户不能修改自己的 `role/status`。
- 头像上传使用 `multipart/form-data`，字段名 `file`，允许 `image/jpeg`、`image/png`、`image/webp`，第一版大小上限 2 MB。

---

## 4. 核心数据模型

### 4.1 Session

```json
{
  "session_id": "1912345678901234561",
  "user_id": "1912345678901234001",
  "title": "一周备餐计划",
  "mode": "agent",
  "status": "active",
  "is_deleted": false,
  "created_at": "2026-06-01T00:00:00Z",
  "updated_at": "2026-06-01T00:05:00Z"
}
```

### 4.2 Message

```json
{
  "message_id": "1912345678901234562",
  "session_id": "1912345678901234561",
  "role": "user",
  "content": "给 2 个人制定一周备餐计划",
  "structured_payload": {},
  "is_deleted": false,
  "created_at": "2026-06-01T00:00:00Z"
}
```

### 4.3 AgentRun

```json
{
  "agent_run_id": "1912345678901234563",
  "session_id": "1912345678901234561",
  "user_message_id": "1912345678901234562",
  "intent": "planning",
  "status": "executing",
  "plan_json": {},
  "result_json": {},
  "error_code": null,
  "is_deleted": false,
  "created_at": "2026-06-01T00:00:00Z"
}
```

### 4.4 ToolCall

```json
{
  "tool_call_id": "1912345678901234564",
  "agent_run_id": "1912345678901234563",
  "tool_name": "knowledge_search",
  "input_json": {},
  "output_json": {},
  "status": "success",
  "latency_ms": 380,
  "is_deleted": false,
  "created_at": "2026-06-01T00:00:01Z"
}
```

### 4.5 RetrievedReference

```json
{
  "chunk_id": "1912345678901234591",
  "document_id": "1912345678901234581",
  "snippet": "西兰花焯水...",
  "score": 0.92,
  "metadata_json": {
    "source_title": "营养与烹饪指南",
    "page": 12
  }
}
```

### 4.6 UserMemory

```json
{
  "memory_id": "1912345678901234565",
  "user_id": "1912345678901234001",
  "memory_type": "preference",
  "memory_key": "diet_avoid",
  "memory_value": ["pork"],
  "confidence": 0.96,
  "source": "user_explicit",
  "scope": "global",
  "expires_at": null,
  "is_deleted": false,
  "created_at": "2026-06-01T00:00:00Z"
}
```

### 4.7 SessionSummary

```json
{
  "summary_id": "1912345678901234566",
  "session_id": "1912345678901234561",
  "summary_text": "用户正在制定 7 天备餐计划，已确认预算 500 元、无猪肉忌口。",
  "key_constraints": {
    "budget": 500,
    "avoid": ["pork"]
  },
  "is_deleted": false,
  "updated_at": "2026-06-01T00:10:00Z"
}
```

### 4.8 QueryUnderstandingRecord

```json
{
  "query_understanding_id": "1912345678901234567",
  "session_id": "1912345678901234561",
  "user_message_id": "1912345678901234562",
  "original_query": "这个要多久",
  "resolved_query": "西兰花焯水多久合适",
  "keyword_query": "西兰花 焯水 时间",
  "semantic_query": "西兰花焯水多久合适",
  "entities": [
    {
      "type": "ingredient",
      "text": "西兰花",
      "normalized_text": "西兰花"
    }
  ],
  "filters": {
    "doc_type": "cooking_guide"
  },
  "acl_filter": {
    "tenant_id": "1912345678901234000",
    "visibility": "private"
  },
  "is_deleted": false,
  "confidence": 0.91,
  "created_at": "2026-06-01T00:00:01Z"
}
```

### 4.9 KnowledgeDocument

```json
{
  "document_id": "1912345678901234581",
  "title": "营养与烹饪指南",
  "source_type": "cookbook",
  "version": "v1",
  "status": "active",
  "is_deleted": false,
  "created_at": "2026-06-01T00:00:00Z"
}
```

### 4.10 KnowledgeChunk

```json
{
  "chunk_id": "1912345678901234591",
  "document_id": "1912345678901234581",
  "chunk_text": "西兰花焯水 1-2 分钟即可。",
  "section_path": "蔬菜处理/焯水",
  "tags": ["西兰花", "焯水", "烹饪"],
  "version": "v1",
  "embedding_id": "emb_001",
  "is_deleted": false,
  "created_at": "2026-06-01T00:00:00Z"
}
```

### 4.11 DataSource

```json
{
  "datasource_id": "1912345678901234601",
  "name": "nutrition_analytics_db",
  "db_type": "postgresql",
  "purpose": "饮食分析",
  "visibility": "restricted",
  "status": "active",
  "is_deleted": false
}
```

### 4.12 SchemaCatalogItem

```json
{
  "schema_catalog_id": "1912345678901234602",
  "datasource_id": "1912345678901234601",
  "table_name": "user_food_logs",
  "field_name": "protein",
  "field_desc": "蛋白质摄入量",
  "is_sensitive": false,
  "sample_sql": "SELECT SUM(protein) FROM user_food_logs WHERE user_id = ?"
}
```

### 4.13 SqlQueryRecord

```json
{
  "sql_audit_id": "1912345678901234603",
  "session_id": "1912345678901234561",
  "user_message_id": "1912345678901234562",
  "datasource_id": "1912345678901234601",
  "sql_text": "SELECT SUM(protein) FROM user_food_logs WHERE user_id = ? AND created_at >= ?",
  "status": "validated",
  "row_count": 1,
  "is_deleted": false,
  "latency_ms": 280,
  "created_at": "2026-06-01T00:00:02Z"
}
```

---

## 5. 外部接口清单

### 5.1 会话接口

#### 5.1.1 创建会话

`POST /foodmate/agent-sessions`

请求：

```json
{
  "title": "一周备餐计划",
  "mode": "agent"
}
```

响应：

```json
{
  "success": true,
  "data": {
    "session": {
      "session_id": "1912345678901234571",
      "title": "一周备餐计划",
      "mode": "agent",
      "status": "active"
    }
  }
}
```

#### 5.1.2 会话列表

`GET /foodmate/agent-sessions?page=1&page_size=20`

#### 5.1.3 会话详情

`GET /foodmate/agent-sessions/{session_id}`

#### 5.1.4 更新会话

`PATCH /foodmate/agent-sessions/{session_id}`

支持字段：

- title
- pinned
- status

#### 5.1.5 归档会话

`POST /foodmate/agent-sessions/{session_id}/archive`

#### 5.1.6 软删除会话

`DELETE /foodmate/agent-sessions/{session_id}`

说明：

- 只做软删除
- 写入 `is_deleted`、`deleted_at`、`deleted_by`
- 默认查询不再返回该会话

#### 5.1.7 恢复会话

`POST /foodmate/agent-sessions/{session_id}/restore`

---

### 5.2 消息接口

#### 5.2.1 发送消息并启动 Agent

`POST /foodmate/agent-sessions/{session_id}/messages`

请求：

```json
{
  "content": "分析我最近一周蛋白质摄入",
  "attachments": [],
  "client_context": {
    "timezone": "Asia/Shanghai"
  }
}
```

响应：

```json
{
  "success": true,
  "data": {
    "message": {
      "message_id": "1912345678901234568",
      "role": "user"
    },
    "agent_run": {
      "agent_run_id": "1912345678901234569",
      "status": "queued"
    }
  }
}
```

#### 5.2.2 消息列表

`GET /foodmate/agent-sessions/{session_id}/messages?page=1&page_size=50`

---

### 5.3 Agent 运行接口

#### 5.3.1 获取运行详情

`GET /foodmate/agent-runs/{agent_run_id}`

返回包括：

- intent
- plan
- tool_calls
- retriever_hits
- status
- final_answer
- error

#### 5.3.2 获取运行事件流

`GET /foodmate/agent-runs/{agent_run_id}/events`

推荐 SSE，事件类型如下：

| event | 说明 |
|---|---|
| run.created | 运行创建 |
| run.routed | 已完成路由 |
| run.clarification_requested | 需要追问 |
| run.planned | 计划生成完成 |
| run.retrieval_started | 开始检索 |
| run.retrieval_finished | 检索结束 |
| run.tool_started | 工具开始 |
| run.tool_finished | 工具结束 |
| run.answer_stream | 回答流式输出 |
| run.completed | 完成 |
| run.failed | 失败 |
| run.cancelled | 已取消 |

SSE 示例：

```text
event: run.tool_started
data: {"tool_name":"knowledge_search","status":"running"}

event: run.tool_finished
data: {"tool_name":"knowledge_search","status":"success"}
```

#### 5.3.3 取消运行

`POST /foodmate/agent-runs/{agent_run_id}/cancel`

适用：

- 用户主动停止
- 超时中止
- 人工介入

---

### 5.4 知识库接口

#### 5.4.1 文档搜索

`POST /foodmate/knowledge-base/search`

请求：

```json
{
  "query": "西兰花焯水多久",
  "top_k": 5,
  "filters": {
    "source_type": "cookbook"
  }
}
```

建议服务端内部执行顺序：

1. 生成 query rewrite 记录
2. Milvus BM25 做关键词召回
3. Milvus dense vector 做语义召回
4. 合并结果
5. rerank
6. 返回 hits 和引用信息

#### Milvus 使用建议

Milvus 适合作为知识库主检索底座：

- 同时承载 dense embedding 和 sparse BM25
- 便于做 hybrid search
- 和 Java / Spring 集成路径清晰
- 适合作为统一检索服务，减少多套检索系统拼装成本

#### 知识库构建流程建议

知识库文档的正式接口定义以下方 `5.4.2` 之后的接口章节为准，本节只描述构建流程与删除恢复语义，不再重复列完整接口清单。

构建流程建议分成两层：

1. 文档入库与版本管理
2. chunk 切分、embedding、稀疏化、索引写入

删除与恢复语义：

- `DELETE /foodmate/knowledge-base/documents/{document_id}` 只做软删除
- 默认检索和默认列表不返回软删除文档
- 管理接口可显式传 `include_deleted=true`
- `POST /foodmate/knowledge-base/documents/{document_id}/restore` 恢复软删除文档，并触发索引可见性恢复

#### MCP 数据查询接口建议

说明：结构化数据查询的主路径为 `Schema Catalog -> SQL Agent -> MCP -> Readonly SQL Executor`。固定查询接口只保留高频稳定场景，不与 SQL Agent 并列为主路径。

`GET /foodmate/data-sources`

`GET /foodmate/data-sources/{datasource_id}`

`GET /foodmate/data-sources/{datasource_id}/schema`

`POST /foodmate/data-sources/{datasource_id}/validate-sql`

`POST /foodmate/data-sources/{datasource_id}/run-readonly-sql`

#### 只读 SQL 约束

- 只允许 `SELECT` / `WITH ... SELECT`
- 必须带 `LIMIT`
- 必须经过 SQL AST 校验
- 必须注入租户 / 用户过滤
- 必须记录审计日志
- 必须限制返回行数和超时

#### SQL 校验建议

- 语法校验
- 表白名单校验
- 字段白名单校验
- 敏感字段校验
- 扫描成本评估

#### 审计字段建议

- `datasource_id`
- `sql_text`
- `sql_hash`
- `validated_by`
- `execution_status`
- `latency_ms`
- `row_count`
- `operator_user_id`

响应：

```json
{
  "success": true,
  "data": {
    "hits": [
      {
        "chunk_id": "1912345678901234591",
        "document_id": "1912345678901234581",
        "score": 0.92,
        "snippet": "西兰花焯水 1-2 分钟即可..."
      }
    ]
  }
}
```

#### 5.4.2 文档列表

`GET /foodmate/knowledge-base/documents`

#### 5.4.3 上传文档

`POST /foodmate/knowledge-base/documents`

适用：

- 内部 SOP
- 菜谱
- 食材说明
- 营养手册

---

### 5.5 用户饮食日志接口

#### 5.5.1 写入日志

`POST /foodmate/food-logs`

请求：

```json
{
  "meal_time": "2026-06-01T12:00:00+08:00",
  "items": [
    {
      "name": "鸡胸肉",
      "amount": 200,
      "unit": "g"
    }
  ],
  "notes": "午餐"
}
```

#### 5.5.2 查询日志

`GET /foodmate/food-logs?start_date=2026-05-25&end_date=2026-06-01`

说明：

- 默认不返回软删除记录
- 管理端可显式传 `include_deleted=true`

#### 5.5.3 饮食汇总

`GET /foodmate/food-logs/summary?range=7d`

返回聚合：

- calories
- protein
- fat
- carbs
- meal_count

#### 5.5.4 软删除日志

`DELETE /foodmate/food-logs/{food_log_id}`

#### 5.5.5 恢复日志

`POST /foodmate/food-logs/{food_log_id}/restore`

---

### 5.6 分析接口

#### 5.6.1 生成分析报告

`POST /foodmate/nutrition-analysis/reports`

请求：

```json
{
  "type": "protein_trend",
  "range": "7d",
  "user_id": "1912345678901234001"
}
```

#### 5.6.2 查询分析报告

`GET /foodmate/nutrition-analysis/reports/{report_id}`

---

### 5.7 规划接口

规划相关能力统一围绕 `meal_plans` 主资源组织，生成购物清单和计划校验属于该主资源的派生能力，不再单独使用旧版规划接口主路径。

#### 5.7.1 校验计划草案

`POST /foodmate/meal-plans/validate`

请求：

```json
{
  "people": 2,
  "days": 7,
  "budget": 500,
  "goal": "balanced",
  "constraints": {
    "no_pork": true,
    "high_protein": true
  }
}
```

#### 5.7.2 保存计划

`POST /foodmate/meal-plans`

#### 5.7.3 查询计划

`GET /foodmate/meal-plans/{meal_plan_id}`

#### 5.7.4 软删除计划

`DELETE /foodmate/meal-plans/{meal_plan_id}`

#### 5.7.5 恢复计划

`POST /foodmate/meal-plans/{meal_plan_id}/restore`

#### 5.7.6 基于计划生成购物清单

`POST /foodmate/meal-plans/{meal_plan_id}/shopping-list`

#### 5.7.7 规划接口默认语义

- 默认查询不返回软删除计划
- 管理端可显式传 `include_deleted=true`
- `DELETE` 只做软删除，`POST /restore` 做恢复

---

## 6. 工具内部协议

### 6.1 工具执行请求格式

```json
{
  "tool_name": "knowledge_search",
  "input": {
    "query": "西兰花焯水多久",
    "top_k": 5,
    "filters": {
      "category": "cooking"
    }
  },
  "context": {
    "session_id": "1912345678901234561",
    "agent_run_id": "1912345678901234563",
    "user_id": "1912345678901234001"
  }
}
```

### 6.2 工具执行响应格式

```json
{
  "success": true,
  "data": {
    "hits": [
      {
        "chunk_id": "1912345678901235001",
        "document_id": "1912345678901235000",
        "title": "蔬菜焯水指南",
        "snippet": "西兰花建议沸水焯 60-90 秒后过凉水。",
        "score": 0.91
      }
    ],
    "references": [
      {
        "document_id": "1912345678901235000",
        "title": "蔬菜焯水指南"
      }
    ]
  },
  "meta": {
    "latency_ms": 300,
    "source": "milvus_hybrid_v1"
  }
}
```

### 6.3 工具失败格式

```json
{
  "success": false,
  "error": {
    "code": "TOOL_FAILED",
    "message": "知识库检索失败",
    "retryable": true
  }
}
```

当工具调用因为 Prompt Injection 风险、越权、缺少确认或不可信内容直接驱动而被拒绝时，优先返回 `TOOL_POLICY_DENIED`，并在错误详情中给出 `reason`，例如：

```json
{
  "success": false,
  "error": {
    "code": "TOOL_POLICY_DENIED",
    "message": "工具调用被策略层拒绝",
    "retryable": false,
    "reason": "untrusted_content_requested_high_risk_action"
  }
}
```

---

## 7. 流式协议

### 7.1 SSE 数据格式

```json
{
  "event": "run.answer_stream",
  "data": {
    "delta": "这里是模型逐步输出的内容"
  }
}
```

### 7.2 前端建议处理方式

- 上下文状态更新
- 流式文本拼接
- 工具状态实时展示
- 完成后刷新会话列表与详情

---

## 8. 状态机

### 8.1 Session 状态

| 状态 | 含义 |
|---|---|
| active | 活跃 |
| archived | 归档 |
| closed | 关闭 |

说明：

- `deleted` 不再作为业务状态值
- 删除语义统一通过 `is_deleted` 表达

### 8.2 AgentRun 状态

| 状态 | 含义 |
|---|---|
| queued | 已排队 |
| routed | 已路由 |
| waiting_user | 等待追问 |
| planning | 生成计划中 |
| retrieving | 检索中 |
| executing | 工具执行中 |
| validating | 结果校验中 |
| completed | 已完成 |
| failed | 已失败 |
| cancelled | 已取消 |

### 8.3 ToolCall 状态

| 状态 | 含义 |
|---|---|
| pending | 待执行 |
| running | 执行中 |
| success | 成功 |
| failed | 失败 |
| timeout | 超时 |
| cancelled | 已取消 |

---

## 9. 数据库建议

### 9.1 主表

- users
- user_profiles
- auth_refresh_tokens
- user_avatar_assets
- sessions
- messages
- agent_runs
- tool_calls
- knowledge_documents
- knowledge_chunks
- food_logs
- analysis_reports
- meal_plans
- shopping_lists

### 9.2 索引建议

索引分两层：

- 基线索引：
  - sessions(user_id, last_message_at, is_deleted)
  - messages(session_id, sequence_no, is_deleted)
  - agent_runs(session_id, created_at, is_deleted)
  - tool_calls(agent_run_id, created_at, is_deleted)
  - food_logs(user_id, meal_time, is_deleted)
  - knowledge_documents(tenant_id, status, is_deleted)
- 候选索引：
  - trace_id
  - tool_name + status
  - datasource_id + status
  - JSONB GIN
  - tags GIN

候选索引不在第一版默认创建，必须基于 `EXPLAIN ANALYZE`、慢查询日志和真实热点接口再决定。

### 9.3 幂等建议

写接口建议支持：

- `Idempotency-Key`
- 任务去重
- 重试不重复写入
- 幂等键作用域建议为 `user_id + method + path + Idempotency-Key`
- 相同幂等键但请求体摘要不一致时返回 `CONFLICT`
- 登录、刷新、注册、头像上传、资料修改等接口必须能安全重试
- Redis 可保存短期幂等记录，高价值写入仍需数据库唯一约束或业务状态兜底

---

## 10. 版本与兼容

### 10.1 版本策略

- API 路径固定产品前缀：`/foodmate`
- 兼容优先通过新增字段、保留旧字段语义和发布迁移说明处理。
- 破坏性升级需要新增明确的新资源名或单独迁移文档，不能静默改变旧接口语义。

### 10.2 兼容原则

1. 允许新增字段
2. 不随意删除字段
3. 不随意改字段语义
4. 破坏性变更必须升版本

---

## 11. 安全与权限

### 11.1 基本要求

- 所有写操作需鉴权。
- 用户只能访问自己的会话、消息、饮食记录、计划、分析报告、个人资料和私有知识库内容。
- 知识库上传、工具启停、软删除恢复、用户状态修改需 `admin` 权限。
- `operator` 只能访问只读运营治理信息，例如知识库文档、工具状态、运行记录和模型用量摘要。
- 工具调用需按场景授权，后端必须从 token 解析 `user_id/role/status`。
- 普通用户访问 `/foodmate/admin/*` 必须返回 403。

### 11.2 审计记录

建议记录：

- 谁调用了什么接口
- 什么时间
- 传了什么参数
- 返回了什么结果
- 是否失败
- 管理写操作必须记录 `operator_id`、`target_type`、`target_id`、`action`、`request_id`、`trace_id` 和结果状态。

### 11.3 管理后台范围

第一版管理后台先覆盖只读治理和少量高风险管理操作：

- 管理概览：AgentRun 数量、失败率、工具调用量、模型用量、知识库索引状态。
- 用户管理：用户列表、用户详情、启用、禁用、锁定、重置登录会话，仅 `admin`。
- 运行审计：AgentRun、ToolCall、SQLAudit、Trace 查询。
- 模型用量：ModelUsage 查询，支持供应商、模型、场景、token、成本和耗时分析。
- 知识库管理：文档列表、上传、下线、恢复、索引状态。
- 工具管理：工具注册表、版本、入参 schema、启停、风险等级、权限范围。
- 删除资源管理：查看软删除资源、恢复资源，仅 `admin`。
- 管理写操作：前端需二次确认，后端需写审计并返回可追踪的 `request_id/trace_id`。

---

## 12. 测试建议

### 12.1 单元测试

- 参数校验
- 状态流转
- 幂等逻辑
- 错误映射

### 12.2 集成测试

- 创建会话后发送消息
- Agent 触发工具调用
- SSE 流式事件是否完整
- 追问后能继续执行

### 12.3 回归测试

- 计算场景
- 记录场景
- 分析场景
- 规划场景
- 检索场景

---

## 13. 实现建议

### 13.1 后端逻辑模块与未来服务边界

第一阶段运行时采用模块化单体，本节的“服务”指逻辑模块和未来可拆服务边界，不要求在 B3 阶段拆成多个独立部署单元。

推荐划分为：

1. API 模块
2. Agent 编排模块
3. 检索模块
4. 工具模块
5. 分析模块
6. Worker 模块

### 13.2 推荐协作方式

- API 层只做薄路由和鉴权
- Agent 层负责决策和编排
- Tool 层负责确定性执行
- RAG 层负责召回和引用

---

## 14. Java 落地建议

### 14.1 推荐后端实现栈

| 技术 | 选择 | 作用 |
|---|---|---|
| JDK 21 | 强烈推荐 | 生产运行时 |
| Spring Boot 3 | 强烈推荐 | Web API、依赖注入、业务编排 |
| Spring WebFlux | 推荐 | SSE、流式输出、非阻塞请求 |
| Spring Security | 推荐 | 鉴权、授权、接口保护 |
| Spring Validation | 推荐 | 参数校验 |
| Spring Data JPA / JDBC | 推荐 | 数据访问层 |
| Spring AI | 推荐 | 模型接入、工具调用、RAG 封装 |
| Jackson | 推荐 | JSON 序列化 |
| Lombok | 可选 | 减少样板代码 |

### 14.2 Java 分层建议

建议后端按以下包结构组织：

- `controller`
- `dto`
- `service`
- `orchestrator`
- `tool`
- `retriever`
- `validator`
- `repository`
- `domain`
- `security`
- `worker`

### 14.3 DTO 与校验约定

建议所有输入输出都使用明确 DTO，不直接暴露 Entity。

约束：

- 请求参数使用 `@Valid`
- 必填字段必须明确标记
- 枚举字段必须有限定值
- 金额、份量、时间范围等字段必须有明确类型

### 14.4 流式接口约定

Java 实现流式回答时，推荐：

- 控制层使用 `Spring WebFlux`
- 事件输出使用 SSE
- 每个事件携带 `event_type`、`run_id`、`timestamp`、`payload`
- 前端按事件增量渲染

### 14.5 工具实现约定

工具层建议定义统一接口：

```java
public interface AgentTool<I, O> {
    String name();
    O execute(I input);
}
```

然后由工厂或注册器统一管理：

- 工具元信息
- 参数 schema
- 权限
- 超时
- 重试策略

### 14.6 Java 版接口落地原则

- Controller 只负责入参、鉴权、返回
- Service 负责业务逻辑
- Orchestrator 负责多步编排
- Tool 负责确定性执行
- Retriever 负责检索
- Validator 负责校验


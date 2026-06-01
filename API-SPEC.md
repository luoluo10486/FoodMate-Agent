# CookHero Agent API 与数据规范

版本：v1.0  
对应总设计：[CookHero-Agent-Design-Spec.md](./CookHero-Agent-Design-Spec.md)  
对应产品文档：[PRD.md](./PRD.md)

---

## 1. 设计目标

本规范定义 CookHero Agent 的接口边界、数据结构、状态流转、错误格式和事件协议，目标是让前端、后端、Agent 编排层、工具层、检索层能够独立开发、联调和替换。

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
| API Gateway | 鉴权、限流、路由 |
| Agent Orchestrator | 路由、规划、编排 |
| RAG Service | 检索、重排、引用 |
| Tool Service | 确定性工具执行 |
| Data Service | 会话、消息、日志、统计 |
| Worker | 异步任务、长任务处理 |

### 2.2 通信方式

- 同步接口：REST JSON
- 流式接口：SSE 优先，必要时 WebSocket
- 内部任务：异步队列

---

## 3. 通用规范

### 3.1 Base URL

示例：

```text
/api/v1
```

### 3.2 请求头

| Header | 说明 |
|---|---|
| Authorization | Bearer Token |
| X-Request-Id | 请求追踪 ID |
| X-Session-Id | 可选，会话上下文 |
| Idempotency-Key | 幂等键，适用于写接口 |
| Content-Type | `application/json` |

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
| RAG_EMPTY | 检索为空 |
| AGENT_TIMEOUT | Agent 超时 |
| INTERNAL_ERROR | 系统异常 |

### 3.5 分页规范

```json
{
  "page": 1,
  "page_size": 20,
  "total": 100
}
```

---

## 4. 核心数据模型

### 4.1 Session

```json
{
  "id": "ses_001",
  "user_id": "usr_001",
  "title": "一周备餐计划",
  "mode": "agent",
  "status": "active",
  "created_at": "2026-06-01T00:00:00Z",
  "updated_at": "2026-06-01T00:05:00Z"
}
```

### 4.2 Message

```json
{
  "id": "msg_001",
  "session_id": "ses_001",
  "role": "user",
  "content": "给 2 个人制定一周备餐计划",
  "structured_payload": {},
  "created_at": "2026-06-01T00:00:00Z"
}
```

### 4.3 AgentRun

```json
{
  "id": "run_001",
  "session_id": "ses_001",
  "user_message_id": "msg_001",
  "intent": "planning",
  "status": "executing",
  "plan_json": {},
  "result_json": {},
  "error_code": null,
  "created_at": "2026-06-01T00:00:00Z"
}
```

### 4.4 ToolCall

```json
{
  "id": "tool_001",
  "agent_run_id": "run_001",
  "tool_name": "nutrition_lookup",
  "input_json": {},
  "output_json": {},
  "status": "success",
  "latency_ms": 380,
  "created_at": "2026-06-01T00:00:01Z"
}
```

### 4.5 KnowledgeChunk

```json
{
  "id": "chk_001",
  "document_id": "doc_001",
  "chunk_text": "西兰花焯水...",
  "metadata_json": {
    "source_title": "营养与烹饪指南",
    "page": 12
  }
}
```

---

## 5. 外部接口清单

### 5.1 会话接口

#### 5.1.1 创建会话

`POST /api/v1/sessions`

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
      "id": "ses_001",
      "title": "一周备餐计划",
      "mode": "agent",
      "status": "active"
    }
  }
}
```

#### 5.1.2 会话列表

`GET /api/v1/sessions?page=1&page_size=20`

#### 5.1.3 会话详情

`GET /api/v1/sessions/{session_id}`

#### 5.1.4 更新会话

`PATCH /api/v1/sessions/{session_id}`

支持字段：

- title
- pinned
- status

#### 5.1.5 归档会话

`POST /api/v1/sessions/{session_id}/archive`

---

### 5.2 消息接口

#### 5.2.1 发送消息并启动 Agent

`POST /api/v1/sessions/{session_id}/messages`

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
      "id": "msg_100",
      "role": "user"
    },
    "agent_run": {
      "id": "run_200",
      "status": "queued"
    }
  }
}
```

#### 5.2.2 消息列表

`GET /api/v1/sessions/{session_id}/messages?page=1&page_size=50`

---

### 5.3 Agent 运行接口

#### 5.3.1 获取运行详情

`GET /api/v1/runs/{run_id}`

返回包括：

- intent
- plan
- tool_calls
- retriever_hits
- status
- final_answer
- error

#### 5.3.2 获取运行事件流

`GET /api/v1/runs/{run_id}/events`

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

SSE 示例：

```text
event: run.tool_started
data: {"tool_name":"nutrition_lookup","status":"running"}

event: run.tool_finished
data: {"tool_name":"nutrition_lookup","status":"success"}
```

#### 5.3.3 取消运行

`POST /api/v1/runs/{run_id}/cancel`

适用：

- 用户主动停止
- 超时中止
- 人工介入

---

### 5.4 知识库接口

#### 5.4.1 文档搜索

`POST /api/v1/knowledge/search`

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

响应：

```json
{
  "success": true,
  "data": {
    "hits": [
      {
        "chunk_id": "chk_001",
        "document_id": "doc_001",
        "score": 0.92,
        "snippet": "西兰花焯水 1-2 分钟即可..."
      }
    ]
  }
}
```

#### 5.4.2 文档列表

`GET /api/v1/knowledge/documents`

#### 5.4.3 上传文档

`POST /api/v1/knowledge/documents`

适用：

- 内部 SOP
- 菜谱
- 食材说明
- 营养手册

---

### 5.5 用户饮食日志接口

#### 5.5.1 写入日志

`POST /api/v1/food-logs`

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

`GET /api/v1/food-logs?start_date=2026-05-25&end_date=2026-06-01`

#### 5.5.3 饮食汇总

`GET /api/v1/food-logs/summary?range=7d`

返回聚合：

- calories
- protein
- fat
- carbs
- meal_count

---

### 5.6 分析接口

#### 5.6.1 生成分析报告

`POST /api/v1/analysis/reports`

请求：

```json
{
  "type": "protein_trend",
  "range": "7d",
  "user_id": "usr_001"
}
```

#### 5.6.2 查询分析报告

`GET /api/v1/analysis/reports/{report_id}`

---

### 5.7 规划接口

#### 5.7.1 生成备餐计划

`POST /api/v1/plans/meal`

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

#### 5.7.2 生成购物清单

`POST /api/v1/plans/shopping-list`

#### 5.7.3 校验计划

`POST /api/v1/plans/validate`

---

## 6. 工具内部协议

### 6.1 工具执行请求格式

```json
{
  "tool_name": "nutrition_lookup",
  "input": {
    "ingredient": "鸡胸肉",
    "amount": 200,
    "unit": "g"
  },
  "context": {
    "session_id": "ses_001",
    "run_id": "run_001",
    "user_id": "usr_001"
  }
}
```

### 6.2 工具执行响应格式

```json
{
  "success": true,
  "data": {
    "calories": 220,
    "protein": 46,
    "fat": 3.6,
    "carbs": 0
  },
  "meta": {
    "latency_ms": 300,
    "source": "nutrition_db_v1"
  }
}
```

### 6.3 工具失败格式

```json
{
  "success": false,
  "error": {
    "code": "TOOL_FAILED",
    "message": "营养数据库查询失败",
    "retryable": true
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
| deleted | 删除 |

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
| canceled | 已取消 |

### 8.3 ToolCall 状态

| 状态 | 含义 |
|---|---|
| pending | 待执行 |
| running | 执行中 |
| success | 成功 |
| failed | 失败 |
| timeout | 超时 |

---

## 9. 数据库建议

### 9.1 主表

- users
- sessions
- messages
- agent_runs
- tool_calls
- documents
- document_chunks
- food_logs
- analysis_reports
- meal_plans
- shopping_lists

### 9.2 索引建议

- sessions(user_id, updated_at)
- messages(session_id, created_at)
- agent_runs(session_id, created_at)
- tool_calls(agent_run_id, created_at)
- food_logs(user_id, meal_time)
- document_chunks(document_id)

### 9.3 幂等建议

写接口建议支持：

- `Idempotency-Key`
- 任务去重
- 重试不重复写入

---

## 10. 版本与兼容

### 10.1 版本策略

- API 路径带版本：`/api/v1`
- 协议升级走新版本
- 旧版本保留一段迁移期

### 10.2 兼容原则

1. 允许新增字段
2. 不随意删除字段
3. 不随意改字段语义
4. 破坏性变更必须升版本

---

## 11. 安全与权限

### 11.1 基本要求

- 所有写操作需鉴权
- 用户只能访问自己的会话和日志
- 知识库上传需管理权限
- 工具调用需按场景授权

### 11.2 审计记录

建议记录：

- 谁调用了什么接口
- 什么时间
- 传了什么参数
- 返回了什么结果
- 是否失败

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

### 13.1 后端服务拆分

推荐拆成：

1. API 服务
2. Agent 编排服务
3. 检索服务
4. 工具服务
5. 分析服务
6. Worker 服务

### 13.2 推荐协作方式

- API 层只做薄路由和鉴权
- Agent 层负责决策和编排
- Tool 层负责确定性执行
- RAG 层负责召回和引用


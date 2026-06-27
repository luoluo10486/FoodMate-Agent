# FoodMate 智能体行为与工具协议

版本：v1.0  
对应总设计：[FoodMate-系统设计与技术方案.md](./FoodMate-系统设计与技术方案.md)  
对应产品文档：[FoodMate-产品需求文档.md](./FoodMate-产品需求文档.md)  
对应接口文档：[FoodMate-接口与数据规范.md](./FoodMate-接口与数据规范.md)
文档边界：本文件只定义 Agent 角色、Prompt 协议、工具协议与调用策略；最终架构分层、工具优先级、查询主路径和模型治理边界以总设计文档为准。

---

## 1. 文档目标

本文定义 FoodMate Agent 的核心行为协议：

- 它是谁
- 它应该怎么思考
- 它什么时候检索
- 它什么时候调用工具
- 它什么时候追问
- 它应该输出什么格式

同时定义工具协议，确保 Agent 与后端工具可以稳定对接。

---

## 2. 总体设计原则

### 2.1 Agent 不直接做的事

Agent 不应该直接负责：

- 精确计算
- 数据写库
- 权限判断
- 复杂规则的最终裁决
- 不能验证的数据编造

这些交给工具、规则、数据库或校验器。

### 2.2 Agent 应该做的事

Agent 应该负责：

- 理解用户意图
- 判断任务类型
- 设计执行计划
- 决定是否需要检索
- 决定是否需要工具
- 组织多步执行
- 总结和解释结果

### 2.3 输出原则

1. 优先结构化
2. 优先可执行
3. 优先有依据
4. 不确定就追问
5. 工具失败要透明

---

## 3. 系统角色定义

### 3.1 Orchestrator

负责总调度。

职责：

- 接收用户输入
- 调用 Router
- 生成执行计划
- 协调检索和工具
- 汇总最终回答

### 3.2 Router

负责意图识别与分流。

输出：

- intent
- confidence
- need_rag
- need_tools
- need_clarification
- missing_slots

### 3.3 Planner

负责把复杂任务拆成步骤。

输入：

- 用户问题
- 当前上下文
- 用户画像
- 已知约束

输出：

- plan steps
- 每步目标
- 每步所需工具
- 终止条件

### 3.4 Answer Composer

负责最终表达。

要求：

- 语言自然
- 结构清晰
- 有引用
- 有下一步建议

### 3.5 Memory Manager

负责记忆处理。

职责：

- 抽取短期记忆
- 维护长期偏好
- 记录任务记忆
- 生成会话摘要

### 3.6 Query Understanding

负责查询理解。

职责：

- 把口语化问题改成可检索 query
- 结合上下文消歧
- 抽取核心实体
- 做实体归一化与时间归一化
- 构建 ACL filter
- 输出关键词 query、语义 query 和结构化过滤条件

### 3.7 Validator

负责结果校验。

职责：

- 校验工具输出
- 校验约束是否满足
- 校验计划是否合理

### 3.8 Data Query Agent

负责结构化数据查询。

职责：

- 选择数据源
- 读取 schema catalog
- 生成只读 SQL
- 校验 SQL 风险
- 执行查询并解释结果

---

## 4. System Prompt 设计

### 4.1 总 System Prompt

建议作为最顶层系统指令：

```text
你是 FoodMate，一个面向餐饮、营养、菜单、记录、分析和备餐规划的任务型 Agent。

你的首要目标不是闲聊，而是帮助用户完成任务。

你必须遵守以下原则：
1. 遇到信息不足时，先追问，不要猜测。
2. 涉及数值计算、单位换算、数据统计、写入操作时，优先调用工具，不要直接凭感觉回答。
3. 涉及知识问答时，优先检索知识库，并在回答中给出来源依据。
4. 遇到复杂任务时，先拆解步骤，再逐步执行。
5. 所有结果尽量结构化，便于前端展示和后续复用。
6. 如果工具失败，要明确说明，不要伪造结果。
7. 如果回答存在假设，要主动说出假设。
8. 健康与营养建议应保持审慎，避免绝对化表述。
```

### 4.2 Router Prompt

用途：分类任务。

```text
请判断用户输入属于哪个意图类别：
- calculation
- record
- analysis
- planning
- knowledge_qna
- mixed

同时判断：
- 是否需要检索知识库
- 是否需要调用工具
- 是否缺少关键参数
- 是否应该追问

输出必须是 JSON，不要输出多余文字。
```

Router 输出示例：

```json
{
  "intent": "planning",
  "confidence": 0.94,
  "need_rag": true,
  "need_tools": true,
  "need_clarification": false,
  "missing_slots": [],
  "sub_intents": ["meal_plan", "shopping_list"]
}
```

### 4.3 Planner Prompt

用途：拆解复杂任务。

```text
你是任务规划器。请把用户目标拆分为最少且必要的执行步骤。

要求：
1. 步骤必须可执行。
2. 每一步都说明目标和产出。
3. 如果缺少关键约束，先标记为需要追问。
4. 如果某一步依赖工具，明确工具名称。
5. 如果任务可一次完成，不要硬拆太多步。

输出必须是 JSON。
```

Planner 输出示例：

```json
{
  "goal": "为 2 个人生成 7 天备餐计划",
  "need_clarification": true,
  "missing_slots": ["budget", "diet_goal", "forbidden_foods"],
  "steps": [
    {
      "id": 1,
      "name": "collect_constraints",
      "tool": null,
      "output": "完整约束集"
    },
    {
      "id": 2,
      "name": "generate_meal_plan",
      "tool": "meal_plan_generator",
      "output": "7 天菜单草案"
    },
    {
      "id": 3,
      "name": "generate_shopping_list",
      "tool": "shopping_list_generator",
      "output": "购物清单"
    },
    {
      "id": 4,
      "name": "validate_plan",
      "tool": "plan_validator",
      "output": "校验结果"
    }
  ]
}
```

### 4.4 Answer Composer Prompt

用途：输出最终结果。

```text
你要把执行结果整理成用户容易理解的答案。

要求：
1. 先给结论。
2. 再给依据。
3. 再给步骤或表格。
4. 最后给下一步建议。
5. 不要堆砌术语。
6. 不要输出内部推理过程。
7. 如果有引用，要清晰标明来源。
```

### 4.5 Query Understanding Prompt

用途：把用户原始问题整理成适合检索、过滤和审计的查询理解结果。

```text
你要先做查询理解，再把用户问题整理成适合检索的形式。

要求：
1. 保留核心实体和动作。
2. 补全上下文中已明确的信息。
3. 去掉无检索价值的口语词。
4. 输出关键词 query 和语义 query。
5. 不要凭空添加用户没有说过的硬约束。
6. 无法确定时优先标记为需要追问。

输出必须是 JSON，不要输出多余文字。
```

示例输出：

```json
{
  "original_query": "这个要多久",
  "resolved_query": "西兰花焯水多久合适",
  "keyword_query": "西兰花 焯水 时间",
  "semantic_query": "西兰花焯水多久合适",
  "filters": {
    "doc_type": "cooking_guide"
  },
  "confidence": 0.91
}
```

### 4.6 SQL Generation Prompt

用途：基于 schema catalog 生成只读 SQL。

```text
你要根据用户问题和数据源 schema 生成只读 SQL。

要求：
1. 只允许生成 SELECT 或 WITH ... SELECT。
2. 必须使用给定的数据源和表结构。
3. 必须优先满足用户的业务问题，而不是追求复杂写法。
4. 必须考虑租户过滤、用户过滤和时间范围过滤。
5. 必须带 LIMIT。
6. 不允许写入、删除、DDL、存储过程或危险函数。
7. 如果信息不足，先输出需要追问的字段，不要胡写。

输出必须是 JSON，不要输出多余文字。
```

示例输出：

```json
{
  "datasource_id": "1912345678901234601",
  "sql": "SELECT SUM(protein) AS total_protein FROM user_food_logs WHERE user_id = ? AND created_at >= ? AND created_at < ? LIMIT 100",
  "bindings": ["1912345678901234001", "2026-05-25", "2026-06-01"],
  "risk_level": "low",
  "need_confirmation": false
}
```

---

## 5. 推理与决策策略

### 5.1 意图分流规则

#### calculation
触发条件：

- 计算热量
- 单位换算
- 克重折算
- 营养估算

优先工具：

- calculator
- time_parser
- knowledge_search

说明：MVP 阶段营养估算先通过 `knowledge_search` 获取数据来源，再用 `calculator` 做克重折算；`nutrition_lookup` 属于 P1 高频封装工具。

#### record
触发条件：

- 记录今天吃了什么
- 写入饮食日志

优先工具：

- food_log_writer

#### analysis
触发条件：

- 统计摄入
- 趋势分析
- 对比目标

优先工具：

- database_query
- plan_validator

#### planning
触发条件：

- 周计划
- 多人餐食规划
- 购物清单

优先工具：

- meal_plan_generator
- shopping_list_generator
- plan_validator

#### knowledge_qna
触发条件：

- 烹饪知识
- 食材保存
- 规则说明

优先工具：

- knowledge_search
- reranker

#### mixed
触发条件：

- 既要分析又要规划
- 既要检索又要写入

优先方式：

- 先路由
- 再规划
- 再分步执行

---

### 5.2 是否追问的决策树

先问自己三个问题：

1. 这个任务少了关键参数还能正确完成吗？
2. 如果继续执行，会不会产生明显错误？
3. 是否存在用户可以一问就补齐的信息？

如果答案是“不能、会错、可以补”，则追问。

### 5.3 追问原则

1. 追问数量尽量少
2. 一次问完关键槽位
3. 问题要短
4. 不要设计成考试题
5. 不要同时问太多无关项

示例：

```text
我可以帮你做一周备餐计划。先确认 3 个信息：
1. 预算大概多少？
2. 有没有忌口？
3. 目标是减脂、增肌还是均衡饮食？
```

---

## 6. 输出协议

### 6.1 最终回答结构

推荐统一输出：

```json
{
  "summary": "一句话结论",
  "answer": "面向用户的完整回答",
  "steps": [
    "步骤1",
    "步骤2"
  ],
  "references": [
    {
      "title": "来源标题",
      "snippet": "引用片段"
    }
  ],
  "warnings": [
    "必要的风险提示或假设"
  ],
  "next_actions": [
    "下一步建议"
  ]
}
```

### 6.2 针对不同任务的输出策略

#### 计算类

- 给出最终结果
- 标明计算口径
- 标明单位

#### 记录类

- 显示已记录的内容
- 返回写入时间
- 返回记录 ID 或摘要

#### 分析类

- 显示统计结果
- 显示趋势
- 显示异常点

#### 规划类

- 分天/分餐输出
- 输出购物清单
- 输出执行说明

#### 问答类

- 先结论
- 再依据
- 再说明

---

## 7. 工具注册协议

### 7.1 工具元数据

每个工具都必须定义以下字段：

```json
{
  "name": "knowledge_search",
  "description": "检索知识库并返回可引用片段",
  "version": "1.0.0",
  "input_schema": {
    "type": "object",
    "properties": {
      "query": { "type": "string" },
      "top_k": { "type": "integer" },
      "filters": { "type": "object" }
    },
    "required": ["query"]
  },
  "output_schema": {
    "type": "object",
    "properties": {
      "hits": { "type": "array" },
      "references": { "type": "array" }
    }
  },
  "permissions": ["read"],
  "timeout_ms": 3000,
  "retryable": true,
  "idempotent": true
}
```

### 7.2 工具必备属性

| 属性 | 说明 |
|---|---|
| name | 工具名 |
| description | 工具用途 |
| input_schema | 输入结构 |
| output_schema | 输出结构 |
| permissions | 权限要求 |
| timeout_ms | 超时时间 |
| retryable | 是否可重试 |
| idempotent | 是否幂等 |

---

## 8. 工具分层与典型定义

当前实现阶段的权威工具分层以总设计文档中的 `P0 + P1 + 内部能力模块` 为准。  
本节只保留行为协议所需的典型工具定义示例，并统一以下口径：

- 结构化数据查询主路：`database_query -> SQL Agent -> MCP`
- `nutrition_lookup`、`meal_plan_generator`、`shopping_list_generator` 属于高频封装工具
- `entity_normalizer`、`prompt_router` 属于内部能力模块，不作为外部工具注册
- `P2` 只保留在总设计里作为未来可选能力，不作为当前行为协议的实现清单

### 8.1 calculator

用途：

- 数学计算
- 比例计算
- 总量汇总

输入：

```json
{
  "expression": "20 * 1.1"
}
```

输出：

```json
{
  "result": 22
}
```

### 8.2 time_parser

用途：

- 解析“昨天”“上周”“本月”等相对时间表达
- 输出标准时间范围和过滤条件

输入：

```json
{
  "text": "上周"
}
```

输出：

```json
{
  "start": "2026-05-25",
  "end": "2026-05-31",
  "timezone": "Asia/Shanghai"
}
```

### 8.3 nutrition_lookup（P1）

用途：

- 根据食材和克重查询热量与宏量营养素
- 属于高频封装查询工具，不替代结构化数据主路

输入：

```json
{
  "ingredient": "鸡胸肉",
  "amount": 200,
  "unit": "g"
}
```

输出：

```json
{
  "calories": 220,
  "protein": 46,
  "fat": 3.6,
  "carbs": 0,
  "source": "nutrition_db_v1"
}
```

### 8.4 food_log_writer

用途：

- 写入饮食记录

输入：

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

输出：

```json
{
  "food_log_id": "1912345678901234573",
  "status": "saved"
}
```

### 8.5 database_query

用途：

- 统一处理结构化数据查询
- 主路为 SQL Agent + MCP + Schema Catalog

输入：

```json
{
  "intent": "analysis",
  "datasource_hint": "food_log",
  "question": "查询最近 7 天蛋白质摄入总量"
}
```

输出：

```json
{
  "rows": [
    {
      "protein_total": 88
    }
  ],
  "sql_audit_id": "1912345678901234603"
}
```

### 8.6 meal_plan_generator

用途：

- 生成多天备餐计划
- 属于高频生成工具，结果仍需 `plan_validator` 校验

输入：

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

输出：

```json
{
  "plan": [
    {
      "day": 1,
      "breakfast": [],
      "lunch": [],
      "dinner": []
    }
  ]
}
```

### 8.7 shopping_list_generator

用途：

- 把计划转换成购物清单

输入：

```json
{
  "meal_plan_id": "1912345678901234574"
}
```

输出：

```json
{
  "items": [
    {
      "name": "鸡胸肉",
      "amount": 1400,
      "unit": "g"
    }
  ]
}
```

### 8.8 knowledge_search

用途：

- 检索知识库

输入：

```json
{
  "query": "西兰花焯水多久",
  "top_k": 5
}
```

输出：

```json
{
  "hits": [
    {
      "title": "烹饪指南",
      "snippet": "西兰花焯水 1-2 分钟即可",
      "score": 0.92
    }
  ]
}
```

### 8.9 plan_validator

用途：

- 校验计划是否满足预算、人数、营养约束

输入：

```json
{
  "meal_plan_id": "1912345678901234574"
}
```

输出：

```json
{
  "valid": true,
  "issues": []
}
```

---

## 9. 工具调用策略

### 9.1 调用顺序

建议顺序：

1. 先判断是否缺参
2. 再规划
3. 再检索或调用工具
4. 再校验
5. 最后总结

### 9.2 并行调用原则

满足以下条件时可以并行：

- 不存在前后依赖
- 输入已确定
- 工具无写冲突

例如：

- 同时查多个食材营养
- 同时查询日志和知识库

### 9.3 重试原则

可重试：

- 网络超时
- 临时服务错误

不可重试：

- 参数错误
- 权限失败
- 用户主动取消

---

## 10. 安全与约束

### 10.1 写入前确认

涉及以下写操作时，Agent 必须确认：

- 写日志
- 生成计划并保存
- 修改偏好
- 删除数据

### 10.2 假设披露

如果必须做假设，要明确告诉用户：

- 使用了默认份量
- 预算未知时采用了保守估计
- 数据缺失时使用了近似值

### 10.3 医疗与营养边界

在健康和营养相关输出中：

- 不能冒充医疗建议
- 不能给出绝对化结论
- 不能替代专业诊断

### 10.4 记忆写入协议

#### 短期记忆

- 当前轮消息
- 当前任务中间变量
- 当前计划状态

#### 长期记忆

- 用户偏好
- 忌口
- 常用单位
- 常用预算
- 常用人数

#### 写入规则

- 只有高置信度信息可以自动写入长期记忆
- 涉及敏感信息要先确认
- 冲突时以用户当前明确输入为准
- 每条长期记忆都要记录来源和时间

#### 记忆写入时机

- 用户明确表达偏好
- 多次行为稳定一致
- 当前任务结束后抽取总结

### 10.5 行为边界

- 不要没有规划就直接答
- 不要把工具输出原样给用户
- 不要跳过观察与校验
- 不要把记忆和检索混成一层
- 不要把原始问题直接丢给检索层

### 10.6 行为范式

FoodMate 推荐采用 **Plan-Act-Observe-Reflect** 范式。

#### 执行步骤

1. `Plan`：先拆步骤
2. `Act`：执行检索或工具
3. `Observe`：读取结果和状态
4. `Reflect`：判断是否继续、修正或终止

#### 为什么不是纯 ReAct

- 纯 ReAct 容易在复杂任务里循环发散
- 不利于任务回放与审计
- 不利于多步骤约束控制

#### 为什么不是纯 Workflow

- 纯 Workflow 不够灵活，遇到不确定问题容易卡死
- 无法很好处理追问、补参、动态分支

#### 推荐使用边界

- Router 负责分类
- Planner 负责拆步
- Executor 负责执行
- Validator 负责校验
- Composer 负责输出

#### 行为约束

- 每一步必须有明确目标
- 每一步必须有结果
- 每一步都要记录状态
- 失败后必须能回到上一步

---

## 11. 调试与评估

### 11.1 调试输出

开发环境建议记录：

- router 输出
- planner 输出
- tool inputs
- tool outputs
- retrieval hits
- final answer schema

### 11.2 评估维度

| 维度 | 关注点 |
|---|---|
| 准确性 | 结果是否正确 |
| 完整性 | 是否覆盖关键约束 |
| 可执行性 | 是否能直接落地 |
| 可解释性 | 是否有依据 |
| 稳定性 | 重复输入是否稳定 |
| 效率 | 是否调用了过多步骤 |

---

## 12. 推荐实现方式

### 12.1 推荐控制流

1. 接收用户输入
2. Router 分类
3. Planner 生成步骤
4. 调用 RAG 或工具
5. 校验
6. 组装回答

### 12.2 推荐输出原则

- 默认输出 JSON 内核
- 前端负责渲染为卡片、表格、段落
- 保留内部字段用于调试

### 12.3 推荐版本控制

- prompt 版本化
- tool schema 版本化
- output schema 版本化
- 回归测试集版本化


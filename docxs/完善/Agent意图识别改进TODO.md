# Agent 意图识别改进 TODO

## 背景

当前 Python `agent_core.py` 中的 `DeterministicRouter` 主要依靠关键词和规则顺序，将用户消息分到 `calculation`、`analysis`、`planning`、`record`、`knowledge_qna` 五类外层意图。该步骤本身不调用 LLM。

这使路由行为可预测、易测试，但对自然语言表达的覆盖有限。否定表达、同义表达、上下文省略和一句话包含多个目标时，关键词规则可能误分类。例如同时询问饮食分析并要求制定计划的请求，当前单一 `RouteDecision.intent` 难以表达完整意图。SQL Planner 的模型辅助模式发生在路由之后，不能修复外层路由已经选错的问题。

## 改进目标

- 提高自然语言请求到业务流程的意图识别准确度，减少仅凭关键词误路由。
- 能识别缺失参数、相关实体和时间范围，并在信息不足时请求澄清。
- 对多意图请求提供明确策略：支持拆分为有序子任务，或安全地请求用户选择；不静默丢弃次要意图。
- 保留规则和模型输出的可测试性、可观测性及版本追踪。
- LLM 只负责语义理解和提出结构化分类；权限、工具可用性、SQL 执行和业务写入仍由 Java 控制面决定。
- SQL 生成从“固定业务模板或模型直接拼接 SQL”演进为“结构化查询计划优先、受限 SQL 生成、Java 最终校验执行”。

## 待办

- [ ] 盘点现有外层意图、触发条件、重叠表达和对应 Workflow，形成版本化分类契约。
- [ ] 定义结构化分类结果，至少包含主意图、可选子意图、实体、时间范围、缺失槽位、置信度、理由码和分类器版本。
- [ ] 为模型输出建立严格 Schema 校验、枚举白名单、长度限制和无效响应处理；输出无效或模型不可用时走明确的澄清/降级策略，不得直接产生工具副作用。
- [ ] 引入模型辅助意图分类，并复用现有 `ModelRouter`、预算、deadline、ProviderAttempt 和用量审计，不建立第二套模型密钥或路由配置。
- [ ] 明确 deterministic 规则的职责：保留高置信度、无歧义的快捷路径和安全硬规则；对低置信度、冲突或多意图表达交给模型分类或请求澄清。
- [ ] 确保分类结果只影响 Workflow 选择，不作为用户授权。Java 继续从登录会话和 AgentRun 派生可信用户范围，并对每个 Tool/SQL Proposal 独立校验。
- [ ] 为分类器建立离线评测集，覆盖常见表达、同义改写、错别字、否定、上下文省略、时间/实体缺失、多意图、越权诱导和高风险营养问题。
- [ ] 指标至少覆盖意图准确率、各类 Precision/Recall、混淆矩阵、澄清率、错误工具提案率、分类延迟和模型成本；按分类器版本记录结果。
- [ ] 为模型不可用、超时、结构化输出无效、低置信度和上下文不足补充回归测试及可观测错误码。
- [ ] 通过离线评测和本地业务回归后，再决定是否将模型辅助分类设为默认路径；切换前保留可回滚配置。

## SQL 生成约束改进

当前 `DeterministicSqlPlanner` 使用经过审查的固定 SQL 模板，`ModelRouterSqlPlanner` 可以让模型输出候选 SQL。固定模板有利于早期稳定性，但表达能力有限；模型直接输出完整 SQL 又会带来字段幻觉、复杂查询失控、提示注入和 Catalog 不同步等风险。

后续应采用以下分层方式：

```text
用户问题
  -> 意图和实体识别
  -> 结构化 QueryPlan
  -> QueryPlan Schema/业务规则校验
  -> 根据当前 Catalog 生成受限 SQL
  -> Java AST Guard、用户范围、软删除、LIMIT、超时
  -> 执行并审计
```

待办：

- [ ] 定义版本化 `QueryPlan`，至少包含 `intent`、`metrics`、`dimensions`、`filters`、`time_range`、`group_by`、`sort`、`limit` 和 `expected_shape`。
- [ ] 将 QueryPlan 的可选意图、指标、维度、过滤字段和聚合函数全部绑定到 Java Catalog，不允许模型自由添加表名、字段名或函数名。
- [ ] 模型只输出结构化 QueryPlan；是否允许模型输出 `candidate_sql` 需要单独配置，默认不把模型 SQL 当作可信输入。
- [ ] 由 Java 或受控 SQL Composer 根据已校验 QueryPlan 和当前 Catalog 生成参数化 SQL，用户输入只作为绑定参数，不能直接拼入 SQL。
- [ ] 对每个业务意图定义结果列和 `expected_shape`，避免模型生成语义正确但结果结构不稳定的查询。
- [ ] 保留 deterministic 模板作为降级和回归基线，但不把模板数量扩展成覆盖所有自然语言表达的主要方案。
- [ ] 让 Java 根据 PostgreSQL 当前 Catalog 生成最小 schema snapshot，替代 Python 中可能过期的固定 `approved_schema`；snapshot 版本进入 QueryPlan/Proposal 摘要。
- [ ] 对复杂查询采用有限的查询算子组合，例如单表、批准的 JOIN、聚合、分组、排序和时间过滤；拒绝递归、任意子查询、窗口函数和未评审算子，除非先扩展 Catalog 与 AST 规则。
- [ ] 增加 SQL 生成安全回归：字段幻觉、表越权、跨用户查询、敏感字段、无界查询、注释绕过、注入字符、错误 JOIN、错误聚合和结果列不匹配。

## 建议分类契约草案

```json
{
  "status": "ready",
  "intents": [
    {
      "name": "analysis",
      "confidence": 0.94,
      "entities": {"metric": "protein", "time_range": "last_7_days"}
    }
  ],
  "missing_slots": [],
  "reason_code": "STRUCTURED_DATA_ANALYSIS",
  "classifier_version": "intent-v1"
}
```

当前 Python 分类器已经实现相同方向的严格契约，并增加 `clarification_question`，供模型直接给出简短澄清问题；多意图请求会进入澄清，不会静默选择其中一项。Prompt 模板、示例和完整字段约束位于 `agent-runtime/intent_classifier.py`。

## 验收标准

1. 分类契约由 Python 生成并经过 Schema/白名单校验，非法类别和额外未授权字段不能进入 Workflow。
2. 离线评测集覆盖五类现有意图、关键歧义和多意图表达，并记录可复现的分类结果及版本。
3. 低置信度、互相冲突或缺少必要槽位时，系统向用户澄清，不生成会触发业务副作用的 Proposal。
4. 分类输出不能扩大 Java 授权上下文、工具白名单、SQL schema 或用户数据范围。
5. 模型路由、预算、deadline、失败审计和 deterministic 回归路径均有自动化测试。
6. 完成受影响的 Python、Java 契约和端到端测试，并在 TODO 中记录实际运行命令与结果后，才可勾选完成。
7. SQL 生成只能从通过 QueryPlan 和 Catalog 校验的结构中产生；模型直接生成的未校验 SQL 不能进入执行器。

## 关联实现

- 外层路由：`agent-runtime/agent_core.py` 的 `DeterministicRouter` 和 `RouteDecision`。
- SQL 子意图规划：`agent-runtime/sql_planner.py` 的 `DeterministicSqlPlanner` 与 `ModelRouterSqlPlanner`。
- Tool/SQL 最终授权和执行：Java `ToolGatewayServiceImpl`、`SqlQueryPlanValidator` 与 `JSqlParserQueryGuard`。
- SQL Agent 业务闭环说明：[`../项目/M2-2 Java Tool Gateway与SQL Agent实施方案.md`](../项目/M2-2%20Java%20Tool%20Gateway与SQL%20Agent实施方案.md)。

## 当前状态

外层意图分类已由共享 ModelRouter 的 LLM 分类器执行；默认模式为 `FOODMATE_AGENT_INTENT_CLASSIFIER_MODE=llm`。无效输出、低置信度、多意图或模型不可用时进入澄清，不回退到关键词规则；`stub` 仅供离线测试显式使用。当前分类结果只选择既有 Workflow，Java 仍负责每个工具的授权和执行。离线评测集、指标、真实模型评测以及 QueryPlan 到受限 SQL Composer 仍未完成；SQL Planner 的候选 SQL 仍必须通过 Java Catalog、权限、AST 和执行校验。

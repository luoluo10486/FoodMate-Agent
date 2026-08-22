"""M1-4 的确定性 Agent 内核。

本模块先提供可替换的执行边界：模型、Java 记忆写入和 Tool/SQL 仍由后续适配器接入，
但 Router、Planner、预算、上下文、Eval 和回答分片已经有稳定且可测试的行为。
"""

from __future__ import annotations

import hashlib
import json
import math
import os
import time
from dataclasses import asdict, dataclass, field
from typing import Any

from model_provider import ModelProviderError, ModelRequest, ModelRouter, ProviderAttempt
from proposal_protocol import Proposal, validate_proposal
from sql_planner import SqlPlannerError, planner_from_environment


def _digest(value: Any) -> str:
    raw = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), default=str)
    return "sha256:" + hashlib.sha256(raw.encode("utf-8")).hexdigest()


@dataclass(frozen=True)
class BudgetSnapshot:
    max_total_tokens: int = 30000
    max_cost_cny: float = 0.50
    max_total_steps: int = 30
    max_model_calls: int = 12
    max_replans: int = 1
    max_answer_rewrites: int = 1
    revision: int = 1
    config_version: str = "m1-4-default"

    @classmethod
    def from_command(cls, command: dict[str, Any]) -> "BudgetSnapshot":
        raw = command.get("budget_snapshot") or command.get("runtime_options", {}).get("budget_snapshot") or {}
        values = {
            key: raw[key] for key in (
                "max_total_tokens", "max_cost_cny", "max_total_steps", "max_model_calls",
                "max_replans", "max_answer_rewrites", "revision", "config_version",
            ) if key in raw
        }
        return cls(**values)


@dataclass
class Usage:
    tokens: int = 0
    cost_cny: float = 0.0
    model_calls: int = 0
    steps: int = 0

    def ratio(self, budget: BudgetSnapshot) -> float:
        token_ratio = self.tokens / budget.max_total_tokens
        cost_ratio = self.cost_cny / budget.max_cost_cny
        return max(token_ratio, cost_ratio)


@dataclass(frozen=True)
class BudgetPolicy:
    mode: str
    allow_reflection: bool
    allow_optional_retrieval: bool
    allow_replan: bool
    allow_answer_rewrite: bool
    allow_new_model_call: bool
    requires_confirmation: bool

    def as_dict(self) -> dict[str, object]:
        return {
            "mode": self.mode,
            "allow_reflection": self.allow_reflection,
            "allow_optional_retrieval": self.allow_optional_retrieval,
            "allow_replan": self.allow_replan,
            "allow_answer_rewrite": self.allow_answer_rewrite,
            "allow_new_model_call": self.allow_new_model_call,
            "requires_confirmation": self.requires_confirmation,
        }


@dataclass(frozen=True)
class RouteDecision:
    intent: str
    complexity: str
    risk_level: str
    missing_slots: tuple[str, ...] = ()


@dataclass(frozen=True)
class Plan:
    steps: tuple[str, ...]
    route: RouteDecision
    plan_version: str = "m1-4-deterministic-plan-v1"


WORKFLOW_EDGES: dict[str, frozenset[str]] = {
    "start": frozenset({"router"}),
    "router": frozenset({"planner", "composer", "terminal"}),
    "planner": frozenset({"clarification", "execution", "composer", "terminal"}),
    "clarification": frozenset({"composer", "terminal"}),
    "execution": frozenset({"validator", "terminal"}),
    "validator": frozenset({"composer", "planner", "terminal"}),
    "composer": frozenset({"eval", "terminal"}),
    "eval": frozenset({"terminal"}),
    "terminal": frozenset(),
}


class WorkflowGraph:
    """M1-4 白名单状态图；节点只能沿固定边推进，不能由模型动态加边。"""

    def __init__(self, max_steps: int):
        self.max_steps = max_steps
        self.current = "start"
        self.nodes: list[str] = []
        self.transitions: list[dict[str, str]] = []
        self.terminal_reason: str | None = None

    def enter(self, node: str) -> bool:
        if node not in WORKFLOW_EDGES.get(self.current, ()):
            raise RuntimeError("WORKFLOW_EDGE_NOT_ALLOWED")
        if len(self.nodes) >= self.max_steps:
            self.terminal_reason = "MAX_TOTAL_STEPS"
            self.current = "terminal"
            if not self.nodes or self.nodes[-1] != "terminal":
                self.nodes.append("terminal")
            return False
        previous = self.current
        self.current = node
        self.nodes.append(node)
        self.transitions.append({"from": previous, "to": node})
        return True

    def as_dict(self) -> dict[str, object]:
        return {
            "nodes": list(self.nodes),
            "transitions": list(self.transitions),
            "terminal_reason": self.terminal_reason,
        }


@dataclass(frozen=True)
class Context:
    messages: tuple[dict[str, Any], ...]
    summary: dict[str, Any] | None
    memories: tuple[dict[str, Any], ...]
    unresolved_slots: tuple[str, ...]
    sources: dict[str, tuple[str, ...]]
    estimated_tokens: int = 0
    tool_results: tuple[dict[str, Any], ...] = ()
    analysis_plan: dict[str, Any] | None = None


class ContextBuilder:
    """按固定优先级组装上下文；Python 只读取 Java 已授权的上下文。"""

    def __init__(self, max_recent_messages: int = 8, max_context_tokens: int = 12000):
        self.max_recent_messages = max_recent_messages
        self.max_context_tokens = max_context_tokens

    @staticmethod
    def _estimate_tokens(messages: tuple[dict[str, Any], ...], summary: dict[str, Any] | None, memories: tuple[dict[str, Any], ...], tool_results: tuple[dict[str, Any], ...] = ()) -> int:
        # 这是预算上界估算，不冒充供应商 tokenizer；真实 usage 仍以 provider 返回为准。
        raw = json.dumps({"messages": messages, "summary": summary, "memories": memories, "tool_results": tool_results}, ensure_ascii=False, separators=(",", ":"), default=str)
        return max(1, len(raw))

    def build(self, command: dict[str, Any], route: RouteDecision) -> Context:
        authorized = command.get("authorized_context") or {}
        raw_messages = list(authorized.get("recent_messages") or [])
        current = command.get("message") or {}
        if current and not any(str(item.get("message_id")) == str(current.get("message_id")) for item in raw_messages):
            raw_messages.append(current)
        messages = list(raw_messages[-self.max_recent_messages:])
        summary = authorized.get("session_summary")
        memories = tuple(authorized.get("long_term_memories") or ())
        tool_results = tuple(authorized.get("tool_results") or ())
        analysis_plan = authorized.get("database_query_plan")
        if not isinstance(analysis_plan, dict):
            analysis_plan = next(
                (
                    item.get("query_plan")
                    for item in reversed(tool_results)
                    if isinstance(item.get("query_plan"), dict)
                ),
                None,
            )
        citations = tuple(authorized.get("citations") or ())
        # 先保留最新消息，再从最旧的原始消息开始裁剪；当前输入在最后，不会被裁掉。
        while len(messages) > 1 and self._estimate_tokens(tuple(messages), summary, memories, tool_results) > self.max_context_tokens:
            messages.pop(0)
        messages = tuple(messages)
        unresolved = tuple(route.missing_slots)
        sources = {
            "message_id": tuple(str(item["message_id"]) for item in messages if item.get("message_id") is not None),
            "summary_id": ((str(summary["summary_id"]),) if summary and summary.get("summary_id") is not None else ()),
            "memory_id": tuple(str(item["memory_id"]) for item in memories if item.get("memory_id") is not None),
            "citation_id": tuple(str(item["citation_id"]) for item in authorized.get("citations") or () if item.get("citation_id") is not None),
            "invocation_id": tuple(str(item["invocation_id"]) for item in tool_results if item.get("invocation_id") is not None),
        }
        return Context(
            messages,
            summary,
            memories,
            unresolved,
            sources,
            self._estimate_tokens(messages, summary, memories, tool_results)
            + len(json.dumps(citations, ensure_ascii=False)),
            tool_results,
            analysis_plan if isinstance(analysis_plan, dict) else None,
        )


class DeterministicRouter:
    """M1-4 初始路由器，输出结构化决策，不调用模型。"""

    def route(self, content: str) -> RouteDecision:
        text = content.strip()
        lower = text.lower()
        risk = "high" if any(word in text for word in ("疾病", "诊断", "处方", "过敏反应")) else "low"
        if any(word in text for word in ("记录", "吃了", "早餐", "午餐", "晚餐")):
            return RouteDecision("record", "complex" if len(text) > 60 else "simple", risk)
        if any(word in text for word in ("计划", "食谱", "购物清单")):
            return RouteDecision("planning", "complex", risk, ("days",) if "天" not in text else ())
        if any(word in text for word in ("分析", "营养", "蛋白质", "热量")):
            return RouteDecision("analysis", "complex", risk)
        return RouteDecision("knowledge_qna", "simple", risk)


class DeterministicPlanner:
    def plan(self, route: RouteDecision) -> Plan:
        if route.missing_slots:
            return Plan(("clarify",), route)
        if route.complexity == "simple":
            return Plan(("compose",), route)
        return Plan(("retrieve_authorized_context", "validate_facts", "compose"), route)


@dataclass(frozen=True)
class EvalDecision:
    result: str
    reason: str
    score: float | None = None
    evaluator_version: str = "deterministic-eval-v1"


class LlmEvalGate:
    """解析独立 Judge 的结构化结果；硬规则仍由 DeterministicEvalGate 主导。"""

    def __init__(self, min_score: float | None = None):
        raw = os.getenv("FOODMATE_AGENT_EVAL_MIN_SCORE", "0.75") if min_score is None else min_score
        try:
            self.min_score = float(raw)
        except (TypeError, ValueError):
            self.min_score = math.nan

    def evaluate(self, response: str) -> EvalDecision:
        try:
            value = json.loads(response)
            passed = value["passed"]
            score = float(value["score"])
            reason = str(value.get("reason") or "LLM_JUDGE")
        except (json.JSONDecodeError, KeyError, TypeError, ValueError):
            return EvalDecision("degrade", "EVAL_SCHEMA_INVALID", evaluator_version="llm-judge-v1")
        if not isinstance(passed, bool) or not math.isfinite(score) or not 0 <= score <= 1:
            return EvalDecision("degrade", "EVAL_SCORE_INVALID", evaluator_version="llm-judge-v1")
        if not math.isfinite(self.min_score) or not 0 <= self.min_score <= 1:
            return EvalDecision("degrade", "EVAL_THRESHOLD_INVALID", score=score, evaluator_version="llm-judge-v1")
        if not passed or score < self.min_score:
            return EvalDecision(
                "degrade",
                "EVAL_SCORE_BELOW_THRESHOLD" if passed else "EVAL_JUDGE_REJECTED",
                score=score,
                evaluator_version="llm-judge-v1",
            )
        return EvalDecision("pass", reason, score=score, evaluator_version="llm-judge-v1")


class DeterministicEvalGate:
    """候选正文发布前的硬规则门禁。"""

    def evaluate(self, answer: str, route: RouteDecision, usage: Usage, budget: BudgetSnapshot) -> EvalDecision:
        if not answer.strip():
            return EvalDecision("reject", "ANSWER_EMPTY", score=0.0)
        if usage.ratio(budget) >= 1:
            return EvalDecision("degrade", "BUDGET_EXHAUSTED", score=0.0)
        if route.risk_level == "high":
            return EvalDecision("degrade", "REQUEST_REVIEW_NO_HUMAN_REVIEWER", score=0.0)
        return EvalDecision("pass", "DETERMINISTIC_RULES_PASSED", score=1.0)


class StepValidator:
    """在进入 Composer 前检查步骤白名单、授权来源和必要输出，避免模型绕过状态机。"""

    ALLOWED_STEPS = frozenset({"clarify", "compose", "retrieve_authorized_context", "validate_facts"})

    def validate(self, route: RouteDecision, plan: Plan, context: Context) -> None:
        if not plan.steps or any(step not in self.ALLOWED_STEPS for step in plan.steps):
            raise ValueError("STEP_VALIDATION_FAILED: unknown plan step")
        if plan.route != route:
            raise ValueError("STEP_VALIDATION_FAILED: plan route mismatch")
        if plan.steps == ("clarify",) and not route.missing_slots:
            raise ValueError("STEP_VALIDATION_FAILED: clarification has no missing slot")
        if route.complexity == "complex" and not route.missing_slots and "validate_facts" not in plan.steps:
            raise ValueError("STEP_VALIDATION_FAILED: complex plan lacks fact validation")
        if route.missing_slots and plan.steps != ("clarify",):
            raise ValueError("STEP_VALIDATION_FAILED: clarification plan has side effects")
        if any(any(not item for item in source) for source in context.sources.values() if source is not None):
            raise ValueError("STEP_VALIDATION_FAILED: invalid context source")
        invocation_ids = [item.get("invocation_id") for item in context.tool_results]
        if any(not item for item in invocation_ids) or len(invocation_ids) != len(set(invocation_ids)):
            raise ValueError("STEP_VALIDATION_FAILED: duplicate or missing tool invocation")
        if any(item.get("status") not in {"succeeded", "failed", "rejected"} for item in context.tool_results):
            raise ValueError("STEP_VALIDATION_FAILED: invalid tool result status")
        if set(invocation_ids) - set(context.sources.get("invocation_id", ())):
            raise ValueError("STEP_VALIDATION_FAILED: tool result source mismatch")


@dataclass(frozen=True)
class ReflectionResult:
    accepted: bool
    reason: str


class Reflector:
    """Deterministic post-composition check; it never calls Eval or a model."""

    MAX_ANSWER_CHARS = 12000

    def reflect(
        self, answer: str, route: RouteDecision, context: Context
    ) -> ReflectionResult:
        if not answer or not answer.strip():
            return ReflectionResult(False, "REFLECTION_ANSWER_EMPTY")
        if len(answer) > self.MAX_ANSWER_CHARS:
            return ReflectionResult(False, "REFLECTION_ANSWER_TOO_LONG")
        if route.missing_slots and not all(slot in answer for slot in route.missing_slots):
            return ReflectionResult(False, "REFLECTION_CLARIFICATION_MISSING_SLOT")
        if route.complexity == "complex" and not context.messages:
            return ReflectionResult(False, "REFLECTION_NO_AUTHORIZED_SOURCE")
        if any(
            item.get("status") == "succeeded"
            and "rows" not in item
            and not item.get("error_code")
            for item in context.tool_results
        ):
            return ReflectionResult(False, "REFLECTION_TOOL_RESULT_INCOMPLETE")
        return ReflectionResult(True, "REFLECTION_PASSED")


class DeterministicComposer:
    def compose(self, content: str, route: RouteDecision, context: Context, budget_mode: str) -> str:
        if route.missing_slots:
            return "为了继续处理，请补充以下信息：" + "、".join(route.missing_slots) + "。"
        if route.intent == "analysis" and context.tool_results:
            analysis = self._compose_analysis(context, budget_mode)
            if analysis is not None:
                return analysis
        prefix = "节省模式：" if budget_mode in {"economy", "partial"} else ""
        recent = len(context.messages)
        tool_note = ""
        if context.tool_results:
            tool_note = f"已回注 {len(context.tool_results)} 个 Java 工具结果。"
        evidence_note = f"已检索到 {len(context.sources['citation_id'])} 条公共知识库证据。" if context.sources["citation_id"] else ""
        return f"{prefix}已完成{route.intent}请求的受控分析，已读取当前会话中的 {recent} 条有效消息。{tool_note}{evidence_note}"

    @staticmethod
    def _compose_analysis(context: Context, budget_mode: str) -> str | None:
        results = list(context.tool_results)
        database = next(
            (item for item in reversed(results) if item.get("tool_name") == "database_query"),
            None,
        )
        if database is None and any(item.get("sql_audit_id") for item in results):
            database = next((item for item in reversed(results) if item.get("sql_audit_id")), None)
        if database is None:
            return None
        plan = context.analysis_plan or {}
        time_range = plan.get("time_range") or {}
        days = time_range.get("days") if isinstance(time_range, dict) else None
        range_text = f"最近 {days} 天" if days else "已授权时间范围"
        metrics = ", ".join(str(item) for item in plan.get("metrics") or ()) or "已批准指标"
        dimensions = ", ".join(str(item) for item in plan.get("dimensions") or ()) or "无分组维度"
        status = database.get("status")
        if status != "succeeded":
            code = str(database.get("error_code") or "DATABASE_QUERY_FAILED")
            return (
                f"{('节省模式：' if budget_mode in {'economy', 'partial'} else '')}"
                f"饮食分析未完成。时间范围：{range_text}；统计口径：{metrics}；"
                f"分组维度：{dimensions}；工具状态：{code}。未生成趋势结论，请稍后重试。"
            )
        rows = database.get("rows") or []
        if not rows:
            return (
                f"时间范围：{range_text}。统计口径：{metrics}；分组维度：{dimensions}。"
                "数据覆盖：未找到可用的饮食记录。当前没有足够数据生成趋势结论，"
                "建议先补充饮食记录后再分析。"
            )
        safe_rows = json.dumps(rows[:20], ensure_ascii=False, separators=(",", ":"), default=str)
        if len(safe_rows) > 2_000:
            safe_rows = safe_rows[:2_000] + "..."
        return (
            f"时间范围：{range_text}。统计口径：{metrics}；分组维度：{dimensions}。"
            f"数据覆盖：返回 {len(rows)} 条已授权聚合结果。结果摘要：{safe_rows}。"
            "建议结合完整记录和个人目标继续查看变化。"
        )


def budget_mode(usage: Usage, budget: BudgetSnapshot) -> str:
    ratio = usage.ratio(budget)
    if ratio >= 1:
        return "partial"
    if ratio >= 0.85:
        return "economy"
    if ratio >= 0.70:
        return "reduced_reflection"
    return "normal"


def budget_policy(usage: Usage, budget: BudgetSnapshot) -> BudgetPolicy:
    """将预算阈值转换为固定动作，禁止模型自行决定越过阈值。"""
    ratio = usage.ratio(budget)
    if ratio >= 1 or usage.model_calls >= budget.max_model_calls:
        return BudgetPolicy("partial", False, False, False, False, False, True)
    if ratio >= 0.85:
        return BudgetPolicy("economy", False, False, False, False, True, False)
    if ratio >= 0.70:
        return BudgetPolicy("reduced_reflection", False, False, True, True, True, False)
    return BudgetPolicy("normal", True, True, True, True, True, False)


def should_run_llm_eval(command: dict[str, Any], route: RouteDecision) -> bool:
    """复杂任务强制 Eval，低风险使用稳定采样，避免随机行为造成不可复现。"""
    if os.getenv("FOODMATE_AGENT_LLM_EVAL_ENABLED", "true").lower() != "true":
        return False
    if route.complexity == "complex" or route.risk_level == "high":
        return True
    ratio = float(os.getenv("FOODMATE_AGENT_LLM_EVAL_SAMPLE_RATIO", "0.20"))
    ratio = min(max(ratio, 0.0), 1.0)
    key = str(command.get("run_id") or command.get("dispatch_id") or "")
    sample = int(hashlib.sha256(key.encode("utf-8")).hexdigest()[:8], 16) / 0xFFFFFFFF
    return sample < ratio


def split_answer(answer: str, max_bytes: int = 2048) -> list[str]:
    """按 UTF-8 字节上限切分，保证单个事件不超过配置上限。"""
    chunks: list[str] = []
    current: list[str] = []
    size = 0
    for char in answer:
        char_size = len(char.encode("utf-8"))
        if current and size + char_size > max_bytes:
            chunks.append("".join(current))
            current, size = [], 0
        current.append(char)
        size += char_size
    if current:
        chunks.append("".join(current))
    return chunks or [""]


def generate_memory_candidates(context: Context, content: str, max_candidates: int = 3) -> list[dict[str, Any]]:
    """只生成候选，不写 Java 的 user_memories 权威表。"""
    candidates: list[dict[str, Any]] = []
    if "我喜欢" in content or "我不吃" in content:
        candidates.append({
            "memory_type": "preference",
            "memory_key": "user_stated_preference",
            "memory_value": {"text": content[:200]},
            "confidence": 0.95,
            "scope": "global",
            "source_message_ids": list(context.sources["message_id"]),
        })
    return candidates[:max_candidates]


def generate_tool_proposals(command: dict[str, Any], route: RouteDecision) -> list[dict[str, Any]]:
    """只包装 Java 授权的工具请求；Python 不自行确认或拼接业务写入。"""
    authorized = command.get("authorized_context") or {}
    tool_results = list(authorized.get("tool_results") or ())
    writer = authorized.get("food_log_writer_request") or {}
    if isinstance(writer, dict) and writer and route.intent == "record":
        invocation_id = str(writer.get("invocation_id") or "")
        proposal = Proposal(
            proposal_id="prop_" + invocation_id,
            run_id=str(command["run_id"]),
            proposal_type="tool",
            schema_version="v1",
            payload={"invocation_id": invocation_id, "idempotency_key": writer.get("idempotency_key")},
            requires_confirmation=True,
            tool_name="food_log_writer",
            confirmation_ref=str(writer.get("confirmation_ref") or ""),
            input=writer.get("input"),
        ).as_dict()
        validate_proposal(Proposal(
            proposal["proposal_id"], proposal["run_id"], proposal["proposal_type"], proposal["schema_version"],
            proposal["payload"], proposal["requires_confirmation"], proposal["request_hash"],
            proposal.get("tool_name"), proposal.get("confirmation_ref"), proposal.get("input"),
        ))
        return [proposal]
    request = authorized.get("sql_read_request") or {}
    if not isinstance(request, dict) or not request.get("statement") or route.intent not in {"record", "analysis"}:
        if route.intent != "analysis":
            return []
    if route.intent == "analysis":
        question = str((command.get("message") or {}).get("content", ""))
        plan = planner_from_environment().plan(question)
        if plan.status == "need_clarification":
            raise SqlPlannerError(
                "SQL_PLANNER_TIME_RANGE_REQUIRED",
                "analysis query requires a time range",
                plan.missing_slots,
            )
        database_result = next(
            (
                item
                for item in reversed(tool_results)
                if item.get("tool_name") == "database_query"
                or item.get("sql_audit_id")
            ),
            None,
        )
        if database_result is not None:
            return []
        time_result = next(
            (item for item in reversed(tool_results) if item.get("tool_name") == "time_parser"),
            None,
        )
        if time_result is None:
            invocation_id = str(
                "time_"
                + hashlib.sha256(
                    (str(command["run_id"]) + question).encode("utf-8")
                ).hexdigest()[:24]
            )
            proposal = Proposal(
                proposal_id="prop_" + invocation_id,
                run_id=str(command["run_id"]),
                proposal_type="tool",
                schema_version="v1",
                payload={
                    "invocation_id": invocation_id,
                    "idempotency_key": "time_"
                    + hashlib.sha256(question.encode("utf-8")).hexdigest()[:32],
                },
                requires_confirmation=False,
                tool_name="time_parser",
                input={
                    "question": question,
                    "timezone": (plan.time_range or {}).get("timezone", "Asia/Shanghai"),
                },
            ).as_dict()
            validate_proposal(
                Proposal(
                    proposal["proposal_id"],
                    proposal["run_id"],
                    proposal["proposal_type"],
                    proposal["schema_version"],
                    proposal["payload"],
                    proposal["requires_confirmation"],
                    proposal["request_hash"],
                    proposal.get("tool_name"),
                    proposal.get("confirmation_ref"),
                    proposal.get("input"),
                )
            )
            return [proposal]
        if time_result.get("status") != "succeeded":
            return []
        statement = str(plan.candidate_sql)
        invocation_id = str(request.get("invocation_id") or "inv_" + hashlib.sha256(statement.encode("utf-8")).hexdigest()[:24])
        input_plan = {
            "intent": plan.intent,
            "time_range": plan.time_range,
            "metrics": list(plan.metrics),
            "dimensions": list(plan.dimensions),
            "filters": dict(plan.filters),
            "candidate_sql": statement,
            "planner_mode": plan.planner_mode,
            "planner_version": plan.planner_version,
        }
        proposal = Proposal(
            proposal_id="prop_" + invocation_id,
            run_id=str(command["run_id"]),
            proposal_type="tool",
            schema_version="v1",
            payload={
                "statement": statement,
                "invocation_id": invocation_id,
                "idempotency_key": "dbq_" + hashlib.sha256((str(command["run_id"]) + statement).encode("utf-8")).hexdigest()[:32],
            },
            requires_confirmation=False,
            tool_name="database_query",
            input=input_plan,
        ).as_dict()
        validate_proposal(Proposal(
            proposal["proposal_id"], proposal["run_id"], proposal["proposal_type"], proposal["schema_version"],
            proposal["payload"], proposal["requires_confirmation"], proposal["request_hash"],
            proposal.get("tool_name"), proposal.get("confirmation_ref"), proposal.get("input"),
        ))
        return [proposal]
    statement = str(request["statement"]).strip()
    invocation_id = str(request.get("invocation_id") or "inv_" + hashlib.sha256(statement.encode("utf-8")).hexdigest()[:24])
    proposal = Proposal(
        proposal_id="prop_" + invocation_id,
        run_id=str(command["run_id"]),
        proposal_type="sql_read",
        schema_version="v1",
        payload={"statement": statement, "invocation_id": invocation_id},
        requires_confirmation=bool(request.get("requires_confirmation", False)),
    ).as_dict()
    validate_proposal(Proposal(
        proposal["proposal_id"], proposal["run_id"], proposal["proposal_type"], proposal["schema_version"],
        proposal["payload"], proposal["requires_confirmation"], proposal["request_hash"],
    ))
    return [proposal]


class InMemoryCheckpoint:
    """本地开发 checkpoint；生产接入 Redis 时复用相同 CAS 接口。"""

    def __init__(self):
        self._values: dict[str, tuple[int, dict[str, Any]]] = {}

    def load(self, key: str) -> tuple[int, dict[str, Any]] | None:
        return self._values.get(key)

    def save(self, key: str, value: dict[str, Any], expected_version: int | None = None) -> int:
        current = self._values.get(key)
        current_version = current[0] if current else 0
        if expected_version is not None and expected_version != current_version:
            raise RuntimeError("CHECKPOINT_CAS_CONFLICT")
        version = current_version + 1
        self._values[key] = (version, value)
        return version


@dataclass
class AgentExecution:
    route: RouteDecision
    plan: Plan
    context: Context
    answer: str
    eval: EvalDecision
    usage: Usage
    budget_mode: str
    memory_candidates: list[dict[str, Any]] = field(default_factory=list)
    model_attempts: list[ProviderAttempt] = field(default_factory=list)
    budget_actions: dict[str, object] = field(default_factory=dict)
    workflow: dict[str, object] = field(default_factory=dict)
    proposals: list[dict[str, Any]] = field(default_factory=list)


def run_deterministic(command: dict[str, Any], checkpoint: InMemoryCheckpoint | None = None, model_router: ModelRouter | None = None) -> AgentExecution:
    # Runtime execution state must not overwrite the resumable tool-wait checkpoint.
    # Direct callers keep the historical key; the RocketMQ path supplies a private
    # state key and leaves the recovery key to checkpoint boundary writes.
    checkpoint_key = str(
        command.get("_checkpoint_key")
        or (str(command["run_id"]) + ":" + str(command["dispatch_id"]))
    )
    """执行 M1-4 内核；默认本地 provider，云模型由环境别名显式启用。"""
    content = str((command.get("message") or {}).get("content", ""))
    budget = BudgetSnapshot.from_command(command)
    route = DeterministicRouter().route(content)
    options = command.get("runtime_options") or {}
    context = ContextBuilder(
        int(options.get("context_max_recent_messages", 8)),
        int(options.get("context_max_tokens", os.getenv("FOODMATE_AGENT_CONTEXT_MAX_TOKENS", "12000"))),
    ).build(command, route)
    plan = DeterministicPlanner().plan(route)
    graph = WorkflowGraph(BudgetSnapshot.from_command(command).max_total_steps)
    graph.enter("router")
    def advance(node: str) -> bool:
        return graph.terminal_reason is None and graph.enter(node)

    if graph.terminal_reason is None:
        if route.missing_slots:
            advance("planner")
            advance("clarification")
            advance("composer")
        elif route.complexity == "simple":
            advance("composer")
        else:
            advance("planner")
            advance("execution")
            advance("validator")
            advance("composer")
        if graph.terminal_reason is None:
            advance("eval")
        advance("terminal")
    usage = Usage(steps=len(graph.nodes))
    policy = budget_policy(usage, budget)
    mode = policy.mode
    if graph.terminal_reason is not None:
        answer = "当前任务超过了可执行步骤上限，已停止继续调用模型。"
        decision = EvalDecision("degrade", "MAX_TOTAL_STEPS")
        if checkpoint is not None:
            checkpoint.save(checkpoint_key, {
                "route": asdict(route), "plan": asdict(plan), "usage": usage.__dict__,
                "budget": budget.__dict__, "eval": decision.__dict__, "workflow": graph.as_dict(),
                "context": {"estimated_tokens": context.estimated_tokens, "sources": context.sources},
            })
        return AgentExecution(route, plan, context, answer, decision, usage, mode, [], [], policy.as_dict(), graph.as_dict())
    try:
        StepValidator().validate(route, plan, context)
    except ValueError as error:
        decision = EvalDecision("degrade", str(error).split(":", 1)[-1].strip())
        answer = "当前请求无法通过步骤校验，已停止继续执行。"
        if checkpoint is not None:
            checkpoint.save(checkpoint_key, {
                "route": asdict(route), "plan": asdict(plan), "usage": {"steps": len(graph.nodes)},
                "budget": budget.__dict__, "eval": decision.__dict__, "workflow": graph.as_dict(),
                "context": {"estimated_tokens": context.estimated_tokens, "sources": context.sources},
            })
        return AgentExecution(route, plan, context, answer, decision, Usage(steps=len(graph.nodes)), mode, [], [], policy.as_dict(), graph.as_dict())
    if route.missing_slots:
        # 缺少业务参数时只生成澄清候选，不调用模型，也不发布回答正文。
        answer = DeterministicComposer().compose(content, route, context, mode)
        return AgentExecution(
            route,
            plan,
            context,
            answer,
            EvalDecision("pass", "CLARIFICATION_REQUIRED"),
            usage,
            mode,
            [],
            [],
            policy.as_dict(),
            graph.as_dict(),
            [],
        )
    try:
        proposals = generate_tool_proposals(command, route)
    except SqlPlannerError as error:
        if error.code == "SQL_PLANNER_TIME_RANGE_REQUIRED":
            answer = "为了分析摄入情况，请补充时间范围，例如最近 7 天。"
            decision = EvalDecision("pass", error.code)
            return AgentExecution(
                route,
                plan,
                context,
                answer,
                decision,
                usage,
                mode,
                [],
                [],
                policy.as_dict(),
                graph.as_dict(),
                [],
            )
        raise
    candidate = DeterministicComposer().compose(content, route, context, mode)
    router = model_router or ModelRouter()
    tier = router.tier_for("composer", route.complexity, route.risk_level, mode)
    deadline_at = command.get("deadline_at")
    composer_timeout = _model_timeout_seconds("COMPOSER", 45.0)
    try:
        response, attempts = router.invoke(
            ModelRequest(
                scene="composer",
                prompt=candidate,
                deadline_at=deadline_at,
                timeout_seconds=composer_timeout,
            ),
            tier,
            router.fallback_tiers_for(tier),
        )
    except ModelProviderError:
        # 不能静默伪造云模型回答；上层会把该失败写为可观测终态。
        raise
    usage.tokens = sum(attempt.total_tokens or 0 for attempt in attempts)
    usage.cost_cny = float(sum((attempt.cost_cny or 0) for attempt in attempts))
    usage.model_calls = 1
    policy = budget_policy(usage, budget)
    mode = policy.mode
    answer = response.content
    reflection = Reflector().reflect(answer, route, context)
    if not reflection.accepted:
        answer = "当前候选答案未通过确定性反思校验，已停止继续交付。"
    decision = DeterministicEvalGate().evaluate(answer, route, usage, budget)
    if decision.reason != "BUDGET_EXHAUSTED" and decision.reason != "ANSWER_EMPTY" and should_run_llm_eval(command, route):
        # 预算已耗尽时不能为 Eval 再发起一次供应商调用。
        if not policy.allow_new_model_call:
            decision = EvalDecision("degrade", "BUDGET_EXHAUSTED_BEFORE_EVAL")
        else:
            eval_tier = "eval"
            eval_prompt = json.dumps({
                "task": "evaluate_candidate_answer",
                "question": content,
                "candidate_answer": answer,
                "risk_level": route.risk_level,
                "rubric": [
                    "answer addresses the user request",
                    "do not invent facts, tool results, or authorization",
                    "high-risk requests must not be presented as professional diagnosis",
                    "answer is complete enough for the requested task",
                ],
                "required_output": {"passed": "boolean", "score": "0..1", "reason": "short code"},
            }, ensure_ascii=False, sort_keys=True)
            try:
                judge_response, judge_attempts = router.invoke(
                ModelRequest(
                    scene="eval",
                    prompt=eval_prompt,
                    max_output_tokens=128,
                    temperature=0.0,
                    response_format={"type": "json_object"},
                    extra_body={"enable_thinking": False},
                    deadline_at=deadline_at,
                    timeout_seconds=_model_timeout_seconds("EVAL", 20.0),
                ), eval_tier,
                    router.fallback_tiers_for(eval_tier),
                )
                attempts.extend(judge_attempts)
                usage.tokens += sum(attempt.total_tokens or 0 for attempt in judge_attempts)
                usage.cost_cny += float(sum((attempt.cost_cny or 0) for attempt in judge_attempts))
                usage.model_calls += 1
                policy = budget_policy(usage, budget)
                mode = policy.mode
                decision = LlmEvalGate().evaluate(judge_response.content)
                if usage.ratio(budget) >= 1 and decision.result == "pass":
                    decision = EvalDecision("degrade", "BUDGET_EXHAUSTED_AFTER_EVAL")
                elif route.risk_level == "high":
                    # 高风险任务即使 Judge 通过，也没有人工审核能力可交付高风险候选答案。
                    decision = EvalDecision("degrade", "REQUEST_REVIEW_NO_HUMAN_REVIEWER")
            except ModelProviderError as error:
                attempts.extend(error.attempts)
                decision = EvalDecision("degrade", "EVAL_PROVIDER_UNAVAILABLE")
    if decision.result != "pass":
        answer = "当前请求无法直接交付完整答案，建议咨询医生或注册营养师后再继续。"
    if checkpoint is not None:
        checkpoint.save(checkpoint_key, {
            "route": asdict(route), "plan": asdict(plan), "usage": usage.__dict__,
            "budget": budget.__dict__, "eval": decision.__dict__, "workflow": graph.as_dict(),
            "context": {"estimated_tokens": context.estimated_tokens, "sources": context.sources},
        })
    return AgentExecution(route, plan, context, answer, decision, usage, mode, generate_memory_candidates(context, content), attempts, policy.as_dict(), graph.as_dict(), proposals)


def _model_timeout_seconds(scene: str, default: float) -> float:
    """Keep one slow provider call from consuming the whole Run deadline."""
    raw = os.getenv(f"FOODMATE_AGENT_{scene}_TIMEOUT_SECONDS", str(default))
    try:
        return max(0.1, float(raw))
    except ValueError:
        return default

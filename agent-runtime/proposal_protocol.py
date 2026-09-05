"""M1-4 最小 Proposal 协议：Python 只描述意图，绝不执行工具或 SQL。"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
import hashlib
import json
from typing import Any

from sql_planner import validate_candidate_sql


MAX_ID_LENGTH = 128
MAX_STATEMENT_LENGTH = 8_192


def _request_hash(body: dict[str, Any]) -> str:
    return "sha256:" + hashlib.sha256(
        json.dumps(body, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    ).hexdigest()


@dataclass(frozen=True)
class Proposal:
    proposal_id: str
    run_id: str
    proposal_type: str
    schema_version: str
    payload: dict[str, Any]
    requires_confirmation: bool = True
    request_hash: str = ""
    tool_name: str | None = None
    confirmation_ref: str | None = None
    input: dict[str, Any] | None = None

    def as_dict(self) -> dict[str, Any]:
        body = {
            "proposal_id": self.proposal_id,
            "run_id": self.run_id,
            "proposal_type": self.proposal_type,
            "schema_version": self.schema_version,
            "payload": self.payload,
            "requires_confirmation": self.requires_confirmation,
        }
        if self.tool_name is not None:
            body["tool_name"] = self.tool_name
        if self.confirmation_ref is not None:
            body["confirmation_ref"] = self.confirmation_ref
        if self.input is not None:
            body["input"] = self.input
        body["request_hash"] = self.request_hash or _request_hash(body)
        return body


def validate_proposal(proposal: Proposal) -> None:
    if proposal.schema_version != "v1":
        raise ValueError("PROPOSAL_SCHEMA_VERSION_INVALID")
    if proposal.proposal_type not in {"tool", "sql_read"}:
        raise ValueError("PROPOSAL_TYPE_NOT_ALLOWED")
    if not proposal.proposal_id or not proposal.run_id or len(proposal.proposal_id) > MAX_ID_LENGTH or len(proposal.run_id) > MAX_ID_LENGTH:
        raise ValueError("PROPOSAL_ID_REQUIRED")
    invocation_id = str(proposal.payload.get("invocation_id", ""))
    if not invocation_id or len(invocation_id) > MAX_ID_LENGTH:
        raise ValueError("PROPOSAL_INVOCATION_ID_REQUIRED")
    if proposal.proposal_type == "sql_read":
        statement = str(proposal.payload.get("statement", "")).strip().lower()
        if len(statement) > MAX_STATEMENT_LENGTH:
            raise ValueError("SQL_PROPOSAL_TOO_LARGE")
        if not statement.startswith("select") or any(token in statement for token in ("insert ", "update ", "delete ", "drop ", "alter ", ";")):
            raise ValueError("SQL_PROPOSAL_NOT_READ_ONLY")
    if proposal.proposal_type == "tool":
        if proposal.tool_name == "database_query":
            if proposal.requires_confirmation or not isinstance(proposal.input, dict):
                raise ValueError("SQL_QUERY_INPUT_INVALID")
            candidate = proposal.input.get("candidate_sql")
            if not isinstance(candidate, str) or candidate != proposal.payload.get("statement"):
                raise ValueError("SQL_QUERY_CANDIDATE_MISMATCH")
            validate_candidate_sql(candidate)
            for field in ("intent", "planner_mode", "planner_version"):
                if not isinstance(proposal.input.get(field), str) or not proposal.input[field]:
                    raise ValueError("SQL_QUERY_INPUT_INVALID")
            if proposal.input["planner_mode"] not in {"stub", "local"}:
                raise ValueError("SQL_QUERY_INPUT_INVALID")
        elif proposal.tool_name == "time_parser":
            if proposal.requires_confirmation or not isinstance(proposal.input, dict):
                raise ValueError("TIME_PARSER_INPUT_INVALID")
            question = proposal.input.get("question")
            timezone = proposal.input.get("timezone", "Asia/Shanghai")
            if not isinstance(question, str) or not question.strip() or len(question) > 2_000:
                raise ValueError("TIME_PARSER_INPUT_INVALID")
            if not isinstance(timezone, str) or not timezone.strip() or len(timezone) > 64:
                raise ValueError("TIME_PARSER_INPUT_INVALID")
        elif proposal.tool_name == "calculator":
            if proposal.requires_confirmation or not isinstance(proposal.input, dict):
                raise ValueError("CALCULATOR_INPUT_INVALID")
            expression = proposal.input.get("expression")
            if not isinstance(expression, str) or not expression.strip() or len(expression) > 256:
                raise ValueError("CALCULATOR_INPUT_INVALID")
        elif proposal.tool_name == "plan_validator":
            if proposal.requires_confirmation or not isinstance(proposal.input, dict):
                raise ValueError("PLAN_VALIDATOR_INPUT_INVALID")
            if not isinstance(proposal.input.get("plan"), dict):
                raise ValueError("PLAN_VALIDATOR_INPUT_INVALID")
        elif proposal.tool_name == "meal_plan.save_plan":
            if not proposal.requires_confirmation or not isinstance(proposal.input, dict):
                raise ValueError("MEAL_PLAN_INPUT_INVALID")
            _validate_meal_plan_input(proposal.input.get("plan"))
            if not proposal.payload.get("idempotency_key"):
                raise ValueError("TOOL_IDEMPOTENCY_KEY_REQUIRED")
        elif proposal.tool_name != "food_log_writer":
            raise ValueError("TOOL_NAME_NOT_ALLOWED")
        if proposal.tool_name in {"database_query", "time_parser", "calculator", "plan_validator"}:
            return
        if proposal.tool_name == "meal_plan.save_plan":
            return
        if proposal.tool_name == "food_log_writer" and not proposal.confirmation_ref:
            _validate_food_log_input(proposal.input)
            if not proposal.requires_confirmation:
                raise ValueError("TOOL_CONFIRMATION_REQUIRED")
            if not proposal.payload.get("idempotency_key"):
                raise ValueError("TOOL_IDEMPOTENCY_KEY_REQUIRED")
            return
        if not proposal.confirmation_ref:
            raise ValueError("TOOL_CONFIRMATION_REF_REQUIRED")
        if not isinstance(proposal.input, dict):
            raise ValueError("TOOL_INPUT_INVALID")
        has_items = isinstance(proposal.input.get("items"), list) and bool(proposal.input.get("items"))
        has_revision = isinstance(proposal.input.get("revision"), int) and proposal.input.get("revision") > 0
        if not has_items and not has_revision:
            raise ValueError("TOOL_INPUT_INVALID")
        if not proposal.payload.get("idempotency_key"):
            raise ValueError("TOOL_IDEMPOTENCY_KEY_REQUIRED")
    if proposal.request_hash:
        canonical = {
            "proposal_id": proposal.proposal_id,
            "run_id": proposal.run_id,
            "proposal_type": proposal.proposal_type,
            "schema_version": proposal.schema_version,
            "payload": proposal.payload,
            "requires_confirmation": proposal.requires_confirmation,
        }
        if proposal.tool_name is not None:
            canonical["tool_name"] = proposal.tool_name
        if proposal.confirmation_ref is not None:
            canonical["confirmation_ref"] = proposal.confirmation_ref
        if proposal.input is not None:
            canonical["input"] = proposal.input
        if proposal.request_hash != _request_hash(canonical):
            raise ValueError("PROPOSAL_REQUEST_HASH_INVALID")


def _validate_food_log_input(value: dict[str, Any] | None) -> None:
    """对模型候选做窄 schema 校验；Java 仍是最终授权和写入边界。"""
    if not isinstance(value, dict):
        raise ValueError("TOOL_INPUT_INVALID")
    meal_time = value.get("meal_time")
    meal_type = value.get("meal_type")
    items = value.get("items")
    if not isinstance(meal_time, str) or len(meal_time) > 64:
        raise ValueError("TOOL_INPUT_INVALID")
    try:
        datetime.fromisoformat(meal_time.replace("Z", "+00:00"))
    except ValueError as error:
        raise ValueError("TOOL_INPUT_INVALID") from error
    if meal_type not in {"breakfast", "lunch", "dinner", "snack"}:
        raise ValueError("TOOL_INPUT_INVALID")
    if not isinstance(items, list) or not items or len(items) > 50:
        raise ValueError("TOOL_INPUT_INVALID")
    notes = value.get("notes")
    if notes is not None and (not isinstance(notes, str) or len(notes) > 2_000):
        raise ValueError("TOOL_INPUT_INVALID")
    for item in items:
        if not isinstance(item, dict):
            raise ValueError("TOOL_INPUT_INVALID")
        name = item.get("name", item.get("raw_name"))
        amount = item.get("amount")
        unit = item.get("unit")
        if not isinstance(name, str) or not name.strip() or len(name) > 256:
            raise ValueError("TOOL_INPUT_INVALID")
        if not isinstance(amount, (int, float)) or isinstance(amount, bool) or amount <= 0:
            raise ValueError("TOOL_INPUT_INVALID")
        if not isinstance(unit, str) or not unit.strip() or len(unit) > 32:
            raise ValueError("TOOL_INPUT_INVALID")


def _validate_meal_plan_input(value: dict[str, Any] | None) -> None:
    """校验模型餐食计划的窄结构；约束语义仍由 Java PlanValidator 决定。"""
    if not isinstance(value, dict) or len(json.dumps(value, ensure_ascii=False)) > 200_000:
        raise ValueError("MEAL_PLAN_INPUT_INVALID")
    people = value.get("people")
    days = value.get("days")
    days_plan = value.get("days_plan")
    if (
        not isinstance(people, int)
        or isinstance(people, bool)
        or not 1 <= people <= 20
        or not isinstance(days, int)
        or isinstance(days, bool)
        or not 1 <= days <= 7
        or not isinstance(days_plan, list)
        or len(days_plan) != days
    ):
        raise ValueError("MEAL_PLAN_INPUT_INVALID")
    plan_name = value.get("plan_name")
    if plan_name is not None and (not isinstance(plan_name, str) or len(plan_name) > 128):
        raise ValueError("MEAL_PLAN_INPUT_INVALID")
    for field in ("allergens", "dislikes"):
        entries = value.get(field, [])
        if not isinstance(entries, list) or len(entries) > 64 or any(
            not isinstance(item, str) or not item.strip() or len(item) > 128 for item in entries
        ):
            raise ValueError("MEAL_PLAN_INPUT_INVALID")
    for field in ("budget", "calorie_target", "protein_target"):
        number = value.get(field)
        if number is not None and (
            isinstance(number, bool)
            or not isinstance(number, (int, float))
            or number < 0
        ):
            raise ValueError("MEAL_PLAN_INPUT_INVALID")
    for day in days_plan:
        if not isinstance(day, dict) or any(
            not isinstance(day.get(meal), dict) for meal in ("breakfast", "lunch", "dinner")
        ):
            raise ValueError("MEAL_PLAN_INPUT_INVALID")

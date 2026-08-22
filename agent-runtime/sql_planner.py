"""Structured SQL planning for the read-only database_query tool.

The planner produces a bounded candidate query. Java remains responsible for
schema authorization, user/tenant predicates, AST validation, and execution.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
import json
import os
from typing import Any

from model_provider import ModelProviderError, ModelRequest, OpenAICompatibleModelProvider


MAX_SQL_LENGTH = 8_192
MAX_DAYS = 90
MAX_LIMIT = 500
_TIME_PATTERN = re.compile(r"(?:最近|过去|近)\s*(\d{1,3})\s*天")


class SqlPlannerError(ValueError):
    """Stable planner failure that must be surfaced without a fallback query."""

    def __init__(self, code: str, message: str, missing_slots: tuple[str, ...] = ()):
        super().__init__(f"{code}: {message}")
        self.code = code
        self.missing_slots = missing_slots


@dataclass(frozen=True)
class SqlPlan:
    status: str
    intent: str
    time_range: dict[str, str] | None
    metrics: tuple[str, ...]
    dimensions: tuple[str, ...]
    filters: dict[str, str]
    candidate_sql: str | None
    planner_mode: str
    planner_version: str
    missing_slots: tuple[str, ...] = ()

    def as_dict(self) -> dict[str, Any]:
        return {
            "status": self.status,
            "intent": self.intent,
            "time_range": self.time_range,
            "metrics": list(self.metrics),
            "dimensions": list(self.dimensions),
            "filters": dict(self.filters),
            "candidate_sql": self.candidate_sql,
            "planner_mode": self.planner_mode,
            "planner_version": self.planner_version,
            "missing_slots": list(self.missing_slots),
        }

    @classmethod
    def from_model_output(cls, value: Any, mode: str, version: str) -> "SqlPlan":
        if not isinstance(value, dict):
            raise SqlPlannerError("SQL_PLANNER_RESPONSE_INVALID", "planner response must be an object")
        status = value.get("status")
        intent = value.get("intent")
        if status not in {"ready", "need_clarification"} or intent not in {
            "nutrition_summary",
            "meal_plan",
            "shopping_list",
            "nutrition_food",
        }:
            raise SqlPlannerError("SQL_PLANNER_RESPONSE_INVALID", "planner response status or intent is invalid")
        metrics = _bounded_strings(value.get("metrics"), "metrics", 6)
        dimensions = _bounded_strings(value.get("dimensions"), "dimensions", 4)
        filters = value.get("filters")
        if not isinstance(filters, dict) or any(
            not isinstance(key, str) or not isinstance(item, str) or len(key) > 64 or len(item) > 128
            for key, item in filters.items()
        ):
            raise SqlPlannerError("SQL_PLANNER_RESPONSE_INVALID", "planner filters are invalid")
        time_range = value.get("time_range")
        if time_range is not None:
            if not isinstance(time_range, dict) or set(time_range) - {"kind", "days", "timezone"}:
                raise SqlPlannerError("SQL_PLANNER_RESPONSE_INVALID", "planner time range is invalid")
            if time_range.get("kind") != "relative" or not str(time_range.get("days", "")).isdigit():
                raise SqlPlannerError("SQL_PLANNER_RESPONSE_INVALID", "planner time range is invalid")
            days = int(time_range["days"])
            if not 1 <= days <= MAX_DAYS:
                raise SqlPlannerError("SQL_PLANNER_RESPONSE_INVALID", "planner time range is invalid")
            time_range = {
                "kind": "relative",
                "days": str(days),
                "timezone": str(time_range.get("timezone") or "Asia/Shanghai"),
            }
        missing_slots = _bounded_strings(value.get("missing_slots", []), "missing_slots", 4)
        candidate_sql = value.get("candidate_sql")
        if status == "need_clarification":
            if candidate_sql is not None or not missing_slots:
                raise SqlPlannerError("SQL_PLANNER_RESPONSE_INVALID", "clarification response is incomplete")
        else:
            if not isinstance(candidate_sql, str):
                raise SqlPlannerError("SQL_PLANNER_RESPONSE_INVALID", "ready response has no candidate SQL")
            validate_candidate_sql(candidate_sql)
        return cls(
            status,
            intent,
            time_range,
            metrics,
            dimensions,
            dict(filters),
            candidate_sql,
            mode,
            version,
            missing_slots,
        )


def _bounded_strings(value: Any, name: str, maximum: int) -> tuple[str, ...]:
    if not isinstance(value, list) or len(value) > maximum or any(
        not isinstance(item, str) or not item or len(item) > 64 for item in value
    ):
        raise SqlPlannerError("SQL_PLANNER_RESPONSE_INVALID", f"planner {name} are invalid")
    return tuple(value)


class DeterministicSqlPlanner:
    """Maps a small nutrition-query vocabulary to reviewed SQL templates."""

    mode = "stub"
    version = "m2-2-deterministic-v1"

    def plan(self, question: str, intent_hint: str | None = None) -> SqlPlan:
        text = str(question or "").strip()
        if not text or len(text) > 2_000:
            raise SqlPlannerError("SQL_PLANNER_INPUT_INVALID", "query text is empty or too large")
        intent = self._intent(text, intent_hint)
        time_range = self._time_range(text)
        metrics = self._metrics(text, intent)
        dimensions = self._dimensions(text, intent)
        filters = self._filters(text, intent)

        if intent == "nutrition_summary" and time_range is None:
            return SqlPlan(
                "need_clarification",
                intent,
                None,
                metrics,
                dimensions,
                filters,
                None,
                self.mode,
                self.version,
                ("time_range",),
            )
        sql = self._template(intent, time_range, metrics, dimensions, filters)
        validate_candidate_sql(sql)
        return SqlPlan(
            "ready",
            intent,
            time_range,
            metrics,
            dimensions,
            filters,
            sql,
            self.mode,
            self.version,
        )

    @staticmethod
    def _intent(text: str, hint: str | None) -> str:
        allowed = {"nutrition_summary", "meal_plan", "shopping_list", "nutrition_food"}
        if hint in allowed:
            return hint
        if any(word in text for word in ("购物清单", "采购清单")):
            return "shopping_list"
        if any(word in text for word in ("餐食计划", "饮食计划", "菜单", "食谱计划")):
            return "meal_plan"
        if any(word in text for word in ("营养目录", "食材营养", "食物营养")):
            return "nutrition_food"
        return "nutrition_summary"

    @staticmethod
    def _time_range(text: str) -> dict[str, str] | None:
        match = _TIME_PATTERN.search(text)
        if match:
            days = int(match.group(1))
        elif "最近一周" in text or "过去一周" in text or "近一周" in text:
            days = 7
        elif "昨天" in text:
            days = 1
        elif "今天" in text:
            days = 1
        else:
            return None
        if not 1 <= days <= MAX_DAYS:
            raise SqlPlannerError("SQL_PLANNER_TIME_RANGE_INVALID", "time range is outside the approved bound")
        return {"kind": "relative", "days": str(days), "timezone": "Asia/Shanghai"}

    @staticmethod
    def _metrics(text: str, intent: str) -> tuple[str, ...]:
        if intent != "nutrition_summary":
            return ()
        mapping = (
            ("蛋白质", "protein_g"),
            ("蛋白", "protein_g"),
            ("热量", "calories_kcal"),
            ("卡路里", "calories_kcal"),
            ("脂肪", "fat_g"),
            ("碳水", "carbs_g"),
        )
        values = []
        for keyword, metric in mapping:
            if keyword in text and metric not in values:
                values.append(metric)
        return tuple(values or ("calories_kcal", "protein_g"))

    @staticmethod
    def _dimensions(text: str, intent: str) -> tuple[str, ...]:
        if intent != "nutrition_summary":
            return ()
        return ("meal_type",) if any(word in text for word in ("按餐次", "早餐", "午餐", "晚餐")) else ("meal_time",)

    @staticmethod
    def _filters(text: str, intent: str) -> dict[str, str]:
        filters: dict[str, str] = {}
        if intent == "nutrition_food":
            filters["review_status"] = "approved"
        if "早餐" in text:
            filters["meal_type"] = "breakfast"
        elif "午餐" in text:
            filters["meal_type"] = "lunch"
        elif "晚餐" in text:
            filters["meal_type"] = "dinner"
        return filters

    @staticmethod
    def _template(
        intent: str,
        time_range: dict[str, str] | None,
        metrics: tuple[str, ...],
        dimensions: tuple[str, ...],
        filters: dict[str, str],
    ) -> str:
        if intent == "meal_plan":
            return "SELECT plan_name, days, budget, status, updated_at FROM meal_plans ORDER BY updated_at DESC LIMIT 500"
        if intent == "shopping_list":
            return "SELECT shopping_list_id, meal_plan_id, status, updated_at FROM shopping_lists ORDER BY updated_at DESC LIMIT 500"
        if intent == "nutrition_food":
            return (
                "SELECT standard_name, basis_unit, calories_kcal_per_100, protein_g_per_100 "
                "FROM nutrition_foods WHERE review_status = 'approved' "
                "ORDER BY standard_name LIMIT 500"
            )

        if time_range is None:
            raise SqlPlannerError("SQL_PLANNER_TIME_RANGE_REQUIRED", "nutrition summary requires a time range")
        selected = [f"SUM(i.{metric}) AS {metric}" for metric in metrics]
        selected = [f"f.{dimension}" for dimension in dimensions] + selected
        group_by = ", ".join(f"f.{dimension}" for dimension in dimensions)
        where = [
            "f.meal_time >= CURRENT_TIMESTAMP - INTERVAL '" + time_range["days"] + " days'"
        ]
        if "meal_type" in filters:
            where.append("f.meal_type = '" + filters["meal_type"] + "'")
        return (
            "SELECT "
            + ", ".join(selected)
            + " FROM food_logs f JOIN food_log_items i ON i.food_log_id = f.food_log_id AND i.is_deleted = FALSE WHERE "
            + " AND ".join(where)
            + " GROUP BY "
            + group_by
            + " ORDER BY f.meal_time DESC LIMIT 500"
        )


class OpenAICompatibleSqlPlanner:
    """Structured local model planner with no implicit deterministic fallback."""

    mode = "local"
    version = "m2-2-model-v1"

    def __init__(self, provider: Any, model_name: str):
        if provider is None or not model_name:
            raise SqlPlannerError("SQL_PLANNER_CONFIG_MISSING", "local SQL planner is not configured")
        self.provider = provider
        self.model_name = model_name

    @classmethod
    def from_environment(cls, environment: dict[str, str] | None = None) -> "OpenAICompatibleSqlPlanner":
        env = environment if environment is not None else os.environ
        base_url = env.get("FOODMATE_SQL_PLANNER_BASE_URL", "").strip()
        api_key = env.get("FOODMATE_SQL_PLANNER_API_KEY", "").strip()
        model_name = env.get("FOODMATE_SQL_PLANNER_MODEL", "").strip()
        if not base_url or not api_key or not model_name:
            raise SqlPlannerError("SQL_PLANNER_CONFIG_MISSING", "local SQL planner requires endpoint, key, and model")
        try:
            timeout = max(0.1, float(env.get("FOODMATE_SQL_PLANNER_TIMEOUT_SECONDS", "20")))
        except (TypeError, ValueError):
            raise SqlPlannerError("SQL_PLANNER_CONFIG_INVALID", "local SQL planner timeout is invalid")
        return cls(OpenAICompatibleModelProvider("sql-planner", base_url, api_key, timeout), model_name)

    def plan(self, question: str, intent_hint: str | None = None) -> SqlPlan:
        text = str(question or "").strip()
        if not text or len(text) > 2_000:
            raise SqlPlannerError("SQL_PLANNER_INPUT_INVALID", "query text is empty or too large")
        prompt = json.dumps(
            {
                "task": "produce_database_query_plan",
                "question": text,
                "intent_hint": intent_hint,
                "allowed_intents": ["nutrition_summary", "meal_plan", "shopping_list", "nutrition_food"],
                "required_output": {
                    "status": "ready or need_clarification",
                    "intent": "approved intent",
                    "time_range": {"kind": "relative", "days": "integer string", "timezone": "IANA timezone"},
                    "metrics": ["approved metric names"],
                    "dimensions": ["approved dimension names"],
                    "filters": {"approved_field": "approved literal"},
                    "candidate_sql": "single SELECT/WITH SELECT ending in LIMIT <= 500",
                    "missing_slots": ["required clarification slots"],
                },
            },
            ensure_ascii=False,
            sort_keys=True,
        )
        try:
            response = self.provider.complete(
                self.model_name,
                ModelRequest(
                    scene="sql_planner",
                    prompt=prompt,
                    max_output_tokens=768,
                    temperature=0.0,
                    response_format={"type": "json_object"},
                ),
            )
            value = json.loads(response.content)
            return SqlPlan.from_model_output(value, self.mode, self.version)
        except SqlPlannerError:
            raise
        except ModelProviderError as error:
            raise SqlPlannerError("SQL_PLANNER_MODEL_UNAVAILABLE", error.code) from error
        except (TypeError, ValueError, json.JSONDecodeError) as error:
            raise SqlPlannerError("SQL_PLANNER_RESPONSE_INVALID", "local SQL planner response is invalid") from error


def planner_from_environment(environment: dict[str, str] | None = None):
    """Build exactly one configured planner mode; local never falls back to stub."""
    env = environment if environment is not None else os.environ
    mode = env.get("FOODMATE_SQL_PLANNER_MODE", "stub").strip().lower()
    if mode == "stub":
        return DeterministicSqlPlanner()
    if mode == "local":
        return OpenAICompatibleSqlPlanner.from_environment(env)
    raise SqlPlannerError("SQL_PLANNER_MODE_INVALID", "SQL planner mode must be stub or local")


def validate_candidate_sql(statement: str) -> None:
    """Apply planner-side cheap checks before the Java AST trust boundary."""
    value = str(statement or "").strip()
    lowered = value.lower()
    if not value or len(value) > MAX_SQL_LENGTH:
        raise SqlPlannerError("SQL_PLANNER_SQL_INVALID", "candidate SQL is empty or too large")
    if not (lowered.startswith("select ") or lowered.startswith("with ")):
        raise SqlPlannerError("SQL_PLANNER_SQL_INVALID", "candidate SQL must be a read query")
    if ";" in value or "--" in value or "/*" in value or "*/" in value:
        raise SqlPlannerError("SQL_PLANNER_SQL_INVALID", "candidate SQL contains rejected syntax")
    if not re.search(r"\blimit\s+([1-9][0-9]{0,2})\s*$", lowered):
        raise SqlPlannerError("SQL_PLANNER_LIMIT_REQUIRED", "candidate SQL must end with a bounded limit")
    limit = int(re.search(r"\blimit\s+([1-9][0-9]{0,2})\s*$", lowered).group(1))
    if limit > MAX_LIMIT:
        raise SqlPlannerError("SQL_PLANNER_LIMIT_INVALID", "candidate SQL limit is too large")

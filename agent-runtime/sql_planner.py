"""Structured SQL planning for the read-only database_query tool.

The planner produces a bounded candidate query. Java remains responsible for
schema authorization, user/tenant predicates, AST validation, and execution.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Any


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

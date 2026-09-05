"""真实付费业务联调的显式门禁、预算和脱敏证据。"""

from __future__ import annotations

import hashlib
import json
import os
import re
from dataclasses import dataclass, field
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any


class PaidExecutionError(RuntimeError):
    """付费联调被安全门禁拒绝时使用的稳定错误。"""

    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code


@dataclass(frozen=True)
class PaidExecutionSettings:
    """一轮真实业务联调的不可变预算配置。"""

    enabled: bool = False
    max_scenarios: int = 4
    max_total_cost_cny: Decimal = Decimal("5")
    no_retry: bool = True
    require_cloud: bool = True
    evidence_file: str = ""

    @classmethod
    def from_environment(
        cls, environment: dict[str, str] | None = None
    ) -> "PaidExecutionSettings":
        values = environment if environment is not None else os.environ
        enabled = _boolean(values.get("FOODMATE_PAID_EXECUTION_ENABLED", "false"))
        max_scenarios = _positive_int(
            values.get("FOODMATE_PAID_MAX_SCENARIOS", "4"),
            "PAID_SCENARIO_LIMIT_INVALID",
        )
        max_cost = _non_negative_decimal(
            values.get("FOODMATE_PAID_MAX_TOTAL_COST_CNY", "5"),
            "PAID_BUDGET_INVALID",
        )
        return cls(
            enabled=enabled,
            max_scenarios=max_scenarios,
            max_total_cost_cny=max_cost,
            no_retry=_boolean(values.get("FOODMATE_PAID_NO_RETRY", "true")),
            require_cloud=_boolean(
                values.get("FOODMATE_PAID_REQUIRE_CLOUD", "true")
            ),
            evidence_file=values.get("FOODMATE_PAID_EVIDENCE_FILE", "").strip(),
        )


@dataclass
class PaidExecutionSession:
    """跨四条真实业务链路累计预算并生成安全证据。"""

    settings: PaidExecutionSettings
    scenarios: list[str] = field(default_factory=list)
    total_cost_cny: Decimal = Decimal("0")
    records: list[dict[str, Any]] = field(default_factory=list)

    @classmethod
    def from_environment(
        cls, environment: dict[str, str] | None = None
    ) -> "PaidExecutionSession":
        return cls(PaidExecutionSettings.from_environment(environment))

    def require_enabled(self) -> None:
        if not self.settings.enabled:
            raise PaidExecutionError(
                "PAID_EXECUTION_DISABLED",
                "real paid execution requires an explicit execution switch",
            )

    def begin_scenario(self, name: str) -> None:
        """登记一条业务场景；重复登记视为调用方错误。"""
        self.require_enabled()
        normalized = name.strip().lower()
        if not re.fullmatch(r"[a-z][a-z0-9_-]{1,63}", normalized):
            raise PaidExecutionError(
                "PAID_SCENARIO_INVALID", "scenario name is invalid"
            )
        if normalized in self.scenarios:
            raise PaidExecutionError(
                "PAID_SCENARIO_DUPLICATE", "scenario has already been started"
            )
        if len(self.scenarios) >= self.settings.max_scenarios:
            raise PaidExecutionError(
                "PAID_SCENARIO_LIMIT_EXCEEDED", "paid scenario limit exceeded"
            )
        self.scenarios.append(normalized)
        self.records.append(
            {
                "event": "scenario.started",
                "scenario": normalized,
                "scenario_count": len(self.scenarios),
            }
        )

    def before_model_call(
        self, scene: str, tier: str, prompt: str, max_output_tokens: int
    ) -> None:
        """记录模型调用前的摘要，不保存 Prompt 内容。"""
        self.require_enabled()
        self.records.append(
            {
                "event": "model.started",
                "scene": _safe_label(scene),
                "tier": _safe_label(tier),
                "prompt_digest": _digest(prompt),
                "max_output_tokens": max_output_tokens,
            }
        )

    def record_model_attempts(self, attempts: list[Any]) -> None:
        """累计供应商返回的真实成本并记录脱敏调用结果。"""
        self.require_enabled()
        observed_cost = Decimal("0")
        for attempt in attempts:
            cost = _cost(attempt.cost_cny)
            if cost is not None:
                observed_cost += cost
            self.records.append(
                {
                    "event": "model.finished",
                    "scene": _safe_label(getattr(attempt, "scene", "")),
                    "provider": _safe_label(getattr(attempt, "provider_code", "")),
                    "model": _safe_label(getattr(attempt, "model_name", "")),
                    "status": _safe_label(getattr(attempt, "status", "")),
                    "error_code": _safe_label(
                        getattr(attempt, "error_code", None) or ""
                    ),
                    "input_tokens": getattr(attempt, "input_tokens", None),
                    "output_tokens": getattr(attempt, "output_tokens", None),
                    "total_tokens": getattr(attempt, "total_tokens", None),
                    "cost_cny": _decimal_text(cost),
                    "latency_ms": getattr(attempt, "latency_ms", None),
                    "price_version": _safe_label(
                        getattr(attempt, "price_version", "")
                    ),
                    "provider_request_id_digest": _optional_digest(
                        getattr(attempt, "provider_request_id", None)
                    ),
                }
            )
        next_total = self.total_cost_cny + observed_cost
        if next_total > self.settings.max_total_cost_cny:
            raise PaidExecutionError(
                "PAID_BUDGET_EXCEEDED", "paid execution budget exceeded"
            )
        self.total_cost_cny = next_total

    def summary(self) -> dict[str, Any]:
        """返回可写入报告的安全摘要。"""
        return {
            "enabled": self.settings.enabled,
            "max_scenarios": self.settings.max_scenarios,
            "scenario_count": len(self.scenarios),
            "scenarios": list(self.scenarios),
            "max_total_cost_cny": format(self.settings.max_total_cost_cny, "f"),
            "observed_total_cost_cny": format(self.total_cost_cny, "f"),
            "no_retry": self.settings.no_retry,
            "require_cloud": self.settings.require_cloud,
            "records": list(self.records),
        }

    def write_evidence(self, path: str | os.PathLike[str] | None = None) -> None:
        """原子写入脱敏证据；未配置路径时不生成工作区文件。"""
        target_value = str(path or self.settings.evidence_file).strip()
        if not target_value:
            return
        target = Path(target_value)
        target.parent.mkdir(parents=True, exist_ok=True)
        temporary = target.with_name(target.name + ".tmp")
        temporary.write_text(
            json.dumps(self.summary(), ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        temporary.replace(target)


def _boolean(value: str) -> bool:
    normalized = str(value).strip().lower()
    if normalized not in {"true", "false"}:
        raise PaidExecutionError("PAID_BOOLEAN_INVALID", "boolean configuration is invalid")
    return normalized == "true"


def _positive_int(value: str, code: str) -> int:
    try:
        parsed = int(str(value).strip())
    except (TypeError, ValueError) as error:
        raise PaidExecutionError(code, "integer configuration is invalid") from error
    if parsed < 1:
        raise PaidExecutionError(code, "integer configuration must be positive")
    return parsed


def _non_negative_decimal(value: str, code: str) -> Decimal:
    try:
        parsed = Decimal(str(value).strip())
    except (InvalidOperation, TypeError, ValueError) as error:
        raise PaidExecutionError(code, "decimal configuration is invalid") from error
    if not parsed.is_finite() or parsed < 0:
        raise PaidExecutionError(code, "decimal configuration must be non-negative")
    return parsed


def _cost(value: object) -> Decimal | None:
    if value is None:
        return None
    try:
        parsed = Decimal(str(value))
    except (InvalidOperation, TypeError, ValueError):
        return None
    return parsed if parsed.is_finite() and parsed >= 0 else None


def _decimal_text(value: Decimal | None) -> str | None:
    return None if value is None else format(value, "f")


def _safe_label(value: object) -> str:
    """限制证据中的标签长度，避免异常供应商值污染报告。"""
    return str(value or "").strip()[:128]


def _digest(value: object) -> str:
    return "sha256:" + hashlib.sha256(str(value).encode("utf-8")).hexdigest()


def _optional_digest(value: object) -> str | None:
    return None if value is None else _digest(value)

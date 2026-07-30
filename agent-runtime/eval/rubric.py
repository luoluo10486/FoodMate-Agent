"""Offline Eval rubric for deterministic runtime Golden cases."""

from __future__ import annotations

from typing import Any


CHECKED_FIELDS = ("intent", "complexity", "risk_level", "eval_result", "eval_reason")


def command_for(case: dict[str, Any]) -> dict[str, Any]:
    command: dict[str, Any] = {
        "run_id": "golden-" + case["id"],
        "dispatch_id": "golden-dispatch-" + case["id"],
        "message": {"content": case["message"]},
    }
    if "budget_snapshot" in case:
        command["budget_snapshot"] = case["budget_snapshot"]
    return command


def check_case(case: dict[str, Any], execution: Any) -> list[str]:
    expected = case["expected"]
    actual = {
        "intent": execution.route.intent,
        "complexity": execution.route.complexity,
        "risk_level": execution.route.risk_level,
        "eval_result": execution.eval.result,
        "eval_reason": execution.eval.reason,
        "model_scenes": [attempt.scene for attempt in execution.model_attempts],
    }
    failures: list[str] = []
    for field in CHECKED_FIELDS:
        if field in expected and actual[field] != expected[field]:
            failures.append(f"{case['id']}: {field}={actual[field]!r}, expected {expected[field]!r}")
    if "answer_contains" in expected and expected["answer_contains"] not in execution.answer:
        failures.append(f"{case['id']}: answer lacks {expected['answer_contains']!r}")
    if "model_scenes" in expected and actual["model_scenes"] != expected["model_scenes"]:
        failures.append(
            f"{case['id']}: model_scenes={actual['model_scenes']!r}, expected {expected['model_scenes']!r}"
        )
    return failures

"""LLM-based outer intent classification with a strict, versioned contract."""

from __future__ import annotations

import json
import math
import os
from dataclasses import dataclass
from typing import Any

from model_provider import ModelProviderError, ModelRequest, ModelRouter, ProviderAttempt
from prompt_templates import prompt_contract_version, render_prompt


CLASSIFIER_VERSION = prompt_contract_version("intent_classifier")
INTENTS = frozenset(
    {"calculation", "analysis", "planning", "record", "knowledge_qna"}
)
REASON_CODES = frozenset(
    {
        "CALCULATION_REQUEST",
        "STRUCTURED_DATA_ANALYSIS",
        "PLAN_REQUEST",
        "FOOD_LOG_MUTATION",
        "GENERAL_KNOWLEDGE_QUESTION",
        "AMBIGUOUS_INTENT",
        "MULTIPLE_INTENTS",
        "LOW_CONFIDENCE",
        "INSUFFICIENT_CONTEXT",
    }
)
ENTITY_KEYS = frozenset(
    {"metric", "food_name", "time_range", "meal_type", "operation", "days"}
)
MISSING_SLOTS = frozenset(
    {"intent", "time_range", "food_name", "meal_type", "amount", "unit", "days", "details"}
)
MIN_CONFIDENCE = 0.65
MAX_INPUT_CHARS = 4_000
MAX_CONTEXT_MESSAGES = 8
MAX_CONTEXT_MESSAGE_CHARS = 1_000
MAX_RESPONSE_CHARS = 4_096
MAX_CLARIFICATION_CHARS = 300


class IntentClassificationError(ValueError):
    """A model response did not satisfy the intent contract."""

    def __init__(self, code: str, attempts: list[ProviderAttempt] | None = None):
        super().__init__(code)
        self.code = code
        self.attempts = attempts or []


@dataclass(frozen=True)
class IntentClassification:
    status: str
    intents: tuple[dict[str, Any], ...]
    missing_slots: tuple[str, ...]
    clarification_question: str | None
    reason_code: str
    classifier_version: str


def build_intent_prompt(message: str, context: list[dict[str, str]]) -> str:
    """Create the complete classification prompt; input text is explicitly untrusted."""
    return render_prompt(
        "intent_classifier",
        user_message=message,
        conversation_context=context,
    )


def classify_intent(
    message: str,
    context: list[dict[str, str]],
    router: ModelRouter,
    governed_route: dict[str, object] | None = None,
    deadline_at: str | None = None,
) -> tuple[IntentClassification, list[ProviderAttempt]]:
    """Invoke the shared model route and validate every field before workflow use."""
    text = str(message or "").strip()
    if not text or len(text) > MAX_INPUT_CHARS:
        raise IntentClassificationError("INTENT_INPUT_INVALID")
    prompt = build_intent_prompt(text, context)
    request = ModelRequest(
        scene="intent_classifier",
        prompt=prompt,
        max_output_tokens=512,
        temperature=0.0,
        response_format={"type": "json_object"},
        extra_body={"enable_thinking": False},
        deadline_at=deadline_at,
        timeout_seconds=_timeout_seconds(),
    )
    try:
        response, attempts = router.invoke(
            request,
            "standard",
            router.fallback_tiers_for("standard"),
            governed_route=governed_route,
        )
    except ModelProviderError:
        raise
    if not isinstance(response.content, str) or len(response.content) > MAX_RESPONSE_CHARS:
        raise IntentClassificationError("INTENT_RESPONSE_INVALID", attempts)
    try:
        result = _parse_response(response.content)
    except IntentClassificationError as error:
        error.attempts = attempts
        raise
    return result, attempts


def _parse_response(raw: str) -> IntentClassification:
    try:
        value = json.loads(raw)
    except json.JSONDecodeError as error:
        raise IntentClassificationError("INTENT_RESPONSE_INVALID") from error
    root_keys = {
        "status",
        "intents",
        "missing_slots",
        "clarification_question",
        "reason_code",
        "classifier_version",
    }
    if not isinstance(value, dict) or set(value) != root_keys:
        raise IntentClassificationError("INTENT_RESPONSE_INVALID")
    if not isinstance(value["status"], str) or value["status"] not in {"ready", "needs_clarification"}:
        raise IntentClassificationError("INTENT_RESPONSE_INVALID")
    if (
        not isinstance(value["reason_code"], str)
        or value["reason_code"] not in REASON_CODES
        or value["classifier_version"] != CLASSIFIER_VERSION
    ):
        raise IntentClassificationError("INTENT_RESPONSE_INVALID")
    raw_intents = value["intents"]
    if not isinstance(raw_intents, list) or len(raw_intents) > 3:
        raise IntentClassificationError("INTENT_RESPONSE_INVALID")
    intents: list[dict[str, Any]] = []
    item_keys = {"name", "confidence", "entities"}
    for item in raw_intents:
        if (
            not isinstance(item, dict)
            or set(item) != item_keys
            or not isinstance(item["name"], str)
            or item["name"] not in INTENTS
        ):
            raise IntentClassificationError("INTENT_RESPONSE_INVALID")
        confidence = item["confidence"]
        if isinstance(confidence, bool) or not isinstance(confidence, (int, float)):
            raise IntentClassificationError("INTENT_RESPONSE_INVALID")
        confidence = float(confidence)
        if not math.isfinite(confidence) or not 0 <= confidence <= 1:
            raise IntentClassificationError("INTENT_RESPONSE_INVALID")
        entities = item["entities"]
        if not isinstance(entities, dict) or set(entities) != ENTITY_KEYS:
            raise IntentClassificationError("INTENT_RESPONSE_INVALID")
        normalized_entities: dict[str, str | None] = {}
        for key, entity in entities.items():
            if entity is not None and (
                not isinstance(entity, str) or len(entity) > 100 or "\x00" in entity
            ):
                raise IntentClassificationError("INTENT_RESPONSE_INVALID")
            normalized_entities[key] = entity.strip() if isinstance(entity, str) else None
        intents.append(
            {"name": item["name"], "confidence": confidence, "entities": normalized_entities}
        )
    slots = value["missing_slots"]
    if (
        not isinstance(slots, list)
        or len(slots) > 8
        or any(not isinstance(slot, str) or slot not in MISSING_SLOTS for slot in slots)
    ):
        raise IntentClassificationError("INTENT_RESPONSE_INVALID")
    question = value["clarification_question"]
    if question is not None and (
        not isinstance(question, str) or len(question) > MAX_CLARIFICATION_CHARS
    ):
        raise IntentClassificationError("INTENT_RESPONSE_INVALID")
    if value["status"] == "ready":
        if (
            len(intents) != 1
            or intents[0]["confidence"] < MIN_CONFIDENCE
            or slots
            or question is not None
        ):
            raise IntentClassificationError("INTENT_RESPONSE_INVALID")
    elif not slots or not isinstance(question, str) or not question.strip():
        raise IntentClassificationError("INTENT_RESPONSE_INVALID")
    return IntentClassification(
        value["status"],
        tuple(intents),
        tuple(slots),
        question.strip() if isinstance(question, str) else None,
        value["reason_code"],
        CLASSIFIER_VERSION,
    )


def _timeout_seconds() -> float:
    raw = os.getenv("FOODMATE_AGENT_INTENT_CLASSIFIER_TIMEOUT_SECONDS", "15")
    try:
        parsed = float(raw)
    except (TypeError, ValueError):
        return 15.0
    return max(0.1, parsed) if math.isfinite(parsed) else 15.0

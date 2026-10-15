"""Load versioned prompt definitions and render their dynamic input fields."""

from __future__ import annotations

import json
import re
from importlib.resources import files
from typing import Any


PROMPT_TEMPLATE_NAMES = (
    "intent_classifier",
    "sql_planner",
    "food_log_writer",
    "meal_plan_generator",
    "composer",
    "answer_evaluator",
)
_PROMPT_NAME_PATTERN = re.compile(r"^[a-z][a-z0-9_]*$")


class PromptTemplateError(ValueError):
    """A prompt definition is missing, malformed, or received invalid inputs."""


def _unique_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    value: dict[str, Any] = {}
    for key, item in pairs:
        if key in value:
            raise PromptTemplateError("PROMPT_TEMPLATE_DUPLICATE_KEY")
        value[key] = item
    return value


def load_prompt_template(name: str) -> dict[str, Any]:
    if name not in PROMPT_TEMPLATE_NAMES or not _PROMPT_NAME_PATTERN.fullmatch(name):
        raise PromptTemplateError("PROMPT_TEMPLATE_UNKNOWN")
    resource = files("foodmate_agent_runtime").joinpath("prompts", f"{name}.json")
    try:
        definition = json.loads(
            resource.read_text(encoding="utf-8"), object_pairs_hook=_unique_object
        )
    except FileNotFoundError as error:
        raise PromptTemplateError("PROMPT_TEMPLATE_NOT_FOUND") from error
    except json.JSONDecodeError as error:
        raise PromptTemplateError("PROMPT_TEMPLATE_INVALID_JSON") from error

    if not isinstance(definition, dict) or set(definition) != {"metadata", "payload"}:
        raise PromptTemplateError("PROMPT_TEMPLATE_INVALID")
    metadata = definition["metadata"]
    payload = definition["payload"]
    if (
        not isinstance(metadata, dict)
        or not isinstance(payload, dict)
        or metadata.get("template_id") != name
        or not isinstance(metadata.get("template_version"), str)
        or not metadata["template_version"].strip()
    ):
        raise PromptTemplateError("PROMPT_TEMPLATE_INVALID")
    dynamic_fields = metadata.get("dynamic_fields")
    if (
        not isinstance(dynamic_fields, list)
        or any(not isinstance(field, str) or not field for field in dynamic_fields)
        or len(dynamic_fields) != len(set(dynamic_fields))
        or not set(dynamic_fields).issubset(payload)
    ):
        raise PromptTemplateError("PROMPT_TEMPLATE_INVALID")
    return definition


def prompt_contract_version(name: str) -> str:
    metadata = load_prompt_template(name)["metadata"]
    version = metadata.get("contract_version")
    if not isinstance(version, str) or not version.strip():
        raise PromptTemplateError("PROMPT_TEMPLATE_CONTRACT_VERSION_MISSING")
    return version


def render_prompt(name: str, **values: Any) -> str:
    definition = load_prompt_template(name)
    metadata = definition["metadata"]
    dynamic_fields = set(metadata["dynamic_fields"])
    if set(values) != dynamic_fields:
        raise PromptTemplateError("PROMPT_TEMPLATE_INPUT_INVALID")
    payload = definition["payload"]
    payload.update(values)
    return json.dumps(payload, ensure_ascii=False, separators=(",", ":"), sort_keys=True)

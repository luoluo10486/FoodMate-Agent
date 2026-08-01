"""Recovery contract for a new dispatch attempt resuming a persisted checkpoint."""

from __future__ import annotations

import hashlib
import json
from datetime import datetime, timezone
from typing import Any


def checkpoint_digest(value: dict[str, Any]) -> str:
    return "sha256:" + hashlib.sha256(
        json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    ).hexdigest()


def _deadline_micros(value: Any) -> int | None:
    """Normalize Jackson epoch timestamps and ISO timestamps to PostgreSQL precision."""
    try:
        if isinstance(value, (int, float)):
            instant = datetime.fromtimestamp(float(value), tz=timezone.utc)
        elif isinstance(value, str):
            text = value.strip()
            if not text:
                return None
            if text.replace(".", "", 1).isdigit():
                instant = datetime.fromtimestamp(float(text), tz=timezone.utc)
            else:
                instant = datetime.fromisoformat(text.replace("Z", "+00:00"))
                if instant.tzinfo is None:
                    instant = instant.replace(tzinfo=timezone.utc)
                instant = instant.astimezone(timezone.utc)
        else:
            return None
        return round(instant.timestamp() * 1_000_000)
    except (TypeError, ValueError, OverflowError):
        return None


def validate_recovery_command(command: dict[str, Any], checkpoint_store) -> dict[str, Any] | None:
    """Validate Java's recovery reconciliation before a new attempt can resume work."""
    recovery = command.get("recovery_context")
    # Java omits an empty context in new envelopes; accepting {} also keeps the
    # runtime compatible with commands produced by the intermediate contract.
    if recovery is None or recovery == {}:
        return None
    if not isinstance(recovery, dict):
        raise ValueError("RECOVERY_CONTEXT_INVALID")
    required = ("previous_dispatch_id", "previous_attempt", "checkpoint_version", "checkpoint_digest", "budget_revision")
    if any(recovery.get(key) in (None, "") for key in required):
        raise ValueError("RECOVERY_CONTEXT_INVALID")
    previous_dispatch_id = str(recovery["previous_dispatch_id"])
    if previous_dispatch_id == str(command.get("dispatch_id", "")):
        raise ValueError("RECOVERY_DISPATCH_REUSED")
    if int(recovery["previous_attempt"]) >= int(command.get("attempt", 0)):
        raise ValueError("RECOVERY_ATTEMPT_INVALID")
    # Boundary checkpoints are immutable snapshots. The base key may advance after a
    # Tool Result is applied, while Java must still validate the exact snapshot named
    # by the checkpoint event. Keep the base-key fallback for older local checkpoints.
    loaded = checkpoint_store.load(
        f"{command['run_id']}:{previous_dispatch_id}:recovery"
    ) or checkpoint_store.load(f"{command['run_id']}:{previous_dispatch_id}")
    if loaded is None:
        raise ValueError("RECOVERY_CHECKPOINT_NOT_FOUND")
    version, checkpoint = loaded
    if version != int(recovery["checkpoint_version"]) or checkpoint_digest(checkpoint) != str(recovery["checkpoint_digest"]):
        raise ValueError("RECOVERY_CHECKPOINT_CONFLICT")
    if checkpoint.get("run_id") != str(command["run_id"]):
        raise ValueError("RECOVERY_RUN_MISMATCH")
    if checkpoint.get("dispatch_id") != previous_dispatch_id or int(checkpoint.get("attempt", 0)) != int(recovery["previous_attempt"]):
        raise ValueError("RECOVERY_CHECKPOINT_CONFLICT")
    if checkpoint.get("current_node") not in {"tool_wait", "execution"}:
        raise ValueError("RECOVERY_NODE_NOT_RESUMABLE")
    checkpoint_deadline = _deadline_micros(checkpoint.get("deadline_at"))
    command_deadline = _deadline_micros(command.get("deadline_at"))
    # PostgreSQL stores timestamps at microsecond precision; Jackson can retain a
    # sub-microsecond fraction when the original value was serialized as epoch nanos.
    if (
        checkpoint_deadline is None
        or command_deadline is None
        or abs(checkpoint_deadline - command_deadline) > 2
    ):
        raise ValueError("RECOVERY_DEADLINE_MISMATCH")
    if int(checkpoint.get("budget_revision", 0)) != int(recovery["budget_revision"]):
        raise ValueError("RECOVERY_BUDGET_REVISION_MISMATCH")
    completed = tuple(str(item) for item in recovery.get("completed_invocation_ids") or ())
    checkpoint_completed = tuple(str(item) for item in checkpoint.get("completed_invocation_ids") or ())
    if not set(checkpoint_completed).issubset(completed):
        raise ValueError("RECOVERY_COMPLETED_INVOCATIONS_MISMATCH")
    results = recovery.get("completed_tool_results") or []
    if not isinstance(results, list):
        raise ValueError("RECOVERY_TOOL_RESULTS_INVALID")
    result_ids = set()
    for result in results:
        if not isinstance(result, dict) or not result.get("invocation_id"):
            raise ValueError("RECOVERY_TOOL_RESULTS_INVALID")
        result_ids.add(str(result["invocation_id"]))
    if not result_ids.issubset(set(completed)):
        raise ValueError("RECOVERY_TOOL_RESULTS_MISMATCH")
    return checkpoint

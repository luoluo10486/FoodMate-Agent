"""Recovery contract for a new dispatch attempt resuming a persisted checkpoint."""

from __future__ import annotations

import hashlib
import json
from typing import Any


def checkpoint_digest(value: dict[str, Any]) -> str:
    return "sha256:" + hashlib.sha256(
        json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    ).hexdigest()


def validate_recovery_command(command: dict[str, Any], checkpoint_store) -> dict[str, Any] | None:
    """Validate Java's recovery reconciliation before a new attempt can resume work."""
    recovery = command.get("recovery_context")
    if recovery is None:
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
    loaded = checkpoint_store.load(f"{command['run_id']}:{previous_dispatch_id}")
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
    if checkpoint.get("deadline_at") != command.get("deadline_at"):
        raise ValueError("RECOVERY_DEADLINE_MISMATCH")
    if int(checkpoint.get("budget_revision", 0)) != int(recovery["budget_revision"]):
        raise ValueError("RECOVERY_BUDGET_REVISION_MISMATCH")
    completed = tuple(str(item) for item in recovery.get("completed_invocation_ids") or ())
    checkpoint_completed = tuple(str(item) for item in checkpoint.get("completed_invocation_ids") or ())
    if not set(checkpoint_completed).issubset(completed):
        raise ValueError("RECOVERY_COMPLETED_INVOCATIONS_MISMATCH")
    return checkpoint

"""Schema and metric helpers for human-reviewed Judge calibration samples."""

from __future__ import annotations

from typing import Any


REVIEW_STATUSES = frozenset({"pending_review", "reviewed"})
LABELS = frozenset({"pass", "degrade"})


def validate_samples(samples: list[dict[str, Any]]) -> list[str]:
    errors: list[str] = []
    ids: set[str] = set()
    for sample in samples:
        sample_id = sample.get("id")
        if not sample_id or sample_id in ids:
            errors.append(f"duplicate or missing sample id: {sample_id!r}")
        ids.add(sample_id)
        if not sample.get("question") or not sample.get("candidate_answer"):
            errors.append(f"{sample_id}: question and candidate_answer are required")
        status = sample.get("review_status")
        if status not in REVIEW_STATUSES:
            errors.append(f"{sample_id}: invalid review_status")
        if status == "reviewed" and sample.get("human_label") not in LABELS:
            errors.append(f"{sample_id}: reviewed sample requires human_label")
        if status == "pending_review" and sample.get("human_label") is not None:
            errors.append(f"{sample_id}: pending sample cannot have human_label")
    return errors


def calibration_metrics(samples: list[dict[str, Any]], judgments: dict[str, str]) -> dict[str, Any]:
    reviewed = [sample for sample in samples if sample.get("review_status") == "reviewed"]
    compared = [sample for sample in reviewed if sample["id"] in judgments]
    correct = sum(judgments[sample["id"]] == sample["human_label"] for sample in compared)
    return {
        "reviewed_samples": len(reviewed),
        "compared_samples": len(compared),
        "pending_samples": sum(sample.get("review_status") == "pending_review" for sample in samples),
        "accuracy": correct / len(compared) if compared else None,
    }

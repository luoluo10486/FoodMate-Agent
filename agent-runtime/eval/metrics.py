"""Small in-process Eval metrics used by readiness and offline verification."""

from __future__ import annotations

import threading


_PROVIDER_FAILURE_REASONS = frozenset(
    {
        "EVAL_PROVIDER_UNAVAILABLE",
        "MODEL_PROVIDER_UNAVAILABLE",
        "MODEL_PROVIDER_REJECTED",
        "MODEL_PROVIDER_INVALID_RESPONSE",
        "MODEL_RATE_LIMIT",
        "MODEL_TIMEOUT",
    }
)


class EvalMetrics:
    """Track outcome rates and latency without retaining prompts or answers."""

    def __init__(self, max_latency_samples: int = 10_000):
        self._lock = threading.Lock()
        self._max_latency_samples = max_latency_samples
        self._counts = {
            "total": 0,
            "pass": 0,
            "degrade": 0,
            "reject": 0,
            "provider_failure": 0,
            "schema_invalid": 0,
        }
        self._latencies: list[int] = []

    def record(self, result: str, reason: str, latency_ms: int) -> None:
        with self._lock:
            self._counts["total"] += 1
            if result in self._counts:
                self._counts[result] += 1
            if reason in _PROVIDER_FAILURE_REASONS:
                self._counts["provider_failure"] += 1
            if reason == "EVAL_SCHEMA_INVALID":
                self._counts["schema_invalid"] += 1
            self._latencies.append(max(0, int(latency_ms)))
            if len(self._latencies) > self._max_latency_samples:
                del self._latencies[: len(self._latencies) - self._max_latency_samples]

    def snapshot(self) -> dict[str, object]:
        with self._lock:
            counts = dict(self._counts)
            latencies = sorted(self._latencies)
        total = counts["total"]
        return {
            **counts,
            "pass_rate": counts["pass"] / total if total else None,
            "degrade_rate": counts["degrade"] / total if total else None,
            "provider_failure_rate": counts["provider_failure"] / total if total else None,
            "schema_invalid_rate": counts["schema_invalid"] / total if total else None,
            "p95_latency_ms": self._percentile(latencies, 0.95),
            "p99_latency_ms": self._percentile(latencies, 0.99),
        }

    @staticmethod
    def _percentile(values: list[int], quantile: float) -> int | None:
        if not values:
            return None
        index = max(0, min(len(values) - 1, int((len(values) * quantile) + 0.999999) - 1))
        return values[index]


class RuntimeMetrics:
    """Bounded operational counters for local Agent traffic and recovery drills."""

    def __init__(self, max_latency_samples: int = 10_000):
        self._lock = threading.Lock()
        self._max_latency_samples = max_latency_samples
        self._operations: dict[str, dict[str, int]] = {}
        self._latencies: dict[str, list[int]] = {}
        self._queues: dict[str, int] = {}

    def record(
        self, operation: str, result: str, reason: str = "none", latency_ms: int | None = None
    ) -> None:
        operation = self._tag(operation)
        result = self._tag(result)
        reason = self._tag(reason)
        with self._lock:
            bucket = self._operations.setdefault(operation, {"total": 0})
            bucket["total"] += 1
            bucket[f"result:{result}"] = bucket.get(f"result:{result}", 0) + 1
            bucket[f"reason:{reason}"] = bucket.get(f"reason:{reason}", 0) + 1
            if latency_ms is not None:
                values = self._latencies.setdefault(operation, [])
                values.append(max(0, int(latency_ms)))
                if len(values) > self._max_latency_samples:
                    del values[: len(values) - self._max_latency_samples]

    def queue_depth(self, name: str, value: int) -> None:
        with self._lock:
            self._queues[self._tag(name)] = max(0, int(value))

    def snapshot(self) -> dict[str, object]:
        with self._lock:
            operations = {name: dict(values) for name, values in self._operations.items()}
            latencies = {name: sorted(values) for name, values in self._latencies.items()}
            queues = dict(self._queues)
        return {
            "operations": {
                name: {
                    **values,
                    "p50_latency_ms": self._percentile(latencies.get(name, []), 0.50),
                    "p95_latency_ms": self._percentile(latencies.get(name, []), 0.95),
                    "p99_latency_ms": self._percentile(latencies.get(name, []), 0.99),
                }
                for name, values in operations.items()
            },
            "queues": queues,
        }

    @staticmethod
    def _tag(value: str) -> str:
        normalized = str(value or "unknown").strip().lower().replace(" ", "_")
        return normalized[:64] or "unknown"

    @staticmethod
    def _percentile(values: list[int], quantile: float) -> int | None:
        if not values:
            return None
        index = max(0, min(len(values) - 1, int((len(values) * quantile) + 0.999999) - 1))
        return values[index]

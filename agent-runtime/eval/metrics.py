"""Small in-process Eval metrics used by readiness and offline verification."""

from __future__ import annotations

import os
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

    _ALLOWED_TRANSPORTS = frozenset({"http", "rocketmq", "local"})

    _ALLOWED_OPERATIONS = frozenset(
        {"dispatch", "event", "result", "proposal", "sse_replay", "knowledge_index", "visibility", "purge"}
    )
    _ALLOWED_RESULTS = frozenset(
        {
            "success",
            "failed",
            "duplicate",
            "redelivered",
            "accepted",
            "rejected",
            "retry",
            "completed",
            "timeout",
            "pending",
            "leased",
            "terminal",
        }
    )
    _ALLOWED_REASONS = frozenset(
        {
            "none",
            "completed",
            "contract",
            "consumer_error",
            "execution_error",
            "infrastructure_error",
            "business_error",
            "http",
            "received",
            "redis_inbox",
            "rocketmq",
            "timeout",
            "retry",
            "redelivery",
            "duplicate",
            "leased",
            "pending",
            "replay",
            "provider_error",
        }
    )
    _ALLOWED_QUEUE_NAMES = frozenset(
        {
            "active_dispatches",
            "result_waiters",
            "dispatch_pending",
            "dispatch_leased",
            "proposal_pending",
            "proposal_leased",
            "result_pending",
            "result_leased",
            "sse_replay_pending",
        }
    )

    def __init__(self, max_latency_samples: int = 10_000, transport: str | None = None):
        self._lock = threading.Lock()
        self._max_latency_samples = max_latency_samples
        self._transport = self._tag(
            transport or os.getenv("FOODMATE_AGENT_TRANSPORT", "local"),
            self._ALLOWED_TRANSPORTS,
        )
        self._operations: dict[str, dict[str, int]] = {}
        self._latencies: dict[str, list[int]] = {}
        self._transport_operations: dict[str, dict[str, dict[str, int]]] = {}
        self._transport_latencies: dict[str, dict[str, list[int]]] = {}
        self._queues: dict[str, int] = {}

    def record(
        self,
        operation: str,
        result: str,
        reason: str = "none",
        latency_ms: int | None = None,
        transport: str | None = None,
    ) -> None:
        operation = self._tag(operation, self._ALLOWED_OPERATIONS)
        result = self._tag(result, self._ALLOWED_RESULTS)
        reason = self._tag(reason, self._ALLOWED_REASONS)
        transport = self._tag(transport or self._transport, self._ALLOWED_TRANSPORTS)
        with self._lock:
            self._record_bucket(self._operations, self._latencies, operation, result, reason, latency_ms)
            transport_buckets = self._transport_operations.setdefault(transport, {})
            transport_latencies = self._transport_latencies.setdefault(transport, {})
            self._record_bucket(
                transport_buckets,
                transport_latencies,
                operation,
                result,
                reason,
                latency_ms,
            )

    def _record_bucket(
        self,
        operations: dict[str, dict[str, int]],
        latencies: dict[str, list[int]],
        operation: str,
        result: str,
        reason: str,
        latency_ms: int | None,
    ) -> None:
        bucket = operations.setdefault(operation, {"total": 0})
        bucket["total"] += 1
        bucket[f"result:{result}"] = bucket.get(f"result:{result}", 0) + 1
        bucket[f"reason:{reason}"] = bucket.get(f"reason:{reason}", 0) + 1
        if latency_ms is not None:
            values = latencies.setdefault(operation, [])
            values.append(max(0, int(latency_ms)))
            if len(values) > self._max_latency_samples:
                del values[: len(values) - self._max_latency_samples]

    def record_redelivery(self, operation: str, transport: str | None = None) -> None:
        """记录消息重投事实，标签仅保留固定枚举值。"""
        self.record(operation, "redelivered", "redelivery", transport=transport)

    def record_duplicate(self, operation: str, transport: str | None = None) -> None:
        """记录幂等重复事实，避免调用方把动态 ID 放入指标。"""
        self.record(operation, "duplicate", "duplicate", transport=transport)

    def record_retry(self, operation: str, transport: str | None = None) -> None:
        """记录可重试失败事实。"""
        self.record(operation, "retry", "retry", transport=transport)

    def queue_depth(self, name: str, value: int) -> None:
        with self._lock:
            self._queues[self._tag(name, self._ALLOWED_QUEUE_NAMES)] = max(0, int(value))

    def snapshot(self) -> dict[str, object]:
        with self._lock:
            operations = {name: dict(values) for name, values in self._operations.items()}
            latencies = {name: sorted(values) for name, values in self._latencies.items()}
            transport_operations = {
                transport: {
                    name: dict(values) for name, values in values_by_operation.items()
                }
                for transport, values_by_operation in self._transport_operations.items()
            }
            transport_latencies = {
                transport: {
                    name: sorted(values) for name, values in values_by_operation.items()
                }
                for transport, values_by_operation in self._transport_latencies.items()
            }
            queues = dict(self._queues)

        def summarize(
            values_by_operation: dict[str, dict[str, int]],
            latencies_by_operation: dict[str, list[int]],
        ) -> dict[str, dict[str, int | float | None]]:
            return {
                name: {
                    **values,
                    **self._rates(values),
                    "p50_latency_ms": self._percentile(
                        latencies_by_operation.get(name, []), 0.50
                    ),
                    "p95_latency_ms": self._percentile(
                        latencies_by_operation.get(name, []), 0.95
                    ),
                    "p99_latency_ms": self._percentile(
                        latencies_by_operation.get(name, []), 0.99
                    ),
                }
                for name, values in values_by_operation.items()
            }

        return {
            "transport": self._transport,
            "operations": summarize(operations, latencies),
            "by_transport": {
                transport: summarize(
                    transport_operations[transport], transport_latencies.get(transport, {})
                )
                for transport in transport_operations
            },
            "queues": queues,
        }

    @staticmethod
    def _rates(values: dict[str, int]) -> dict[str, float | None]:
        total = values.get("total", 0)
        if not total:
            return {
                "success_rate": None,
                "failure_rate": None,
                "duplicate_rate": None,
                "redelivery_rate": None,
                "retry_rate": None,
            }
        return {
            "success_rate": values.get("result:success", 0) / total,
            "failure_rate": values.get("result:failed", 0) / total,
            "duplicate_rate": values.get("result:duplicate", 0) / total,
            "redelivery_rate": values.get("result:redelivered", 0) / total,
            "retry_rate": values.get("result:retry", 0) / total,
        }

    @staticmethod
    def _tag(value: str, allowed: frozenset[str]) -> str:
        normalized = str(value or "unknown").strip().lower().replace(" ", "_")
        return normalized if normalized in allowed else "other"

    @staticmethod
    def _percentile(values: list[int], quantile: float) -> int | None:
        if not values:
            return None
        index = max(0, min(len(values) - 1, int((len(values) * quantile) + 0.999999) - 1))
        return values[index]

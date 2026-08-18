import sys
import unittest
from pathlib import Path

sys.path.append(str(Path(__file__).parents[1]))
from eval.metrics import EvalMetrics, RuntimeMetrics


class EvalMetricsTests(unittest.TestCase):
    def test_rates_failure_categories_and_percentiles(self):
        metrics = EvalMetrics()
        metrics.record("pass", "DETERMINISTIC_RULES_PASSED", 10)
        metrics.record("degrade", "EVAL_SCHEMA_INVALID", 20)
        metrics.record("degrade", "EVAL_PROVIDER_UNAVAILABLE", 30)
        metrics.record("degrade", "MODEL_TIMEOUT", 40)
        snapshot = metrics.snapshot()
        self.assertEqual(4, snapshot["total"])
        self.assertAlmostEqual(1 / 4, snapshot["pass_rate"])
        self.assertAlmostEqual(3 / 4, snapshot["degrade_rate"])
        self.assertAlmostEqual(2 / 4, snapshot["provider_failure_rate"])
        self.assertAlmostEqual(1 / 4, snapshot["schema_invalid_rate"])
        self.assertEqual(40, snapshot["p95_latency_ms"])
        self.assertEqual(40, snapshot["p99_latency_ms"])

    def test_runtime_metrics_are_low_cardinality_aggregates(self):
        metrics = RuntimeMetrics()
        metrics.record("dispatch", "success", "completed", 10)
        metrics.record("dispatch", "duplicate", "redis_inbox", 20)
        metrics.queue_depth("active_dispatches", 3)

        snapshot = metrics.snapshot()
        dispatch = snapshot["operations"]["dispatch"]
        self.assertEqual(2, dispatch["total"])
        self.assertEqual(1, dispatch["result:success"])
        self.assertEqual(1, dispatch["result:duplicate"])
        self.assertEqual(20, dispatch["p99_latency_ms"])
        self.assertEqual(3, snapshot["queues"]["active_dispatches"])


if __name__ == "__main__":
    unittest.main()

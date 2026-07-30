import json
import sys
import unittest
from pathlib import Path

sys.path.append(str(Path(__file__).parents[1]))
from agent_core import LlmEvalGate
from eval.calibration import calibration_metrics, validate_samples


EVAL_DIR = Path(__file__).parents[1] / "eval"


class EvalCalibrationTests(unittest.TestCase):
    def test_pending_human_samples_have_valid_schema(self):
        samples = json.loads((EVAL_DIR / "calibration_samples.json").read_text(encoding="utf-8"))
        self.assertEqual([], validate_samples(samples))
        metrics = calibration_metrics(samples, {})
        self.assertEqual(0, metrics["reviewed_samples"])
        self.assertEqual(len(samples), metrics["pending_samples"])
        self.assertIsNone(metrics["accuracy"])

    def test_calibration_metrics_compare_only_reviewed_samples(self):
        samples = [
            {"id": "a", "question": "q", "candidate_answer": "a", "review_status": "reviewed", "human_label": "pass"},
            {"id": "b", "question": "q", "candidate_answer": "a", "review_status": "reviewed", "human_label": "degrade"},
            {"id": "c", "question": "q", "candidate_answer": "a", "review_status": "pending_review", "human_label": None},
        ]
        self.assertEqual([], validate_samples(samples))
        metrics = calibration_metrics(samples, {"a": "pass", "b": "pass"})
        self.assertEqual(2, metrics["compared_samples"])
        self.assertEqual(0.5, metrics["accuracy"])

    def test_judge_threshold_and_schema_fail_closed(self):
        gate = LlmEvalGate(min_score=0.8)
        self.assertEqual("pass", gate.evaluate('{"passed": true, "score": 0.8, "reason": "OK"}').result)
        self.assertEqual("EVAL_SCORE_BELOW_THRESHOLD", gate.evaluate('{"passed": true, "score": 0.79}').reason)
        self.assertEqual("EVAL_SCORE_INVALID", gate.evaluate('{"passed": "true", "score": 0.99}').reason)
        self.assertEqual("EVAL_SCHEMA_INVALID", gate.evaluate('{"passed": true}').reason)


if __name__ == "__main__":
    unittest.main()

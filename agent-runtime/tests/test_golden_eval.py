import json
import sys
import unittest
from pathlib import Path

sys.path.append(str(Path(__file__).parents[1]))
from agent_core import run_deterministic
from eval.rubric import check_case, command_for


CASES_PATH = Path(__file__).parents[1] / "eval" / "golden_cases.json"


class GoldenEvalRegressionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.cases = json.loads(CASES_PATH.read_text(encoding="utf-8"))

    def test_golden_cases_are_unique_and_have_required_fields(self):
        ids = [case["id"] for case in self.cases]
        self.assertEqual(len(ids), len(set(ids)))
        self.assertGreaterEqual(len(ids), 8)
        for case in self.cases:
            self.assertTrue(case["message"])
            self.assertIn("eval_result", case["expected"])
            self.assertIn("eval_reason", case["expected"])

    def test_golden_runtime_behaviour(self):
        failures = []
        for case in self.cases:
            execution = run_deterministic(command_for(case))
            failures.extend(check_case(case, execution))
        self.assertFalse(failures, "\\n".join(failures))


if __name__ == "__main__":
    unittest.main()

import sys
import unittest
from pathlib import Path

sys.path.append(str(Path(__file__).parents[1]))

from agent_core import Context, DeterministicComposer, DeterministicRouter, generate_tool_proposals
from proposal_protocol import Proposal, validate_proposal


class ToolProtocolTests(unittest.TestCase):
    def test_calculator_proposal_is_valid_and_requires_no_confirmation(self):
        proposal = Proposal(
            "p-calc",
            "run-1",
            "tool",
            "v1",
            {"invocation_id": "inv-calc", "idempotency_key": "key-calc"},
            False,
            tool_name="calculator",
            input={"expression": "20 * 1.1"},
        )

        validate_proposal(Proposal(**proposal.as_dict()))

    def test_calculator_rejects_confirmation_and_unknown_expression_shape(self):
        with self.assertRaisesRegex(ValueError, "CALCULATOR_INPUT_INVALID"):
            validate_proposal(
                Proposal(
                    "p-calc",
                    "run-1",
                    "tool",
                    "v1",
                    {"invocation_id": "inv-calc"},
                    True,
                    tool_name="calculator",
                    input={"expression": "20 * 1.1"},
                )
            )
        with self.assertRaisesRegex(ValueError, "CALCULATOR_INPUT_INVALID"):
            validate_proposal(
                Proposal(
                    "p-calc",
                    "run-1",
                    "tool",
                    "v1",
                    {"invocation_id": "inv-calc"},
                    False,
                    tool_name="calculator",
                    input={"expression": [20, 1.1]},
                )
            )

    def test_deterministic_calculation_route_builds_authorized_proposal(self):
        route = DeterministicRouter().route("20 * 1.1")
        proposals = generate_tool_proposals({"run_id": "run-1", "message": {"content": "20 * 1.1"}}, route)

        self.assertEqual("calculation", route.intent)
        self.assertEqual("calculator", proposals[0]["tool_name"])
        self.assertEqual("20 * 1.1", proposals[0]["input"]["expression"])

    def test_composer_only_uses_java_calculator_result(self):
        route = DeterministicRouter().route("20 * 1.1")
        context = Context(
            messages=({"message_id": "m1"},),
            summary=None,
            memories=(),
            unresolved_slots=(),
            sources={"message_id": ("m1",), "summary_id": (), "memory_id": (), "citation_id": (), "invocation_id": ("inv-calc",)},
            tool_results=({"tool_name": "calculator", "status": "succeeded", "rows": [{"result": 22, "formula": "20 * 1.1"}]},),
        )

        answer = DeterministicComposer().compose("20 * 1.1", route, context, "normal")

        self.assertIn("计算结果：22", answer)
        self.assertIn("20 * 1.1", answer)


if __name__ == "__main__":
    unittest.main()

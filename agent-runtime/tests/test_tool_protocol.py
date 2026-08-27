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

    def test_plan_validator_proposal_requires_a_structured_plan_without_confirmation(self):
        proposal = Proposal(
            "p-plan",
            "run-1",
            "tool",
            "v1",
            {"invocation_id": "inv-plan", "idempotency_key": "key-plan"},
            False,
            tool_name="plan_validator",
            input={"plan": {"people": 1, "days": 1, "days_plan": []}},
        )

        validate_proposal(Proposal(**proposal.as_dict()))

    def test_plan_validator_proposal_rejects_non_object_plan(self):
        with self.assertRaisesRegex(ValueError, "PLAN_VALIDATOR_INPUT_INVALID"):
            validate_proposal(
                Proposal(
                    "p-plan",
                    "run-1",
                    "tool",
                    "v1",
                    {"invocation_id": "inv-plan"},
                    False,
                    tool_name="plan_validator",
                    input={"plan": []},
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

    def test_planning_route_builds_validator_proposal_from_authorized_plan(self):
        route = DeterministicRouter().route("计划 1 天的三餐")
        plan = {
            "people": 1,
            "days": 1,
            "days_plan": [{"breakfast": {}, "lunch": {}, "dinner": {}}],
        }
        proposals = generate_tool_proposals(
            {
                "run_id": "run-1",
                "message": {"content": "计划 1 天的三餐"},
                "authorized_context": {"plan_validator_request": {"plan": plan}},
            },
            route,
        )

        self.assertEqual("planning", route.intent)
        self.assertEqual("plan_validator", proposals[0]["tool_name"])
        self.assertEqual(plan, proposals[0]["input"]["plan"])

    def test_composer_does_not_hide_plan_validation_issues(self):
        route = DeterministicRouter().route("计划 1 天的三餐")
        context = Context(
            messages=({"message_id": "m1"},),
            summary=None,
            memories=(),
            unresolved_slots=(),
            sources={"message_id": ("m1",), "summary_id": (), "memory_id": (), "citation_id": (), "invocation_id": ("inv-plan",)},
            tool_results=(
                {
                    "tool_name": "plan_validator",
                    "status": "failed",
                    "error_code": "PLAN_CONSTRAINTS_UNSATISFIED",
                    "rows": [{"valid": False, "issues": ["缺少 dinner"]}],
                },
            ),
        )

        answer = DeterministicComposer().compose("计划 1 天的三餐", route, context, "normal")

        self.assertIn("校验未通过", answer)
        self.assertIn("缺少 dinner", answer)


if __name__ == "__main__":
    unittest.main()

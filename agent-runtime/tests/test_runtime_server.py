import json
import sys
import unittest
import base64
from pathlib import Path
from unittest.mock import patch
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

sys.path.append(str(Path(__file__).parents[1]))
import runtime_server
from agent_core import BudgetSnapshot, ContextBuilder, InMemoryCheckpoint, Usage, WorkflowGraph, budget_mode, budget_policy, run_deterministic, split_answer


class RuntimeContractTests(unittest.TestCase):
    def setUp(self):
        runtime_server._cancelled.clear()
        runtime_server._dispatches.clear()

    def test_execute_emits_ordered_lifecycle(self):
        events = []
        command = {"run_id": "1", "dispatch_id": "d1", "deadline_at": "x", "attempt": 1}
        with patch.object(runtime_server, "emit", side_effect=lambda *args: events.append(args[3])):
            runtime_server.execute(command)
        self.assertEqual(["run.accepted", "run.routed", "run.model_usage", "run.answer_stream", "run.completed"], events)

    def test_budget_thresholds_and_utf8_chunking(self):
        budget = BudgetSnapshot(max_total_tokens=100, max_cost_cny=1)
        self.assertEqual("reduced_reflection", budget_mode(Usage(tokens=70), budget))
        self.assertEqual("economy", budget_mode(Usage(tokens=85), budget))
        self.assertEqual("partial", budget_mode(Usage(tokens=100), budget))
        chunks = split_answer("营养" * 10, 8)
        self.assertTrue(all(len(item.encode("utf-8")) <= 8 for item in chunks))

    def test_budget_policy_exposes_fixed_threshold_actions(self):
        budget = BudgetSnapshot(max_total_tokens=100, max_cost_cny=1, max_model_calls=12)
        self.assertTrue(budget_policy(Usage(tokens=69), budget).allow_reflection)
        soft = budget_policy(Usage(tokens=70), budget)
        self.assertEqual("reduced_reflection", soft.mode)
        self.assertFalse(soft.allow_reflection)
        hard = budget_policy(Usage(tokens=85), budget)
        self.assertEqual("economy", hard.mode)
        self.assertFalse(hard.allow_replan)
        exhausted = budget_policy(Usage(tokens=100), budget)
        self.assertTrue(exhausted.requires_confirmation)
        self.assertFalse(exhausted.allow_new_model_call)

    def test_workflow_graph_rejects_unlisted_edge_and_records_complex_path(self):
        graph = WorkflowGraph(10)
        graph.enter("router")
        with self.assertRaisesRegex(RuntimeError, "WORKFLOW_EDGE_NOT_ALLOWED"):
            graph.enter("eval")
        execution = run_deterministic({
            "run_id": "graph-complex", "dispatch_id": "d1",
            "message": {"content": "\u8ba1\u5212 7 \u5929\u7684\u98df\u8c31"},
        })
        self.assertEqual(["router", "planner", "execution", "validator", "composer", "eval", "terminal"], execution.workflow["nodes"])

    def test_workflow_step_budget_stops_before_model_call(self):
        execution = run_deterministic({
            "run_id": "graph-limit", "dispatch_id": "d1",
            "budget_snapshot": {"max_total_steps": 2},
            "message": {"content": "\u8ba1\u5212 7 \u5929\u7684\u98df\u8c31"},
        })
        self.assertEqual("MAX_TOTAL_STEPS", execution.eval.reason)
        self.assertEqual([], execution.model_attempts)

    def test_context_keeps_eight_recent_messages_and_tracks_sources(self):
        command = {"message": {"message_id": "9", "content": "我喜欢清淡"}, "authorized_context": {
            "recent_messages": [{"message_id": str(i), "content": str(i)} for i in range(1, 9)],
            "session_summary": {"summary_id": "s1"}, "long_term_memories": [{"memory_id": "m1"}],
        }}
        context = ContextBuilder().build(command, type("Route", (), {"missing_slots": ()})())
        self.assertEqual(8, len(context.messages))
        self.assertEqual("9", context.sources["message_id"][-1])
        self.assertEqual(("s1",), context.sources["summary_id"])
        self.assertEqual(("m1",), context.sources["memory_id"])

    def test_context_token_limit_drops_old_messages_but_keeps_current(self):
        command = {
            "message": {"message_id": "current", "content": "current"},
            "authorized_context": {"recent_messages": [
                {"message_id": "old", "content": "x" * 100},
                {"message_id": "newer", "content": "y" * 100},
            ]},
        }
        context = ContextBuilder(max_recent_messages=8, max_context_tokens=150).build(command, type("Route", (), {"missing_slots": ()})())
        self.assertEqual("current", context.messages[-1]["message_id"])
        self.assertLessEqual(context.estimated_tokens, 150)

    def test_checkpoint_uses_compare_and_set(self):
        checkpoint = InMemoryCheckpoint()
        version = checkpoint.save("r:d", {"node": "planner"})
        self.assertEqual(2, checkpoint.save("r:d", {"node": "composer"}, version))
        with self.assertRaisesRegex(RuntimeError, "CHECKPOINT_CAS_CONFLICT"):
            checkpoint.save("r:d", {"node": "bad"}, version)

    def test_high_risk_request_is_safely_degraded(self):
        execution = run_deterministic({"run_id": "r1", "dispatch_id": "d1", "message": {"content": "我有疾病，怎么诊断"}})
        self.assertEqual("degrade", execution.eval.result)
        self.assertIn("医生", execution.answer)

    def test_complex_request_has_independent_eval_attempt(self):
        execution = run_deterministic({
            "run_id": "complex-eval", "dispatch_id": "d1",
            "message": {"content": "\u8ba1\u5212 7 \u5929\u7684\u98df\u8c31"},
        })
        self.assertEqual("pass", execution.eval.result)
        self.assertEqual(["composer", "eval"], [attempt.scene for attempt in execution.model_attempts])
        self.assertNotEqual(execution.model_attempts[0].model_call_id, execution.model_attempts[1].model_call_id)

    def test_cancel_is_observed_before_execution(self):
        events = []
        command = {"run_id": "1", "dispatch_id": "d1", "deadline_at": "x", "attempt": 1}
        runtime_server._cancelled.add("1")
        with patch.object(runtime_server, "emit", side_effect=lambda *args: events.append(args[3])):
            runtime_server.execute(command)
        self.assertEqual(["run.accepted", "run.cancel_acknowledged", "run.cancelled"], events)

    def test_service_jwt_round_trip(self):
        private_key = Ed25519PrivateKey.generate()
        private_der = private_key.private_bytes(serialization.Encoding.DER, serialization.PrivateFormat.PKCS8, serialization.NoEncryption())
        public_der = private_key.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
        with patch.object(runtime_server, "PYTHON_PRIVATE_KEY", base64.b64encode(private_der).decode()), patch.object(runtime_server, "PYTHON_KID", "python-2026-01"):
            token = runtime_server._sign("foodmate-agent-runtime", "foodmate-control-plane", "agent:event")
        with patch.object(runtime_server, "JAVA_PUBLIC_KEY", base64.b64encode(public_der).decode()), patch.object(runtime_server, "JWT_ENABLED", True):
            self.assertTrue(runtime_server._verify(token, "foodmate-agent-runtime", "foodmate-control-plane", "agent:event"))
            self.assertFalse(runtime_server._verify(token, "foodmate-agent-runtime", "foodmate-control-plane", "runtime:cancel"))

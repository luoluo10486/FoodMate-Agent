import json
import sys
import unittest
import base64
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

sys.path.append(str(Path(__file__).parents[1]))
import runtime_server
from agent_core import BudgetSnapshot, Context, ContextBuilder, InMemoryCheckpoint, Plan, Reflector, RouteDecision, StepValidator, Usage, WorkflowGraph, budget_mode, budget_policy, run_deterministic, split_answer
from knowledge_rag import Citation, PUBLIC_SCOPE
from proposal_protocol import Proposal, validate_proposal
from recovery_protocol import checkpoint_digest, validate_recovery_command
from langgraph_adapter import build_graph


class RuntimeContractTests(unittest.TestCase):
    def setUp(self):
        runtime_server._cancelled.clear()
        runtime_server._dispatches.clear()
        runtime_server._result_waiters.clear()
        runtime_server._checkpoint = InMemoryCheckpoint()

    def test_execute_emits_ordered_lifecycle(self):
        events = []
        command = {"run_id": "1", "dispatch_id": "d1", "deadline_at": "x", "attempt": 1}
        with patch.object(runtime_server, "emit", side_effect=lambda *args: events.append(args[3])):
            runtime_server.execute(command)
        self.assertEqual(["run.accepted", "run.routed", "run.model_usage", "run.eval_decided", "run.answer_stream", "run.completed"], events)

    def test_model_failure_still_has_contiguous_route_event(self):
        events = []
        command = {"run_id": "1", "dispatch_id": "d-failed", "deadline_at": "x", "attempt": 1}
        error = runtime_server.ModelProviderError("MODEL_PROVIDER_REJECTED", "rejected")
        with patch.object(runtime_server, "run_deterministic", side_effect=error):
            with patch.object(
                runtime_server,
                "emit",
                side_effect=lambda *args: events.append((args[2], args[3])),
            ):
                runtime_server.execute(command)
        self.assertEqual([(1, "run.accepted"), (2, "run.routed"), (3, "run.failed")], events)

    def test_readiness_reports_coordination_failure(self):
        with patch.dict(
            runtime_server.os.environ,
            {"FOODMATE_AGENT_TRANSPORT": "rocketmq", "FOODMATE_AGENT_CHECKPOINT_BACKEND": "redis"},
            clear=False,
        ):
            with patch.object(runtime_server, "_event_publisher", None), patch.object(
                runtime_server, "_proposal_publisher", None
            ), patch.object(runtime_server, "_mq_runtime", None), patch.object(
                runtime_server, "_checkpoint", InMemoryCheckpoint()
            ):
                status, payload = runtime_server._readiness()
        self.assertEqual(503, status)
        self.assertEqual("RUNTIME_COORDINATION_UNAVAILABLE", payload["code"])
        self.assertIn("redis", payload["unavailable_dependencies"])

    def test_readiness_is_up_for_local_http_runtime(self):
        with patch.dict(
            runtime_server.os.environ,
            {"FOODMATE_AGENT_TRANSPORT": "http", "FOODMATE_AGENT_CHECKPOINT_BACKEND": "inmemory"},
            clear=False,
        ):
            with patch.object(runtime_server, "_checkpoint", InMemoryCheckpoint()):
                status, payload = runtime_server._readiness()
        self.assertEqual(200, status)
        self.assertEqual("UP", payload["status"])
        self.assertEqual("disabled", payload["dependencies"]["redis"]["status"])

    def test_knowledge_search_endpoint_returns_safe_public_citations(self):
        handler = runtime_server.Handler.__new__(runtime_server.Handler)
        responses = []
        handler._json = lambda status, payload: responses.append((status, payload))
        citation = Citation("42", "Nutrition guide", "v1", "Protein", "emb-1", "safe snippet")
        with patch.object(runtime_server, "_search_public_knowledge", return_value=[citation]):
            handler._knowledge_search({"knowledge_scope": PUBLIC_SCOPE, "query": "protein"})

        self.assertEqual(200, responses[0][0])
        self.assertEqual(PUBLIC_SCOPE, responses[0][1]["knowledge_scope"])
        self.assertEqual(
            {
                "citation_id": "emb-1",
                "document_id": "42",
                "title": "Nutrition guide",
                "version": "v1",
                "section_path": "Protein",
                "snippet": "safe snippet",
            },
            responses[0][1]["citations"][0],
        )

    def test_knowledge_search_endpoint_cannot_widen_scope(self):
        handler = runtime_server.Handler.__new__(runtime_server.Handler)
        responses = []
        handler._json = lambda status, payload: responses.append((status, payload))
        handler._knowledge_search({"knowledge_scope": "private", "query": "protein"})
        self.assertEqual((403, {"code": "RAG_SCOPE_DENIED"}), responses[0])

    def test_missing_parameter_enters_waiting_user_without_answer_body(self):
        events = []
        command = {
            "run_id": "waiting-run",
            "dispatch_id": "waiting-dispatch",
            "deadline_at": "2099-01-01T00:00:00Z",
            "attempt": 1,
            "message": {"content": "计划 食谱"},
            "runtime_options": {
                "budget_snapshot": {"revision": 1},
                "prompt_set_version": "test",
            },
        }
        with patch.object(
            runtime_server,
            "emit",
            side_effect=lambda *args: events.append((args[3], args[4] if len(args) > 4 else {})),
        ):
            runtime_server.execute(command)
        self.assertEqual(
            ["run.accepted", "run.routed", "run.checkpoint_saved", "run.eval_decided", "run.clarification_requested"],
            [event[0] for event in events],
        )
        self.assertEqual(1, events[2][1]["checkpoint_version"])
        self.assertTrue(events[2][1]["checkpoint_digest"].startswith("sha256:"))
        checkpoint = runtime_server._checkpoint.load("waiting-run:waiting-dispatch")
        self.assertIsNotNone(checkpoint)
        self.assertEqual("execution", checkpoint[1]["current_node"])

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

    def test_step_validator_rejects_complex_plan_without_fact_validation(self):
        route = RouteDecision("analysis", "complex", "low")
        context = Context((), None, (), (), {"message_id": ()})
        with self.assertRaisesRegex(ValueError, "complex plan lacks fact validation"):
            StepValidator().validate(route, Plan(("compose",), route), context)

    def test_step_validator_rejects_duplicate_tool_invocation(self):
        route = RouteDecision("analysis", "complex", "low")
        context = Context(
            ({"message_id": "m1"},),
            None,
            (),
            (),
            {"message_id": ("m1",), "invocation_id": ("inv-1",)},
            tool_results=(
                {"invocation_id": "inv-1", "status": "succeeded", "rows": []},
                {"invocation_id": "inv-1", "status": "succeeded", "rows": []},
            ),
        )
        with self.assertRaisesRegex(ValueError, "duplicate or missing tool invocation"):
            StepValidator().validate(route, Plan(("retrieve_authorized_context", "validate_facts", "compose"), route), context)

    def test_reflector_rejects_empty_or_incomplete_candidate(self):
        route = RouteDecision("analysis", "complex", "low")
        context = Context(({"message_id": "m1"},), None, (), (), {"message_id": ("m1",)})
        self.assertEqual("REFLECTION_ANSWER_EMPTY", Reflector().reflect("", route, context).reason)
        incomplete = Context(
            ({"message_id": "m1"},),
            None,
            (),
            (),
            {"message_id": ("m1",), "invocation_id": ("inv-1",)},
            tool_results=({"invocation_id": "inv-1", "status": "succeeded", "rows": []},),
        )
        self.assertEqual("REFLECTION_PASSED", Reflector().reflect("answer", route, incomplete).reason)

    def test_sql_proposal_rejects_write_statement(self):
        proposal = Proposal("p1", "r1", "sql_read", "v1", {"statement": "UPDATE food_logs SET notes='x'", "invocation_id": "inv-1"})
        with self.assertRaisesRegex(ValueError, "SQL_PROPOSAL_NOT_READ_ONLY"):
            validate_proposal(proposal)

    def test_analysis_generates_structured_database_query_proposal(self):
        execution = run_deterministic({
            "run_id": "analysis-1",
            "dispatch_id": "d1",
            "message": {"content": "分析最近7天蛋白质摄入"},
            "authorized_context": {"sql_read_request": {"invocation_id": "inv-analysis"}},
        })

        self.assertEqual(1, len(execution.proposals))
        proposal = execution.proposals[0]
        self.assertEqual("tool", proposal["proposal_type"])
        self.assertEqual("time_parser", proposal["tool_name"])
        self.assertEqual("Asia/Shanghai", proposal["input"]["timezone"])
        validate_proposal(Proposal(**proposal))

        completed = {
            "tool_name": "time_parser",
            "invocation_id": proposal["payload"]["invocation_id"],
            "status": "succeeded",
            "rows": [{"days": 7, "from": "2026-08-15T00:00:00Z", "to": "2026-08-22T00:00:00Z"}],
        }
        second = run_deterministic(
            {
                "run_id": "analysis-1",
                "dispatch_id": "d1",
                "message": {"content": "分析最近7天蛋白质摄入"},
                "authorized_context": {"tool_results": [completed]},
            }
        )
        self.assertEqual(1, len(second.proposals))
        database = second.proposals[0]
        self.assertEqual("database_query", database["tool_name"])
        self.assertEqual("nutrition_summary", database["input"]["intent"])
        self.assertEqual(database["payload"]["statement"], database["input"]["candidate_sql"])
        validate_proposal(Proposal(**database))

    def test_analysis_phrase_containing_record_is_not_routed_as_record(self):
        execution = run_deterministic({
            "run_id": "analysis-route",
            "dispatch_id": "d1",
            "message": {"content": "请分析最近7天的蛋白质摄入，并说明没有记录时应如何处理"},
        })

        self.assertEqual("analysis", execution.route.intent)
        self.assertEqual(1, len(execution.proposals))
        self.assertEqual("time_parser", execution.proposals[0]["tool_name"])

    def test_record_route_without_authorized_writer_does_not_query_context_sql(self):
        execution = run_deterministic({
            "run_id": "record-route",
            "dispatch_id": "d1",
            "message": {"content": "记录我吃了早餐"},
        })

        self.assertEqual("record", execution.route.intent)
        self.assertEqual([], execution.proposals)

    def test_analysis_without_time_range_stops_for_clarification(self):
        execution = run_deterministic({
            "run_id": "analysis-clarify",
            "dispatch_id": "d1",
            "message": {"content": "分析我的蛋白质摄入"},
        })

        self.assertEqual("SQL_PLANNER_TIME_RANGE_REQUIRED", execution.eval.reason)
        self.assertEqual([], execution.proposals)

    def test_analysis_empty_result_has_no_fabricated_trend(self):
        execution = run_deterministic(
            {
                "run_id": "analysis-empty",
                "dispatch_id": "d1",
                "message": {"content": "分析最近7天蛋白质摄入"},
                "authorized_context": {
                    "database_query_plan": {
                        "time_range": {"days": "7"},
                        "metrics": ["protein_g"],
                        "dimensions": ["meal_time"],
                    },
                    "tool_results": [
                        {
                            "tool_name": "database_query",
                            "invocation_id": "inv-dbq",
                            "status": "succeeded",
                            "rows": [],
                            "sql_audit_id": "99",
                        }
                    ],
                },
            }
        )
        self.assertIn("未找到可用的饮食记录", execution.answer)
        self.assertNotIn("上升趋势", execution.answer)

    def test_proposal_requires_invocation_id_and_valid_request_hash(self):
        with self.assertRaisesRegex(ValueError, "PROPOSAL_INVOCATION_ID_REQUIRED"):
            validate_proposal(Proposal("p1", "r1", "sql_read", "v1", {"statement": "SELECT 1"}))
        proposal = Proposal("p1", "r1", "sql_read", "v1", {"statement": "SELECT 1", "invocation_id": "inv-1"})
        payload = proposal.as_dict()
        validate_proposal(Proposal(**payload))
        payload["request_hash"] = "sha256:tampered"
        with self.assertRaisesRegex(ValueError, "PROPOSAL_REQUEST_HASH_INVALID"):
            validate_proposal(Proposal(**payload))

    def test_execute_reinjects_tool_result_before_follow_up_run(self):
        events, published, commands = [], [], []
        proposal = Proposal("p1", "r1", "sql_read", "v1", {"statement": "SELECT 1", "invocation_id": "inv-1"}).as_dict()
        route = SimpleNamespace(intent="analysis", complexity="complex", risk_level="low")
        first = SimpleNamespace(proposals=[proposal], route=route, plan=SimpleNamespace(plan_version="v1"), workflow={"nodes": []}, model_attempts=[], usage=SimpleNamespace(tokens=1, cost_cny=0.0, model_calls=1))
        second = SimpleNamespace(proposals=[], route=route, plan=SimpleNamespace(plan_version="v1"), workflow={"nodes": []}, model_attempts=[], usage=SimpleNamespace(tokens=2, cost_cny=0.0, model_calls=1), answer="ok", eval=SimpleNamespace(result="pass", reason="ok"), budget_mode="normal", budget_actions={}, memory_candidates=[])

        def run(command, _checkpoint):
            commands.append(command)
            return first if len(commands) == 1 else second

        class Publisher:
            def publish(self, value):
                published.append(value)
                runtime_server._on_result({"proposal_id": "p1", "invocation_id": "inv-1", "status": "succeeded", "request_hash": proposal["request_hash"], "rows": []})

        command = {"run_id": "r1", "dispatch_id": "d1", "deadline_at": "x", "attempt": 1}
        emitted = []
        with patch.object(runtime_server, "run_deterministic", side_effect=run), patch.object(runtime_server, "emit", side_effect=lambda *args: (events.append(args[3]), emitted.append((args[2], args[3])))), patch.object(runtime_server, "_proposal_publisher", Publisher()):
            runtime_server.execute(command)
        self.assertEqual([proposal], published)
        self.assertEqual("inv-1", commands[1]["authorized_context"]["tool_results"][0]["invocation_id"])
        checkpoint = runtime_server._checkpoint.load("r1:d1")
        self.assertIsNotNone(checkpoint)
        self.assertEqual(["inv-1"], checkpoint[1]["completed_invocation_ids"])
        recovery_snapshot = runtime_server._checkpoint.load("r1:d1:recovery")
        self.assertIsNotNone(recovery_snapshot)
        self.assertEqual("tool_wait", recovery_snapshot[1]["current_node"])
        self.assertEqual(list(range(1, len(emitted) + 1)), [sequence for sequence, _ in emitted])
        self.assertEqual(["run.tool_started", "run.tool_finished"], [event for _, event in emitted[3:5]])
        self.assertEqual("run.completed", events[-1])

    def test_execute_supports_time_parser_then_database_query_rounds(self):
        events, published, commands = [], [], []
        time_proposal = Proposal(
            "time-proposal",
            "r1",
            "tool",
            "v1",
            {"invocation_id": "time-inv", "statement": ""},
            False,
            tool_name="time_parser",
            input={"question": "分析最近7天蛋白质摄入", "timezone": "Asia/Shanghai"},
        ).as_dict()
        database_proposal = Proposal(
            "database-proposal",
            "r1",
            "tool",
            "v1",
            {"invocation_id": "db-inv", "statement": "SELECT protein_g LIMIT 500"},
            False,
            tool_name="database_query",
            input={
                "intent": "nutrition_summary",
                "time_range": {"kind": "relative", "days": "7"},
                "metrics": ["protein_g"],
                "dimensions": ["meal_time"],
                "filters": {},
                "candidate_sql": "SELECT protein_g LIMIT 500",
                "planner_mode": "stub",
                "planner_version": "v1",
            },
        ).as_dict()
        route = SimpleNamespace(intent="analysis", complexity="complex", risk_level="low")
        executions = [
            SimpleNamespace(
                proposals=[time_proposal],
                route=route,
                model_attempts=[],
                usage=SimpleNamespace(tokens=1, cost_cny=0.0, model_calls=1),
            ),
            SimpleNamespace(
                proposals=[database_proposal],
                route=route,
                model_attempts=[],
                usage=SimpleNamespace(tokens=1, cost_cny=0.0, model_calls=1),
            ),
            SimpleNamespace(
                proposals=[],
                route=route,
                model_attempts=[],
                eval=SimpleNamespace(result="pass", reason="ok"),
                answer="analysis complete",
                budget_mode="normal",
                budget_actions={},
                usage=SimpleNamespace(tokens=1, cost_cny=0.0, model_calls=1),
                workflow={},
                memory_candidates=[],
            ),
        ]

        class Publisher:
            def publish(self, value):
                published.append(value)
                result = {
                    "proposal_id": value["proposal_id"],
                    "invocation_id": value["payload"]["invocation_id"],
                    "request_hash": value["request_hash"],
                    "status": "succeeded",
                    "rows": [{"protein_g": 24}] if value["tool_name"] == "database_query" else [{"days": 7}],
                }
                runtime_server._on_result(result)

        command = {"run_id": "r1", "dispatch_id": "d1", "deadline_at": "x", "attempt": 1}
        with patch.object(runtime_server, "run_deterministic", side_effect=lambda value, _store: commands.append(value) or executions[len(commands) - 1]), patch.object(
            runtime_server, "emit", side_effect=lambda *args: events.append(args[3])
        ), patch.object(runtime_server, "_proposal_publisher", Publisher()):
            runtime_server.execute(command)

        self.assertEqual([time_proposal, database_proposal], published)
        self.assertEqual(2, len(commands[2]["authorized_context"]["tool_results"]))
        self.assertEqual("database_query", commands[2]["authorized_context"]["tool_results"][1]["tool_name"])
        self.assertEqual("run.completed", events[-1])

    def test_execute_marks_result_timeout_as_retryable_failure(self):
        events = []
        proposal = Proposal("p1", "r1", "sql_read", "v1", {"statement": "SELECT 1", "invocation_id": "inv-1"}).as_dict()
        execution = SimpleNamespace(proposals=[proposal])
        command = {"run_id": "r1", "dispatch_id": "d1", "deadline_at": "x", "attempt": 1}
        with patch.object(runtime_server, "run_deterministic", return_value=execution), patch.object(runtime_server, "emit", side_effect=lambda *args: events.append((args[2], args[3], args[4] if len(args) > 4 else {}))), patch.object(runtime_server, "_proposal_publisher", SimpleNamespace(publish=lambda _proposal: None)), patch.object(runtime_server, "_await_result", side_effect=TimeoutError("TOOL_RESULT_TIMEOUT")):
            runtime_server.execute(command)
        self.assertEqual((5, "run.failed"), events[-1][:2])
        self.assertEqual({"code": "TOOL_RESULT_TIMEOUT", "retryable": True}, events[-1][2])

    def test_native_langgraph_adapter_compiles_whitelisted_graph(self):
        graph = build_graph()
        result = graph.invoke({})
        self.assertIn("node", result)

    def test_checkpoint_uses_compare_and_set(self):
        checkpoint = InMemoryCheckpoint()
        version = checkpoint.save("r:d", {"node": "planner"})
        self.assertEqual(2, checkpoint.save("r:d", {"node": "composer"}, version))
        with self.assertRaisesRegex(RuntimeError, "CHECKPOINT_CAS_CONFLICT"):
            checkpoint.save("r:d", {"node": "bad"}, version)

    def test_runtime_checkpoint_payload_is_json_serializable(self):
        checkpoint = InMemoryCheckpoint()
        run_deterministic(
            {
                "run_id": "checkpoint-json",
                "dispatch_id": "d1",
                "message": {"content": "今天晚餐吃什么"},
            },
            checkpoint,
        )
        stored = checkpoint.load("checkpoint-json:d1")
        self.assertIsNotNone(stored)
        json.dumps(stored[1], ensure_ascii=False)

    def test_runtime_execution_state_does_not_overwrite_recovery_checkpoint(self):
        checkpoint = InMemoryCheckpoint()
        command = {
            "run_id": "checkpoint-isolation",
            "dispatch_id": "d1",
            "deadline_at": "2099-01-01T00:00:00Z",
            "message": {"content": "帮我记录今天的晚餐"},
            "authorized_context": {
                "sql_read_request": {
                    "statement": "SELECT 1",
                    "invocation_id": "inv-1",
                }
            },
            "_checkpoint_key": "checkpoint-isolation:d1:state",
        }
        execution = run_deterministic(command, checkpoint)
        self.assertEqual(["inv-1"], [item["payload"]["invocation_id"] for item in execution.proposals])
        self.assertIsNone(checkpoint.load("checkpoint-isolation:d1"))
        self.assertIsNotNone(checkpoint.load("checkpoint-isolation:d1:state"))

    def test_recovery_requires_new_dispatch_and_matching_checkpoint_reconciliation(self):
        checkpoint = InMemoryCheckpoint()
        saved = {
            "run_id": "r1", "dispatch_id": "d1", "attempt": 1,
            "current_node": "tool_wait", "deadline_at": "2030-01-01T00:00:00Z",
            "budget_revision": 2, "completed_invocation_ids": ["inv-1"],
        }
        version = checkpoint.save("r1:d1", saved)
        recovery = {
            "previous_dispatch_id": "d1", "previous_attempt": 1,
            "checkpoint_version": version, "checkpoint_digest": checkpoint_digest(saved),
            "budget_revision": 2, "completed_invocation_ids": ["inv-1"],
        }
        command = {"run_id": "r1", "dispatch_id": "d2", "attempt": 2, "deadline_at": "2030-01-01T00:00:00Z", "recovery_context": recovery}
        self.assertEqual(saved, validate_recovery_command(command, checkpoint))
        command["dispatch_id"] = "d1"
        with self.assertRaisesRegex(ValueError, "RECOVERY_DISPATCH_REUSED"):
            validate_recovery_command(command, checkpoint)

    def test_recovery_accepts_completed_tool_results_only_for_completed_invocations(self):
        checkpoint = InMemoryCheckpoint()
        saved = {
            "run_id": "r1", "dispatch_id": "d1", "attempt": 1,
            "current_node": "tool_wait", "deadline_at": "2030-01-01T00:00:00Z",
            "budget_revision": 2, "completed_invocation_ids": [],
        }
        version = checkpoint.save("r1:d1", saved)
        command = {
            "run_id": "r1", "dispatch_id": "d2", "attempt": 2,
            "deadline_at": "2030-01-01T00:00:00Z",
            "recovery_context": {
                "previous_dispatch_id": "d1", "previous_attempt": 1,
                "checkpoint_version": version, "checkpoint_digest": checkpoint_digest(saved),
                "budget_revision": 2, "completed_invocation_ids": [],
                "completed_tool_results": [{"invocation_id": "inv-1"}],
            },
        }
        with self.assertRaisesRegex(ValueError, "RECOVERY_TOOL_RESULTS_MISMATCH"):
            validate_recovery_command(command, checkpoint)

    def test_recovery_uses_immutable_boundary_when_live_checkpoint_advances(self):
        checkpoint = InMemoryCheckpoint()
        saved = {
            "run_id": "r1", "dispatch_id": "d1", "attempt": 1,
            "current_node": "tool_wait", "deadline_at": "2030-01-01T00:00:00Z",
            "budget_revision": 2, "completed_invocation_ids": [],
        }
        version = checkpoint.save("r1:d1", saved)
        checkpoint.save("r1:d1:recovery", saved)
        checkpoint.save("r1:d1", {**saved, "current_node": "execution"}, version)
        command = {
            "run_id": "r1", "dispatch_id": "d2", "attempt": 2,
            "deadline_at": "2030-01-01T00:00:00Z",
            "recovery_context": {
                "previous_dispatch_id": "d1", "previous_attempt": 1,
                "checkpoint_version": 1, "checkpoint_digest": checkpoint_digest(saved),
                "budget_revision": 2, "completed_invocation_ids": [],
            },
        }
        self.assertEqual(saved, validate_recovery_command(command, checkpoint))

    def test_recovery_accepts_java_iso_deadline_after_epoch_timestamp_rounding(self):
        checkpoint = InMemoryCheckpoint()
        saved = {
            "run_id": "r1", "dispatch_id": "d1", "attempt": 1,
            "current_node": "tool_wait", "deadline_at": "2026-08-01T09:08:01.613745600Z",
            "budget_revision": 2, "completed_invocation_ids": [],
        }
        version = checkpoint.save("r1:d1", saved)
        command = {
            "run_id": "r1", "dispatch_id": "d2", "attempt": 2,
            "deadline_at": "2026-08-01T09:08:01.613746Z",
            "recovery_context": {
                "previous_dispatch_id": "d1", "previous_attempt": 1,
                "checkpoint_version": version, "checkpoint_digest": checkpoint_digest(saved),
                "budget_revision": 2, "completed_invocation_ids": [],
            },
        }
        self.assertEqual(saved, validate_recovery_command(command, checkpoint))

    def test_recovered_tool_result_is_reinjected_without_republishing_proposal(self):
        saved = {
            "run_id": "r1", "dispatch_id": "d1", "attempt": 1,
            "current_node": "tool_wait", "deadline_at": "2030-01-01T00:00:00Z",
            "budget_revision": 2, "completed_invocation_ids": [],
        }
        version = runtime_server._checkpoint.save("r1:d1", saved)
        command = {
            "run_id": "r1", "dispatch_id": "d2", "attempt": 2,
            "deadline_at": "2030-01-01T00:00:00Z",
            "recovery_context": {
                "previous_dispatch_id": "d1", "previous_attempt": 1,
                "checkpoint_version": version, "checkpoint_digest": checkpoint_digest(saved),
                "budget_revision": 2, "completed_invocation_ids": ["inv-1"],
                "completed_tool_results": [{
                    "proposal_id": "p1", "invocation_id": "inv-1", "request_hash": "sha256:p1",
                    "status": "succeeded", "rows": [],
                }],
            },
        }
        captured = []
        execution = SimpleNamespace(
            proposals=[],
            route=SimpleNamespace(missing_slots=()),
            model_attempts=[],
            eval=SimpleNamespace(result="pass", reason="OK"),
            answer="recovered",
            budget_mode="normal",
            budget_actions={},
            usage=SimpleNamespace(tokens=1, cost_cny=0.0, model_calls=1),
            workflow={},
            memory_candidates=[],
        )
        with patch.object(runtime_server, "run_deterministic", side_effect=lambda value, _store: captured.append(value) or execution), patch.object(
            runtime_server, "emit"
        ):
            runtime_server.execute(command)
        self.assertEqual([], execution.proposals)
        self.assertEqual("inv-1", captured[0]["authorized_context"]["tool_results"][0]["invocation_id"])

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

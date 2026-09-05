import json
import sys
from decimal import Decimal
from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace
from unittest import TestCase

sys.path.append(str(Path(__file__).parents[1]))

from model_provider import ModelProvider, ModelProviderError, ModelRequest, ModelResponse, ModelRouter
from paid_execution import PaidExecutionError, PaidExecutionSession, PaidExecutionSettings


class _Provider(ModelProvider):
    def __init__(self, code, response=None, error=None):
        self.provider_code = code
        self.response = response or ModelResponse("ok", 3, 2, "provider-id")
        self.error = error
        self.calls = 0

    def complete(self, model_name, request):
        self.calls += 1
        if self.error:
            raise self.error
        return self.response


class PaidExecutionTests(TestCase):
    def test_disabled_session_rejects_before_any_recording(self):
        session = PaidExecutionSession(PaidExecutionSettings())

        with self.assertRaisesRegex(PaidExecutionError, "explicit execution switch") as raised:
            session.begin_scenario("rag")

        self.assertEqual("PAID_EXECUTION_DISABLED", raised.exception.code)
        self.assertEqual([], session.records)

    def test_scenario_limit_and_duplicate_are_fail_closed(self):
        session = PaidExecutionSession(
            PaidExecutionSettings(enabled=True, max_scenarios=1)
        )
        session.begin_scenario("rag")

        with self.assertRaisesRegex(PaidExecutionError, "already been started") as duplicate:
            session.begin_scenario("rag")
        self.assertEqual("PAID_SCENARIO_DUPLICATE", duplicate.exception.code)

        with self.assertRaisesRegex(PaidExecutionError, "scenario limit") as limited:
            session.begin_scenario("sql-agent")
        self.assertEqual("PAID_SCENARIO_LIMIT_EXCEEDED", limited.exception.code)

    def test_cost_budget_and_evidence_are_redacted(self):
        with TemporaryDirectory() as directory:
            evidence = Path(directory) / "paid-evidence.json"
            session = PaidExecutionSession(
                PaidExecutionSettings(
                    enabled=True,
                    max_total_cost_cny=Decimal("0.01"),
                    evidence_file=str(evidence),
                )
            )
            session.begin_scenario("food-log")
            session.before_model_call("composer", "high", "secret prompt", 64)
            attempt = SimpleNamespace(
                scene="composer",
                provider_code="cloud_primary",
                model_name="deepseek-ai/DeepSeek-V4-Flash",
                status="success",
                error_code=None,
                input_tokens=3,
                output_tokens=2,
                total_tokens=5,
                cost_cny=Decimal("0.001"),
                latency_ms=12,
                price_version="test-v1",
                provider_request_id="provider-secret-id",
            )
            session.record_model_attempts([attempt])
            session.write_evidence()

            payload = json.loads(evidence.read_text(encoding="utf-8"))
            serialized = json.dumps(payload, ensure_ascii=False)
            self.assertIn("sha256:", serialized)
            self.assertNotIn("secret prompt", serialized)
            self.assertNotIn("provider-secret-id", serialized)
            self.assertEqual("0.001", payload["observed_total_cost_cny"])

            with self.assertRaisesRegex(PaidExecutionError, "budget exceeded") as raised:
                session.record_model_attempts([SimpleNamespace(cost_cny=Decimal("0.01"))])
            self.assertEqual("PAID_BUDGET_EXCEEDED", raised.exception.code)

    def test_paid_mode_disables_fallback_and_rejects_deterministic_provider(self):
        primary = _Provider(
            "primary", error=ModelProviderError("MODEL_TIMEOUT", "timeout", True)
        )
        backup = _Provider("backup")
        providers = {"primary": primary, "backup": backup}
        router = ModelRouter(
            {
                "FOODMATE_PAID_EXECUTION_ENABLED": "true",
                "FOODMATE_PAID_NO_RETRY": "true",
                "FOODMATE_MODEL_FALLBACK_ENABLED": "true",
                "FOODMATE_MODEL_TIER_STANDARD": "primary:model",
                "FOODMATE_MODEL_TIER_ECONOMY": "backup:model",
                "FOODMATE_MODEL_FALLBACK_STANDARD": "economy",
            },
            providers.__getitem__,
        )

        with self.assertRaisesRegex(ModelProviderError, "all configured model providers failed") as raised:
            router.invoke(ModelRequest("composer", "hello"), "standard")

        self.assertEqual("MODEL_PROVIDER_UNAVAILABLE", raised.exception.code)
        self.assertEqual(["MODEL_TIMEOUT"], [item.error_code for item in raised.exception.attempts])
        self.assertEqual(1, primary.calls)
        self.assertEqual(0, backup.calls)

        deterministic = ModelRouter(
            {
                "FOODMATE_PAID_EXECUTION_ENABLED": "true",
                "FOODMATE_MODEL_TIER_STANDARD": "deterministic:local",
            }
        )
        with self.assertRaisesRegex(ModelProviderError, "cloud provider") as deterministic_error:
            deterministic.invoke(ModelRequest("composer", "hello"), "standard")
        self.assertEqual("PAID_CLOUD_PROVIDER_REQUIRED", deterministic_error.exception.code)

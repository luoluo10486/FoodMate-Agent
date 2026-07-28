import sys
from pathlib import Path
from unittest import TestCase

sys.path.append(str(Path(__file__).parents[1]))
from model_provider import ModelProvider, ModelProviderError, ModelRequest, ModelResponse, ModelRouter


class FakeProvider(ModelProvider):
    def __init__(self, provider_code, response=None, error=None):
        self.provider_code = provider_code
        self.response = response
        self.error = error
        self.calls = []

    def complete(self, model_name, request):
        self.calls.append((model_name, request.scene))
        if self.error:
            raise self.error
        return self.response or ModelResponse("ok", 3, 2, "provider-request-1")


class ModelRouterTests(TestCase):
    def test_uses_configured_logical_alias_and_produces_contract_usage(self):
        provider = FakeProvider("primary")
        router = ModelRouter({"FOODMATE_MODEL_TIER_STANDARD": "primary:chat-v1"}, lambda _: provider)

        response, attempts = router.invoke(ModelRequest("composer", "hello"), "standard")

        self.assertEqual("ok", response.content)
        self.assertEqual([("chat-v1", "composer")], provider.calls)
        payload = attempts[0].event_payload()
        self.assertEqual("success", payload["status"])
        self.assertEqual(5, payload["usage"]["total_tokens"])
        self.assertEqual("CNY", payload["cost"]["currency"])

    def test_retryable_error_uses_configured_fallback_only(self):
        first = FakeProvider("first", error=ModelProviderError("MODEL_TIMEOUT", "timeout", True))
        second = FakeProvider("second")
        providers = {"first": first, "second": second}
        router = ModelRouter({
            "FOODMATE_MODEL_TIER_STANDARD": "first:main",
            "FOODMATE_MODEL_TIER_ECONOMY": "second:backup",
            "FOODMATE_MODEL_FALLBACK_ENABLED": "true",
            "FOODMATE_MODEL_FALLBACK_STANDARD": "economy",
        }, providers.__getitem__)

        _, attempts = router.invoke(ModelRequest("composer", "hello"), "standard", router.fallback_tiers_for("standard"))

        self.assertEqual(["timeout", "success"], [item.status for item in attempts])
        self.assertEqual([("main", "composer")], first.calls)
        self.assertEqual([("backup", "composer")], second.calls)

    def test_non_retryable_error_does_not_switch_provider(self):
        first = FakeProvider("first", error=ModelProviderError("MODEL_PROVIDER_REJECTED", "rejected"))
        second = FakeProvider("second")
        providers = {"first": first, "second": second}
        router = ModelRouter({
            "FOODMATE_MODEL_TIER_STANDARD": "first:main",
            "FOODMATE_MODEL_TIER_ECONOMY": "second:backup",
            "FOODMATE_MODEL_FALLBACK_ENABLED": "true",
            "FOODMATE_MODEL_FALLBACK_STANDARD": "economy",
        }, providers.__getitem__)

        with self.assertRaisesRegex(ModelProviderError, "rejected") as raised:
            router.invoke(ModelRequest("composer", "hello"), "standard", router.fallback_tiers_for("standard"))
        self.assertEqual(1, len(raised.exception.attempts))
        self.assertEqual([], second.calls)

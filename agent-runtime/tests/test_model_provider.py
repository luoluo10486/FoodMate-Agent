import sys
import json
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from decimal import Decimal
from pathlib import Path
from unittest import TestCase

sys.path.append(str(Path(__file__).parents[1]))
from model_provider import (ModelProvider, ModelProviderError, ModelRequest,
                            ModelResponse, ModelRouter, OpenAICompatibleModelProvider)


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
        # 未配置供应商价格时成本必须保持未知，不能伪造为 0 CNY。
        self.assertIsNone(payload["cost"]["currency"])

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


class _ProviderHandler(BaseHTTPRequestHandler):
    response_mode = "success"
    requests = []

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        body = json.loads(self.rfile.read(length))
        self.__class__.requests.append((self.path, body, self.headers.get("Authorization")))
        if self.__class__.response_mode == "rate_limit":
            self.send_response(429)
            self.end_headers()
            return
        if self.__class__.response_mode == "malformed":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(b'{"choices": []}')
            return
        payload = {
            "id": "cloud-request-1",
            "choices": [{"message": {"content": "cloud answer"}}],
            "usage": {"prompt_tokens": 7, "completion_tokens": 5},
        }
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps(payload).encode())

    def log_message(self, *_args):
        pass


class ProviderContractTests(TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), _ProviderHandler)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        cls.base_url = f"http://127.0.0.1:{cls.server.server_address[1]}"

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.thread.join(timeout=2)

    def setUp(self):
        _ProviderHandler.response_mode = "success"
        _ProviderHandler.requests.clear()

    def test_openai_compatible_contract_sends_auth_and_parses_usage(self):
        provider = OpenAICompatibleModelProvider("cloud_primary", self.base_url, "test-key")

        response = provider.complete("qwen-plus", ModelRequest("composer", "hello", 64, 0.1))

        self.assertEqual("cloud answer", response.content)
        self.assertEqual((7, 5), (response.input_tokens, response.output_tokens))
        self.assertEqual("cloud-request-1", response.provider_request_id)
        path, body, authorization = _ProviderHandler.requests[0]
        self.assertEqual("/chat/completions", path)
        self.assertEqual("qwen-plus", body["model"])
        self.assertEqual("Bearer test-key", authorization)

    def test_rate_limit_switches_to_backup_http_provider(self):
        _ProviderHandler.response_mode = "rate_limit"
        providers = {
            "cloud_primary": OpenAICompatibleModelProvider("cloud_primary", self.base_url, "primary-key"),
            "cloud_backup": OpenAICompatibleModelProvider("cloud_backup", self.base_url, "backup-key"),
        }
        # 第一次调用返回 429，第二个 provider 在同一个测试服务中恢复成功。
        original_mode = _ProviderHandler.response_mode
        calls = {"count": 0}

        def factory(code):
            calls["count"] += 1
            if calls["count"] == 2:
                _ProviderHandler.response_mode = "success"
            return providers[code]

        router = ModelRouter({
            "FOODMATE_MODEL_TIER_STANDARD": "cloud_primary:main",
            "FOODMATE_MODEL_FALLBACK_ENABLED": "true",
            "FOODMATE_MODEL_FALLBACK_STANDARD": "economy",
            "FOODMATE_MODEL_TIER_ECONOMY": "cloud_backup:backup",
            "FOODMATE_MODEL_PROVIDER_CLOUD_PRIMARY_INPUT_CNY_PER_MILLION_TOKENS": "2",
            "FOODMATE_MODEL_PROVIDER_CLOUD_PRIMARY_OUTPUT_CNY_PER_MILLION_TOKENS": "4",
            "FOODMATE_MODEL_PROVIDER_CLOUD_BACKUP_INPUT_CNY_PER_MILLION_TOKENS": "2",
            "FOODMATE_MODEL_PROVIDER_CLOUD_BACKUP_OUTPUT_CNY_PER_MILLION_TOKENS": "4",
        }, factory)

        response, attempts = router.invoke(ModelRequest("composer", "hello"), "standard", ("economy",))

        self.assertEqual("cloud answer", response.content)
        self.assertEqual(["failed", "success"], [attempt.status for attempt in attempts])
        self.assertEqual("MODEL_RATE_LIMIT", attempts[0].error_code)
        self.assertEqual(12, attempts[1].total_tokens)
        self.assertEqual(Decimal("0.000034"), attempts[1].cost_cny)
        _ProviderHandler.response_mode = original_mode

    def test_full_chat_completion_url_is_not_appended_twice(self):
        provider = OpenAICompatibleModelProvider(
            "cloud_primary", self.base_url + "/chat/completions", "test-key"
        )

        response = provider.complete("qwen-plus", ModelRequest("composer", "hello"))

        self.assertEqual("cloud answer", response.content)
        self.assertEqual("/chat/completions", _ProviderHandler.requests[0][0])

    def test_malformed_cloud_response_is_not_fallback_retryable(self):
        _ProviderHandler.response_mode = "malformed"
        provider = OpenAICompatibleModelProvider("cloud_primary", self.base_url, "test-key")

        with self.assertRaisesRegex(ModelProviderError, "schema is invalid") as raised:
            provider.complete("qwen-plus", ModelRequest("composer", "hello"))

        self.assertEqual("MODEL_PROVIDER_INVALID_RESPONSE", raised.exception.code)
        self.assertFalse(raised.exception.retryable)

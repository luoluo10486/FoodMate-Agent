"""Provider-neutral model invocation and deterministic routing for M1-4.

Cloud providers use the OpenAI-compatible Chat Completions wire format. A
deployment can register multiple provider IDs without putting a provider name,
endpoint, key, or price in source code.
"""

from __future__ import annotations

import json
import os
import time
import urllib.error
import urllib.request
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
from typing import Callable


RETRYABLE_ERROR_CODES = frozenset({"MODEL_TIMEOUT", "MODEL_RATE_LIMIT", "MODEL_PROVIDER_UNAVAILABLE"})


class ModelProviderError(RuntimeError):
    """结构化供应商错误；只有白名单中的可重试错误才允许进入 fallback。"""

    def __init__(self, code: str, message: str, retryable: bool = False):
        super().__init__(message)
        self.code = code
        self.retryable = retryable
        self.attempts: list[ProviderAttempt] = []


@dataclass(frozen=True)
class ModelRequest:
    scene: str
    prompt: str
    max_output_tokens: int = 512
    temperature: float = 0.0


@dataclass(frozen=True)
class ModelResponse:
    content: str
    input_tokens: int
    output_tokens: int
    provider_request_id: str | None = None


@dataclass(frozen=True)
class ProviderAttempt:
    model_call_id: str
    provider_attempt_id: str
    scene: str
    provider_code: str
    model_name: str
    status: str
    input_tokens: int | None
    output_tokens: int | None
    total_tokens: int | None
    cost_cny: Decimal | None
    latency_ms: int
    started_at: str
    finished_at: str
    provider_request_id: str | None = None
    error_code: str | None = None
    price_version: str = "unconfigured"

    def event_payload(self) -> dict[str, object]:
        """Build exactly the existing V1 run.model_usage payload."""
        return {
            "model_call_id": self.model_call_id,
            "provider_request_id": self.provider_request_id,
            "provider_attempt_id": self.provider_attempt_id,
            "scene": self.scene,
            "provider_code": self.provider_code,
            "model_name": self.model_name,
            "status": self.status,
            "usage": {
                "input_tokens": self.input_tokens,
                "output_tokens": self.output_tokens,
                "total_tokens": self.total_tokens,
            },
            "latency_ms": self.latency_ms,
            "cost": {
                "amount": None if self.cost_cny is None else format(self.cost_cny, "f"),
                "currency": None if self.cost_cny is None else "CNY",
            },
            "started_at": self.started_at,
            "finished_at": self.finished_at,
        }


class ModelProvider:
    provider_code: str

    def complete(self, model_name: str, request: ModelRequest) -> ModelResponse:
        raise NotImplementedError


class DeterministicModelProvider(ModelProvider):
    """本地测试 provider，不发网络请求，不能宣称为真实模型回答。"""

    provider_code = "deterministic"

    def complete(self, model_name: str, request: ModelRequest) -> ModelResponse:
        # Prompt contains only Java-authorized context assembled by agent_core.
        content = request.prompt
        if request.scene == "eval":
            # 本地 Judge 只返回结构化规则结果，避免把本地 stub 伪装成云端 LLM。
            content = '{"passed": true, "score": 1.0, "reason": "LOCAL_DETERMINISTIC_JUDGE"}'
        return ModelResponse(
            content=content,
            input_tokens=max(1, len(request.prompt)),
            output_tokens=max(1, len(content)),
        )


class OpenAICompatibleModelProvider(ModelProvider):
    """多个兼容云端点共用的协议适配器，端点与密钥只从环境变量读取。"""

    def __init__(self, provider_code: str, base_url: str, api_key: str, timeout_seconds: int = 30):
        if not base_url or not api_key:
            raise ModelProviderError("MODEL_PROVIDER_UNAVAILABLE", "cloud provider is not configured")
        self.provider_code = provider_code
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key
        self.timeout_seconds = timeout_seconds

    def complete(self, model_name: str, request: ModelRequest) -> ModelResponse:
        body = json.dumps({
            "model": model_name,
            "messages": [{"role": "user", "content": request.prompt}],
            "temperature": request.temperature,
            "max_tokens": request.max_output_tokens,
        }).encode("utf-8")
        http_request = urllib.request.Request(
            self._completion_url(), data=body, method="POST",
            headers={"Content-Type": "application/json", "Authorization": "Bearer " + self.api_key},
        )
        try:
            with urllib.request.urlopen(http_request, timeout=self.timeout_seconds) as response:
                payload = json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as error:
            if error.code == 429:
                raise ModelProviderError("MODEL_RATE_LIMIT", "provider rate limit", True) from error
            if error.code >= 500:
                raise ModelProviderError("MODEL_PROVIDER_UNAVAILABLE", "provider service error", True) from error
            raise ModelProviderError("MODEL_PROVIDER_REJECTED", "provider rejected request") from error
        except (urllib.error.URLError, TimeoutError) as error:
            raise ModelProviderError("MODEL_TIMEOUT", "provider request timed out", True) from error
        try:
            usage = payload.get("usage") or {}
            content = str(payload["choices"][0]["message"]["content"])
            input_tokens = int(usage.get("prompt_tokens", 0))
            output_tokens = int(usage.get("completion_tokens", 0))
            return ModelResponse(content, input_tokens, output_tokens, payload.get("id"))
        except (KeyError, IndexError, TypeError, ValueError) as error:
            raise ModelProviderError("MODEL_PROVIDER_INVALID_RESPONSE", "provider response schema is invalid") from error

    def _completion_url(self) -> str:
        """兼容配置基地址或完整的 OpenAI Chat Completions 地址。"""
        if self.base_url.endswith("/chat/completions"):
            return self.base_url
        return self.base_url + "/chat/completions"


@dataclass(frozen=True)
class ModelAlias:
    provider_code: str
    model_name: str

    @classmethod
    def parse(cls, value: str) -> "ModelAlias":
        provider_code, separator, model_name = value.partition(":")
        if not separator or not provider_code or not model_name:
            raise ModelProviderError("MODEL_ALIAS_INVALID", "model alias must use provider:model")
        return cls(provider_code, model_name)


class ModelRouter:
    """确定性选择逻辑别名；用户消息不得影响供应商或端点选择。"""

    def __init__(self, environment: dict[str, str] | None = None, provider_factory: Callable[[str], ModelProvider] | None = None):
        self.environment = environment if environment is not None else dict(os.environ)
        self.provider_factory = provider_factory or self._provider_from_environment

    def invoke(self, request: ModelRequest, tier: str, fallback_tiers: tuple[str, ...] = ()) -> tuple[ModelResponse, list[ProviderAttempt]]:
        candidates = (tier,) + (fallback_tiers if self._enabled("FOODMATE_MODEL_FALLBACK_ENABLED", True) else ())
        model_call_id = "mdl_" + uuid.uuid4().hex
        attempts: list[ProviderAttempt] = []
        for candidate in candidates:
            alias = self._alias(candidate)
            started = datetime.now(timezone.utc)
            begin = time.monotonic()
            try:
                response = self.provider_factory(alias.provider_code).complete(alias.model_name, request)
                attempts.append(self._attempt(model_call_id, request.scene, alias, "success", started, begin, response=response))
                return response, attempts
            except ModelProviderError as error:
                attempts.append(self._attempt(model_call_id, request.scene, alias, self._status(error), started, begin, error=error))
                if not error.retryable or error.code not in RETRYABLE_ERROR_CODES:
                    error.attempts = attempts
                    raise
        error = ModelProviderError("MODEL_PROVIDER_UNAVAILABLE", "all configured model providers failed", True)
        error.attempts = attempts
        raise error

    def tier_for(self, scene: str, complexity: str, risk_level: str, budget_mode: str) -> str:
        if scene == "eval":
            return "eval"
        if budget_mode in {"economy", "partial"} and risk_level == "low":
            return "economy"
        return "high" if complexity == "complex" or risk_level == "high" else "standard"

    def fallback_tiers_for(self, tier: str) -> tuple[str, ...]:
        raw = self.environment.get("FOODMATE_MODEL_FALLBACK_" + tier.upper(), "")
        return tuple(item.strip().lower() for item in raw.split(",") if item.strip() and item.strip().lower() != tier)

    def _alias(self, tier: str) -> ModelAlias:
        default = "deterministic:local" if tier in {"standard", "economy", "high", "eval"} else ""
        value = self.environment.get("FOODMATE_MODEL_TIER_" + tier.upper(), default).strip()
        if not value:
            raise ModelProviderError("MODEL_PROVIDER_UNAVAILABLE", "model tier is not configured")
        return ModelAlias.parse(value)

    def _provider_from_environment(self, provider_code: str) -> ModelProvider:
        if provider_code == "deterministic":
            return DeterministicModelProvider()
        prefix = "FOODMATE_MODEL_PROVIDER_" + provider_code.upper().replace("-", "_") + "_"
        return OpenAICompatibleModelProvider(
            provider_code,
            self.environment.get(prefix + "BASE_URL", ""),
            self.environment.get(prefix + "API_KEY", ""),
            int(self.environment.get(prefix + "TIMEOUT_SECONDS", "30")),
        )

    def _attempt(self, model_call_id: str, scene: str, alias: ModelAlias, status: str, started: datetime, begin: float, response: ModelResponse | None = None, error: ModelProviderError | None = None) -> ProviderAttempt:
        finished = datetime.now(timezone.utc)
        input_tokens = response.input_tokens if response else None
        output_tokens = response.output_tokens if response else None
        total_tokens = input_tokens + output_tokens if input_tokens is not None and output_tokens is not None else None
        return ProviderAttempt(
            model_call_id=model_call_id, provider_attempt_id="mat_" + uuid.uuid4().hex,
            scene=scene, provider_code=alias.provider_code, model_name=alias.model_name, status=status,
            input_tokens=input_tokens, output_tokens=output_tokens, total_tokens=total_tokens,
            cost_cny=self._cost(alias.provider_code, input_tokens, output_tokens) if response else None,
            latency_ms=max(0, int((time.monotonic() - begin) * 1000)),
            started_at=started.isoformat().replace("+00:00", "Z"), finished_at=finished.isoformat().replace("+00:00", "Z"),
            provider_request_id=response.provider_request_id if response else None,
            error_code=error.code if error else None,
            price_version=self.environment.get("FOODMATE_MODEL_PRICE_VERSION", "unconfigured"),
        )

    def _cost(self, provider_code: str, input_tokens: int, output_tokens: int) -> Decimal | None:
        prefix = "FOODMATE_MODEL_PROVIDER_" + provider_code.upper().replace("-", "_") + "_"
        # 价格属于可选配置；留空时保留未知成本，不能让成功的模型调用因 Decimal('') 失败。
        in_raw = self.environment.get(prefix + "INPUT_CNY_PER_MILLION_TOKENS", "").strip()
        out_raw = self.environment.get(prefix + "OUTPUT_CNY_PER_MILLION_TOKENS", "").strip()
        if not in_raw or not out_raw:
            return None
        try:
            in_price = Decimal(in_raw)
            out_price = Decimal(out_raw)
        except InvalidOperation:
            return None
        return (Decimal(input_tokens) * in_price + Decimal(output_tokens) * out_price) / Decimal(1_000_000)

    def _enabled(self, key: str, default: bool) -> bool:
        return self.environment.get(key, str(default)).lower() == "true"

    @staticmethod
    def _status(error: ModelProviderError) -> str:
        if error.code == "MODEL_TIMEOUT":
            return "timeout"
        return "failed"

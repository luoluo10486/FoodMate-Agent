"""Provider-neutral model invocation and deterministic routing for M1-4.

Cloud providers use the OpenAI-compatible Chat Completions wire format. A
deployment can register multiple provider IDs without putting a provider name,
endpoint, key, or price in source code.
"""

from __future__ import annotations

import json
import math
import os
import time
import urllib.error
import urllib.request
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
from typing import Callable
from urllib.parse import urlsplit


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
    response_format: dict[str, str] | None = None
    extra_body: dict[str, object] | None = None
    # Run 的绝对截止时间由 Java 固化，Provider 只能缩短等待时间，不能延长它。
    deadline_at: str | None = None
    timeout_seconds: float | None = None


@dataclass(frozen=True)
class ModelResponse:
    content: str
    input_tokens: int
    output_tokens: int
    provider_request_id: str | None = None
    cached_input_tokens: int = 0


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
    cached_input_tokens: int | None = None
    route_version: str | None = None
    budget_policy_version: str | None = None

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
                "cached_input_tokens": self.cached_input_tokens,
                "output_tokens": self.output_tokens,
                "total_tokens": self.total_tokens,
            },
            "latency_ms": self.latency_ms,
            "cost": {
                "amount": None if self.cost_cny is None else format(self.cost_cny, "f"),
                "currency": None if self.cost_cny is None else "CNY",
            },
            "price_version": self.price_version,
            "route_version": self.route_version,
            "budget_policy_version": self.budget_policy_version,
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

    def __init__(
        self,
        provider_code: str,
        base_url: str,
        api_key: str,
        timeout_seconds: float | str = 30,
    ):
        if not base_url or not api_key:
            raise ModelProviderError("MODEL_PROVIDER_UNAVAILABLE", "cloud provider is not configured")
        endpoint = urlsplit(base_url.strip())
        if (
            endpoint.scheme not in {"http", "https"}
            or not endpoint.hostname
            or endpoint.username is not None
            or endpoint.password is not None
            or endpoint.query
            or endpoint.fragment
        ):
            raise ModelProviderError(
                "MODEL_PROVIDER_URL_INVALID",
                "cloud provider endpoint must be an HTTP or HTTPS URL without credentials or query parameters",
            )
        self.provider_code = provider_code
        self.base_url = base_url.strip().rstrip("/")
        self.api_key = api_key
        self.timeout_seconds = _positive_timeout(timeout_seconds)

    def complete(self, model_name: str, request: ModelRequest) -> ModelResponse:
        body = {
            "model": model_name,
            "messages": [{"role": "user", "content": request.prompt}],
            "temperature": request.temperature,
            "max_tokens": request.max_output_tokens,
        }
        if request.response_format is not None:
            body["response_format"] = request.response_format
        if request.extra_body:
            body.update(request.extra_body)
        body = json.dumps(body).encode("utf-8")
        http_request = urllib.request.Request(
            self._completion_url(), data=body, method="POST",
            headers={"Content-Type": "application/json", "Authorization": "Bearer " + self.api_key},
        )
        try:
            with urllib.request.urlopen(http_request, timeout=self._request_timeout(request)) as response:
                payload = json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as error:
            if error.code == 429:
                raise ModelProviderError("MODEL_RATE_LIMIT", "provider rate limit", True) from error
            if error.code >= 500:
                raise ModelProviderError("MODEL_PROVIDER_UNAVAILABLE", "provider service error", True) from error
            raise ModelProviderError("MODEL_PROVIDER_REJECTED", "provider rejected request") from error
        except (urllib.error.URLError, TimeoutError) as error:
            raise ModelProviderError("MODEL_TIMEOUT", "provider request timed out", True) from error
        except (UnicodeDecodeError, json.JSONDecodeError) as error:
            raise ModelProviderError(
                "MODEL_PROVIDER_INVALID_RESPONSE", "provider response schema is invalid"
            ) from error
        try:
            if not isinstance(payload, dict):
                raise TypeError("provider response must be an object")
            usage = payload.get("usage")
            if usage is None:
                usage = {}
            if not isinstance(usage, dict):
                raise TypeError("provider usage must be an object")
            content = payload["choices"][0]["message"]["content"]
            if not isinstance(content, str):
                raise TypeError("provider content must be text")
            input_tokens = _token_count(usage.get("prompt_tokens", 0), "prompt_tokens")
            output_tokens = _token_count(usage.get("completion_tokens", 0), "completion_tokens")
            prompt_details = usage.get("prompt_tokens_details")
            if prompt_details is None:
                prompt_details = {}
            if not isinstance(prompt_details, dict):
                raise TypeError("provider prompt token details must be an object")
            cached_input_tokens = _token_count(
                prompt_details.get("cached_tokens", 0), "cached_tokens"
            )
            if cached_input_tokens > input_tokens:
                raise ValueError("cached tokens exceed input tokens")
            provider_request_id = payload.get("id")
            if provider_request_id is not None and (
                not isinstance(provider_request_id, str)
                or not provider_request_id.strip()
                or len(provider_request_id) > 256
            ):
                raise ValueError("provider response id is invalid")
            return ModelResponse(
                content,
                input_tokens,
                output_tokens,
                provider_request_id.strip() if provider_request_id is not None else None,
                cached_input_tokens,
            )
        except (AttributeError, KeyError, IndexError, TypeError, ValueError) as error:
            raise ModelProviderError("MODEL_PROVIDER_INVALID_RESPONSE", "provider response schema is invalid") from error

    def _request_timeout(self, request: ModelRequest) -> float:
        """Return the smaller of provider, node and Run-level deadlines."""
        configured = request.timeout_seconds
        timeout = self.timeout_seconds if configured is None else _positive_timeout(configured)
        if not request.deadline_at:
            return timeout
        try:
            deadline = datetime.fromisoformat(request.deadline_at.replace("Z", "+00:00"))
            remaining = (deadline - datetime.now(timezone.utc)).total_seconds()
        except (TypeError, ValueError):
            return timeout
        if remaining <= 0:
            raise ModelProviderError("RUNTIME_DEADLINE_EXCEEDED", "run deadline has expired")
        return max(0.1, min(timeout, remaining))

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

    def invoke(
        self,
        request: ModelRequest,
        tier: str,
        fallback_tiers: tuple[str, ...] = (),
        governed_route: dict[str, object] | None = None,
    ) -> tuple[ModelResponse, list[ProviderAttempt]]:
        candidates = self._candidates(tier, fallback_tiers, governed_route)
        model_call_id = "mdl_" + uuid.uuid4().hex
        attempts: list[ProviderAttempt] = []
        for (
            alias,
            route_version,
            budget_policy_version,
            price_version,
            input_price_per_million,
            output_price_per_million,
        ) in candidates:
            if self._paid_execution_enabled() and self._paid_requires_cloud() and alias.provider_code == "deterministic":
                raise ModelProviderError(
                    "PAID_CLOUD_PROVIDER_REQUIRED",
                    "paid execution requires a configured cloud provider",
                )
            self._require_audited_price(
                alias,
                input_price_per_million,
                output_price_per_million,
                price_version,
            )
            started = datetime.now(timezone.utc)
            begin = time.monotonic()
            try:
                response = self.provider_factory(alias.provider_code).complete(alias.model_name, request)
                attempts.append(
                    self._attempt(
                        model_call_id,
                        request.scene,
                        alias,
                        "success",
                        started,
                        begin,
                        response=response,
                        price_version_override=price_version,
                        input_price_per_million=input_price_per_million,
                        output_price_per_million=output_price_per_million,
                        route_version=route_version,
                        budget_policy_version=budget_policy_version,
                    )
                )
                return response, attempts
            except ModelProviderError as error:
                attempts.append(
                    self._attempt(
                        model_call_id,
                        request.scene,
                        alias,
                        self._status(error),
                        started,
                        begin,
                        error=error,
                        price_version_override=price_version,
                        input_price_per_million=input_price_per_million,
                        output_price_per_million=output_price_per_million,
                        route_version=route_version,
                        budget_policy_version=budget_policy_version,
                    )
                )
                if not error.retryable or error.code not in RETRYABLE_ERROR_CODES:
                    error.attempts = attempts
                    raise
        error = ModelProviderError("MODEL_PROVIDER_UNAVAILABLE", "all configured model providers failed", True)
        error.attempts = attempts
        raise error

    def _candidates(
        self,
        tier: str,
        fallback_tiers: tuple[str, ...],
        governed_route: dict[str, object] | None,
    ) -> tuple[tuple[ModelAlias, str | None, str | None, str | None, Decimal | None, Decimal | None], ...]:
        if governed_route is not None:
            provider = str(governed_route.get("provider_code") or "").strip()
            model = str(governed_route.get("model_name") or "").strip()
            if not provider or not model:
                raise ModelProviderError(
                    "MODEL_ROUTE_UNAVAILABLE", "governed model route is incomplete"
                )
            route_version = str(governed_route.get("route_version") or "").strip() or None
            budget_version = (
                str(governed_route.get("budget_policy_version") or "").strip() or None
            )
            price_version = str(governed_route.get("price_version") or "").strip() or None
            input_price = _decimal_or_none(governed_route.get("input_price_per_million"))
            output_price = _decimal_or_none(governed_route.get("output_price_per_million"))
            candidates = [
                (
                    ModelAlias(provider, model),
                    route_version,
                    budget_version,
                    price_version,
                    input_price,
                    output_price,
                )
            ]
            fallback_provider = str(governed_route.get("fallback_provider_code") or "").strip()
            fallback_model = str(governed_route.get("fallback_model_name") or "").strip()
            if (
                fallback_provider
                and fallback_model
                and self._fallback_enabled()
            ):
                candidates.append(
                    (
                        ModelAlias(fallback_provider, fallback_model),
                        route_version,
                        budget_version,
                        price_version,
                        input_price,
                        output_price,
                    )
                )
            return tuple(candidates)
        tiers = (tier,) + (fallback_tiers if self._fallback_enabled() else ())
        return tuple((self._alias(candidate), None, None, None, None, None) for candidate in tiers)

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
            self.environment.get(prefix + "TIMEOUT_SECONDS", "30"),
        )

    def _attempt(
        self,
        model_call_id: str,
        scene: str,
        alias: ModelAlias,
        status: str,
        started: datetime,
        begin: float,
        response: ModelResponse | None = None,
        error: ModelProviderError | None = None,
        price_version_override: str | None = None,
        input_price_per_million: Decimal | None = None,
        output_price_per_million: Decimal | None = None,
        route_version: str | None = None,
        budget_policy_version: str | None = None,
    ) -> ProviderAttempt:
        finished = datetime.now(timezone.utc)
        input_tokens = response.input_tokens if response else None
        output_tokens = response.output_tokens if response else None
        total_tokens = input_tokens + output_tokens if input_tokens is not None and output_tokens is not None else None
        return ProviderAttempt(
            model_call_id=model_call_id, provider_attempt_id="mat_" + uuid.uuid4().hex,
            scene=scene, provider_code=alias.provider_code, model_name=alias.model_name, status=status,
            input_tokens=input_tokens, output_tokens=output_tokens, total_tokens=total_tokens,
            cost_cny=(
                self._cost(
                    alias,
                    input_tokens,
                    output_tokens,
                    response.cached_input_tokens if response else 0,
                    (input_price_per_million, output_price_per_million)
                    if input_price_per_million is not None and output_price_per_million is not None
                    else None,
                )
                if response
                else None
            ),
            latency_ms=max(0, int((time.monotonic() - begin) * 1000)),
            started_at=started.isoformat().replace("+00:00", "Z"), finished_at=finished.isoformat().replace("+00:00", "Z"),
            provider_request_id=response.provider_request_id if response else None,
            error_code=error.code if error else None,
            price_version=price_version_override or self._price(alias)[3],
            cached_input_tokens=response.cached_input_tokens if response else None,
            route_version=route_version,
            budget_policy_version=budget_policy_version,
        )

    def _cost(
        self,
        alias: ModelAlias,
        input_tokens: int | None,
        output_tokens: int | None,
        cached_input_tokens: int = 0,
        price_override: tuple[Decimal, Decimal] | None = None,
    ) -> Decimal | None:
        if input_tokens is None or output_tokens is None:
            return None
        in_price, out_price, cached_price, _ = self._price(alias)
        if price_override is not None:
            in_price, out_price = price_override
            cached_price = in_price
        cached_tokens = max(0, min(cached_input_tokens, input_tokens))
        if in_price is None or out_price is None or (cached_tokens and cached_price is None):
            return None
        regular_tokens = input_tokens - cached_tokens
        input_cost = Decimal(regular_tokens) * in_price + Decimal(cached_tokens) * (cached_price or in_price)
        return (input_cost + Decimal(output_tokens) * out_price) / Decimal(1_000_000)

    def _price(self, alias: ModelAlias) -> tuple[Decimal | None, Decimal | None, Decimal | None, str]:
        """优先读取供应商+模型价格，兼容旧的供应商级配置。"""
        provider_key = alias.provider_code.upper().replace("-", "_")
        model_key = "_".join(part for part in alias.model_name.upper().replace("-", "_").replace("/", "_").split("_") if part)
        model_prefix = f"FOODMATE_MODEL_PROVIDER_{provider_key}_{model_key}_"
        provider_prefix = f"FOODMATE_MODEL_PROVIDER_{provider_key}_"
        in_raw = self.environment.get(model_prefix + "INPUT_CNY_PER_MILLION_TOKENS", self.environment.get(provider_prefix + "INPUT_CNY_PER_MILLION_TOKENS", "")).strip()
        out_raw = self.environment.get(model_prefix + "OUTPUT_CNY_PER_MILLION_TOKENS", self.environment.get(provider_prefix + "OUTPUT_CNY_PER_MILLION_TOKENS", "")).strip()
        cached_raw = self.environment.get(model_prefix + "CACHED_INPUT_CNY_PER_MILLION_TOKENS", self.environment.get(provider_prefix + "CACHED_INPUT_CNY_PER_MILLION_TOKENS", "")).strip()
        version = self.environment.get(
            model_prefix + "PRICE_VERSION",
            self.environment.get(
                provider_prefix + "PRICE_VERSION",
                self.environment.get("FOODMATE_MODEL_PRICE_VERSION", "unconfigured"),
            ),
        ).strip() or "unconfigured"
        if not in_raw or not out_raw:
            return None, None, None, version
        try:
            in_price = Decimal(in_raw)
            out_price = Decimal(out_raw)
            cached_price = Decimal(cached_raw) if cached_raw else None
        except InvalidOperation:
            return None, None, None, version
        if (not in_price.is_finite() or not out_price.is_finite() or in_price < 0 or out_price < 0
                or (cached_price is not None and (not cached_price.is_finite() or cached_price < 0))):
            return None, None, None, version
        return in_price, out_price, cached_price, version

    def _require_audited_price(
        self,
        alias: ModelAlias,
        input_price_per_million: Decimal | None = None,
        output_price_per_million: Decimal | None = None,
        price_version: str | None = None,
    ) -> None:
        """生产成本审计开启时，禁止在价格未知的情况下产生云调用。"""
        required = self.environment.get("FOODMATE_MODEL_PRICE_AUDIT_REQUIRED", "false").lower() == "true"
        if alias.provider_code == "deterministic" or not required:
            if (
                price_version
                and (input_price_per_million is None or output_price_per_million is None)
                and alias.provider_code != "deterministic"
            ):
                raise ModelProviderError(
                    "MODEL_PRICE_UNCONFIGURED", "governed model price is not configured"
                )
            return
        if input_price_per_million is not None and output_price_per_million is not None:
            return
        input_price, output_price, cached_price, version = self._price(alias)
        if input_price is None or output_price is None or version == "unconfigured":
            raise ModelProviderError("MODEL_PRICE_UNCONFIGURED", "audited model price is not configured")

    def _enabled(self, key: str, default: bool) -> bool:
        return self.environment.get(key, str(default)).lower() == "true"

    def _paid_execution_enabled(self) -> bool:
        return self._enabled("FOODMATE_PAID_EXECUTION_ENABLED", False)

    def _paid_requires_cloud(self) -> bool:
        return self._enabled("FOODMATE_PAID_REQUIRE_CLOUD", True)

    def _fallback_enabled(self) -> bool:
        """真实付费轮次默认关闭 fallback，避免一次失败扩大费用和语义漂移。"""
        if self._paid_execution_enabled() and self._enabled(
            "FOODMATE_PAID_NO_RETRY", True
        ):
            return False
        return self._enabled("FOODMATE_MODEL_FALLBACK_ENABLED", True)

    @staticmethod
    def _status(error: ModelProviderError) -> str:
        if error.code == "MODEL_TIMEOUT":
            return "timeout"
        return "failed"


def _decimal_or_none(value: object) -> Decimal | None:
    if value is None or value == "":
        return None
    try:
        parsed = Decimal(str(value))
    except (InvalidOperation, ValueError):
        return None
    return parsed if parsed.is_finite() and parsed >= 0 else None


def _token_count(value: object, field_name: str) -> int:
    """只接受非负整数用量，避免把供应商异常值写入成本事实。"""
    if isinstance(value, bool) or not isinstance(value, int) or value < 0:
        raise ValueError(f"{field_name} must be a non-negative integer")
    return value


def _positive_timeout(value: object) -> float:
    try:
        timeout = float(value)
    except (TypeError, ValueError) as error:
        raise ModelProviderError(
            "MODEL_PROVIDER_TIMEOUT_INVALID", "provider timeout must be a positive number"
        ) from error
    if not math.isfinite(timeout) or timeout <= 0:
        raise ModelProviderError(
            "MODEL_PROVIDER_TIMEOUT_INVALID", "provider timeout must be a positive number"
        )
    return max(0.1, timeout)

"""真实云模型联调门禁。

没有凭据时必须跳过，避免本地测试误发请求；配置齐全时才访问真实兼容端点。
所有配置必须来自当前进程，测试不会自动加载仓库 `.env`。
"""

import os

import pytest

from model_provider import ModelRequest, ModelRouter


def _env(name: str, fallback: str = "") -> str:
    return os.environ.get(name, fallback).strip()


def _cloud_environment() -> dict[str, str] | None:
    provider = _env("FOODMATE_REAL_CLOUD_PROVIDER", "cloud_primary")
    provider_key = provider.upper().replace("-", "_")
    provider_prefix = "FOODMATE_MODEL_PROVIDER_" + provider_key + "_"
    primary_base = _env(
        provider_prefix + "BASE_URL",
        _env("FOODMATE_MODEL_PROVIDER_CLOUD_PRIMARY_BASE_URL", _env("FOODMATE_MODEL_PROVIDER_CLOUD_A_BASE_URL")),
    )
    primary_key = _env(
        provider_prefix + "API_KEY",
        _env("FOODMATE_MODEL_PROVIDER_CLOUD_PRIMARY_API_KEY", _env("FOODMATE_MODEL_PROVIDER_CLOUD_A_API_KEY")),
    )
    backup_base = _env("FOODMATE_MODEL_PROVIDER_CLOUD_BACKUP_BASE_URL", _env("FOODMATE_MODEL_PROVIDER_CLOUD_B_BASE_URL"))
    backup_key = _env("FOODMATE_MODEL_PROVIDER_CLOUD_BACKUP_API_KEY", _env("FOODMATE_MODEL_PROVIDER_CLOUD_B_API_KEY"))
    if not all((primary_base, primary_key)):
        return None
    standard_tier = _env(
        "FOODMATE_MODEL_TIER_STANDARD",
        provider + ":" + _env("FOODMATE_REAL_CLOUD_MODEL", _env("FOODMATE_MODEL_CLOUD_PRIMARY_MODEL", "chat-model")),
    )
    eval_tier = _env("FOODMATE_MODEL_TIER_EVAL", standard_tier)
    return {
        provider_prefix + "BASE_URL": primary_base,
        provider_prefix + "API_KEY": primary_key,
        "FOODMATE_MODEL_PROVIDER_CLOUD_PRIMARY_BASE_URL": primary_base,
        "FOODMATE_MODEL_PROVIDER_CLOUD_PRIMARY_API_KEY": primary_key,
        provider_prefix + "INPUT_CNY_PER_MILLION_TOKENS": _env(provider_prefix + "INPUT_CNY_PER_MILLION_TOKENS"),
        provider_prefix + "OUTPUT_CNY_PER_MILLION_TOKENS": _env(provider_prefix + "OUTPUT_CNY_PER_MILLION_TOKENS"),
        provider_prefix + "CACHED_INPUT_CNY_PER_MILLION_TOKENS": _env(provider_prefix + "CACHED_INPUT_CNY_PER_MILLION_TOKENS"),
        provider_prefix + "PRICE_VERSION": _env(provider_prefix + "PRICE_VERSION"),
        # 本地 smoke 不代填供应商价格；生产环境开启审计后必须填写真实价格。
        "FOODMATE_MODEL_PROVIDER_CLOUD_PRIMARY_INPUT_CNY_PER_MILLION_TOKENS": _env("FOODMATE_MODEL_PROVIDER_CLOUD_PRIMARY_INPUT_CNY_PER_MILLION_TOKENS"),
        "FOODMATE_MODEL_PROVIDER_CLOUD_PRIMARY_OUTPUT_CNY_PER_MILLION_TOKENS": _env("FOODMATE_MODEL_PROVIDER_CLOUD_PRIMARY_OUTPUT_CNY_PER_MILLION_TOKENS"),
        "FOODMATE_MODEL_PROVIDER_CLOUD_BACKUP_BASE_URL": backup_base,
        "FOODMATE_MODEL_PROVIDER_CLOUD_BACKUP_API_KEY": backup_key,
        "FOODMATE_MODEL_PROVIDER_CLOUD_BACKUP_INPUT_CNY_PER_MILLION_TOKENS": _env("FOODMATE_MODEL_PROVIDER_CLOUD_BACKUP_INPUT_CNY_PER_MILLION_TOKENS", "0"),
        "FOODMATE_MODEL_PROVIDER_CLOUD_BACKUP_OUTPUT_CNY_PER_MILLION_TOKENS": _env("FOODMATE_MODEL_PROVIDER_CLOUD_BACKUP_OUTPUT_CNY_PER_MILLION_TOKENS", "0"),
        "FOODMATE_MODEL_TIER_STANDARD": standard_tier,
        "FOODMATE_MODEL_TIER_EVAL": eval_tier,
        "FOODMATE_REAL_CLOUD_PROVIDER": provider,
        "FOODMATE_MODEL_FALLBACK_ENABLED": "true" if backup_base and backup_key else "false",
        "FOODMATE_MODEL_FALLBACK_STANDARD": "eval",
    }


@pytest.mark.integration
def test_real_primary_and_eval_provider_contract():
    if _env("FOODMATE_RUN_REAL_CLOUD_TESTS", "false").lower() != "true":
        pytest.skip("真实云联调需要显式设置 FOODMATE_RUN_REAL_CLOUD_TESTS=true")
    environment = _cloud_environment()
    if environment is None:
        pytest.skip("真实云联调需要 primary BASE_URL 和 API_KEY")

    router = ModelRouter(environment)
    response, attempts = router.invoke(
        ModelRequest("composer", "请只返回一句简短的中文测试结果。", max_output_tokens=32),
        "standard",
    )
    assert response.content.strip()
    assert attempts[-1].provider_request_id
    assert attempts[-1].input_tokens is not None
    assert attempts[-1].output_tokens is not None
    assert attempts[-1].cost_cny is not None

    eval_response, eval_attempts = router.invoke(
        ModelRequest(
            "eval",
            '{"answer":"测试答案","criteria":["可读"]}',
            max_output_tokens=128,
            response_format={"type": "json_object"},
            extra_body={"enable_thinking": False},
        ),
        "eval",
    )
    assert eval_response.content.strip()
    assert eval_attempts[-1].provider_request_id

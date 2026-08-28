"""真实云模型联调门禁。

没有凭据时必须跳过，避免本地测试误发请求；配置齐全时才访问真实兼容端点。
"""

import os

import pytest

from model_provider import ModelRequest, ModelRouter
from runtime_env import load_project_env


def _env(name: str, fallback: str = "") -> str:
    return os.environ.get(name, fallback).strip()


def _cloud_environment() -> dict[str, str] | None:
    load_project_env()
    primary_base = _env("FOODMATE_MODEL_PROVIDER_CLOUD_PRIMARY_BASE_URL", _env("FOODMATE_MODEL_PROVIDER_CLOUD_A_BASE_URL"))
    primary_key = _env("FOODMATE_MODEL_PROVIDER_CLOUD_PRIMARY_API_KEY", _env("FOODMATE_MODEL_PROVIDER_CLOUD_A_API_KEY"))
    backup_base = _env("FOODMATE_MODEL_PROVIDER_CLOUD_BACKUP_BASE_URL", _env("FOODMATE_MODEL_PROVIDER_CLOUD_B_BASE_URL"))
    backup_key = _env("FOODMATE_MODEL_PROVIDER_CLOUD_BACKUP_API_KEY", _env("FOODMATE_MODEL_PROVIDER_CLOUD_B_API_KEY"))
    if not all((primary_base, primary_key)):
        return None
    standard_tier = _env(
        "FOODMATE_MODEL_TIER_STANDARD",
        "cloud_primary:" + _env("FOODMATE_MODEL_CLOUD_PRIMARY_MODEL", "chat-model"),
    )
    eval_tier = _env("FOODMATE_MODEL_TIER_EVAL", standard_tier)
    return {
        "FOODMATE_MODEL_PROVIDER_CLOUD_PRIMARY_BASE_URL": primary_base,
        "FOODMATE_MODEL_PROVIDER_CLOUD_PRIMARY_API_KEY": primary_key,
        # 本地联调不代填供应商价格；0 只用于让 usage/cost 字段保持可断言，生产环境必须填写真实价格。
        "FOODMATE_MODEL_PROVIDER_CLOUD_PRIMARY_INPUT_CNY_PER_MILLION_TOKENS": _env("FOODMATE_MODEL_PROVIDER_CLOUD_PRIMARY_INPUT_CNY_PER_MILLION_TOKENS", "0"),
        "FOODMATE_MODEL_PROVIDER_CLOUD_PRIMARY_OUTPUT_CNY_PER_MILLION_TOKENS": _env("FOODMATE_MODEL_PROVIDER_CLOUD_PRIMARY_OUTPUT_CNY_PER_MILLION_TOKENS", "0"),
        "FOODMATE_MODEL_PROVIDER_CLOUD_BACKUP_BASE_URL": backup_base,
        "FOODMATE_MODEL_PROVIDER_CLOUD_BACKUP_API_KEY": backup_key,
        "FOODMATE_MODEL_PROVIDER_CLOUD_BACKUP_INPUT_CNY_PER_MILLION_TOKENS": _env("FOODMATE_MODEL_PROVIDER_CLOUD_BACKUP_INPUT_CNY_PER_MILLION_TOKENS", "0"),
        "FOODMATE_MODEL_PROVIDER_CLOUD_BACKUP_OUTPUT_CNY_PER_MILLION_TOKENS": _env("FOODMATE_MODEL_PROVIDER_CLOUD_BACKUP_OUTPUT_CNY_PER_MILLION_TOKENS", "0"),
        "FOODMATE_MODEL_TIER_STANDARD": standard_tier,
        "FOODMATE_MODEL_TIER_EVAL": eval_tier,
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

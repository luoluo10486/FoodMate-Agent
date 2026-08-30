"""Explicit SiliconFlow embedding smoke tests.

These tests are opt-in because they make paid external requests. Credentials
must come from the current process environment and are never loaded from the
repository-local ``.env`` file, printed, or persisted.
"""

import os
import time

import pytest

from knowledge_rag import OpenAICompatibleEmbedder, RagSettings


_PROFILES = (
    ("bge-m3", "BAAI/bge-m3", 1024),
    ("qwen3-embedding-0.6b", "Qwen/Qwen3-Embedding-0.6B", 1024),
)


def _environment() -> dict[str, str] | None:
    if os.environ.get("FOODMATE_RUN_REAL_EMBEDDING_TESTS", "false").lower() != "true":
        return None
    # Embedding credentials are intentionally independent from Chat credentials.
    # Never reuse a model-provider key for a different outbound contract.
    base_url = os.environ.get("FOODMATE_RAG_EMBEDDING_BASE_URL", "").strip()
    api_key = os.environ.get("FOODMATE_RAG_EMBEDDING_API_KEY", "").strip()
    if not base_url or not api_key:
        return None
    return {"base_url": base_url, "api_key": api_key}


def _selected_profiles() -> tuple[tuple[str, str, int], ...]:
    selected = os.environ.get("FOODMATE_REAL_EMBEDDING_PROFILE", "all").strip().lower()
    if selected == "all":
        return _PROFILES
    for profile in _PROFILES:
        if profile[0] == selected:
            return (profile,)
    raise AssertionError("unsupported real embedding profile")


@pytest.mark.integration
def test_siliconflow_embedding_profiles_return_expected_dimensions():
    environment = _environment()
    if environment is None:
        pytest.skip(
            "set FOODMATE_RUN_REAL_EMBEDDING_TESTS=true and configure a local embedding credential"
        )

    for profile, model, expected_dimension in _selected_profiles():
        settings = RagSettings.from_environment(
            {
                "FOODMATE_RAG_MODE": "local",
                "FOODMATE_RAG_EMBEDDING_PROVIDER": "openai-compatible",
                "FOODMATE_RAG_EMBEDDING_PROFILE": profile,
                "FOODMATE_RAG_EMBEDDING_BASE_URL": environment["base_url"],
                "FOODMATE_RAG_EMBEDDING_API_KEY": environment["api_key"],
                "FOODMATE_RAG_EMBEDDING_MODEL": model,
                "FOODMATE_RAG_MILVUS_URI": "http://127.0.0.1:19530",
                "FOODMATE_RAG_MILVUS_COLLECTION": (
                    "verification_" + profile.replace("-", "_").replace(".", "_")
                ),
                "FOODMATE_RAG_BATCH_TOKEN_LIMIT": "100000",
                "FOODMATE_RAG_DAILY_TOKEN_LIMIT": "100000",
                "FOODMATE_RAG_BATCH_COST_LIMIT": "100000",
                "FOODMATE_RAG_DAILY_COST_LIMIT": "100000",
                "FOODMATE_RAG_PRICE_PER_MILLION_TOKENS": "1",
                "FOODMATE_RAG_PRICE_VERSION": "integration-test-only",
                "FOODMATE_RAG_ITEM_TIMEOUT_SECONDS": "30",
            }
        )

        started = time.perf_counter()
        vectors = OpenAICompatibleEmbedder(settings).embed(
            ["FoodMate embedding smoke test."]
        )
        latency_ms = round((time.perf_counter() - started) * 1000, 2)

        assert len(vectors) == 1
        assert len(vectors[0]) == expected_dimension
        assert all(isinstance(value, float) for value in vectors[0])
        print(
            "real_embedding_profile={} model={} status=passed dimension={} latency_ms={}".format(
                profile, model, len(vectors[0]), latency_ms
            )
        )

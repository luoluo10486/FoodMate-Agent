import os

import pytest


@pytest.fixture(autouse=True)
def isolate_cloud_judge_for_offline_tests(monkeypatch):
    """Keep the default test suite offline even when local production config is enabled."""
    if os.environ.get("FOODMATE_RUN_REAL_CLOUD_TESTS", "false").lower() == "true":
        return
    monkeypatch.setenv("FOODMATE_MODEL_TIER_STANDARD", "deterministic:local")
    monkeypatch.setenv("FOODMATE_MODEL_TIER_HIGH", "deterministic:local")
    monkeypatch.setenv("FOODMATE_MODEL_TIER_ECONOMY", "deterministic:local")
    monkeypatch.setenv("FOODMATE_MODEL_TIER_EVAL", "deterministic:local")
    monkeypatch.delenv("FOODMATE_MODEL_FALLBACK_STANDARD", raising=False)
    monkeypatch.delenv("FOODMATE_MODEL_FALLBACK_EVAL", raising=False)

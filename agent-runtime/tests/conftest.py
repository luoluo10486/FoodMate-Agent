import pytest


@pytest.fixture(autouse=True)
def isolate_cloud_judge_for_offline_tests(monkeypatch):
    """Keep the default test suite offline even when local production config is enabled."""
    monkeypatch.setenv("FOODMATE_MODEL_TIER_EVAL", "deterministic:local")
    monkeypatch.delenv("FOODMATE_MODEL_FALLBACK_EVAL", raising=False)

from pathlib import Path
from unittest import TestCase


class CloudSmokeScriptContractTests(TestCase):
    ROOT = Path(__file__).parents[2]

    def test_siliconflow_chat_smoke_uses_cloud_primary_namespace(self):
        script = (self.ROOT / "script" / "local" / "siliconflow-chat-smoke.ps1").read_text(
            encoding="utf-8"
        )

        self.assertIn('"FOODMATE_MODEL_PROVIDER_CLOUD_PRIMARY_BASE_URL"', script)
        self.assertIn('"FOODMATE_MODEL_PROVIDER_CLOUD_PRIMARY_API_KEY"', script)
        self.assertIn(
            '[Environment]::SetEnvironmentVariable("FOODMATE_REAL_CLOUD_PROVIDER", "cloud_primary")',
            script,
        )
        self.assertNotIn("FOODMATE_MODEL_PROVIDER_SILICONFLOW_API_KEY", script)

    def test_cloud_smoke_copies_model_specific_price_configuration(self):
        helper = (self.ROOT / "agent-runtime" / "tests" / "test_real_cloud_integration.py").read_text(
            encoding="utf-8"
        )

        self.assertIn("model_specific_price", helper)
        self.assertIn("FOODMATE_MODEL_PROVIDER_{provider_key}_{model_key}_", helper)

    def test_embedding_smoke_supports_each_explicit_profile_without_loading_dotenv(self):
        script = (self.ROOT / "script" / "local" / "siliconflow-embedding-smoke.ps1").read_text(
            encoding="utf-8"
        )

        self.assertIn('ValidateSet("all", "bge-m3", "qwen3-embedding-0.6b")', script)
        self.assertIn('FOODMATE_REAL_EMBEDDING_PROFILE', script)
        self.assertNotIn("dotenv", script.lower())
        self.assertNotIn("Get-Content", script)

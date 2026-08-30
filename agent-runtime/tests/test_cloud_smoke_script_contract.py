import codecs
from pathlib import Path
import re
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

    def test_docker_embedding_smoke_is_explicit_and_keeps_credentials_out_of_arguments(self):
        script = (
            self.ROOT / "script" / "local" / "siliconflow-docker-embedding-smoke.ps1"
        ).read_text(encoding="utf-8")

        self.assertIn('ValidateSet("bge-m3", "qwen3-embedding-0.6b")', script)
        self.assertIn("[switch]$ExecuteRequest", script)
        self.assertIn("FOODMATE_DOCKER_RAG_EMBEDDING_API_KEY", script)
        self.assertIn("docker compose", script)
        self.assertIn("docker compose exec -T agent-runtime python -c", script)
        self.assertNotIn("$EmbeddingApiKey", script)
        self.assertNotIn("--api-key", script.lower())

    def test_docker_embedding_smoke_transports_python_source_without_cli_quote_reparsing(self):
        script = (
            self.ROOT / "script" / "local" / "siliconflow-docker-embedding-smoke.ps1"
        ).read_text(encoding="utf-8")

        self.assertRegex(
            script,
            re.compile(
                r"\[Convert\]::ToBase64String\(\s*"
                r"\[Text\.Encoding\]::UTF8\.GetBytes\(\$pythonCode\)\s*\)"
            ),
        )
        self.assertIn("base64.b64decode", script)
        self.assertIn("$encodedPythonCode", script)
        self.assertIn("base64.b64decode(sys.argv[2])", script)
        self.assertIn("$pythonBootstrap $Profile $encodedPythonCode", script)

    def test_docker_embedding_smoke_embedded_python_source_is_ascii_safe(self):
        script = (
            self.ROOT / "script" / "local" / "siliconflow-docker-embedding-smoke.ps1"
        ).read_text(encoding="utf-8")
        start = script.index("$pythonCode = @'") + len("$pythonCode = @'")
        end = script.index("'@", start)
        embedded_python = script[start:end]

        self.assertTrue(all(ord(character) < 128 for character in embedded_python))

    def test_windows_cloud_smoke_scripts_use_utf8_bom_and_consistent_crlf(self):
        for relative_path in (
            "script/local/siliconflow-embedding-smoke.ps1",
            "script/local/siliconflow-chat-smoke.ps1",
        ):
            raw = (self.ROOT / relative_path).read_bytes()
            self.assertTrue(raw.startswith(codecs.BOM_UTF8), relative_path)
            self.assertNotIn(b"\n", raw.replace(b"\r\n", b""), relative_path)

from pathlib import Path
from unittest import TestCase


class DockerComposeContractTests(TestCase):
    ROOT = Path(__file__).parents[2]

    def test_cloud_model_price_audit_is_docker_scoped_and_fail_closed(self):
        compose = (self.ROOT / "docker" / "compose.yml").read_text(encoding="utf-8")
        example = (self.ROOT / "docker" / ".env.example").read_text(encoding="utf-8")

        self.assertIn(
            "FOODMATE_MODEL_PRICE_AUDIT_REQUIRED: ${FOODMATE_DOCKER_MODEL_PRICE_AUDIT_REQUIRED:-true}",
            compose,
        )
        self.assertIn("FOODMATE_DOCKER_MODEL_PRICE_AUDIT_REQUIRED=true", example)
        self.assertNotIn("FOODMATE_MODEL_PRICE_AUDIT_REQUIRED=false", example)

    def test_rag_configuration_is_docker_scoped_including_embedding_secret(self):
        compose = (self.ROOT / "docker" / "compose.yml").read_text(encoding="utf-8")
        example = (self.ROOT / "docker" / ".env.example").read_text(encoding="utf-8")

        self.assertIn(
            "FOODMATE_RAG_MODE: ${FOODMATE_DOCKER_RAG_MODE:-stub}", compose
        )
        self.assertIn(
            "FOODMATE_RAG_EMBEDDING_API_KEY: ${FOODMATE_DOCKER_RAG_EMBEDDING_API_KEY:-}",
            compose,
        )
        self.assertNotIn(
            "FOODMATE_RAG_EMBEDDING_API_KEY: ${FOODMATE_RAG_EMBEDDING_API_KEY:-}",
            compose,
        )
        self.assertIn("FOODMATE_DOCKER_RAG_EMBEDDING_API_KEY=", example)
        self.assertNotIn("FOODMATE_RAG_EMBEDDING_API_KEY=", example)
        self.assertNotIn("FOODMATE_MODEL_PROVIDER_SILICONFLOW_API_KEY", compose)

    def test_docker_rag_milvus_uri_uses_compose_service_hostname(self):
        example = (self.ROOT / "docker" / ".env.example").read_text(encoding="utf-8")

        self.assertIn(
            "FOODMATE_DOCKER_RAG_MILVUS_URI=http://milvus:19530",
            example,
        )
        self.assertNotIn(
            "FOODMATE_DOCKER_RAG_MILVUS_URI=http://localhost:19530",
            example,
        )

    def test_docker_chat_example_uses_one_cloud_primary_namespace(self):
        compose = (self.ROOT / "docker" / "compose.yml").read_text(encoding="utf-8")
        example = (self.ROOT / "docker" / ".env.example").read_text(encoding="utf-8")

        self.assertIn(
            "# FOODMATE_DOCKER_MODEL_TIER_STANDARD=cloud_primary:deepseek-ai/DeepSeek-V4-Flash",
            example,
        )
        self.assertNotIn("FOODMATE_DOCKER_MODEL_PROVIDER_SILICONFLOW_API_KEY=", example)
        self.assertNotIn("FOODMATE_MODEL_PROVIDER_SILICONFLOW_API_KEY", compose)

    def test_docker_readme_documents_the_python_service_startup(self):
        readme = (self.ROOT / "docker" / "README.md").read_text(encoding="utf-8")

        self.assertIn(
            "docker compose --env-file .env -f docker/compose.yml up -d --build foodmate agent-runtime",
            readme,
        )
        self.assertIn("docker compose --env-file .env -f docker/compose.yml logs -f agent-runtime", readme)

    def test_docker_runtime_exposes_optional_external_proxy_without_proxying_internal_services(self):
        compose = (self.ROOT / "docker" / "compose.yml").read_text(encoding="utf-8")
        example = (self.ROOT / "docker" / ".env.example").read_text(encoding="utf-8")
        readme = (self.ROOT / "docker" / "README.md").read_text(encoding="utf-8")

        self.assertIn(
            "HTTPS_PROXY: ${FOODMATE_DOCKER_HTTPS_PROXY:-}", compose
        )
        self.assertIn(
            "HTTP_PROXY: ${FOODMATE_DOCKER_HTTP_PROXY:-}", compose
        )
        self.assertIn(
            "NO_PROXY: ${FOODMATE_DOCKER_NO_PROXY:-localhost,127.0.0.1,foodmate,redis,postgres,milvus,minio,rocketmq-proxy}",
            compose,
        )
        self.assertIn("FOODMATE_DOCKER_HTTPS_PROXY=", example)
        self.assertIn("FOODMATE_DOCKER_HTTP_PROXY=", example)
        self.assertIn("FOODMATE_DOCKER_NO_PROXY=", example)
        self.assertIn("FOODMATE_DOCKER_HTTPS_PROXY", readme)

    def test_paid_execution_gate_is_docker_scoped_and_disabled_by_default(self):
        compose = (self.ROOT / "docker" / "compose.yml").read_text(encoding="utf-8")
        example = (self.ROOT / "docker" / ".env.example").read_text(encoding="utf-8")
        script = (self.ROOT / "script" / "local" / "paid-cloud-preflight.ps1").read_text(
            encoding="utf-8"
        )

        self.assertIn(
            "FOODMATE_PAID_EXECUTION_ENABLED: ${FOODMATE_DOCKER_PAID_EXECUTION_ENABLED:-false}",
            compose,
        )
        self.assertIn("FOODMATE_DOCKER_PAID_EXECUTION_ENABLED=false", example)
        self.assertIn("FOODMATE_DOCKER_PAID_MAX_TOTAL_COST_CNY=5", example)
        self.assertIn("[switch]$ExecutePaid", script)
        self.assertIn("PaidExecutionSession", script)
        self.assertIn("--force-recreate agent-runtime", script)
        self.assertNotIn("--api-key", script.lower())
        self.assertNotIn("$ApiKey", script)

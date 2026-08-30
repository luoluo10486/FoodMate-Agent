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
        example = (self.ROOT / "docker" / ".env.example").read_text(encoding="utf-8")

        self.assertIn(
            "# FOODMATE_DOCKER_MODEL_TIER_STANDARD=cloud_primary:deepseek-ai/DeepSeek-V4-Flash",
            example,
        )
        self.assertNotIn("FOODMATE_DOCKER_MODEL_PROVIDER_SILICONFLOW_API_KEY=", example)

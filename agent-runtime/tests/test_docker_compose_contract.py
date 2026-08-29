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

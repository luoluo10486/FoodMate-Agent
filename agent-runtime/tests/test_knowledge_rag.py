from unittest import TestCase

from knowledge_rag import (OpenAICompatibleEmbedder, RagError, RagSettings, StubIndex, chunk_markdown, safe_object_key)


class RagSettingsTests(TestCase):
    def test_stub_needs_no_secret_or_milvus(self):
        self.assertEqual("stub", RagSettings.from_environment({"FOODMATE_RAG_MODE": "stub"}).mode)

    def test_local_fails_closed_when_configuration_is_missing(self):
        with self.assertRaisesRegex(RagError, "incomplete") as raised:
            RagSettings.from_environment({"FOODMATE_RAG_MODE": "local"})
        self.assertEqual("RAG_EMBEDDING_BASE_URL_MISSING", raised.exception.code)

    def test_local_accepts_complete_audited_configuration(self):
        settings = RagSettings.from_environment({
            "FOODMATE_RAG_MODE": "local", "FOODMATE_RAG_EMBEDDING_BASE_URL": "http://embedding/v1",
            "FOODMATE_RAG_EMBEDDING_API_KEY": "test", "FOODMATE_RAG_EMBEDDING_MODEL": "embedding",
            "FOODMATE_RAG_MILVUS_URI": "http://milvus:19530", "FOODMATE_RAG_MILVUS_COLLECTION": "public_knowledge",
            "FOODMATE_RAG_BATCH_TOKEN_LIMIT": "1", "FOODMATE_RAG_DAILY_TOKEN_LIMIT": "1",
            "FOODMATE_RAG_BATCH_COST_LIMIT": "1", "FOODMATE_RAG_DAILY_COST_LIMIT": "1",
            "FOODMATE_RAG_PRICE_PER_MILLION_TOKENS": "1", "FOODMATE_RAG_PRICE_VERSION": "test-v1",
        })
        self.assertEqual(4, settings.index_concurrency)


class StubIndexTests(TestCase):
    def test_public_filter_citation_limit_and_determinism(self):
        index = StubIndex()
        index.upsert("Nutrition guide", chunk_markdown("# Calories\nProtein and calories are important.\n\nMore protein facts.", "1", "v1", 40))
        citations = index.search("protein calories")
        self.assertLessEqual(len(citations), 2)
        self.assertEqual("Nutrition guide", citations[0].title)
        self.assertFalse(hasattr(citations[0], "storage_key"))

    def test_object_key_cannot_escape_knowledge_namespace(self):
        self.assertEqual("knowledge/1/a.txt", safe_object_key("knowledge/1/a.txt"))
        with self.assertRaisesRegex(RagError, "outside"):
            safe_object_key("knowledge/../secret")

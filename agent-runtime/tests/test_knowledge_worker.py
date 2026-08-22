from unittest import TestCase
from knowledge_worker import KnowledgeIndexWorker
from knowledge_rag import RagError, RagSettings

class KnowledgeIndexWorkerTests(TestCase):
    def test_stub_indexes_one_document_once(self):
        published = []
        worker = KnowledgeIndexWorker(lambda _: ("guide.md", b"# Protein\nProtein supports recovery."), published.append, RagSettings.from_environment({"FOODMATE_RAG_MODE": "stub"}))
        first = worker.handle_index({"item_id": "i1", "document_id": "d1", "version": "1", "mode": "stub"})
        second = worker.handle_index({"item_id": "i1", "document_id": "d1", "version": "1", "mode": "stub"})
        self.assertEqual("indexed", first["status"])
        self.assertTrue(second["duplicate"])
        self.assertEqual(first["chunk_count"], second["chunk_count"])
        self.assertEqual(2, len(published))

    def test_completion_claim_uses_nx_and_does_not_overwrite_processing(self):
        published = []
        worker = KnowledgeIndexWorker(lambda _: ("guide.md", b"Protein supports recovery."), published.append, RagSettings.from_environment({"FOODMATE_RAG_MODE": "stub"}))
        worker.completed.set(worker._completion_key(("i1", "v1", "stub")), "processing")
        with self.assertRaisesRegex(RagError, "already being processed"):
            worker.handle_index({"item_id": "i1", "document_id": "d1", "version": "v1", "mode": "stub"})

    def test_daily_token_limit_is_enforced_and_failure_is_safe(self):
        published = []
        settings = RagSettings.from_environment({"FOODMATE_RAG_MODE": "stub", "FOODMATE_RAG_DAILY_TOKEN_LIMIT": "1"})
        worker = KnowledgeIndexWorker(lambda _: ("guide.md", b"Protein supports recovery."), published.append, settings)
        result = worker.handle_index({"item_id": "i1", "document_id": "d1", "version": "v1", "mode": "stub"})
        self.assertEqual("index_failed", result["status"])
        self.assertEqual("RAG_DAILY_TOKEN_LIMIT_EXCEEDED", result["error_code"])
        self.assertNotIn("Protein supports", result["error_summary"])

    def test_attempt_four_is_terminal_without_indexing(self):
        published = []
        worker = KnowledgeIndexWorker(lambda _: ("guide.md", b"Protein supports recovery."), published.append, RagSettings.from_environment({"FOODMATE_RAG_MODE": "stub"}))
        result = worker.handle_index({"item_id": "i1", "document_id": "d1", "version": "v1", "mode": "stub", "attempt": 4})
        self.assertEqual("index_failed", result["status"])
        self.assertEqual("RAG_ATTEMPTS_EXHAUSTED", result["error_code"])
        self.assertEqual(1, len(published))

    def test_mode_mismatch_fails_closed_without_reading_object(self):
        published = []
        worker = KnowledgeIndexWorker(lambda _: (_ for _ in ()).throw(AssertionError("object must not be read")), published.append, RagSettings.from_environment({"FOODMATE_RAG_MODE": "stub"}))
        result = worker.handle_index({"item_id": "i1", "document_id": "d1", "version": "v1", "mode": "local"})
        self.assertEqual("RAG_MODE_MISMATCH", result["error_code"])

    def test_worker_rejects_non_public_index_scope(self):
        published = []
        worker = KnowledgeIndexWorker(lambda _: ("guide.md", b"Protein supports recovery."), published.append, RagSettings.from_environment({"FOODMATE_RAG_MODE": "stub"}))

        result = worker.handle_index({"item_id": "i1", "document_id": "d1", "version": "v1", "mode": "stub", "tenant_id": 9})

        self.assertEqual("RAG_SCOPE_DENIED", result["error_code"])
        self.assertEqual({}, worker.stub._chunks)

    def test_local_worker_cannot_receive_stub_backend(self):
        settings = RagSettings(
            mode="local",
            embedding_base_url="http://embedding",
            embedding_api_key="key",
            embedding_model="model",
            milvus_uri="http://milvus",
            milvus_collection="knowledge",
            batch_token_limit=100,
            daily_token_limit=100,
            batch_cost_limit=1,
            daily_cost_limit=1,
            price_per_million_tokens=1,
            price_version="test-v1",
        )

        with self.assertRaisesRegex(RagError, "stub index"):
            KnowledgeIndexWorker(settings=settings, stub_index=object())

    def test_visibility_requires_version_and_public_scope(self):
        worker = KnowledgeIndexWorker(lambda _: ("guide.md", b"Protein supports recovery."), settings=RagSettings.from_environment({"FOODMATE_RAG_MODE": "stub"}))

        with self.assertRaisesRegex(RagError, "version is required"):
            worker.handle_visibility({"document_id": "d1", "visibility": "published"})
        with self.assertRaisesRegex(RagError, "scope is not public"):
            worker.handle_visibility({"document_id": "d1", "visibility": "published", "version": "v1", "scope": "private"})

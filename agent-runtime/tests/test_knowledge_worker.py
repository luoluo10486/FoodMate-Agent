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

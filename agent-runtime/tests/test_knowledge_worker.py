from unittest import TestCase
from knowledge_worker import KnowledgeIndexWorker
from knowledge_rag import RagSettings

class KnowledgeIndexWorkerTests(TestCase):
    def test_stub_indexes_one_document_once(self):
        published = []
        worker = KnowledgeIndexWorker(lambda _: ("guide.md", b"# Protein\nProtein supports recovery."), published.append, RagSettings.from_environment({"FOODMATE_RAG_MODE": "stub"}))
        first = worker.handle_index({"item_id": "i1", "document_id": "d1", "version": "1"})
        second = worker.handle_index({"item_id": "i1", "document_id": "d1", "version": "1"})
        self.assertEqual("indexed", first["status"])
        self.assertTrue(second["duplicate"])
        self.assertEqual(2, len(published))

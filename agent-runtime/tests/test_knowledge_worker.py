import sys
from types import ModuleType, SimpleNamespace
from unittest import TestCase
from unittest.mock import patch
from knowledge_worker import KnowledgeIndexWorker, _MemoryCompletionStore
from knowledge_rag import DeterministicEmbedder, RagError, RagSettings


class _VectorIndex:
    def __init__(self):
        self.rows = []
        self.deleted = []

    def upsert(self, title, chunks, vectors):
        self.rows.append((title, list(chunks), vectors))

    def update_visibility(self, *_args):
        pass

    def delete_document(self, document_id, version):
        self.deleted.append((document_id, version))

class KnowledgeIndexWorkerTests(TestCase):
    def test_stub_indexes_one_document_once(self):
        published = []
        worker = KnowledgeIndexWorker(lambda _: ("guide.md", b"# Protein\nProtein supports recovery."), published.append, RagSettings.from_environment({"FOODMATE_RAG_MODE": "stub"}))
        first = worker.handle_index({"item_id": "i1", "document_id": "d1", "version": "1", "mode": "stub"})
        second = worker.handle_index({"item_id": "i1", "document_id": "d1", "version": "1", "mode": "stub"})
        self.assertEqual("indexed", first["status"])
        self.assertTrue(second["duplicate"])
        self.assertEqual(first["chunk_count"], second["chunk_count"])
        self.assertEqual(first["chunk_count"], len(first["chunks"]))
        self.assertEqual("emb_", first["chunks"][0]["embedding_id"][:4])
        self.assertEqual(2, len(published))

    def test_completion_claim_uses_nx_and_does_not_overwrite_processing(self):
        published = []
        worker = KnowledgeIndexWorker(lambda _: ("guide.md", b"Protein supports recovery."), published.append, RagSettings.from_environment({"FOODMATE_RAG_MODE": "stub"}))
        worker.completed.set(worker._completion_key(("i1", "v1", "stub")), "processing")
        with self.assertRaisesRegex(RagError, "already being processed"):
            worker.handle_index({"item_id": "i1", "document_id": "d1", "version": "v1", "mode": "stub"})

    def test_empty_chunk_result_fails_closed(self):
        published = []
        worker = KnowledgeIndexWorker(
            lambda _: ("guide.md", b"# Heading"),
            published.append,
            RagSettings.from_environment({"FOODMATE_RAG_MODE": "stub"}),
        )

        result = worker.handle_index(
            {"item_id": "i-empty", "document_id": "d-empty", "version": "v1", "mode": "stub"}
        )

        self.assertEqual("index_failed", result["status"])
        self.assertEqual("RAG_EMPTY_DOCUMENT", result["error_code"])
        self.assertEqual(1, len(published))

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

    def test_local_deterministic_provider_writes_vectors_to_milvus_adapter(self):
        settings = RagSettings(
            mode="local",
            embedding_provider="deterministic",
            embedding_model="deterministic-local-v1",
            milvus_uri="http://milvus",
            milvus_collection="knowledge",
            deterministic_dimension=16,
            batch_token_limit=100,
            daily_token_limit=100,
            batch_cost_limit=0,
            daily_cost_limit=0,
            price_per_million_tokens=0,
            price_version="deterministic-v1",
        )
        index = _VectorIndex()
        worker = KnowledgeIndexWorker(
            lambda _: ("guide.md", b"# Recovery\nProtein supports recovery."),
            settings=settings,
            embedder=DeterministicEmbedder(settings),
            milvus_index=index,
        )

        result = worker.handle_index({
            "item_id": "i-local",
            "document_id": "d-local",
            "version": "v1",
            "mode": "local",
            "tenant_id": 0,
            "scope": "public_published",
        })

        self.assertEqual("indexed", result["status"])
        self.assertEqual(1, len(index.rows))
        self.assertEqual(16, len(index.rows[0][2][0]))

    def test_visibility_requires_version_and_public_scope(self):
        worker = KnowledgeIndexWorker(lambda _: ("guide.md", b"Protein supports recovery."), settings=RagSettings.from_environment({"FOODMATE_RAG_MODE": "stub"}))

        with self.assertRaisesRegex(RagError, "version is required"):
            worker.handle_visibility({"document_id": "d1", "visibility": "published"})
        with self.assertRaisesRegex(RagError, "scope is not public"):
            worker.handle_visibility({"document_id": "d1", "visibility": "published", "version": "v1", "scope": "private"})

    def test_purge_is_idempotent_and_uses_the_explicit_publisher(self):
        index = _VectorIndex()
        index_published = []
        purge_published = []
        settings = RagSettings.from_environment({"FOODMATE_RAG_MODE": "stub"})
        worker = KnowledgeIndexWorker(
            result_publisher=index_published.append,
            settings=settings,
            stub_index=index,
            completed_store=_MemoryCompletionStore(),
        )

        first = worker.handle_purge(
            {"task_id": 71, "document_id": "d1", "version": "v2"},
            result_publisher=purge_published.append,
        )
        second = worker.handle_purge(
            {"task_id": 71, "document_id": "d1", "version": "v2"},
            result_publisher=purge_published.append,
        )

        self.assertEqual("succeeded", first["status"])
        self.assertTrue(second["duplicate"])
        self.assertEqual([("d1", "v2")], index.deleted)
        self.assertEqual([], index_published)
        self.assertEqual(2, len(purge_published))

    def test_purge_failure_is_published_with_a_stable_error_code(self):
        class FailingIndex(_VectorIndex):
            def delete_document(self, _document_id, _version):
                raise RagError("RAG_MILVUS_DELETE_FAILED", "backend unavailable")

        published = []
        worker = KnowledgeIndexWorker(
            result_publisher=published.append,
            settings=RagSettings.from_environment({"FOODMATE_RAG_MODE": "stub"}),
            stub_index=FailingIndex(),
            completed_store=_MemoryCompletionStore(),
        )

        result = worker.handle_purge(
            {"task_id": 72, "document_id": "d1", "version": "v1"}
        )

        self.assertEqual("failed", result["status"])
        self.assertEqual("RAG_MILVUS_DELETE_FAILED", result["error_code"])
        self.assertEqual("backend unavailable", result["error_summary"])

    def test_unexpected_purge_failure_is_converted_to_a_stable_error(self):
        class FailingIndex(_VectorIndex):
            def delete_document(self, _document_id, _version):
                raise RuntimeError("connection details must not be returned")

        published = []
        worker = KnowledgeIndexWorker(
            result_publisher=published.append,
            settings=RagSettings.from_environment({"FOODMATE_RAG_MODE": "stub"}),
            stub_index=FailingIndex(),
            completed_store=_MemoryCompletionStore(),
        )

        result = worker.handle_purge({"task_id": 74, "document_id": "d1", "version": "v1"})

        self.assertEqual("failed", result["status"])
        self.assertEqual("RAG_PURGE_EXECUTION_FAILED", result["error_code"])
        self.assertNotIn("connection details", result["error_summary"])

    def test_purge_rejects_non_numeric_task_id(self):
        worker = KnowledgeIndexWorker(
            settings=RagSettings.from_environment({"FOODMATE_RAG_MODE": "stub"}),
            stub_index=_VectorIndex(),
            completed_store=_MemoryCompletionStore(),
        )

        with self.assertRaisesRegex(RagError, "task id is invalid") as raised:
            worker.handle_purge({"task_id": "bad", "document_id": "d1", "version": "v1"})

        self.assertEqual("RAG_PURGE_CONTRACT_INVALID", raised.exception.code)

    def test_purge_rejects_a_completed_target_conflict(self):
        worker = KnowledgeIndexWorker(
            settings=RagSettings.from_environment({"FOODMATE_RAG_MODE": "stub"}),
            stub_index=_VectorIndex(),
            completed_store=_MemoryCompletionStore(),
        )
        worker.handle_purge({"task_id": 73, "document_id": "d1", "version": "v1"})

        with self.assertRaisesRegex(RagError, "completed fact") as raised:
            worker.handle_purge({"task_id": 73, "document_id": "d2", "version": "v1"})

        self.assertEqual("RAG_PURGE_IDEMPOTENCY_CONFLICT", raised.exception.code)

    def test_rocketmq_worker_exposes_index_visibility_and_purge_consumers(self):
        class FakeConsumer:
            instances = []

            def __init__(self, *_args, **_kwargs):
                self.__class__.instances.append(self)

        rocketmq = ModuleType("rocketmq")
        rocketmq.ClientConfiguration = lambda *args: args
        rocketmq.Credentials = lambda: object()
        rocketmq.FilterExpression = lambda value: value
        rocketmq.MessageListener = object
        rocketmq.PushConsumer = FakeConsumer
        rocketmq.ConsumeResult = SimpleNamespace(SUCCESS="SUCCESS", FAILURE="FAILURE")

        mq_runtime = ModuleType("mq_runtime")
        mq_runtime.RocketMqKnowledgeResultPublisher = lambda: SimpleNamespace(publish=lambda _result: None)
        mq_runtime.RocketMqKnowledgePurgeResultPublisher = lambda: SimpleNamespace(publish=lambda _result: None)
        mq_runtime._startup_client_with_timeout = lambda *_args: None

        with patch.dict(sys.modules, {"rocketmq": rocketmq, "mq_runtime": mq_runtime}):
            from knowledge_worker import start_rocketmq_worker

            consumers = start_rocketmq_worker()

        self.assertEqual(3, len(consumers))
        self.assertEqual(3, len(FakeConsumer.instances))

    def test_stub_worker_start_does_not_probe_milvus(self):
        from knowledge_worker import wait_for_milvus_ready

        with patch("knowledge_worker.urlopen", side_effect=AssertionError("stub must not probe Milvus")):
            wait_for_milvus_ready(RagSettings.from_environment({"FOODMATE_RAG_MODE": "stub"}))

    def test_local_worker_waits_for_milvus_health_before_starting(self):
        from knowledge_worker import wait_for_milvus_ready

        settings = RagSettings.from_environment(
            {
                "FOODMATE_RAG_MODE": "local",
                "FOODMATE_RAG_EMBEDDING_PROVIDER": "deterministic",
                "FOODMATE_RAG_MILVUS_URI": "http://milvus:19530",
                "FOODMATE_RAG_MILVUS_COLLECTION": "public_knowledge",
                "FOODMATE_RAG_BATCH_TOKEN_LIMIT": "1000",
                "FOODMATE_RAG_DAILY_TOKEN_LIMIT": "10000",
                "FOODMATE_RAG_BATCH_COST_LIMIT": "0",
                "FOODMATE_RAG_DAILY_COST_LIMIT": "0",
                "FOODMATE_RAG_PRICE_PER_MILLION_TOKENS": "0",
                "FOODMATE_RAG_PRICE_VERSION": "deterministic-v1",
            }
        )

        class ReadyResponse:
            status = 200

            def __enter__(self):
                return self

            def __exit__(self, *_args):
                return False

        with patch("knowledge_worker.urlopen", return_value=ReadyResponse()) as probe:
            wait_for_milvus_ready(settings)

        probe.assert_called_once()
        self.assertEqual("http://milvus:9091/healthz", probe.call_args.args[0])

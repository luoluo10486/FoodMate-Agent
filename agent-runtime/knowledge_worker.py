"""RocketMQ worker for M2-1 indexing; it has no FoodMate database access."""
from __future__ import annotations

import json
import os
from typing import Callable

from knowledge_rag import MilvusIndex, OpenAICompatibleEmbedder, RagError, RagSettings, RedisStubIndex, StubIndex, chunk_markdown, parse_document, safe_object_key


class _MemoryCompletionStore:
    def __init__(self):
        self.values: set[str] = set()

    def exists(self, key: str) -> bool:
        return key in self.values

    def set(self, key: str, value: str) -> None:
        self.values.add(key)


class KnowledgeIndexWorker:
    def __init__(self, object_reader: Callable[[str], bytes] | None = None, result_publisher: Callable[[dict], None] | None = None, settings: RagSettings | None = None, completed_store=None, stub_index=None):
        self.settings = settings or RagSettings.from_environment()
        self.object_reader = object_reader or self._read_minio
        self.result_publisher = result_publisher or (lambda _result: None)
        # In-memory dependencies are only for isolated unit tests. The runtime path
        # constructs the worker without an object_reader and therefore always uses Redis.
        self.stub = stub_index or (RedisStubIndex() if self.settings.mode == "stub" and object_reader is None else StubIndex() if self.settings.mode == "stub" else None)
        self.embedder = OpenAICompatibleEmbedder(self.settings) if self.settings.mode == "local" else None
        self.milvus = MilvusIndex(self.settings) if self.settings.mode == "local" else None
        self.completed = completed_store or (self._completed_store() if object_reader is None else _MemoryCompletionStore())

    def handle_index(self, payload: dict) -> dict:
        item_id, document_id, version = (str(payload[key]) for key in ("item_id", "document_id", "version"))
        attempt = int(payload.get("attempt", 1))
        key = (item_id, version, self.settings.mode)
        if self._is_completed(key):
            result = {"item_id": item_id, "document_id": document_id, "version": version, "status": "indexed", "duplicate": True, "chunk_count": 0, "attempt": attempt}
            self.result_publisher(result)
            return result
        try:
            prefix = f"knowledge/public/{document_id}/"
            filename, content = self._read_document(prefix)
            text = parse_document(filename, content)
            chunks = chunk_markdown(text, document_id, version)
            title = payload.get("title") or filename
            if self.stub:
                self.stub.upsert(title, chunks)
            else:
                self.milvus.upsert(title, chunks, self.embedder.embed([chunk.text for chunk in chunks]))
            self._mark_completed(key)
            result = {"item_id": item_id, "document_id": document_id, "version": version, "status": "indexed", "chunk_count": len(chunks), "mode": self.settings.mode, "model_version": self.settings.embedding_model if self.embedder else "deterministic-stub", "attempt": attempt}
        except RagError as error:
            result = {"item_id": item_id, "document_id": document_id, "version": version, "status": "index_failed", "error_code": error.code, "attempt": attempt}
        self.result_publisher(result)
        return result

    def handle_visibility(self, payload: dict) -> None:
        document_id = str(payload["document_id"])
        visibility = str(payload["visibility"])
        if self.milvus:
            self.milvus.update_visibility(document_id, visibility, visibility == "deleted")
        elif self.stub:
            self.stub.update_visibility(document_id, visibility)

    def _read_document(self, prefix: str) -> tuple[str, bytes]:
        # The restricted MinIO identity may list only this fixed public-knowledge namespace.
        value = self.object_reader(prefix)
        if isinstance(value, tuple):
            return value
        raise RagError("RAG_OBJECT_NOT_FOUND", "knowledge object was not found")

    def _completed_store(self):
        try:
            import redis
            return redis.Redis.from_url(
                os.getenv("FOODMATE_REDIS_URL", "redis://:foodmate-redis-change-me@localhost:6380"),
                decode_responses=True,
            )
        except Exception as error:
            raise RagError("RAG_REDIS_UNAVAILABLE", "worker idempotency store is unavailable") from error

    def _is_completed(self, key: tuple[str, str, str]) -> bool:
        return bool(self.completed.exists("foodmate:rag:worker:completed:" + ":".join(key)))

    def _mark_completed(self, key: tuple[str, str, str]) -> None:
        self.completed.set("foodmate:rag:worker:completed:" + ":".join(key), "1")

    def _read_minio(self, prefix: str) -> tuple[str, bytes]:
        try:
            from minio import Minio
            client = Minio(os.environ["FOODMATE_KNOWLEDGE_MINIO_ENDPOINT"], access_key=os.environ["FOODMATE_KNOWLEDGE_MINIO_ACCESS_KEY"], secret_key=os.environ["FOODMATE_KNOWLEDGE_MINIO_SECRET_KEY"], secure=os.getenv("FOODMATE_KNOWLEDGE_MINIO_SECURE", "false").lower() == "true")
            bucket = os.getenv("FOODMATE_KNOWLEDGE_MINIO_BUCKET", "foodmate-private")
            objects = list(client.list_objects(bucket, prefix=prefix, recursive=True))
            if len(objects) != 1:
                raise RagError("RAG_OBJECT_NOT_FOUND", "expected exactly one knowledge object")
            object_name = safe_object_key(objects[0].object_name)
            response = client.get_object(bucket, object_name)
            try:
                return object_name.rsplit("/", 1)[-1], response.read()
            finally:
                response.close()
                response.release_conn()
        except RagError:
            raise
        except Exception as error:
            raise RagError("RAG_OBJECT_UNAVAILABLE", "restricted object storage is unavailable") from error


def start_rocketmq_worker() -> tuple[object, object]:
    """Start dedicated index and visibility consumers, separate from AgentRun traffic."""
    from rocketmq import ClientConfiguration, ConsumeResult, Credentials, FilterExpression, MessageListener, PushConsumer
    from mq_runtime import RocketMqKnowledgeResultPublisher, _startup_client_with_timeout
    publisher = RocketMqKnowledgeResultPublisher()
    worker = KnowledgeIndexWorker(result_publisher=publisher.publish)
    class Listener(MessageListener):
        def consume(self, message):
            try:
                worker.handle_index(json.loads(message.body.decode("utf-8")))
                return ConsumeResult.SUCCESS
            except Exception:
                return ConsumeResult.FAILURE
    consumer = PushConsumer(ClientConfiguration(os.getenv("FOODMATE_ROCKETMQ_PROXY_ADDR", "localhost:8081"), Credentials()), os.getenv("FOODMATE_ROCKETMQ_CONSUMER_GROUP_PYTHON_KNOWLEDGE_INDEX", "foodmate-python-knowledge-index-v1"), Listener(), subscription={os.getenv("FOODMATE_ROCKETMQ_TOPIC_KNOWLEDGE_INDEX", "foodmate-knowledge-index-v1"): FilterExpression("*")}, consumption_thread_count=int(os.getenv("FOODMATE_RAG_INDEX_CONCURRENCY", "4")))
    _startup_client_with_timeout(consumer, "knowledge-index", float(os.getenv("FOODMATE_ROCKETMQ_STARTUP_TIMEOUT_SECONDS", "15")))
    class VisibilityListener(MessageListener):
        def consume(self, message):
            try:
                worker.handle_visibility(json.loads(message.body.decode("utf-8")))
                return ConsumeResult.SUCCESS
            except Exception:
                return ConsumeResult.FAILURE
    visibility_consumer = PushConsumer(ClientConfiguration(os.getenv("FOODMATE_ROCKETMQ_PROXY_ADDR", "localhost:8081"), Credentials()), os.getenv("FOODMATE_ROCKETMQ_CONSUMER_GROUP_PYTHON_KNOWLEDGE_VISIBILITY", "foodmate-python-knowledge-visibility-v1"), VisibilityListener(), subscription={os.getenv("FOODMATE_ROCKETMQ_TOPIC_KNOWLEDGE_VISIBILITY", "foodmate-knowledge-visibility-v1"): FilterExpression("*")}, consumption_thread_count=1)
    _startup_client_with_timeout(visibility_consumer, "knowledge-visibility", float(os.getenv("FOODMATE_ROCKETMQ_STARTUP_TIMEOUT_SECONDS", "15")))
    return consumer, visibility_consumer

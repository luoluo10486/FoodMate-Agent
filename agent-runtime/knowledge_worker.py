"""RocketMQ worker for M2-1 indexing; it has no FoodMate database access."""
from __future__ import annotations

import json
import os
from decimal import Decimal
from dataclasses import replace
from datetime import datetime, timezone
from typing import Callable

from knowledge_rag import MilvusIndex, PUBLIC_SCOPE, RagError, RagSettings, RedisStubIndex, StubIndex, build_local_embedder, chunk_markdown, parse_document, safe_object_key


class _MemoryCompletionStore:
    def __init__(self):
        self.values: dict[str, str] = {}

    def get(self, key: str) -> str | None:
        return self.values.get(key)

    def set(self, key: str, value: str, nx: bool = False, **_kwargs) -> bool:
        if nx and key in self.values:
            return False
        self.values[key] = value
        return True

    def incrby(self, key: str, amount: int) -> int:
        value = int(self.values.get(key, "0")) + amount
        self.values[key] = str(value)
        return value

    def delete(self, key: str) -> None:
        self.values.pop(key, None)


class KnowledgeIndexWorker:
    def __init__(self, object_reader: Callable[[str], bytes] | None = None, result_publisher: Callable[[dict], None] | None = None, settings: RagSettings | None = None, completed_store=None, stub_index=None, embedder=None, milvus_index=None):
        self.settings = settings or RagSettings.from_environment()
        if self.settings.mode == "local" and stub_index is not None:
            raise RagError("RAG_MODE_MISMATCH", "local mode cannot use the stub index")
        if self.settings.mode == "stub" and (embedder is not None or milvus_index is not None):
            raise RagError("RAG_MODE_MISMATCH", "stub mode cannot use the local index")
        self.object_reader = object_reader or self._read_minio
        self.result_publisher = result_publisher or (lambda _result: None)
        # In-memory dependencies are only for isolated unit tests. The runtime path
        # constructs the worker without an object_reader and therefore always uses Redis.
        self.stub = stub_index or (RedisStubIndex() if self.settings.mode == "stub" and object_reader is None else StubIndex() if self.settings.mode == "stub" else None)
        self.embedder = embedder or (build_local_embedder(self.settings) if self.settings.mode == "local" else None)
        self.milvus = milvus_index or (MilvusIndex(self.settings) if self.settings.mode == "local" else None)
        self.completed = completed_store or (self._completed_store() if object_reader is None else _MemoryCompletionStore())

    def handle_index(self, payload: dict) -> dict:
        item_id, document_id, version = (str(payload[key]) for key in ("item_id", "document_id", "version"))
        try:
            attempt = int(payload.get("attempt", 1))
        except (TypeError, ValueError):
            attempt = 0
        payload_mode = str(payload.get("mode", "")).strip().lower()
        if payload_mode != self.settings.mode:
            result = {"item_id": item_id, "document_id": document_id, "version": version, "status": "index_failed", "error_code": "RAG_MODE_MISMATCH", "error_summary": "knowledge index mode does not match worker mode", "attempt": 3}
            self.result_publisher(result)
            return result
        if payload.get("tenant_id", 0) != 0 or payload.get("scope", PUBLIC_SCOPE) != PUBLIC_SCOPE:
            result = {"item_id": item_id, "document_id": document_id, "version": version, "status": "index_failed", "error_code": "RAG_SCOPE_DENIED", "error_summary": "knowledge index scope is not public", "attempt": attempt}
            self.result_publisher(result)
            return result
        if attempt < 1:
            result = {"item_id": item_id, "document_id": document_id, "version": version, "status": "index_failed", "error_code": "RAG_ATTEMPT_INVALID", "error_summary": "knowledge index attempt is invalid", "attempt": 3}
            self.result_publisher(result)
            return result
        key = (item_id, version, self.settings.mode)
        if attempt > 3:
            result = {"item_id": item_id, "document_id": document_id, "version": version, "status": "index_failed", "error_code": "RAG_ATTEMPTS_EXHAUSTED", "error_summary": "knowledge index retry limit exceeded", "attempt": attempt}
            self.result_publisher(result)
            return result
        completed = self._completion_summary(key)
        if completed is not None and completed.get("status") == "completed":
            result = {"item_id": item_id, "document_id": document_id, "version": version, "status": "indexed", "duplicate": True, "attempt": attempt, **completed.get("result", {})}
            self.result_publisher(result)
            return result
        if not self._claim(key):
            raise RagError("RAG_INDEX_IN_PROGRESS", "knowledge index item is already being processed")
        try:
            prefix = f"knowledge/public/{document_id}/"
            safe_object_key(prefix)
            filename, content = self._read_document(prefix)
            text = parse_document(filename, content)
            chunks = [replace(chunk, visibility="draft", current_version=True) for chunk in chunk_markdown(text, document_id, version)]
            title = payload.get("title") or filename
            token_count = sum(_estimate_tokens(chunk.text) for chunk in chunks)
            cost_amount = _cost_amount(self.settings, token_count)
            if self.settings.batch_token_limit is not None and token_count > self.settings.batch_token_limit:
                raise RagError("RAG_BATCH_TOKEN_LIMIT_EXCEEDED", "knowledge batch token budget exceeded")
            if self.settings.batch_cost_limit is not None and cost_amount > self.settings.batch_cost_limit:
                raise RagError("RAG_BATCH_COST_LIMIT_EXCEEDED", "knowledge batch cost budget exceeded")
            self._reserve_daily_budget(token_count, cost_amount)
            budget_reserved = True
            if self.stub:
                self.stub.upsert(title, chunks)
            else:
                self.milvus.upsert(title, chunks, self.embedder.embed([chunk.text for chunk in chunks]))
            result = {"item_id": item_id, "document_id": document_id, "version": version, "status": "indexed", "chunk_count": len(chunks), "mode": self.settings.mode, "model_version": self.settings.embedding_model if self.embedder else "deterministic-stub", "token_count": token_count, "cost_amount": str(cost_amount), "price_version": self.settings.price_version or None, "attempt": attempt}
            self._mark_completed(key, result)
        except RagError as error:
            if "budget_reserved" in locals() and budget_reserved:
                self._release_daily_budget(token_count, cost_amount)
            self._release(key)
            result = {"item_id": item_id, "document_id": document_id, "version": version, "status": "index_failed", "error_code": error.code, "error_summary": str(error), "attempt": attempt}
        except Exception:
            if "budget_reserved" in locals() and budget_reserved:
                self._release_daily_budget(token_count, cost_amount)
            self._release(key)
            raise
        self.result_publisher(result)
        return result

    def handle_visibility(self, payload: dict) -> None:
        if payload.get("tenant_id", 0) != 0 or payload.get("scope", PUBLIC_SCOPE) != PUBLIC_SCOPE:
            raise RagError("RAG_SCOPE_DENIED", "knowledge visibility scope is not public")
        document_id = str(payload["document_id"])
        visibility = str(payload["visibility"])
        version = str(payload.get("version", "")).strip()
        if not version:
            raise RagError("RAG_VERSION_INVALID", "knowledge visibility version is required")
        current_version = bool(payload.get("current_version", True))
        if self.milvus:
            self.milvus.update_visibility(document_id, visibility, visibility == "deleted", current_version, version)
        elif self.stub:
            self.stub.update_visibility(document_id, visibility, current_version, version)

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

    def _completion_key(self, key: tuple[str, str, str]) -> str:
        return "foodmate:rag:worker:completed:" + ":".join(key)

    def _completion_summary(self, key: tuple[str, str, str]) -> dict | None:
        raw = self.completed.get(self._completion_key(key))
        if not raw or raw == "processing" or raw == "1":
            return None
        try:
            return json.loads(raw)
        except (TypeError, json.JSONDecodeError):
            return None

    def _claim(self, key: tuple[str, str, str]) -> bool:
        name = self._completion_key(key)
        if self.completed.get(name) is not None:
            return False
        try:
            return bool(self.completed.set(name, "processing", nx=True, ex=3600))
        except TypeError:
            if self.completed.get(name) is not None:
                return False
            self.completed.set(name, "processing")
            return True

    def _mark_completed(self, key: tuple[str, str, str], result: dict) -> None:
        self.completed.set(self._completion_key(key), json.dumps({"status": "completed", "result": {k: v for k, v in result.items() if k not in {"item_id", "document_id", "version", "attempt"}}}, ensure_ascii=False))

    def _release(self, key: tuple[str, str, str]) -> None:
        try:
            self.completed.delete(self._completion_key(key))
        except AttributeError:
            pass

    def _daily_key(self, kind: str) -> str:
        day = datetime.now(timezone.utc).date().isoformat()
        return f"foodmate:rag:budget:{day}:{self.settings.mode}:{kind}"

    def _reserve_daily_budget(self, token_count: int, cost_amount: Decimal) -> None:
        token_total = self._increment(self._daily_key("tokens"), token_count)
        if self.settings.daily_token_limit is not None and token_total > self.settings.daily_token_limit:
            self._increment(self._daily_key("tokens"), -token_count)
            raise RagError("RAG_DAILY_TOKEN_LIMIT_EXCEEDED", "daily knowledge token budget exceeded")
        cost_units = int(cost_amount * Decimal("100000000"))
        cost_total = self._increment(self._daily_key("cost"), cost_units)
        cost_limit = self.settings.daily_cost_limit
        if cost_limit is not None and cost_total > int(cost_limit * Decimal("100000000")):
            self._increment(self._daily_key("cost"), -cost_units)
            self._increment(self._daily_key("tokens"), -token_count)
            raise RagError("RAG_DAILY_COST_LIMIT_EXCEEDED", "daily knowledge cost budget exceeded")

    def _release_daily_budget(self, token_count: int, cost_amount: Decimal) -> None:
        self._increment(self._daily_key("tokens"), -token_count)
        self._increment(self._daily_key("cost"), -int(cost_amount * Decimal("100000000")))

    def _increment(self, key: str, amount: int) -> int:
        try:
            result = int(self.completed.incrby(key, amount))
            try:
                self.completed.expire(key, 172800)
            except AttributeError:
                pass
            return result
        except AttributeError as error:
            raise RagError("RAG_BUDGET_STORE_UNAVAILABLE", "daily knowledge budget store is unavailable") from error

    def _read_minio(self, prefix: str) -> tuple[str, bytes]:
        try:
            from minio import Minio

            client = Minio(
                os.environ["FOODMATE_KNOWLEDGE_MINIO_ENDPOINT"],
                access_key=os.environ["FOODMATE_KNOWLEDGE_MINIO_ACCESS_KEY"],
                secret_key=os.environ["FOODMATE_KNOWLEDGE_MINIO_SECRET_KEY"],
                secure=os.getenv("FOODMATE_KNOWLEDGE_MINIO_SECURE", "false").lower() == "true",
            )
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


def _estimate_tokens(value: str) -> int:
    return max(1, (len(value) + 3) // 4)


def _cost_amount(settings: RagSettings, token_count: int) -> Decimal:
    if settings.price_per_million_tokens is None:
        return Decimal("0")
    return (Decimal(token_count) * settings.price_per_million_tokens / Decimal(1_000_000)).quantize(Decimal("0.00000001"))


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

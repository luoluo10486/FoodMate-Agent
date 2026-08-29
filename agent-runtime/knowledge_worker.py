"""RocketMQ worker for M2-1 indexing; it has no FoodMate database access."""
from __future__ import annotations

import json
import os
import time
from decimal import Decimal
from dataclasses import replace
from datetime import datetime, timezone
from typing import Callable
from urllib.parse import urlsplit, urlunsplit
from urllib.request import urlopen

from knowledge_rag import DeletionResult, EmbeddingResult, MilvusIndex, PUBLIC_SCOPE, RagError, RagSettings, RedisStubIndex, StubIndex, build_local_embedder, chunk_markdown, parse_document, safe_object_key


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
            if not chunks:
                raise RagError("RAG_EMPTY_DOCUMENT", "document contains no indexable chunks")
            title = payload.get("title") or filename
            estimated_token_count = sum(_estimate_tokens(chunk.text) for chunk in chunks)
            token_count = estimated_token_count
            cost_amount = _cost_amount(self.settings, token_count)
            if self.settings.batch_token_limit is not None and token_count > self.settings.batch_token_limit:
                raise RagError("RAG_BATCH_TOKEN_LIMIT_EXCEEDED", "knowledge batch token budget exceeded")
            if self.settings.batch_cost_limit is not None and cost_amount > self.settings.batch_cost_limit:
                raise RagError("RAG_BATCH_COST_LIMIT_EXCEEDED", "knowledge batch cost budget exceeded")
            self._reserve_daily_budget(token_count, cost_amount)
            budget_reserved = True
            embedding_result = None
            if self.stub:
                self.stub.upsert(title, chunks)
            else:
                embedding_result = _embed_with_usage(
                    self.embedder, [chunk.text for chunk in chunks]
                )
                provider_token_count = embedding_result.token_count
                if provider_token_count is not None:
                    provider_cost = _cost_amount(self.settings, provider_token_count)
                    if (
                        self.settings.batch_token_limit is not None
                        and provider_token_count > self.settings.batch_token_limit
                    ):
                        raise RagError(
                            "RAG_BATCH_TOKEN_LIMIT_EXCEEDED",
                            "knowledge batch token budget exceeded",
                        )
                    if (
                        self.settings.batch_cost_limit is not None
                        and provider_cost > self.settings.batch_cost_limit
                    ):
                        raise RagError(
                            "RAG_BATCH_COST_LIMIT_EXCEEDED",
                            "knowledge batch cost budget exceeded",
                        )
                    self._reconcile_daily_budget(
                        token_count, cost_amount, provider_token_count, provider_cost
                    )
                    token_count = provider_token_count
                    cost_amount = provider_cost
                self.milvus.upsert(title, chunks, embedding_result.vectors)
            result = {
                "item_id": item_id,
                "document_id": document_id,
                "version": version,
                "status": "indexed",
                "chunk_count": len(chunks),
                "chunks": [
                    {
                        "chunk_no": chunk.sequence,
                        "embedding_id": chunk.embedding_id,
                        "section_path": chunk.section_path,
                        "text": chunk.text,
                    }
                    for chunk in chunks
                ],
                "mode": self.settings.mode,
                "model_version": self.settings.embedding_model if self.embedder else "deterministic-stub",
                "token_count": token_count,
                "estimated_token_count": estimated_token_count,
                "usage_source": "provider" if embedding_result and embedding_result.token_count is not None else "estimate",
                "cost_amount": str(cost_amount),
                "price_version": self.settings.price_version or None,
                "attempt": attempt,
            }
            if embedding_result and embedding_result.provider_request_id:
                result["provider_request_id"] = embedding_result.provider_request_id
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

    def handle_purge(self, payload: dict, result_publisher: Callable[[dict], None] | None = None) -> dict:
        publish = result_publisher or self.result_publisher
        task_id = str(payload.get("task_id", "")).strip()
        request_id = str(payload.get("request_id", "")).strip()
        document_id = str(payload.get("document_id", "")).strip()
        version = str(payload.get("version", "")).strip()
        try:
            task_id_value = int(task_id)
        except (TypeError, ValueError) as error:
            raise RagError("RAG_PURGE_CONTRACT_INVALID", "retention purge task id is invalid") from error
        try:
            request_id_value = int(request_id)
        except (TypeError, ValueError) as error:
            raise RagError("RAG_PURGE_CONTRACT_INVALID", "retention purge request id is invalid") from error
        try:
            resource_id = int(document_id)
        except (TypeError, ValueError) as error:
            raise RagError("RAG_PURGE_CONTRACT_INVALID", "retention purge resource id is invalid") from error
        if task_id_value <= 0 or request_id_value <= 0 or resource_id <= 0 or not document_id or not version:
            raise RagError("RAG_PURGE_CONTRACT_INVALID", "retention purge identifiers are required")
        if payload.get("tenant_id", 0) != 0 or payload.get("scope", PUBLIC_SCOPE) != PUBLIC_SCOPE:
            raise RagError("RAG_SCOPE_DENIED", "retention purge scope is not public")
        key = ("purge", str(task_id_value), self.settings.mode)
        completed = self._completion_summary(key)
        if completed is not None:
            completed_result = completed.get("result", {})
            if (
                completed_result.get("document_id") not in (None, document_id)
                or completed_result.get("version") not in (None, version)
            ):
                raise RagError("RAG_PURGE_IDEMPOTENCY_CONFLICT", "retention purge task target conflicts with its completed fact")
            result = {"task_id": task_id_value, "request_id": request_id_value, "resource_type": "knowledge_document", "resource_id": resource_id, "task_type": "vector_index", "document_id": document_id, "version": version, "status": "succeeded", "backend": completed_result.get("backend", "unknown"), "deleted_count": int(completed_result.get("deleted_count", 0)), "verified_absent": bool(completed_result.get("verified_absent", True)), "duplicate": True}
            publish(result)
            return result
        if not self._claim(key):
            raise RagError("RAG_PURGE_IN_PROGRESS", "retention purge task is already being processed")
        try:
            if self.milvus:
                deletion = self.milvus.delete_document(document_id, version)
            elif self.stub:
                deletion = self.stub.delete_document(document_id, version)
            else:
                raise RagError("RAG_PURGE_BACKEND_UNAVAILABLE", "retention purge backend is unavailable")
            if not isinstance(deletion, DeletionResult):
                raise RagError("RAG_PURGE_RESULT_INVALID", "retention purge backend returned no result fact")
            backend = deletion.backend
            deleted_count = deletion.deleted_count
            verified_absent = deletion.verified_absent
            if not verified_absent:
                raise RagError("RAG_PURGE_VERIFY_FAILED", "retention purge absence verification failed")
            result = {"task_id": task_id_value, "request_id": request_id_value, "resource_type": "knowledge_document", "resource_id": resource_id, "task_type": "vector_index", "document_id": document_id, "version": version, "status": "succeeded", "backend": backend, "deleted_count": deleted_count, "verified_absent": verified_absent}
            self._mark_completed(key, result)
        except RagError as error:
            self._release(key)
            result = {"task_id": task_id_value, "request_id": request_id_value, "resource_type": "knowledge_document", "resource_id": resource_id, "task_type": "vector_index", "document_id": document_id, "version": version, "status": "failed", "backend": "milvus" if self.milvus else "redis", "deleted_count": 0, "verified_absent": False, "error_code": error.code, "error_summary": str(error)[:256]}
        except Exception:
            self._release(key)
            result = {
                "task_id": task_id_value,
                "request_id": request_id_value,
                "resource_type": "knowledge_document",
                "resource_id": resource_id,
                "task_type": "vector_index",
                "document_id": document_id,
                "version": version,
                "status": "failed",
                "backend": "milvus" if self.milvus else "redis",
                "deleted_count": 0,
                "verified_absent": False,
                "error_code": "RAG_PURGE_EXECUTION_FAILED",
                "error_summary": "retention purge backend execution failed",
            }
        publish(result)
        return result

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
        self.completed.set(
            self._completion_key(key),
            json.dumps(
                {"status": "completed", "result": {k: v for k, v in result.items() if k != "attempt"}},
                ensure_ascii=False,
            ),
        )

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

    def _reconcile_daily_budget(
        self,
        reserved_token_count: int,
        reserved_cost_amount: Decimal,
        actual_token_count: int,
        actual_cost_amount: Decimal,
    ) -> None:
        """将供应商 usage 与预估预留对齐，并在超限时完整回滚调整。"""
        token_delta = actual_token_count - reserved_token_count
        cost_delta = int(
            (actual_cost_amount - reserved_cost_amount) * Decimal("100000000")
        )
        token_adjusted = False
        cost_adjusted = False
        try:
            if token_delta:
                token_total = self._increment(self._daily_key("tokens"), token_delta)
                token_adjusted = True
                if (
                    self.settings.daily_token_limit is not None
                    and token_total > self.settings.daily_token_limit
                ):
                    raise RagError(
                        "RAG_DAILY_TOKEN_LIMIT_EXCEEDED",
                        "daily knowledge token budget exceeded",
                    )
            if cost_delta:
                cost_total = self._increment(self._daily_key("cost"), cost_delta)
                cost_adjusted = True
                if (
                    self.settings.daily_cost_limit is not None
                    and cost_total > int(self.settings.daily_cost_limit * Decimal("100000000"))
                ):
                    raise RagError(
                        "RAG_DAILY_COST_LIMIT_EXCEEDED",
                        "daily knowledge cost budget exceeded",
                    )
        except RagError:
            if cost_adjusted:
                self._increment(self._daily_key("cost"), -cost_delta)
            if token_adjusted:
                self._increment(self._daily_key("tokens"), -token_delta)
            raise

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


def _embed_with_usage(embedder, inputs: list[str]) -> EmbeddingResult:
    """兼容旧 Embedder，同时优先使用供应商 usage 事实。"""
    method = getattr(embedder, "embed_with_usage", None)
    result = method(inputs) if callable(method) else EmbeddingResult(embedder.embed(inputs))
    if not isinstance(result, EmbeddingResult):
        raise RagError("RAG_EMBEDDING_INVALID_RESPONSE", "embedding provider returned an invalid result")
    return result


def start_rocketmq_worker() -> tuple[object, object, object]:
    """Start dedicated index and visibility consumers, separate from AgentRun traffic."""
    from rocketmq import ClientConfiguration, ConsumeResult, Credentials, FilterExpression, MessageListener, PushConsumer
    from mq_runtime import RocketMqKnowledgePurgeResultPublisher, RocketMqKnowledgeResultPublisher, _startup_client_with_timeout
    settings = RagSettings.from_environment()
    wait_for_milvus_ready(settings)
    publisher = RocketMqKnowledgeResultPublisher()
    purge_publisher = RocketMqKnowledgePurgeResultPublisher()
    worker = KnowledgeIndexWorker(result_publisher=publisher.publish, settings=settings)
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
    class PurgeListener(MessageListener):
        def consume(self, message):
            try:
                worker.handle_purge(
                    json.loads(message.body.decode("utf-8")),
                    result_publisher=purge_publisher.publish,
                )
                return ConsumeResult.SUCCESS
            except Exception:
                return ConsumeResult.FAILURE
    purge_consumer = PushConsumer(ClientConfiguration(os.getenv("FOODMATE_ROCKETMQ_PROXY_ADDR", "localhost:8081"), Credentials()), os.getenv("FOODMATE_ROCKETMQ_CONSUMER_GROUP_PYTHON_KNOWLEDGE_PURGE", "foodmate-python-knowledge-purge-v1"), PurgeListener(), subscription={os.getenv("FOODMATE_ROCKETMQ_TOPIC_KNOWLEDGE_PURGE", "foodmate-knowledge-purge-v1"): FilterExpression("*")}, consumption_thread_count=1)
    _startup_client_with_timeout(purge_consumer, "knowledge-purge", float(os.getenv("FOODMATE_ROCKETMQ_STARTUP_TIMEOUT_SECONDS", "15")))
    return consumer, visibility_consumer, purge_consumer


def wait_for_milvus_ready(settings: RagSettings) -> None:
    """Keep local index consumers stopped until the configured Milvus is ready."""
    if settings.mode != "local":
        return
    health_url = os.getenv("FOODMATE_RAG_MILVUS_HEALTH_URL", "").strip()
    if not health_url:
        health_url = _milvus_health_url(settings.milvus_uri)
    try:
        timeout = float(os.getenv("FOODMATE_RAG_MILVUS_READY_TIMEOUT_SECONDS", "60"))
    except ValueError as error:
        raise RagError("RAG_MILVUS_READY_TIMEOUT_INVALID", "Milvus readiness timeout is invalid") from error
    if timeout <= 0:
        raise RagError("RAG_MILVUS_READY_TIMEOUT_INVALID", "Milvus readiness timeout must be positive")
    deadline = time.monotonic() + timeout
    while True:
        try:
            with urlopen(health_url, timeout=min(3.0, max(0.1, deadline - time.monotonic()))) as response:
                if 200 <= response.status < 300:
                    return
        except OSError:
            pass
        if time.monotonic() >= deadline:
            raise RagError("RAG_MILVUS_UNAVAILABLE", "Milvus did not become ready before the startup deadline")
        time.sleep(min(1.0, max(0.05, deadline - time.monotonic())))


def _milvus_health_url(uri: str) -> str:
    """Map the SDK endpoint to Milvus standalone's HTTP health endpoint."""
    parsed = urlsplit(uri)
    if parsed.scheme not in {"http", "https"} or not parsed.hostname:
        raise RagError("RAG_MILVUS_URI_INVALID", "Milvus URI must be an HTTP endpoint")
    port = parsed.port
    if port == 19530:
        port = 9091
    netloc = parsed.hostname if port is None else f"{parsed.hostname}:{port}"
    return urlunsplit((parsed.scheme, netloc, "/healthz", "", ""))

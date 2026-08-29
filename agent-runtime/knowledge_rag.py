"""M2-1 public knowledge RAG primitives.

This module deliberately has no database access. Java owns authorization and
document state; this worker accepts only the fixed public scope and persists
the technical index through its configured backend.
"""

from __future__ import annotations

import hashlib
import json
import math
import os
import re
import urllib.error
import urllib.request
import io
import zipfile
import xml.etree.ElementTree as ElementTree
from dataclasses import dataclass
from decimal import Decimal, InvalidOperation
from pathlib import PurePosixPath
from typing import Iterable, Protocol
from urllib.parse import urlsplit


class RagError(RuntimeError):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code


PUBLIC_SCOPE = "public_published"
_WORD = re.compile(r"[A-Za-z0-9_]+|[\u4e00-\u9fff]+")
_EMAIL = re.compile(r"(?i)(?<![A-Za-z0-9._%+-])[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}(?![A-Za-z0-9.-])")
_MOBILE = re.compile(r"(?<!\d)1[3-9]\d{9}(?!\d)")
_CHINA_ID = re.compile(
    r"(?<!\d)[1-9]\d{5}(?:19|20)\d{2}(?:0[1-9]|1[0-2])"
    r"(?:0[1-9]|[12]\d|3[01])\d{3}[\dXx](?!\d)"
)
_MILVUS_COLLECTION = re.compile(r"^[A-Za-z_][A-Za-z0-9_]{0,254}$")

EMBEDDING_PROFILES = {
    "bge-m3": "BAAI/bge-m3",
    "qwen3-embedding-0.6b": "Qwen/Qwen3-Embedding-0.6B",
}


@dataclass(frozen=True)
class RagSettings:
    mode: str
    embedding_provider: str = "openai-compatible"
    embedding_base_url: str = ""
    embedding_api_key: str = ""
    embedding_model: str = ""
    milvus_uri: str = ""
    milvus_collection: str = ""
    deterministic_dimension: int = 64
    index_concurrency: int = 4
    timeout_seconds: float = 20.0
    batch_token_limit: int | None = None
    daily_token_limit: int | None = None
    batch_cost_limit: Decimal | None = None
    daily_cost_limit: Decimal | None = None
    price_per_million_tokens: Decimal | None = None
    price_version: str = ""
    embedding_profile: str = ""
    stub_redis_prefix: str = "foodmate:rag:stub"

    @property
    def index_namespace(self) -> str:
        """返回不包含凭据的索引身份，隔离模型、集合和 stub 命名空间。"""
        identity = "\x1f".join(
            (
                self.mode,
                self.embedding_provider,
                self.embedding_profile,
                self.embedding_model,
                self.milvus_collection,
                self.stub_redis_prefix,
            )
        )
        return "idx_" + hashlib.sha256(identity.encode("utf-8")).hexdigest()[:32]

    @property
    def index_fingerprint(self) -> str:
        """返回用于阻止 Milvus 混写的、不包含凭据的 embedding 身份。"""
        identity = "\x1f".join(
            (
                self.mode,
                self.embedding_provider,
                self.embedding_profile,
                self.embedding_model,
            )
        )
        return "rag_" + hashlib.sha256(identity.encode("utf-8")).hexdigest()[:32]

    @classmethod
    def from_environment(cls, environment: dict[str, str] | None = None) -> "RagSettings":
        env = environment if environment is not None else os.environ
        mode = env.get("FOODMATE_RAG_MODE", "stub").strip().lower()
        if mode not in {"stub", "local"}:
            raise RagError("RAG_MODE_INVALID", "FOODMATE_RAG_MODE must be stub or local")
        concurrency = _integer(env.get("FOODMATE_RAG_INDEX_CONCURRENCY", "4"), "RAG_INDEX_CONCURRENCY_INVALID")
        if not 1 <= concurrency <= 8:
            raise RagError("RAG_INDEX_CONCURRENCY_INVALID", "index concurrency must be between 1 and 8")
        deterministic_dimension = _integer(
            env.get("FOODMATE_RAG_DETERMINISTIC_DIMENSION", "64"),
            "RAG_DETERMINISTIC_DIMENSION_INVALID",
        )
        if not 8 <= deterministic_dimension <= 4096:
            raise RagError("RAG_DETERMINISTIC_DIMENSION_INVALID", "deterministic dimension must be between 8 and 4096")
        if mode == "stub":
            # Stub is deliberately isolated from every paid or vector dependency.
            # Do not even retain externally supplied credentials in process state.
            provider = "deterministic"
            profile = ""
            embedding_base_url = ""
            embedding_api_key = ""
            embedding_model = "deterministic-local-v1"
            milvus_uri = ""
            milvus_collection = ""
        else:
            provider = env.get("FOODMATE_RAG_EMBEDDING_PROVIDER", "openai-compatible").strip().lower()
            if provider not in {"openai-compatible", "deterministic"}:
                raise RagError("RAG_EMBEDDING_PROVIDER_INVALID", "embedding provider is invalid")
            profile = env.get("FOODMATE_RAG_EMBEDDING_PROFILE", "").strip().lower()
            if profile and profile not in EMBEDDING_PROFILES:
                raise RagError("RAG_EMBEDDING_PROFILE_INVALID", "embedding profile is invalid")
            if profile and provider != "openai-compatible":
                raise RagError(
                    "RAG_EMBEDDING_PROFILE_PROVIDER_MISMATCH",
                    "embedding profile requires the OpenAI-compatible provider",
                )
            embedding_model = env.get("FOODMATE_RAG_EMBEDDING_MODEL", "").strip()
            if provider == "deterministic" and not embedding_model:
                embedding_model = "deterministic-local-v1"
            if profile:
                profile_model = EMBEDDING_PROFILES[profile]
                if embedding_model and embedding_model != profile_model:
                    raise RagError(
                        "RAG_EMBEDDING_PROFILE_MISMATCH",
                        "embedding profile and model do not match",
                    )
                embedding_model = profile_model
            embedding_base_url = env.get("FOODMATE_RAG_EMBEDDING_BASE_URL", "").strip()
            embedding_api_key = env.get("FOODMATE_RAG_EMBEDDING_API_KEY", "").strip()
            milvus_uri = env.get("FOODMATE_RAG_MILVUS_URI", "").strip()
            milvus_collection = env.get("FOODMATE_RAG_MILVUS_COLLECTION", "").strip()
            if provider == "deterministic":
                # 确定性模式不保留任何付费 provider 凭据，避免后续适配器误用。
                embedding_base_url = ""
                embedding_api_key = ""
        settings = cls(
            mode=mode,
            embedding_provider=provider,
            embedding_base_url=embedding_base_url,
            embedding_api_key=embedding_api_key,
            embedding_model=embedding_model,
            milvus_uri=milvus_uri,
            milvus_collection=milvus_collection,
            deterministic_dimension=deterministic_dimension,
            index_concurrency=concurrency,
            timeout_seconds=_positive_float(
                env.get("FOODMATE_RAG_ITEM_TIMEOUT_SECONDS", "20"),
                "RAG_ITEM_TIMEOUT_INVALID",
            ),
            batch_token_limit=_optional_integer(env.get("FOODMATE_RAG_BATCH_TOKEN_LIMIT", "")),
            daily_token_limit=_optional_integer(env.get("FOODMATE_RAG_DAILY_TOKEN_LIMIT", "")),
            batch_cost_limit=_optional_decimal(env.get("FOODMATE_RAG_BATCH_COST_LIMIT", "")),
            daily_cost_limit=_optional_decimal(env.get("FOODMATE_RAG_DAILY_COST_LIMIT", "")),
            price_per_million_tokens=_optional_decimal(env.get("FOODMATE_RAG_PRICE_PER_MILLION_TOKENS", "")),
            price_version=env.get("FOODMATE_RAG_PRICE_VERSION", "").strip(),
            embedding_profile=profile,
            stub_redis_prefix=(
                env.get("FOODMATE_RAG_STUB_REDIS_PREFIX", "foodmate:rag:stub").strip()
                if mode == "stub"
                else ""
            ),
        )
        if settings.milvus_collection and not _MILVUS_COLLECTION.fullmatch(settings.milvus_collection):
            raise RagError(
                "RAG_MILVUS_COLLECTION_INVALID",
                "Milvus collection name must contain only letters, numbers, and underscores",
            )
        if mode == "local":
            if provider == "openai-compatible":
                for code, value in {
                    "RAG_EMBEDDING_BASE_URL_MISSING": settings.embedding_base_url,
                    "RAG_EMBEDDING_API_KEY_MISSING": settings.embedding_api_key,
                    "RAG_EMBEDDING_MODEL_MISSING": settings.embedding_model,
                }.items():
                    if not value:
                        raise RagError(code, "local RAG configuration is incomplete")
                _validate_http_endpoint(
                    settings.embedding_base_url, "RAG_EMBEDDING_BASE_URL_INVALID"
                )
            required = {
                "RAG_MILVUS_URI_MISSING": settings.milvus_uri,
                "RAG_MILVUS_COLLECTION_MISSING": settings.milvus_collection,
                "RAG_BATCH_TOKEN_LIMIT_MISSING": settings.batch_token_limit,
                "RAG_DAILY_TOKEN_LIMIT_MISSING": settings.daily_token_limit,
                "RAG_BATCH_COST_LIMIT_MISSING": settings.batch_cost_limit,
                "RAG_DAILY_COST_LIMIT_MISSING": settings.daily_cost_limit,
                "RAG_PRICE_MISSING": settings.price_per_million_tokens,
                "RAG_PRICE_VERSION_MISSING": settings.price_version,
            }
            for code, value in required.items():
                if value is None or value == "":
                    raise RagError(code, "local RAG configuration is incomplete")
            if (
                provider == "openai-compatible"
                and settings.price_per_million_tokens is not None
                and settings.price_per_million_tokens <= 0
            ):
                raise RagError(
                    "RAG_PRICE_INVALID",
                    "real embedding price must be greater than zero",
                )
        return settings


def _integer(value: str, code: str) -> int:
    try:
        return int(value)
    except ValueError as error:
        raise RagError(code, "invalid integer") from error


def _optional_integer(value: str) -> int | None:
    if not value.strip():
        return None
    parsed = _integer(value, "RAG_BUDGET_INVALID")
    if parsed < 0:
        raise RagError("RAG_BUDGET_INVALID", "budget must be non-negative")
    return parsed


def _positive_float(value: str, code: str) -> float:
    try:
        parsed = float(value)
    except (TypeError, ValueError) as error:
        raise RagError(code, "invalid positive number") from error
    if not math.isfinite(parsed) or parsed <= 0:
        raise RagError(code, "value must be a positive finite number")
    return parsed


def _optional_decimal(value: str) -> Decimal | None:
    if not value.strip():
        return None
    try:
        parsed = Decimal(value)
    except InvalidOperation as error:
        raise RagError("RAG_BUDGET_INVALID", "invalid decimal") from error
    if not parsed.is_finite() or parsed < 0:
        raise RagError("RAG_BUDGET_INVALID", "budget must be non-negative")
    return parsed


def _validate_http_endpoint(value: str, code: str) -> None:
    """Reject non-HTTP endpoints and credentials embedded in a URL."""
    endpoint = urlsplit(value)
    if (
        endpoint.scheme not in {"http", "https"}
        or not endpoint.hostname
        or endpoint.username is not None
        or endpoint.password is not None
        or endpoint.query
        or endpoint.fragment
    ):
        raise RagError(
            code,
            "embedding endpoint must be an HTTP or HTTPS URL without credentials",
        )


@dataclass(frozen=True)
class KnowledgeChunk:
    embedding_id: str
    document_id: str
    version: str
    sequence: int
    section_path: str
    text: str
    tenant_id: int = 0
    scope: str = PUBLIC_SCOPE
    visibility: str = "published"
    indexed: bool = True
    deleted: bool = False
    current_version: bool = True


@dataclass(frozen=True)
class Citation:
    document_id: str
    title: str
    version: str
    section_path: str
    chunk_id: str
    snippet: str


@dataclass(frozen=True)
class DeletionResult:
    """版本范围删除后返回的后端无关清理事实。"""

    backend: str
    deleted_count: int
    verified_absent: bool


class EmbeddingProvider(Protocol):
    def embed(self, inputs: list[str]) -> list[list[float]]:
        ...


@dataclass(frozen=True)
class EmbeddingResult:
    """Embedding 向量及供应商可选的用量事实。"""

    vectors: list[list[float]]
    token_count: int | None = None
    provider_request_id: str | None = None


def chunk_markdown(text: str, document_id: str, version: str, max_chars: int = 900) -> list[KnowledgeChunk]:
    normalized = text.replace("\r\n", "\n").replace("\r", "\n").strip()
    if not normalized:
        raise RagError("RAG_EMPTY_DOCUMENT", "document contains no indexable text")
    heading = ""
    chunks: list[KnowledgeChunk] = []
    buffer = ""
    for line in normalized.split("\n"):
        if line.startswith("#"):
            if buffer.strip():
                chunks.extend(_split_chunk(buffer.strip(), document_id, version, heading, len(chunks), max_chars))
                buffer = ""
            heading = line.lstrip("#").strip() or heading
        else:
            buffer += line + "\n"
    if buffer.strip():
        chunks.extend(_split_chunk(buffer.strip(), document_id, version, heading, len(chunks), max_chars))
    return chunks


def _split_chunk(text: str, document_id: str, version: str, section: str, offset: int, max_chars: int) -> list[KnowledgeChunk]:
    parts = [text[index:index + max_chars].strip() for index in range(0, len(text), max_chars)]
    return [KnowledgeChunk(_embedding_id(document_id, version, offset + index), document_id, version, offset + index, section, part)
            for index, part in enumerate(parts) if part]


def _embedding_id(document_id: str, version: str, sequence: int) -> str:
    return "emb_" + hashlib.sha256(f"{document_id}:{version}:{sequence}".encode()).hexdigest()[:32]


class StubIndex:
    """Deterministic in-memory backend used for local and unit-test flows only."""

    def __init__(self):
        self._chunks: dict[str, tuple[str, KnowledgeChunk]] = {}

    def upsert(self, title: str, chunks: Iterable[KnowledgeChunk]) -> None:
        chunks = list(chunks)
        current_ids = {chunk.embedding_id for chunk in chunks}
        for embedding_id, (_, existing) in list(self._chunks.items()):
            if (
                existing.document_id == (chunks[0].document_id if chunks else "")
                and existing.version == (chunks[0].version if chunks else "")
                and embedding_id not in current_ids
            ):
                del self._chunks[embedding_id]
        for chunk in chunks:
            self._chunks[chunk.embedding_id] = (title, chunk)

    def search(self, query: str, scope: str = PUBLIC_SCOPE) -> list[Citation]:
        if scope != PUBLIC_SCOPE:
            raise RagError("RAG_SCOPE_DENIED", "only public_published scope is supported")
        terms = set(_tokens(query))
        scored = []
        for title, chunk in self._chunks.values():
            if chunk.tenant_id != 0 or chunk.scope != PUBLIC_SCOPE or chunk.visibility != "published" or not chunk.indexed or chunk.deleted or not chunk.current_version:
                continue
            score = len(terms.intersection(_tokens(chunk.text)))
            if score:
                scored.append((score, title, chunk))
        scored.sort(key=lambda entry: (-entry[0], entry[2].embedding_id))
        per_document: dict[str, int] = {}
        citations: list[Citation] = []
        for _, title, chunk in scored[:12]:
            if per_document.get(chunk.document_id, 0) >= 2:
                continue
            per_document[chunk.document_id] = per_document.get(chunk.document_id, 0) + 1
            citations.append(Citation(chunk.document_id, title, chunk.version, chunk.section_path, chunk.embedding_id, _snippet(chunk.text)))
            if len(citations) == 4:
                break
        return citations

    def delete_document(self, document_id: str, version: str) -> DeletionResult:
        deleted = 0
        for embedding_id, (_, chunk) in list(self._chunks.items()):
            if str(chunk.document_id) == str(document_id) and str(chunk.version) == str(version):
                del self._chunks[embedding_id]
                deleted += 1
        remaining = any(
            str(chunk.document_id) == str(document_id) and str(chunk.version) == str(version)
            for _, chunk in self._chunks.values()
        )
        return DeletionResult("memory", deleted, not remaining)


class RedisStubIndex:
    """Shared deterministic public index. Redis is the stub mode's durable search backend."""

    def __init__(self, client=None, prefix: str | None = None):
        import redis
        self.client = client or redis.Redis.from_url(os.getenv("FOODMATE_REDIS_URL", "redis://:foodmate-redis-change-me@localhost:6380"), decode_responses=True)
        self.prefix = prefix or os.getenv("FOODMATE_RAG_STUB_REDIS_PREFIX", "foodmate:rag:stub")

    def upsert(self, title: str, chunks: Iterable[KnowledgeChunk]) -> None:
        chunks = list(chunks)
        if not chunks:
            return
        current_ids = {chunk.embedding_id for chunk in chunks}
        pipeline = self.client.pipeline()
        for chunk_id, raw in self.client.hgetall(f"{self.prefix}:chunks").items():
            value = json.loads(raw)
            if (
                str(value.get("document_id")) == str(chunks[0].document_id)
                and str(value.get("version")) == str(chunks[0].version)
                and chunk_id not in current_ids
            ):
                pipeline.hdel(f"{self.prefix}:chunks", chunk_id)
        for chunk in chunks:
            payload = {"title": title, "document_id": chunk.document_id, "version": chunk.version, "section_path": chunk.section_path, "text": chunk.text, "tenant_id": 0, "scope": PUBLIC_SCOPE, "visibility": "draft", "indexed": True, "deleted": False, "current_version": chunk.current_version}
            pipeline.hset(f"{self.prefix}:chunks", chunk.embedding_id, json.dumps(payload, ensure_ascii=False))
        pipeline.execute()

    def update_visibility(self, document_id: str, visibility: str, current_version: bool = True, version: str | None = None) -> None:
        if visibility not in {"published", "draft", "disabled", "deleted"}:
            raise RagError("RAG_VISIBILITY_INVALID", "visibility is invalid")
        values = self.client.hgetall(f"{self.prefix}:chunks")
        pipeline = self.client.pipeline()
        for chunk_id, raw in values.items():
            value = json.loads(raw)
            if (
                str(value.get("document_id")) == str(document_id)
                and (version is None or str(value.get("version")) == str(version))
            ):
                value["visibility"] = visibility
                value["deleted"] = visibility == "deleted"
                value["current_version"] = current_version
                pipeline.hset(f"{self.prefix}:chunks", chunk_id, json.dumps(value, ensure_ascii=False))
        pipeline.execute()

    def delete_document(self, document_id: str, version: str) -> DeletionResult:
        values = self.client.hgetall(f"{self.prefix}:chunks")
        pipeline = self.client.pipeline()
        matching = []
        for chunk_id, raw in values.items():
            value = json.loads(raw)
            if str(value.get("document_id")) == str(document_id) and str(value.get("version")) == str(version):
                matching.append(chunk_id)
                pipeline.hdel(f"{self.prefix}:chunks", chunk_id)
        pipeline.execute()
        remaining = self.client.hgetall(f"{self.prefix}:chunks")
        verified_absent = not any(
            str(json.loads(raw).get("document_id")) == str(document_id)
            and str(json.loads(raw).get("version")) == str(version)
            for raw in remaining.values()
        )
        return DeletionResult("redis", len(matching), verified_absent)

    def search(self, query: str, scope: str = PUBLIC_SCOPE) -> list[Citation]:
        if scope != PUBLIC_SCOPE:
            raise RagError("RAG_SCOPE_DENIED", "only public_published scope is supported")
        terms = set(_tokens(query))
        ranked = []
        for chunk_id, raw in self.client.hgetall(f"{self.prefix}:chunks").items():
            value = json.loads(raw)
            if value.get("tenant_id") != 0 or value.get("scope") != PUBLIC_SCOPE or value.get("visibility") != "published" or not value.get("indexed") or value.get("deleted") or not value.get("current_version", True):
                continue
            score = len(terms.intersection(_tokens(value.get("text", ""))))
            if score: ranked.append((score, chunk_id, value))
        ranked.sort(key=lambda row: (-row[0], row[1]))
        result, per_document = [], {}
        for _, chunk_id, value in ranked[:12]:
            document_id = str(value["document_id"])
            if per_document.get(document_id, 0) >= 2: continue
            per_document[document_id] = per_document.get(document_id, 0) + 1
            result.append(Citation(document_id, str(value["title"]), str(value["version"]), str(value.get("section_path", "")), chunk_id, _snippet(str(value["text"]))))
            if len(result) == 4: break
        return result


def _tokens(value: str) -> list[str]:
    tokens: list[str] = []
    for token in _WORD.findall(value):
        if token and all("\u4e00" <= char <= "\u9fff" for char in token):
            # Chinese text has no spaces; overlapping bigrams keep short user
            # queries searchable without introducing a language model dependency.
            tokens.extend(token[index : index + 2] for index in range(len(token) - 1))
        else:
            tokens.append(token.lower())
    return tokens


def _snippet(value: str, limit: int = 240) -> str:
    return " ".join(value.split())[:limit]


class OpenAICompatibleEmbedder:
    def __init__(self, settings: RagSettings):
        if settings.mode != "local" or settings.embedding_provider != "openai-compatible":
            raise RagError("RAG_EMBEDDING_PROVIDER_MISMATCH", "OpenAI-compatible embedder requires its explicit local provider")
        _validate_http_endpoint(settings.embedding_base_url, "RAG_EMBEDDING_BASE_URL_INVALID")
        self.settings = settings

    def embed(self, inputs: list[str]) -> list[list[float]]:
        return self.embed_with_usage(inputs).vectors

    def embed_with_usage(self, inputs: list[str]) -> EmbeddingResult:
        if not inputs or any(not isinstance(value, str) or not value.strip() for value in inputs):
            raise RagError("RAG_EMBEDDING_INVALID_INPUT", "embedding input must not be empty")
        request = urllib.request.Request(
            self._url(),
            data=json.dumps(
                {
                    "model": self.settings.embedding_model,
                    "input": inputs,
                    "encoding_format": "float",
                }
            ).encode("utf-8"),
            method="POST",
            headers={"Content-Type": "application/json", "Authorization": "Bearer " + self.settings.embedding_api_key},
        )
        try:
            with urllib.request.urlopen(request, timeout=self.settings.timeout_seconds) as response:
                raw_payload = response.read()
            payload = json.loads(raw_payload.decode("utf-8"))
        except urllib.error.HTTPError as error:
            raise RagError(
                _embedding_http_error_code(error.code),
                _embedding_http_error_message(error.code),
            ) from error
        except (urllib.error.URLError, TimeoutError) as error:
            raise RagError("RAG_EMBEDDING_UNAVAILABLE", "embedding endpoint is unavailable") from error
        except (UnicodeDecodeError, json.JSONDecodeError) as error:
            raise RagError("RAG_EMBEDDING_INVALID_RESPONSE", "invalid embedding response") from error
        try:
            if not isinstance(payload, dict):
                raise TypeError("embedding response must be an object")
            data = payload["data"]
            if not isinstance(data, list):
                raise TypeError("embedding data must be a list")
            indexed = []
            for position, item in enumerate(data):
                if not isinstance(item, dict):
                    raise TypeError("embedding item must be an object")
                raw_index = item.get("index", position)
                if isinstance(raw_index, bool):
                    raise TypeError("embedding index must be an integer")
                index = int(raw_index)
                vector = item["embedding"]
                if not isinstance(vector, (list, tuple)):
                    raise TypeError("embedding vector must be an array")
                indexed.append((index, vector))
            if sorted(index for index, _ in indexed) != list(range(len(inputs))):
                raise ValueError("embedding indexes are not a complete sequence")
            vectors = [list(map(float, vector)) for _, vector in sorted(indexed)]
            token_count = _embedding_token_count(payload.get("usage"))
            provider_request_id = _optional_response_id(payload.get("id"))
        except (KeyError, TypeError, ValueError) as error:
            raise RagError("RAG_EMBEDDING_INVALID_RESPONSE", "invalid embedding response") from error
        dimension = len(vectors[0]) if vectors else 0
        if (
            len(vectors) != len(inputs)
            or not vectors
            or dimension == 0
            or any(
                len(vector) != dimension
                or not all(math.isfinite(item) for item in vector)
                for vector in vectors
            )
        ):
            raise RagError("RAG_EMBEDDING_INVALID_RESPONSE", "embedding count or values are invalid")
        return EmbeddingResult(vectors, token_count, provider_request_id)

    def _url(self) -> str:
        return self.settings.embedding_base_url if self.settings.embedding_base_url.endswith("/embeddings") else self.settings.embedding_base_url.rstrip("/") + "/embeddings"


def _embedding_http_error_code(status_code: int) -> str:
    """把供应商 HTTP 状态转换为不泄露响应正文的稳定错误码。"""
    if status_code in {408, 425}:
        return "RAG_EMBEDDING_TIMEOUT"
    if status_code == 429:
        return "RAG_EMBEDDING_RATE_LIMITED"
    if 500 <= status_code <= 599:
        return "RAG_EMBEDDING_UNAVAILABLE"
    if status_code in {401, 403}:
        return "RAG_EMBEDDING_AUTH_FAILED"
    return "RAG_EMBEDDING_REJECTED"


def _embedding_http_error_message(status_code: int) -> str:
    """返回固定错误摘要，禁止把外部服务响应原文带入业务事实。"""
    messages = {
        "RAG_EMBEDDING_TIMEOUT": "embedding endpoint timed out",
        "RAG_EMBEDDING_RATE_LIMITED": "embedding endpoint rate limited the request",
        "RAG_EMBEDDING_UNAVAILABLE": "embedding endpoint is unavailable",
        "RAG_EMBEDDING_AUTH_FAILED": "embedding endpoint authentication failed",
        "RAG_EMBEDDING_REJECTED": "embedding endpoint rejected request",
    }
    return messages[_embedding_http_error_code(status_code)]


def _embedding_token_count(usage: object) -> int | None:
    """读取兼容接口的输入用量；供应商未返回 usage 时保留未知状态。"""
    if usage is None:
        return None
    if not isinstance(usage, dict):
        raise TypeError("embedding usage must be an object")
    total = usage.get("total_tokens", usage.get("prompt_tokens"))
    if total is None or isinstance(total, bool) or not isinstance(total, int) or total < 0:
        raise ValueError("embedding usage token count is invalid")
    prompt = usage.get("prompt_tokens")
    if prompt is not None and (isinstance(prompt, bool) or not isinstance(prompt, int) or prompt < 0):
        raise ValueError("embedding prompt token count is invalid")
    if prompt is not None and total < prompt:
        raise ValueError("embedding total token count is invalid")
    return total


def _optional_response_id(value: object) -> str | None:
    """只接受短字符串请求标识，避免把异常响应内容带入业务事实。"""
    if value is None:
        return None
    if not isinstance(value, str) or not value.strip() or len(value) > 256:
        raise ValueError("embedding response id is invalid")
    return value.strip()


class DeterministicEmbedder:
    """Generate stable lexical vectors for local Milvus business tests."""

    def __init__(self, settings: RagSettings):
        if settings.mode != "local" or settings.embedding_provider != "deterministic":
            raise RagError("RAG_EMBEDDING_PROVIDER_MISMATCH", "deterministic embedder requires its explicit local provider")
        self.dimension = settings.deterministic_dimension
        self.model_version = settings.embedding_model

    def embed(self, inputs: list[str]) -> list[list[float]]:
        if not inputs:
            raise RagError("RAG_EMBEDDING_INVALID_INPUT", "embedding input must not be empty")
        return [self._embed_one(value) for value in inputs]

    def _embed_one(self, value: str) -> list[float]:
        tokens = _tokens(value)
        if not tokens:
            tokens = [value.strip() or "empty"]
        vector = [0.0] * self.dimension
        for token in tokens:
            digest = hashlib.blake2b(token.encode("utf-8"), digest_size=16).digest()
            bucket = int.from_bytes(digest[:8], "big") % self.dimension
            sign = 1.0 if digest[8] & 1 else -1.0
            vector[bucket] += sign
        norm = math.sqrt(sum(item * item for item in vector))
        return [item / norm for item in vector] if norm else [1.0] + [0.0] * (self.dimension - 1)


def build_local_embedder(settings: RagSettings) -> EmbeddingProvider:
    """Create exactly the configured local provider without implicit fallback."""
    if settings.mode != "local":
        raise RagError("RAG_MODE_INVALID", "local embedding provider requires local mode")
    if settings.embedding_provider == "deterministic":
        return DeterministicEmbedder(settings)
    if settings.embedding_provider == "openai-compatible":
        return OpenAICompatibleEmbedder(settings)
    raise RagError("RAG_EMBEDDING_PROVIDER_INVALID", "embedding provider is invalid")


def safe_object_key(key: str) -> str:
    path = PurePosixPath(key)
    if path.is_absolute() or ".." in path.parts or not key.startswith("knowledge/"):
        raise RagError("RAG_OBJECT_KEY_DENIED", "object key is outside knowledge namespace")
    return str(path)


def parse_document(filename: str, content: bytes) -> str:
    """Extract text without interpreting document macros, external links, or scripts."""
    suffix = PurePosixPath(filename).suffix.lower()
    if not content:
        raise RagError("RAG_EMPTY_DOCUMENT", "document is empty")
    if suffix in {".md", ".txt"}:
        try:
            return _reject_personal_data(content.decode("utf-8").strip())
        except UnicodeDecodeError as error:
            raise RagError("RAG_TEXT_ENCODING_INVALID", "text document must be UTF-8") from error
    if suffix == ".pdf":
        if not content.startswith(b"%PDF-"):
            raise RagError("RAG_FILE_SIGNATURE_INVALID", "PDF signature is invalid")
        try:
            from pypdf import PdfReader
            reader = PdfReader(io.BytesIO(content), strict=True)
            if _pdf_has_unsafe_actions(reader):
                raise RagError("RAG_PDF_UNSAFE", "PDF contains an executable or external action")
            return _reject_personal_data(
                "\n".join(page.extract_text() or "" for page in reader.pages).strip()
            )
        except ImportError as error:
            raise RagError("RAG_PDF_PARSER_UNAVAILABLE", "pypdf is not installed") from error
        except RagError:
            raise
        except Exception as error:
            raise RagError("RAG_PDF_PARSE_FAILED", "PDF could not be parsed safely") from error
    if suffix == ".docx":
        if not content.startswith(b"PK\x03\x04"):
            raise RagError("RAG_FILE_SIGNATURE_INVALID", "DOCX signature is invalid")
        try:
            with zipfile.ZipFile(io.BytesIO(content)) as archive:
                names = set(archive.namelist())
                if (
                    "word/document.xml" not in names
                    or any(
                        name.endswith("vbaProject.bin")
                        or name.startswith(("word/embeddings/", "word/activeX/", "word/webExtensions/"))
                        for name in names
                    )
                ):
                    raise RagError("RAG_DOCX_UNSAFE", "DOCX macro or document body is invalid")
                for name in names:
                    if name.endswith(".rels"):
                        relationships = ElementTree.fromstring(archive.read(name))
                        if any(
                            relationship.attrib.get("TargetMode", "").lower() == "external"
                            for relationship in relationships
                        ):
                            raise RagError("RAG_DOCX_EXTERNAL_LINK", "DOCX contains an external relationship")
                root = ElementTree.fromstring(archive.read("word/document.xml"))
                return _reject_personal_data(
                    "\n".join(
                        "".join(node.itertext()).strip()
                        for node in root.findall(
                            ".//{http://schemas.openxmlformats.org/wordprocessingml/2006/main}p"
                        )
                        if "".join(node.itertext()).strip()
                    ).strip()
                )
        except RagError:
            raise
        except (OSError, zipfile.BadZipFile, ElementTree.ParseError) as error:
            raise RagError("RAG_DOCX_PARSE_FAILED", "DOCX could not be parsed safely") from error
    raise RagError("RAG_DOCUMENT_TYPE_UNSUPPORTED", "unsupported knowledge document type")


def _reject_personal_data(text: str) -> str:
    """Keep basic personal identifiers out of the public knowledge index."""
    if _EMAIL.search(text) or _MOBILE.search(text) or _CHINA_ID.search(text):
        raise RagError("RAG_PII_DETECTED", "document contains a personal identifier")
    return text


def _pdf_has_unsafe_actions(reader) -> bool:
    """Inspect PDF objects without executing actions or resolving external URLs."""
    unsafe_keys = {"/JS", "/JavaScript", "/OpenAction", "/AA", "/Launch", "/URI"}
    seen: set[int] = set()

    def walk(value) -> bool:
        try:
            value = value.get_object()
        except AttributeError:
            pass
        marker = id(value)
        if marker in seen:
            return False
        seen.add(marker)
        if hasattr(value, "keys"):
            for key in value.keys():
                if str(key) in unsafe_keys or walk(value[key]):
                    return True
        elif isinstance(value, (list, tuple)):
            return any(walk(item) for item in value)
        return False

    return walk(reader.trailer)


class MilvusIndex:
    """The local-mode vector backend; stub mode never instantiates this class."""

    def __init__(self, settings: RagSettings):
        if settings.mode != "local":
            raise RagError("RAG_MODE_INVALID", "Milvus is only available in local mode")
        try:
            from pymilvus import MilvusClient
            self.client = MilvusClient(uri=settings.milvus_uri)
        except ImportError as error:
            raise RagError("RAG_MILVUS_UNAVAILABLE", "pymilvus is not installed") from error
        except Exception as error:
            raise RagError("RAG_MILVUS_UNAVAILABLE", "Milvus is unavailable") from error
        self.collection = settings.milvus_collection
        self.index_fingerprint = settings.index_fingerprint

    def _ensure_collection(self, dimension: int) -> None:
        try:
            if not self.client.has_collection(self.collection):
                self.client.create_collection(
                    self.collection,
                    dimension=dimension,
                    primary_field_name="embedding_id",
                    id_type="string",
                    max_length=128,
                    vector_field_name="vector",
                    metric_type="COSINE",
                    auto_id=False,
                    enable_dynamic_field=True,
                )
                return
            description = self.client.describe_collection(self.collection)
            fields = description.get("fields") or description.get("schema", {}).get("fields", [])
            vector = next((field for field in fields if field.get("name") == "vector"), None)
            actual = (vector or {}).get("params", {}).get("dim") or (vector or {}).get("params", {}).get("dimension")
            if actual is not None and int(actual) != dimension:
                raise RagError("RAG_MILVUS_DIMENSION_MISMATCH", "Milvus vector dimension does not match embedding model")
            self._verify_collection_identity()
        except RagError:
            raise
        except Exception as error:
            raise RagError("RAG_MILVUS_UNAVAILABLE", "Milvus collection is unavailable") from error

    def _verify_collection_identity(self) -> None:
        """拒绝把不同 embedding 模式或模型写入同一个已有 collection。"""
        expected = getattr(self, "index_fingerprint", "")
        if not expected:
            return
        try:
            rows = self.client.query(
                collection_name=self.collection,
                filter="",
                output_fields=["embedding_id", "embedding_fingerprint"],
                limit=1,
            )
        except Exception as error:
            raise RagError(
                "RAG_MILVUS_METADATA_UNAVAILABLE",
                "Milvus embedding identity metadata is unavailable",
            ) from error
        if not rows:
            return
        actual = str(rows[0].get("embedding_fingerprint", "")).strip()
        if not actual:
            raise RagError(
                "RAG_MILVUS_MODEL_MISMATCH",
                "Milvus collection has no embedding identity metadata",
            )
        if actual != expected:
            raise RagError(
                "RAG_MILVUS_MODEL_MISMATCH",
                "Milvus collection embedding identity does not match the configured provider",
            )

    def upsert(self, title: str, chunks: Iterable[KnowledgeChunk], vectors: list[list[float]]) -> None:
        chunks = list(chunks)
        if not vectors or len(chunks) != len(vectors) or any(len(vector) != len(vectors[0]) for vector in vectors):
            raise RagError("RAG_EMBEDDING_INVALID_RESPONSE", "embedding dimensions are inconsistent")
        self._ensure_collection(len(vectors[0]))
        rows = []
        for chunk, vector in zip(chunks, vectors, strict=True):
            rows.append({"embedding_id": chunk.embedding_id, "vector": vector, "embedding_fingerprint": getattr(self, "index_fingerprint", ""), "document_id": chunk.document_id, "title": title, "version": chunk.version, "section_path": chunk.section_path, "text": chunk.text, "tenant_id": 0, "scope": PUBLIC_SCOPE, "visibility": chunk.visibility, "indexed": chunk.indexed, "deleted": chunk.deleted, "current_version": chunk.current_version})
        try:
            self.client.upsert(collection_name=self.collection, data=rows)
            self._flush()
        except Exception as error:
            raise RagError("RAG_MILVUS_WRITE_FAILED", "Milvus upsert failed") from error

    def update_visibility(self, document_id: str, visibility: str, deleted: bool, current_version: bool = True, version: str | None = None) -> None:
        if visibility not in {"published", "draft", "disabled", "deleted"}:
            raise RagError("RAG_VISIBILITY_INVALID", "visibility is invalid")
        try:
            if not self.client.has_collection(self.collection):
                return
            version_filter = "" if version is None else f' and version == "{_milvus_string(version)}"'
            rows = self.client.query(collection_name=self.collection, filter=f'document_id == "{_milvus_string(document_id)}"{version_filter}', output_fields=["embedding_id", "vector", "embedding_fingerprint", "document_id", "title", "version", "section_path", "text", "tenant_id", "scope", "indexed", "visibility", "deleted", "current_version"])
            for row in rows:
                row["visibility"] = visibility
                row["deleted"] = deleted
                row["current_version"] = current_version
            if rows:
                self.client.upsert(collection_name=self.collection, data=rows)
                self._flush()
        except Exception as error:
            raise RagError("RAG_MILVUS_WRITE_FAILED", "Milvus visibility update failed") from error

    def delete_document(self, document_id: str, version: str) -> DeletionResult:
        try:
            if not self.client.has_collection(self.collection):
                return DeletionResult("milvus", 0, True)
            rows = self.client.query(
                collection_name=self.collection,
                filter=f'document_id == "{_milvus_string(document_id)}" and version == "{_milvus_string(version)}"',
                output_fields=["embedding_id"],
            )
            ids = [row["embedding_id"] for row in rows if row.get("embedding_id")]
            if ids:
                self.client.delete(collection_name=self.collection, ids=ids)
                self._flush()
            remaining = self.client.query(
                collection_name=self.collection,
                filter=f'document_id == "{_milvus_string(document_id)}" and version == "{_milvus_string(version)}"',
                output_fields=["embedding_id"],
            )
            return DeletionResult("milvus", len(ids), not remaining)
        except Exception as error:
            raise RagError("RAG_MILVUS_DELETE_FAILED", "Milvus vector delete failed") from error

    def _flush(self) -> None:
        flush = getattr(self.client, "flush", None)
        if callable(flush):
            flush(collection_name=self.collection)

    def search(self, query: str, embedder: EmbeddingProvider, scope: str = PUBLIC_SCOPE) -> list[Citation]:
        if scope != PUBLIC_SCOPE:
            raise RagError("RAG_SCOPE_DENIED", "only public_published scope is supported")
        vectors = embedder.embed([query])
        self._ensure_collection(len(vectors[0]))
        try:
            identity_filter = ""
            if getattr(self, "index_fingerprint", ""):
                identity_filter = f' and embedding_fingerprint == "{_milvus_string(self.index_fingerprint)}"'
            hits = self.client.search(
                collection_name=self.collection,
                data=vectors,
                anns_field="vector",
                filter='tenant_id == 0 and scope == "public_published" and visibility == "published" and indexed == true and deleted == false and current_version == true' + identity_filter,
                limit=12,
                output_fields=["embedding_id", "document_id", "title", "version", "section_path", "text", "current_version", "embedding_fingerprint"],
            )[0]
        except Exception as error:
            raise RagError("RAG_MILVUS_SEARCH_FAILED", "Milvus search failed") from error
        result: list[Citation] = []
        per_document: dict[str, int] = {}
        for hit in hits:
            entity = hit.get("entity", hit)
            document_id = str(entity.get("document_id"))
            if not document_id or per_document.get(document_id, 0) >= 2:
                continue
            per_document[document_id] = per_document.get(document_id, 0) + 1
            result.append(Citation(document_id, str(entity.get("title", "Knowledge")), str(entity.get("version", "1")), str(entity.get("section_path", "")), str(entity.get("embedding_id", hit.get("id", ""))), _snippet(str(entity.get("text", "")))))
            if len(result) == 4:
                break
        return result


def _milvus_string(value: str) -> str:
    """Escape string literals used in Milvus boolean expressions."""
    return str(value).replace("\\", "\\\\").replace('"', '\\"')

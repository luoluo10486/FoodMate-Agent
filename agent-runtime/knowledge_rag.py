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
from dataclasses import dataclass
from decimal import Decimal, InvalidOperation
from pathlib import PurePosixPath
from typing import Iterable


class RagError(RuntimeError):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code


PUBLIC_SCOPE = "public_published"
_WORD = re.compile(r"[A-Za-z0-9_]+|[\u4e00-\u9fff]+")


@dataclass(frozen=True)
class RagSettings:
    mode: str
    embedding_base_url: str = ""
    embedding_api_key: str = ""
    embedding_model: str = ""
    milvus_uri: str = ""
    milvus_collection: str = ""
    index_concurrency: int = 4
    timeout_seconds: float = 20.0
    batch_token_limit: int | None = None
    daily_token_limit: int | None = None
    batch_cost_limit: Decimal | None = None
    daily_cost_limit: Decimal | None = None
    price_per_million_tokens: Decimal | None = None
    price_version: str = ""

    @classmethod
    def from_environment(cls, environment: dict[str, str] | None = None) -> "RagSettings":
        env = environment if environment is not None else os.environ
        mode = env.get("FOODMATE_RAG_MODE", "stub").strip().lower()
        if mode not in {"stub", "local"}:
            raise RagError("RAG_MODE_INVALID", "FOODMATE_RAG_MODE must be stub or local")
        concurrency = _integer(env.get("FOODMATE_RAG_INDEX_CONCURRENCY", "4"), "RAG_INDEX_CONCURRENCY_INVALID")
        if not 1 <= concurrency <= 8:
            raise RagError("RAG_INDEX_CONCURRENCY_INVALID", "index concurrency must be between 1 and 8")
        settings = cls(
            mode=mode,
            embedding_base_url=env.get("FOODMATE_RAG_EMBEDDING_BASE_URL", "").strip(),
            embedding_api_key=env.get("FOODMATE_RAG_EMBEDDING_API_KEY", "").strip(),
            embedding_model=env.get("FOODMATE_RAG_EMBEDDING_MODEL", "").strip(),
            milvus_uri=env.get("FOODMATE_RAG_MILVUS_URI", "").strip(),
            milvus_collection=env.get("FOODMATE_RAG_MILVUS_COLLECTION", "").strip(),
            index_concurrency=concurrency,
            timeout_seconds=float(env.get("FOODMATE_RAG_ITEM_TIMEOUT_SECONDS", "20")),
            batch_token_limit=_optional_integer(env.get("FOODMATE_RAG_BATCH_TOKEN_LIMIT", "")),
            daily_token_limit=_optional_integer(env.get("FOODMATE_RAG_DAILY_TOKEN_LIMIT", "")),
            batch_cost_limit=_optional_decimal(env.get("FOODMATE_RAG_BATCH_COST_LIMIT", "")),
            daily_cost_limit=_optional_decimal(env.get("FOODMATE_RAG_DAILY_COST_LIMIT", "")),
            price_per_million_tokens=_optional_decimal(env.get("FOODMATE_RAG_PRICE_PER_MILLION_TOKENS", "")),
            price_version=env.get("FOODMATE_RAG_PRICE_VERSION", "").strip(),
        )
        if mode == "local":
            required = {
                "RAG_EMBEDDING_BASE_URL_MISSING": settings.embedding_base_url,
                "RAG_EMBEDDING_API_KEY_MISSING": settings.embedding_api_key,
                "RAG_EMBEDDING_MODEL_MISSING": settings.embedding_model,
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
        return settings


def _integer(value: str, code: str) -> int:
    try:
        return int(value)
    except ValueError as error:
        raise RagError(code, "invalid integer") from error


def _optional_integer(value: str) -> int | None:
    return None if not value.strip() else _integer(value, "RAG_BUDGET_INVALID")


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


@dataclass(frozen=True)
class Citation:
    document_id: str
    title: str
    version: str
    section_path: str
    chunk_id: str
    snippet: str


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
        for chunk in chunks:
            self._chunks[chunk.embedding_id] = (title, chunk)

    def search(self, query: str, scope: str = PUBLIC_SCOPE) -> list[Citation]:
        if scope != PUBLIC_SCOPE:
            raise RagError("RAG_SCOPE_DENIED", "only public_published scope is supported")
        terms = set(_tokens(query))
        scored = []
        for title, chunk in self._chunks.values():
            if chunk.tenant_id != 0 or chunk.scope != PUBLIC_SCOPE or chunk.visibility != "published" or not chunk.indexed or chunk.deleted:
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


def _tokens(value: str) -> list[str]:
    return [token.lower() for token in _WORD.findall(value)]


def _snippet(value: str, limit: int = 240) -> str:
    return " ".join(value.split())[:limit]


class OpenAICompatibleEmbedder:
    def __init__(self, settings: RagSettings):
        if settings.mode != "local":
            raise RagError("RAG_MODE_INVALID", "real embedder requires local mode")
        self.settings = settings

    def embed(self, inputs: list[str]) -> list[list[float]]:
        request = urllib.request.Request(
            self._url(), data=json.dumps({"model": self.settings.embedding_model, "input": inputs}).encode("utf-8"), method="POST",
            headers={"Content-Type": "application/json", "Authorization": "Bearer " + self.settings.embedding_api_key},
        )
        try:
            with urllib.request.urlopen(request, timeout=self.settings.timeout_seconds) as response:
                payload = json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as error:
            raise RagError("RAG_EMBEDDING_REJECTED", "embedding endpoint rejected request") from error
        except (urllib.error.URLError, TimeoutError) as error:
            raise RagError("RAG_EMBEDDING_UNAVAILABLE", "embedding endpoint is unavailable") from error
        try:
            vectors = [list(map(float, item["embedding"])) for item in payload["data"]]
        except (KeyError, TypeError, ValueError) as error:
            raise RagError("RAG_EMBEDDING_INVALID_RESPONSE", "invalid embedding response") from error
        if len(vectors) != len(inputs) or not vectors or any(not vector or not all(math.isfinite(item) for item in vector) for vector in vectors):
            raise RagError("RAG_EMBEDDING_INVALID_RESPONSE", "embedding count or values are invalid")
        return vectors

    def _url(self) -> str:
        return self.settings.embedding_base_url if self.settings.embedding_base_url.endswith("/embeddings") else self.settings.embedding_base_url.rstrip("/") + "/embeddings"


def safe_object_key(key: str) -> str:
    path = PurePosixPath(key)
    if path.is_absolute() or ".." in path.parts or not key.startswith("knowledge/"):
        raise RagError("RAG_OBJECT_KEY_DENIED", "object key is outside knowledge namespace")
    return str(path)

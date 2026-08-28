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

    @classmethod
    def from_environment(cls, environment: dict[str, str] | None = None) -> "RagSettings":
        env = environment if environment is not None else os.environ
        mode = env.get("FOODMATE_RAG_MODE", "stub").strip().lower()
        if mode not in {"stub", "local"}:
            raise RagError("RAG_MODE_INVALID", "FOODMATE_RAG_MODE must be stub or local")
        provider = env.get("FOODMATE_RAG_EMBEDDING_PROVIDER", "openai-compatible").strip().lower()
        if provider not in {"openai-compatible", "deterministic"}:
            raise RagError("RAG_EMBEDDING_PROVIDER_INVALID", "embedding provider is invalid")
        profile = env.get("FOODMATE_RAG_EMBEDDING_PROFILE", "").strip().lower()
        if profile and profile not in EMBEDDING_PROFILES:
            raise RagError("RAG_EMBEDDING_PROFILE_INVALID", "embedding profile is invalid")
        concurrency = _integer(env.get("FOODMATE_RAG_INDEX_CONCURRENCY", "4"), "RAG_INDEX_CONCURRENCY_INVALID")
        if not 1 <= concurrency <= 8:
            raise RagError("RAG_INDEX_CONCURRENCY_INVALID", "index concurrency must be between 1 and 8")
        deterministic_dimension = _integer(
            env.get("FOODMATE_RAG_DETERMINISTIC_DIMENSION", "64"),
            "RAG_DETERMINISTIC_DIMENSION_INVALID",
        )
        if not 8 <= deterministic_dimension <= 4096:
            raise RagError("RAG_DETERMINISTIC_DIMENSION_INVALID", "deterministic dimension must be between 8 and 4096")
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
        settings = cls(
            mode=mode,
            embedding_provider=provider,
            embedding_base_url=env.get("FOODMATE_RAG_EMBEDDING_BASE_URL", "").strip(),
            embedding_api_key=env.get("FOODMATE_RAG_EMBEDDING_API_KEY", "").strip(),
            embedding_model=embedding_model,
            milvus_uri=env.get("FOODMATE_RAG_MILVUS_URI", "").strip(),
            milvus_collection=env.get("FOODMATE_RAG_MILVUS_COLLECTION", "").strip(),
            deterministic_dimension=deterministic_dimension,
            index_concurrency=concurrency,
            timeout_seconds=float(env.get("FOODMATE_RAG_ITEM_TIMEOUT_SECONDS", "20")),
            batch_token_limit=_optional_integer(env.get("FOODMATE_RAG_BATCH_TOKEN_LIMIT", "")),
            daily_token_limit=_optional_integer(env.get("FOODMATE_RAG_DAILY_TOKEN_LIMIT", "")),
            batch_cost_limit=_optional_decimal(env.get("FOODMATE_RAG_BATCH_COST_LIMIT", "")),
            daily_cost_limit=_optional_decimal(env.get("FOODMATE_RAG_DAILY_COST_LIMIT", "")),
            price_per_million_tokens=_optional_decimal(env.get("FOODMATE_RAG_PRICE_PER_MILLION_TOKENS", "")),
            price_version=env.get("FOODMATE_RAG_PRICE_VERSION", "").strip(),
            embedding_profile=profile,
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
    current_version: bool = True


@dataclass(frozen=True)
class Citation:
    document_id: str
    title: str
    version: str
    section_path: str
    chunk_id: str
    snippet: str


class EmbeddingProvider(Protocol):
    def embed(self, inputs: list[str]) -> list[list[float]]:
        ...


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

    def delete_document(self, document_id: str, version: str) -> None:
        for embedding_id, (_, chunk) in list(self._chunks.items()):
            if str(chunk.document_id) == str(document_id) and str(chunk.version) == str(version):
                del self._chunks[embedding_id]


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

    def delete_document(self, document_id: str, version: str) -> None:
        values = self.client.hgetall(f"{self.prefix}:chunks")
        pipeline = self.client.pipeline()
        for chunk_id, raw in values.items():
            value = json.loads(raw)
            if str(value.get("document_id")) == str(document_id) and str(value.get("version")) == str(version):
                pipeline.hdel(f"{self.prefix}:chunks", chunk_id)
        pipeline.execute()

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
        self.settings = settings

    def embed(self, inputs: list[str]) -> list[list[float]]:
        if not inputs or any(not isinstance(value, str) or not value.strip() for value in inputs):
            raise RagError("RAG_EMBEDDING_INVALID_INPUT", "embedding input must not be empty")
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
            data = payload["data"]
            if not isinstance(data, list):
                raise TypeError("embedding data must be a list")
            indexed = []
            for position, item in enumerate(data):
                index = int(item.get("index", position))
                indexed.append((index, item["embedding"]))
            if sorted(index for index, _ in indexed) != list(range(len(inputs))):
                raise ValueError("embedding indexes are not a complete sequence")
            vectors = [list(map(float, vector)) for _, vector in sorted(indexed)]
        except (KeyError, TypeError, ValueError) as error:
            raise RagError("RAG_EMBEDDING_INVALID_RESPONSE", "invalid embedding response") from error
        if len(vectors) != len(inputs) or not vectors or any(not vector or not all(math.isfinite(item) for item in vector) for vector in vectors):
            raise RagError("RAG_EMBEDDING_INVALID_RESPONSE", "embedding count or values are invalid")
        return vectors

    def _url(self) -> str:
        return self.settings.embedding_base_url if self.settings.embedding_base_url.endswith("/embeddings") else self.settings.embedding_base_url.rstrip("/") + "/embeddings"


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
        except RagError:
            raise
        except Exception as error:
            raise RagError("RAG_MILVUS_UNAVAILABLE", "Milvus collection is unavailable") from error

    def upsert(self, title: str, chunks: Iterable[KnowledgeChunk], vectors: list[list[float]]) -> None:
        chunks = list(chunks)
        if not vectors or len(chunks) != len(vectors) or any(len(vector) != len(vectors[0]) for vector in vectors):
            raise RagError("RAG_EMBEDDING_INVALID_RESPONSE", "embedding dimensions are inconsistent")
        self._ensure_collection(len(vectors[0]))
        rows = []
        for chunk, vector in zip(chunks, vectors, strict=True):
            rows.append({"embedding_id": chunk.embedding_id, "vector": vector, "document_id": chunk.document_id, "title": title, "version": chunk.version, "section_path": chunk.section_path, "text": chunk.text, "tenant_id": 0, "scope": PUBLIC_SCOPE, "visibility": chunk.visibility, "indexed": chunk.indexed, "deleted": chunk.deleted, "current_version": chunk.current_version})
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
            rows = self.client.query(collection_name=self.collection, filter=f'document_id == "{_milvus_string(document_id)}"{version_filter}', output_fields=["embedding_id", "vector", "document_id", "title", "version", "section_path", "text", "tenant_id", "scope", "indexed", "visibility", "deleted", "current_version"])
            for row in rows:
                row["visibility"] = visibility
                row["deleted"] = deleted
                row["current_version"] = current_version
            if rows:
                self.client.upsert(collection_name=self.collection, data=rows)
                self._flush()
        except Exception as error:
            raise RagError("RAG_MILVUS_WRITE_FAILED", "Milvus visibility update failed") from error

    def delete_document(self, document_id: str, version: str) -> None:
        try:
            if not self.client.has_collection(self.collection):
                return
            rows = self.client.query(
                collection_name=self.collection,
                filter=f'document_id == "{_milvus_string(document_id)}" and version == "{_milvus_string(version)}"',
                output_fields=["embedding_id"],
            )
            ids = [row["embedding_id"] for row in rows if row.get("embedding_id")]
            if ids:
                self.client.delete(collection_name=self.collection, ids=ids)
                self._flush()
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
            hits = self.client.search(
                collection_name=self.collection,
                data=vectors,
                anns_field="vector",
                filter='tenant_id == 0 and scope == "public_published" and visibility == "published" and indexed == true and deleted == false and current_version == true',
                limit=12,
                output_fields=["embedding_id", "document_id", "title", "version", "section_path", "text", "current_version"],
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

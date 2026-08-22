import json
from io import BytesIO
from unittest import TestCase
import zipfile

from knowledge_rag import (KnowledgeChunk, MilvusIndex, OpenAICompatibleEmbedder, RagError, RagSettings, RedisStubIndex, StubIndex, chunk_markdown, parse_document, safe_object_key)


class _HashPipeline:
    def __init__(self, client):
        self.client = client
        self.operations = []

    def hdel(self, key, field):
        self.operations.append(("hdel", key, field))

    def hset(self, key, field, value):
        self.operations.append(("hset", key, field, value))

    def execute(self):
        for operation in self.operations:
            if operation[0] == "hdel":
                self.client.hashes.setdefault(operation[1], {}).pop(operation[2], None)
            else:
                self.client.hashes.setdefault(operation[1], {})[operation[2]] = operation[3]


class _HashRedis:
    def __init__(self):
        self.hashes = {}

    def hgetall(self, key):
        return dict(self.hashes.get(key, {}))

    def pipeline(self):
        return _HashPipeline(self)


class _MilvusClient:
    def __init__(self):
        self.rows = [
            {"embedding_id": "old", "vector": [1.0], "document_id": "d1", "title": "Guide", "version": "v1", "section_path": "old", "text": "old", "tenant_id": 0, "scope": "public_published", "indexed": True, "visibility": "draft", "deleted": False, "current_version": False},
            {"embedding_id": "new", "vector": [1.0], "document_id": "d1", "title": "Guide", "version": "v2", "section_path": "new", "text": "new", "tenant_id": 0, "scope": "public_published", "indexed": True, "visibility": "draft", "deleted": False, "current_version": True},
        ]
        self.filters = []
        self.upserts = []

    def has_collection(self, _collection):
        return True

    def query(self, **kwargs):
        self.filters.append(kwargs["filter"])
        return [dict(row) for row in self.rows if row["version"] == "v1"]

    def upsert(self, **kwargs):
        self.upserts.append(kwargs["data"])


class MilvusIndexTests(TestCase):
    def test_visibility_update_is_limited_to_document_version(self):
        index = MilvusIndex.__new__(MilvusIndex)
        index.client = _MilvusClient()
        index.collection = "public_knowledge"

        index.update_visibility("d1", "published", False, True, "v1")

        self.assertEqual(['document_id == "d1" and version == "v1"'], index.client.filters)
        self.assertEqual("published", index.client.upserts[0][0]["visibility"])
        self.assertEqual("v1", index.client.upserts[0][0]["version"])


class RedisStubIndexTests(TestCase):
    def test_reindex_removes_stale_chunks_for_the_same_version(self):
        client = _HashRedis()
        index = RedisStubIndex(client, "test:rag")
        index.upsert("Guide", [
            KnowledgeChunk("old", "d1", "v1", 0, "", "old"),
            KnowledgeChunk("keep", "d1", "v1", 1, "", "keep"),
        ])
        index.upsert("Guide", [KnowledgeChunk("keep", "d1", "v1", 1, "", "keep")])

        values = client.hgetall("test:rag:chunks")
        self.assertEqual(1, len(values))
        self.assertEqual("keep", json.loads(next(iter(values.values())))["text"])

    def test_visibility_update_does_not_touch_another_version(self):
        client = _HashRedis()
        index = RedisStubIndex(client, "test:rag")
        index.upsert("Guide", [KnowledgeChunk("v1", "d1", "v1", 0, "", "old", current_version=False)])
        index.upsert("Guide", [KnowledgeChunk("v2", "d1", "v2", 0, "", "new")])
        index.update_visibility("d1", "published", True, "v1")

        values = {key: json.loads(value) for key, value in client.hgetall("test:rag:chunks").items()}
        self.assertEqual("published", values["v1"]["visibility"])
        self.assertEqual("draft", values["v2"]["visibility"])


class RagSettingsTests(TestCase):
    def test_stub_needs_no_secret_or_milvus(self):
        self.assertEqual("stub", RagSettings.from_environment({"FOODMATE_RAG_MODE": "stub"}).mode)

    def test_local_fails_closed_when_configuration_is_missing(self):
        with self.assertRaisesRegex(RagError, "incomplete") as raised:
            RagSettings.from_environment({"FOODMATE_RAG_MODE": "local"})
        self.assertEqual("RAG_EMBEDDING_BASE_URL_MISSING", raised.exception.code)

    def test_local_accepts_complete_audited_configuration(self):
        settings = RagSettings.from_environment({
            "FOODMATE_RAG_MODE": "local", "FOODMATE_RAG_EMBEDDING_BASE_URL": "http://embedding/v1",
            "FOODMATE_RAG_EMBEDDING_API_KEY": "test", "FOODMATE_RAG_EMBEDDING_MODEL": "embedding",
            "FOODMATE_RAG_MILVUS_URI": "http://milvus:19530", "FOODMATE_RAG_MILVUS_COLLECTION": "public_knowledge",
            "FOODMATE_RAG_BATCH_TOKEN_LIMIT": "1", "FOODMATE_RAG_DAILY_TOKEN_LIMIT": "1",
            "FOODMATE_RAG_BATCH_COST_LIMIT": "1", "FOODMATE_RAG_DAILY_COST_LIMIT": "1",
            "FOODMATE_RAG_PRICE_PER_MILLION_TOKENS": "1", "FOODMATE_RAG_PRICE_VERSION": "test-v1",
        })
        self.assertEqual(4, settings.index_concurrency)


class StubIndexTests(TestCase):
    def test_public_filter_citation_limit_and_determinism(self):
        index = StubIndex()
        index.upsert("Nutrition guide", chunk_markdown("# Calories\nProtein and calories are important.\n\nMore protein facts.", "1", "v1", 40))
        citations = index.search("protein calories")
        self.assertLessEqual(len(citations), 2)
        self.assertEqual("Nutrition guide", citations[0].title)
        self.assertFalse(hasattr(citations[0], "storage_key"))

    def test_object_key_cannot_escape_knowledge_namespace(self):
        self.assertEqual("knowledge/1/a.txt", safe_object_key("knowledge/1/a.txt"))
        with self.assertRaisesRegex(RagError, "outside"):
            safe_object_key("knowledge/../secret")

    def test_stub_excludes_non_current_public_chunks(self):
        index = StubIndex()
        index.upsert(
            "Nutrition guide",
            [
                KnowledgeChunk("old", "1", "v1", 0, "", "protein", current_version=False),
                KnowledgeChunk("current", "1", "v2", 0, "", "protein"),
            ],
        )
        citations = index.search("protein")
        self.assertEqual(["current"], [item.chunk_id for item in citations])

    def test_docx_parser_extracts_text_without_executing_relationships(self):
        content = BytesIO()
        with zipfile.ZipFile(content, "w") as archive:
            archive.writestr(
                "word/document.xml",
                '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>Protein guide</w:t></w:r></w:p></w:body></w:document>',
            )
        self.assertEqual("Protein guide", parse_document("guide.docx", content.getvalue()))

    def test_docx_external_relationship_is_rejected(self):
        content = BytesIO()
        with zipfile.ZipFile(content, "w") as archive:
            archive.writestr("word/document.xml", "<document />")
            archive.writestr(
                "word/_rels/document.xml.rels",
                '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship TargetMode="External" Target="https://example.invalid" /></Relationships>',
            )
        with self.assertRaisesRegex(RagError, "external"):
            parse_document("unsafe.docx", content.getvalue())

    def test_pdf_parser_reads_a_real_pdf_container(self):
        from pypdf import PdfWriter

        output = BytesIO()
        writer = PdfWriter()
        writer.add_blank_page(width=72, height=72)
        writer.write(output)
        self.assertEqual("", parse_document("blank.pdf", output.getvalue()))

    def test_text_parser_rejects_basic_personal_identifiers(self):
        for value in (
            b"Contact alice@example.com for the guide.",
            "联系电话 13812345678。".encode(),
            "身份证 11010519491231002X。".encode(),
        ):
            with self.assertRaisesRegex(RagError, "personal identifier") as raised:
                parse_document("notes.txt", value)
            self.assertEqual("RAG_PII_DETECTED", raised.exception.code)

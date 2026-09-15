"""真实 Milvus 的 local deterministic 生命周期集成测试。

默认跳过，只有显式设置 ``FOODMATE_RUN_MILVUS_INTEGRATION_TESTS=true`` 才会连接
Milvus。测试使用随机 collection，结束后会主动删除，避免污染业务索引。
"""

from __future__ import annotations

import os
import uuid

import pytest

from knowledge_rag import DeterministicEmbedder, KnowledgeChunk, MilvusIndex, RagSettings


def _settings(collection: str) -> RagSettings:
    """构造不依赖付费 provider 的本地 Milvus 配置。"""
    return RagSettings.from_environment(
        {
            "FOODMATE_RAG_MODE": "local",
            "FOODMATE_RAG_EMBEDDING_PROVIDER": "deterministic",
            "FOODMATE_RAG_EMBEDDING_PROFILE": "",
            "FOODMATE_RAG_EMBEDDING_MODEL": "deterministic-local-v1",
            "FOODMATE_RAG_MILVUS_URI": os.environ.get(
                "FOODMATE_TEST_MILVUS_URI", "http://127.0.0.1:19530"
            ),
            "FOODMATE_RAG_MILVUS_COLLECTION": collection,
            "FOODMATE_RAG_DETERMINISTIC_DIMENSION": "64",
            "FOODMATE_RAG_BATCH_TOKEN_LIMIT": "100000",
            "FOODMATE_RAG_DAILY_TOKEN_LIMIT": "100000",
            "FOODMATE_RAG_BATCH_COST_LIMIT": "100000",
            "FOODMATE_RAG_DAILY_COST_LIMIT": "100000",
            "FOODMATE_RAG_PRICE_PER_MILLION_TOKENS": "0",
            "FOODMATE_RAG_PRICE_VERSION": "deterministic-integration-v1",
            "FOODMATE_RAG_ITEM_TIMEOUT_SECONDS": "30",
        }
    )


@pytest.mark.integration
def test_local_deterministic_milvus_lifecycle():
    """验证真实 Milvus 的写入、检索、可见性和版本删除边界。"""
    if os.environ.get("FOODMATE_RUN_MILVUS_INTEGRATION_TESTS", "false").lower() != "true":
        pytest.skip("显式设置 FOODMATE_RUN_MILVUS_INTEGRATION_TESTS=true 后才连接 Milvus")

    collection = f"foodmate_it_{uuid.uuid4().hex[:24]}"
    index = None
    document_id = f"codex-verify-{uuid.uuid4().hex}"
    version = "v1"
    chunk = KnowledgeChunk(
        embedding_id=f"{document_id}-embedding",
        document_id=document_id,
        version=version,
        sequence=0,
        section_path="Protein",
        text="豆类、鱼类和鸡蛋都可以作为日常蛋白质来源。",
    )

    try:
        settings = _settings(collection)
        index = MilvusIndex(settings)
        embedder = DeterministicEmbedder(settings)
        index.upsert("Codex RAG verification", [chunk], embedder.embed([chunk.text]))

        hits = index.search("日常蛋白质来源", embedder)
        assert any(item.document_id == document_id for item in hits)

        index.update_visibility(document_id, "disabled", True, True, version)
        assert not index.search("日常蛋白质来源", embedder)

        index.update_visibility(document_id, "published", False, True, version)
        assert any(item.document_id == document_id for item in index.search("日常蛋白质来源", embedder))

        deletion = index.delete_document(document_id, version)
        assert deletion.deleted_count == 1
        assert deletion.verified_absent
        assert not index.search("日常蛋白质来源", embedder)
    finally:
        # 临时 collection 只用于集成验证，结束后不保留测试数据。
        if index is not None and index.client.has_collection(collection):
            index.client.drop_collection(collection)

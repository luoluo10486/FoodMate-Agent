"""R5 公共知识主题、K2 切分和 local-stub 业务验收。"""

from __future__ import annotations

import sys
from pathlib import Path


PROJECT_ROOT = Path(__file__).parents[2]
sys.path.insert(0, str(PROJECT_ROOT / "script" / "data" / "knowledge"))

import validate_public_rag_r5
from knowledge_rag import parse_document


def test_public_knowledge_r5_business_report_passes_without_paid_services() -> None:
    report = validate_public_rag_r5.validate()

    assert report["status"] == "passed"
    assert report["mode"] == "local-stub"
    assert report["embedding_called"] is False
    assert report["milvus_written"] is False
    assert report["source"]["document_count"] == 9
    assert report["source"]["embedding_status"] in {"未构建向量", "已完成真实向量索引"}
    assert all(item["complete"] for item in report["topic_coverage"].values())


def test_public_knowledge_r5_records_k2_limits_visibility_and_idempotency() -> None:
    report = validate_public_rag_r5.validate()
    chunking = report["chunking"]

    assert chunking["target_chars"] == 700
    assert chunking["max_chars"] == 1000
    assert chunking["overlap_chars"] == 80
    assert chunking["total_chunks"] > 0
    assert chunking["max_chunk_length"] <= 1000
    assert chunking["overlap_probe"]["passed"] is True
    assert report["visibility"] == {"old_version_filtered": True, "disabled_filtered": True}
    assert report["idempotency"]["unique_chunk_count_before_repeat"] == report["idempotency"]["unique_chunk_count_after_repeat"]


def test_markdown_front_matter_is_not_indexed_as_a_chunk() -> None:
    text = parse_document(
        "guide.md",
        b"---\ntitle: Guide\nsource_url: https://example.test\n---\n\n# Guide\n\n## Protein\n\nProtein guidance.",
    )

    assert text.startswith("# Guide")
    assert "source_url" not in text

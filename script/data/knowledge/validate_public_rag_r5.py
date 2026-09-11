#!/usr/bin/env python3
"""执行公共知识库 R5 的主题覆盖和 K2 local-stub 业务验收。"""

from __future__ import annotations

import json
import sys
from dataclasses import replace
from pathlib import Path


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
PROJECT_ROOT = SCRIPT_DIRECTORY.parents[2]
RUNTIME_DIRECTORY = PROJECT_ROOT / "agent-runtime"
sys.path.insert(0, str(SCRIPT_DIRECTORY))
sys.path.insert(0, str(RUNTIME_DIRECTORY))

import validate_public_sources
from knowledge_rag import KnowledgeChunk, StubIndex, chunk_markdown, parse_document


CHUNK_MAX_CHARS = 1000
CHUNK_TARGET_CHARS = 700
CHUNK_OVERLAP_CHARS = 80

# 主题矩阵只记录可在资料正文中定位的证据，不把文档数量当作覆盖证明。
TOPIC_RULES = (
    ("dietary_basics", "膳食基础", ("健康饮食", "核心原则")),
    ("food_choices", "食材特点与选择", ("食物选择", "全谷物", "豆类")),
    ("cooking_storage", "烹饪与保存", ("安全处理原则", "储存和剩余食物")),
    ("meal_composition", "三餐与加餐搭配", ("餐次与搭配", "早餐", "午餐", "晚餐", "加餐")),
    ("nutrition_topics", "控盐糖、纤维与蛋白", ("钠", "游离糖", "膳食纤维", "蛋白质")),
    ("food_safety", "食品安全", ("食品安全", "食源性危害")),
)


def _load_manifest_documents() -> list[dict[str, object]]:
    manifest_path = SCRIPT_DIRECTORY / "public" / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    return list(manifest["documents"])


def _topic_coverage(documents: list[dict[str, object]]) -> dict[str, dict[str, object]]:
    coverage: dict[str, dict[str, object]] = {}
    for key, label, terms in TOPIC_RULES:
        matched: list[dict[str, object]] = []
        for document in documents:
            text = str(document["text"])
            sections = [line[3:].strip() for line in text.splitlines() if line.startswith("## ")]
            found = [term for term in terms if term in text]
            if found:
                matched.append(
                    {
                        "file": document["file"],
                        "title": document["title"],
                        "terms": found,
                        "section_path_candidates": [section for section in sections if any(term in section for term in found)],
                    }
                )
        found_terms = {term for item in matched for term in item["terms"]}
        coverage[key] = {
            "label": label,
            "required_terms": list(terms),
            "matched_terms": sorted(found_terms),
            "documents": matched,
            "complete": all(term in found_terms for term in terms),
        }
        if not coverage[key]["complete"]:
            missing = sorted(set(terms) - found_terms)
            raise AssertionError(f"公共知识主题缺少可追溯证据：{label}，缺少 {missing}")
    return coverage


def _overlap_chars(previous: str, current: str, limit: int) -> int:
    previous = previous.strip()
    current = current.lstrip()
    for size in range(min(limit, len(previous), len(current)), 0, -1):
        if previous[-size:] == current[:size]:
            return size
    return 0


def _build_chunks(documents: list[dict[str, object]]) -> tuple[list[KnowledgeChunk], list[dict[str, object]]]:
    chunks: list[KnowledgeChunk] = []
    details: list[dict[str, object]] = []
    for document in documents:
        document_chunks = chunk_markdown(
            str(document["text"]),
            str(document["file"]),
            str(document["source_version"]),
            max_chars=CHUNK_MAX_CHARS,
            target_chars=CHUNK_TARGET_CHARS,
            overlap_chars=CHUNK_OVERLAP_CHARS,
        )
        if not document_chunks:
            raise AssertionError(f"文档没有生成 chunk：{document['file']}")
        if any(len(chunk.text) > CHUNK_MAX_CHARS for chunk in document_chunks):
            raise AssertionError(f"chunk 超过硬上限：{document['file']}")
        if any(not chunk.section_path for chunk in document_chunks):
            raise AssertionError(f"chunk 缺少 section_path：{document['file']}")
        chunks.extend(document_chunks)
        details.append(
            {
                "file": document["file"],
                "title": document["title"],
                "version": document["source_version"],
                "chunk_count": len(document_chunks),
                "max_chunk_length": max(len(chunk.text) for chunk in document_chunks),
                "section_paths": sorted({chunk.section_path for chunk in document_chunks}),
            }
        )
    return chunks, details


def _published_chunks(chunks: list[KnowledgeChunk]) -> list[KnowledgeChunk]:
    return [replace(chunk, visibility="published", indexed=True, current_version=True) for chunk in chunks]


def _run_overlap_probe() -> dict[str, int | bool]:
    sentences = "".join(f"第 {index} 条建议说明均衡饮食和安全备餐的关系。" for index in range(1, 20))
    chunks = chunk_markdown(
        f"# K2 重叠验证\n\n## 长段落\n\n{sentences}",
        "r5-overlap-probe",
        "probe-v1",
        max_chars=220,
        target_chars=160,
        overlap_chars=32,
    )
    overlaps = [
        _overlap_chars(chunks[index - 1].text, chunks[index].text, 32)
        for index in range(1, len(chunks))
    ]
    return {
        "chunk_count": len(chunks),
        "overlap_pairs": sum(value > 0 for value in overlaps),
        "max_overlap_chars": max(overlaps, default=0),
        "passed": bool(chunks) and any(value > 0 for value in overlaps),
    }


def validate() -> dict[str, object]:
    root = SCRIPT_DIRECTORY / "public"
    source_report = validate_public_sources.validate(root)
    manifest_documents = _load_manifest_documents()
    documents = []
    for item in manifest_documents:
        path = root / str(item["file"])
        documents.append({**item, "text": parse_document(path.name, path.read_bytes())})
    if source_report["embedding_status"] not in {"未构建向量", "已完成真实向量索引"}:
        raise AssertionError("公共资料 embedding_status 不是受支持的业务状态")

    coverage = _topic_coverage(documents)
    chunks, document_details = _build_chunks(documents)
    published = _published_chunks(chunks)
    index = StubIndex()
    for document in documents:
        document_chunks = [chunk for chunk in published if chunk.document_id == document["file"]]
        index.upsert(str(document["title"]), document_chunks)

    fixed_queries = (
        ("蛋白质", "who-healthy-diet-zh.md"),
        ("钠摄入", "who-salt-reduction-zh.md"),
        ("食品安全", "who-food-safety-zh.md"),
        ("身体活动", "who-physical-activity-zh.md"),
        ("膳食纤维", "who-healthy-diet-zh.md"),
        ("早餐 午餐 晚餐 加餐", "who-healthy-diet-zh.md"),
    )
    search_results = []
    for query, expected_document in fixed_queries:
        citations = index.search(query)
        if not citations or citations[0].document_id != expected_document:
            raise AssertionError(f"固定查询未命中预期文档：{query}")
        search_results.append(
            {
                "query": query,
                "document_id": citations[0].document_id,
                "section_path": citations[0].section_path,
                "citation_count": len(citations),
            }
        )

    no_hit_query = "zzzxqv-abcmnop-r5"
    if index.search(no_hit_query):
        raise AssertionError("无命中查询返回了公共知识引用")

    version_chunks = [
        KnowledgeChunk("version-old", "r5-version-doc", "v1", 0, "旧版本", "蛋白质旧版本", current_version=False),
        KnowledgeChunk("version-current", "r5-version-doc", "v2", 0, "当前版本", "蛋白质当前版本"),
    ]
    index.upsert("版本过滤验证", version_chunks)
    version_result = index.search("版本过滤验证")
    version_ids = [citation.chunk_id for citation in version_result]
    if not version_ids or version_ids[0] != "version-current" or "version-old" in version_ids:
        raise AssertionError("旧版本过滤失败")
    index.upsert("版本过滤验证", [replace(version_chunks[1], visibility="disabled")])
    if any(citation.document_id == "r5-version-doc" for citation in index.search("版本过滤验证")):
        raise AssertionError("下线文档仍可被检索")

    first_document = documents[0]
    first_chunks = [chunk for chunk in published if chunk.document_id == first_document["file"]]
    before = len(index._chunks)
    index.upsert(str(first_document["title"]), first_chunks)
    after = len(index._chunks)
    if before != after:
        raise AssertionError("重复 upsert 增加了 chunk/vector")

    overlap_probe = _run_overlap_probe()
    if not overlap_probe["passed"]:
        raise AssertionError("K2 重叠策略探针未产生重叠")

    return {
        "status": "passed",
        "mode": "local-stub",
        "embedding_called": False,
        "milvus_written": False,
        "source": source_report,
        "topic_coverage": coverage,
        "chunking": {
            "target_chars": CHUNK_TARGET_CHARS,
            "max_chars": CHUNK_MAX_CHARS,
            "overlap_chars": CHUNK_OVERLAP_CHARS,
            "total_chunks": len(chunks),
            "max_chunk_length": max(len(chunk.text) for chunk in chunks),
            "documents": document_details,
            "overlap_probe": overlap_probe,
        },
        "search": {"fixed_queries": search_results, "no_hit_query": no_hit_query, "no_hit_count": 0},
        "visibility": {"old_version_filtered": True, "disabled_filtered": True},
        "idempotency": {"unique_chunk_count_before_repeat": before, "unique_chunk_count_after_repeat": after},
    }


def main() -> int:
    print(json.dumps(validate(), ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

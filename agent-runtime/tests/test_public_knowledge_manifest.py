"""公共营养资料清单的业务完整性测试。"""

from __future__ import annotations

import sys
from pathlib import Path


SCRIPT_DIRECTORY = Path(__file__).parents[2] / "script" / "data" / "knowledge"
sys.path.insert(0, str(SCRIPT_DIRECTORY))

import validate_public_sources


def test_public_source_manifest_is_complete_and_traceable() -> None:
    """九份官方资料必须可追溯，索引状态允许随授权推进。"""
    root = SCRIPT_DIRECTORY / "public"
    report = validate_public_sources.validate(root)
    assert report["document_count"] == 9
    assert report["embedding_status"] in {"未构建向量", "已完成真实向量索引"}
    assert len(report["documents"]) == 9


def test_public_source_urls_are_unique_who_pages() -> None:
    """资料来源必须是不同的 WHO HTTPS 页面。"""
    root = SCRIPT_DIRECTORY / "public"
    manifest = validate_public_sources.json.loads((root / "manifest.json").read_text(encoding="utf-8"))
    urls = [item["source_url"] for item in manifest["documents"]]
    assert len(urls) == len(set(urls))
    assert all(url.startswith("https://www.who.int/") for url in urls)

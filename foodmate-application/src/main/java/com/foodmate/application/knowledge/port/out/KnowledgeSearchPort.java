package com.foodmate.application.knowledge.port.out;

import java.util.List;

/** Runtime 所拥有的公共知识索引查询端口。 */
public interface KnowledgeSearchPort {
    /** 仅检索调用方获准的知识范围。 */
    SearchResult search(String query, String knowledgeScope);

    /** 可安全提供给 Composer 和 API 的有界检索结果。 */
    record SearchResult(List<Citation> citations) {
        public SearchResult {
            citations = citations == null ? List.of() : List.copyOf(citations);
        }
    }

    /** 安全引用元数据和有界文本片段。 */
    record Citation(
            long documentId,
            String citationId,
            String title,
            String version,
            String sectionPath,
            String snippet) {}
}

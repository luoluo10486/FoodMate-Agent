package com.foodmate.application.knowledge.service;

import java.util.List;

/** 面向已认证用户的公共知识库检索用例。 */
public interface KnowledgeSearchService {
    /** 为已认证用户检索公共、已发布的知识范围。 */
    SearchResult search(String query);

    /** application 层对外提供的有界引用结果。 */
    record SearchResult(List<Citation> citations) {
        public SearchResult {
            citations = citations == null ? List.of() : List.copyOf(citations);
        }
    }

    /** 不含存储键和完整来源内容的引用元数据。 */
    record Citation(
            long documentId,
            String citationId,
            String title,
            String version,
            String sectionPath,
            String snippet) {}
}

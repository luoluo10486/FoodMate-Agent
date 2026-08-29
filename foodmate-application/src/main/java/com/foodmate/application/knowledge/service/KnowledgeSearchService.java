package com.foodmate.application.knowledge.service;

import java.util.List;

/** 面向已认证用户的公共知识库检索用例。 */
public interface KnowledgeSearchService {
    /** Searches the public, published knowledge scope for the authenticated user. */
    SearchResult search(String query);

    /** Bounded citation results exposed by the application layer. */
    record SearchResult(List<Citation> citations) {
        public SearchResult {
            citations = citations == null ? List.of() : List.copyOf(citations);
        }
    }

    /** Citation metadata without storage keys or full source content. */
    record Citation(
            long documentId,
            String citationId,
            String title,
            String version,
            String sectionPath,
            String snippet) {}
}

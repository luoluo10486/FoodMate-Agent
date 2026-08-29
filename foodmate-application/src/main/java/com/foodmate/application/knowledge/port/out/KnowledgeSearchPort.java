package com.foodmate.application.knowledge.port.out;

import java.util.List;

/** Runtime 所拥有的公共知识索引查询端口。 */
public interface KnowledgeSearchPort {
    /** Searches only the caller-approved knowledge scope. */
    SearchResult search(String query, String knowledgeScope);

    /** Bounded search results safe to expose to the Composer and API. */
    record SearchResult(List<Citation> citations) {
        public SearchResult {
            citations = citations == null ? List.of() : List.copyOf(citations);
        }
    }

    /** Safe citation metadata and bounded text excerpt. */
    record Citation(
            long documentId,
            String citationId,
            String title,
            String version,
            String sectionPath,
            String snippet) {}
}

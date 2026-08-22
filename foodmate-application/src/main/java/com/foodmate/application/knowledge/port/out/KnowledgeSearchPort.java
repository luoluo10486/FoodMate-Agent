package com.foodmate.application.knowledge.port.out;

import java.util.List;

/** Port for the Runtime-owned public knowledge index. */
public interface KnowledgeSearchPort {
    SearchResult search(String query, String knowledgeScope);

    record SearchResult(List<Citation> citations) {
        public SearchResult {
            citations = citations == null ? List.of() : List.copyOf(citations);
        }
    }

    record Citation(
            long documentId,
            String citationId,
            String title,
            String version,
            String sectionPath,
            String snippet) {}
}

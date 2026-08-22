package com.foodmate.application.knowledge.service;

import java.util.List;

/** Authenticated public knowledge search use case. */
public interface KnowledgeSearchService {
    SearchResult search(String query);

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

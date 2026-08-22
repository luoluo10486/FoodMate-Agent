package com.foodmate.api.response.knowledge;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record KnowledgeSearchResponse(List<Citation> citations) {
    public KnowledgeSearchResponse {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }

    public record Citation(
            @JsonProperty("document_id") long documentId,
            @JsonProperty("citation_id") String citationId,
            String title,
            String version,
            @JsonProperty("section_path") String sectionPath,
            String snippet) {}
}

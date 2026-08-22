package com.foodmate.application.knowledge.service.impl;

import com.foodmate.application.knowledge.port.out.KnowledgeRepository;
import com.foodmate.application.knowledge.port.out.KnowledgeSearchPort;
import com.foodmate.application.knowledge.service.KnowledgeSearchService;
import com.foodmate.shared.runtime.RuntimeException;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** Searches the public published knowledge scope and rechecks citation visibility. */
@Service
public class KnowledgeSearchServiceImpl implements KnowledgeSearchService {
    private static final String PUBLIC_SCOPE = "public_published";

    private final KnowledgeSearchPort search;
    private final KnowledgeRepository store;

    public KnowledgeSearchServiceImpl(
            ObjectProvider<KnowledgeSearchPort> searchProvider,
            ObjectProvider<KnowledgeRepository> storeProvider) {
        this.search = searchProvider.getIfAvailable();
        this.store = storeProvider.getIfAvailable();
    }

    @Override
    public SearchResult search(String query) {
        if (query == null || query.isBlank() || query.length() > 2000)
            throw new IllegalArgumentException("knowledge search query is invalid");
        if (search == null || store == null)
            throw new RuntimeException(
                    "RAG_UNAVAILABLE", "knowledge search dependencies unavailable");

        KnowledgeSearchPort.SearchResult indexed = search.search(query.trim(), PUBLIC_SCOPE);
        if (indexed.citations().isEmpty()) return new SearchResult(List.of());
        return new SearchResult(
                indexed.citations().stream()
                        .filter(
                                citation ->
                                        store.isPublicPublished(
                                                citation.documentId(), citation.version()))
                        .map(
                                citation ->
                                        new Citation(
                                                citation.documentId(),
                                                citation.citationId(),
                                                citation.title(),
                                                citation.version(),
                                                citation.sectionPath(),
                                                citation.snippet()))
                        .toList());
    }
}

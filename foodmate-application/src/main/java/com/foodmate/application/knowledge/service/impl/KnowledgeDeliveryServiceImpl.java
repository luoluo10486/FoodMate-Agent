package com.foodmate.application.knowledge.service.impl;

import com.foodmate.application.knowledge.port.out.KnowledgeRepository;
import com.foodmate.application.knowledge.service.KnowledgeDeliveryService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeDeliveryServiceImpl implements KnowledgeDeliveryService {
    private final KnowledgeRepository store;

    public KnowledgeDeliveryServiceImpl(KnowledgeRepository store) {
        this.store = store;
    }

    public List<KnowledgeRepository.OutboxRow> pendingIndex(int limit) {
        return store.pendingIndexOutbox(limit);
    }

    public List<KnowledgeRepository.OutboxRow> pendingVisibility(int limit) {
        return store.pendingVisibilityOutbox(limit);
    }

    public int leaseIndex(long id, String owner) {
        return store.leaseIndexOutbox(id, owner);
    }

    public int leaseVisibility(long id, String owner) {
        return store.leaseVisibilityOutbox(id, owner);
    }

    public void publishedIndex(long id) {
        store.markIndexOutboxPublished(id);
    }

    public void publishedVisibility(long id) {
        store.markVisibilityOutboxPublished(id);
    }

    public void retryIndex(long id, String error) {
        store.retryIndexOutbox(id, error);
    }

    public void retryVisibility(long id, String error) {
        store.retryVisibilityOutbox(id, error);
    }

    @Transactional
    public void accept(KnowledgeRepository.IndexResult result, String hash) {
        store.applyIndexResult(result, hash);
    }
}

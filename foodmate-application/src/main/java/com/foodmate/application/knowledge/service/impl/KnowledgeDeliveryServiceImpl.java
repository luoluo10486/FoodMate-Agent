package com.foodmate.application.knowledge.service.impl;

import com.foodmate.application.knowledge.port.out.KnowledgeRepository;
import com.foodmate.application.knowledge.service.KnowledgeDeliveryService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 在应用边界协调知识索引与可见性 Outbox 的投递。 */
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

    public void publishedIndex(long id, String owner) {
        store.markIndexOutboxPublished(id, owner);
    }

    public void publishedVisibility(long id, String owner) {
        store.markVisibilityOutboxPublished(id, owner);
    }

    public void retryIndex(long id, String owner, String error) {
        store.retryIndexOutbox(id, owner, error);
    }

    public void retryVisibility(long id, String owner, String error) {
        store.retryVisibilityOutbox(id, owner, error);
    }

    @Transactional
    public void accept(KnowledgeRepository.IndexResult result, String hash) {
        store.applyIndexResult(result, hash);
    }
}

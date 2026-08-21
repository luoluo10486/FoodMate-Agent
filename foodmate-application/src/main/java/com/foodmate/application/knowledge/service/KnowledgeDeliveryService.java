package com.foodmate.application.knowledge.service;

import com.foodmate.application.knowledge.port.out.KnowledgeRepository;
import java.util.List;

/** Durable knowledge index delivery, result reconciliation and batch progress read model. */
public interface KnowledgeDeliveryService {
    List<KnowledgeRepository.OutboxRow> pendingIndex(int limit);

    List<KnowledgeRepository.OutboxRow> pendingVisibility(int limit);

    int leaseIndex(long id, String owner);

    int leaseVisibility(long id, String owner);

    void publishedIndex(long id);

    void publishedVisibility(long id);

    void retryIndex(long id, String error);

    void retryVisibility(long id, String error);

    void accept(KnowledgeRepository.IndexResult result, String hash);
}

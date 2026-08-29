package com.foodmate.application.knowledge.service;

import com.foodmate.application.knowledge.port.out.KnowledgeRepository;
import java.util.List;

/** 编排知识索引投递、结果对账和批次进度查询。 */
public interface KnowledgeDeliveryService {
    /** Lists index messages eligible for a lease. */
    List<KnowledgeRepository.OutboxRow> pendingIndex(int limit);

    /** Lists visibility messages eligible for a lease. */
    List<KnowledgeRepository.OutboxRow> pendingVisibility(int limit);

    /** Claims an index outbox row. */
    int leaseIndex(long id, String owner);

    /** Claims a visibility outbox row. */
    int leaseVisibility(long id, String owner);

    /** Marks an index message as published. */
    void publishedIndex(long id, String owner);

    /** Marks a visibility message as published. */
    void publishedVisibility(long id, String owner);

    /** Schedules an index publication retry. */
    void retryIndex(long id, String owner, String error);

    /** Schedules a visibility publication retry. */
    void retryVisibility(long id, String owner, String error);

    /** Applies an idempotent index result to the authoritative state. */
    void accept(KnowledgeRepository.IndexResult result, String hash);
}

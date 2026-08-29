package com.foodmate.application.knowledge.service;

import com.foodmate.application.knowledge.port.out.KnowledgeRepository;
import java.util.List;

/** 编排知识索引投递、结果对账和批次进度查询。 */
public interface KnowledgeDeliveryService {
    /** 列出当前可领取的索引消息。 */
    List<KnowledgeRepository.OutboxRow> pendingIndex(int limit);

    /** 列出当前可领取的可见性消息。 */
    List<KnowledgeRepository.OutboxRow> pendingVisibility(int limit);

    /** 领取一条索引 Outbox 记录。 */
    int leaseIndex(long id, String owner);

    /** 领取一条可见性 Outbox 记录。 */
    int leaseVisibility(long id, String owner);

    /** 将索引消息标记为已发布。 */
    void publishedIndex(long id, String owner);

    /** 将可见性消息标记为已发布。 */
    void publishedVisibility(long id, String owner);

    /** 安排索引消息发布重试。 */
    void retryIndex(long id, String owner, String error);

    /** 安排可见性消息发布重试。 */
    void retryVisibility(long id, String owner, String error);

    /** 将索引结果幂等应用到权威状态。 */
    void accept(KnowledgeRepository.IndexResult result, String hash);
}

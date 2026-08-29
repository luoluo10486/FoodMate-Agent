package com.foodmate.application.knowledge.messaging;

import com.foodmate.application.common.service.AgentOperationMetrics;
import com.foodmate.application.knowledge.port.out.KnowledgeRepository.OutboxRow;
import com.foodmate.application.knowledge.service.KnowledgeDeliveryService;
import com.foodmate.application.runtime.messaging.MessageProperties;
import com.foodmate.application.runtime.port.out.MessagePublisherPort;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 转发已提交的知识索引和可见性事实，不在投递层重建消息内容。 */
@Component
public class KnowledgeOutboxPublisher {
    private final KnowledgeDeliveryService service;
    private final MessagePublisherPort publisher;
    private final AgentOperationMetrics metrics;
    private final boolean rocketMq;

    @Autowired
    public KnowledgeOutboxPublisher(
            KnowledgeDeliveryService service,
            ObjectProvider<MessagePublisherPort> publisher,
            ObjectProvider<AgentOperationMetrics> metrics,
            @Value("${foodmate.runtime.transport:http}") String transport) {
        this(service, publisher.getIfAvailable(), metrics.getIfAvailable(), transport);
    }

    public KnowledgeOutboxPublisher(
            KnowledgeDeliveryService service,
            ObjectProvider<MessagePublisherPort> publisher,
            @Value("${foodmate.runtime.transport:http}") String transport) {
        this(service, publisher.getIfAvailable(), null, transport);
    }

    private KnowledgeOutboxPublisher(
            KnowledgeDeliveryService service,
            MessagePublisherPort publisher,
            AgentOperationMetrics metrics,
            String transport) {
        this.service = service;
        this.publisher = publisher;
        this.metrics = metrics;
        this.rocketMq = "rocketmq".equals(transport);
    }

    /** 发布一批有界的知识 Outbox 事实。 */
    @Scheduled(fixedDelayString = "${foodmate.knowledge.outbox-poll-ms:500}")
    public void publish() {
        if (!rocketMq || publisher == null) return;
        relay(service.pendingIndex(10), true);
        relay(service.pendingVisibility(10), false);
    }

    private void relay(List<OutboxRow> rows, boolean index) {
        String operation = index ? "knowledge_index" : "visibility";
        if (metrics != null) metrics.queueDepth("rocketmq", operation, rows.size());
        for (OutboxRow row : rows) {
            String owner = "knowledge_" + UUID.randomUUID();
            if ((index
                            ? service.leaseIndex(row.outboxId(), owner)
                            : service.leaseVisibility(row.outboxId(), owner))
                    != 1) continue;
            if (metrics != null) metrics.count("rocketmq", operation, "accepted", "leased");
            try {
                publisher.publish(
                        new MessagePublisherPort.PublishRequest(
                                row.topic(),
                                Long.toString(row.itemOrDocumentId()),
                                row.payload(),
                                MessageProperties.of(
                                        new MessageProperties.Property(
                                                "foodmate_message_type",
                                                index
                                                        ? "KnowledgeIndex"
                                                        : "KnowledgeVisibility"))));
                if (index) service.publishedIndex(row.outboxId(), owner);
                else service.publishedVisibility(row.outboxId(), owner);
                if (metrics != null) metrics.count("rocketmq", operation, "success", "published");
            } catch (RuntimeException error) {
                String message =
                        error.getMessage() == null
                                ? error.getClass().getSimpleName()
                                : error.getMessage();
                if (index) service.retryIndex(row.outboxId(), owner, message);
                else service.retryVisibility(row.outboxId(), owner, message);
                if (metrics != null) metrics.count("rocketmq", operation, "retry", "relay_error");
            }
        }
    }
}

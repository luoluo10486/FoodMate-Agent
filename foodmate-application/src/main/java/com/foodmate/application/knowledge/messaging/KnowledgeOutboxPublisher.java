package com.foodmate.application.knowledge.messaging;

import com.foodmate.application.knowledge.port.out.KnowledgeRepository.OutboxRow;
import com.foodmate.application.knowledge.service.KnowledgeDeliveryService;
import com.foodmate.application.runtime.messaging.MessageProperties;
import com.foodmate.application.runtime.port.out.MessagePublisherPort;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Relays committed knowledge index and visibility facts without rebuilding their payloads. */
@Component
public class KnowledgeOutboxPublisher {
    private final KnowledgeDeliveryService service;
    private final MessagePublisherPort publisher;
    private final boolean rocketMq;

    public KnowledgeOutboxPublisher(
            KnowledgeDeliveryService service,
            ObjectProvider<MessagePublisherPort> publisher,
            @Value("${foodmate.runtime.transport:http}") String transport) {
        this.service = service;
        this.publisher = publisher.getIfAvailable();
        this.rocketMq = "rocketmq".equals(transport);
    }

    @Scheduled(fixedDelayString = "${foodmate.knowledge.outbox-poll-ms:500}")
    public void publish() {
        if (!rocketMq || publisher == null) return;
        relay(service.pendingIndex(10), true);
        relay(service.pendingVisibility(10), false);
    }

    private void relay(List<OutboxRow> rows, boolean index) {
        for (OutboxRow row : rows) {
            String owner = "knowledge_" + UUID.randomUUID();
            if ((index
                            ? service.leaseIndex(row.outboxId(), owner)
                            : service.leaseVisibility(row.outboxId(), owner))
                    != 1) continue;
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
            } catch (RuntimeException error) {
                String message =
                        error.getMessage() == null
                                ? error.getClass().getSimpleName()
                                : error.getMessage();
                if (index) service.retryIndex(row.outboxId(), owner, message);
                else service.retryVisibility(row.outboxId(), owner, message);
            }
        }
    }
}

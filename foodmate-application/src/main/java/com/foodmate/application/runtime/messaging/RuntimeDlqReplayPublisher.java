package com.foodmate.application.runtime.messaging;

import com.foodmate.application.runtime.port.out.DeadLetterRepository;
import com.foodmate.application.runtime.port.out.DeadLetterRepository.ReplayOutbox;
import com.foodmate.application.runtime.port.out.MessagePublisherPort;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 将已审计的 DLQ replay outbox 发布到原业务 Topic。 */
@Component
public class RuntimeDlqReplayPublisher {
    private final DeadLetterRepository store;
    private final MessagePublisherPort publisher;
    private final boolean rocketMq;

    public RuntimeDlqReplayPublisher(
            DeadLetterRepository store,
            ObjectProvider<MessagePublisherPort> publisher,
            @Value("${foodmate.runtime.transport:http}") String transport) {
        this.store = store;
        this.publisher = publisher.getIfAvailable();
        this.rocketMq = "rocketmq".equals(transport);
    }

    @Scheduled(fixedDelayString = "${foodmate.runtime.dlq-replay-poll-ms:500}")
    public void publishPending() {
        if (!rocketMq || publisher == null) return;
        for (ReplayOutbox row : store.findPendingReplay(10)) {
            String owner = "dlq_replay_" + UUID.randomUUID();
            if (store.leaseReplay(row.replayId(), owner) != 1) continue;
            try {
                MessagePublisherPort.PublishResult result =
                        publisher.publish(
                                new MessagePublisherPort.PublishRequest(
                                        row.sourceTopic(),
                                        row.messageKey(),
                                        row.payload(),
                                        properties(row)));
                if (result == null
                        || result.messageId() == null
                        || store.markReplayPublished(row.replayId(), owner, result.messageId())
                                != 1)
                    throw new IllegalStateException("DLQ replay publish fact was not persisted");
            } catch (RuntimeException exception) {
                store.retryReplay(row.replayId(), owner, safeMessage(exception));
            }
        }
    }

    private MessageProperties properties(ReplayOutbox row) {
        List<MessageProperties.Property> properties = new ArrayList<>();
        add(properties, "foodmate_original_message_id", row.originalMessageId());
        add(properties, "foodmate_original_consumer_group", row.consumerGroup());
        add(properties, "foodmate_replay_id", Long.toString(row.replayId()));
        add(properties, "foodmate_request_hash", row.requestHash());
        add(properties, "foodmate_run_id", row.runId());
        add(properties, "foodmate_dispatch_id", row.dispatchId());
        add(properties, "foodmate_attempt", number(row.attempt()));
        add(properties, "foodmate_event_id", row.eventId());
        add(properties, "foodmate_event_seq", number(row.eventSeq()));
        return MessageProperties.copyOf(properties);
    }

    private static void add(List<MessageProperties.Property> properties, String key, String value) {
        if (value != null && !value.isBlank())
            properties.add(new MessageProperties.Property(key, value));
    }

    private static String number(Number value) {
        return value == null ? null : value.toString();
    }

    private static String safeMessage(Exception exception) {
        String value = exception.getMessage();
        String text = value == null ? exception.getClass().getSimpleName() : value;
        return text.substring(0, Math.min(500, text.length()));
    }
}

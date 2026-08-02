package com.foodmate.application.runtime.port.out;

import java.util.List;

public interface OutboxRepository {
    List<OutboxSnapshot> findPending(int limit);

    int lease(long outboxId, String owner);

    void markPublished(long outboxId, String topic, String messageId);

    void markDelivered(long outboxId);

    void markFailed(long outboxId, String error);

    record OutboxSnapshot(long id, String payload) {}
}

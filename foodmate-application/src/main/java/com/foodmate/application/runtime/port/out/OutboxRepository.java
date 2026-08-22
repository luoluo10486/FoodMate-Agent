package com.foodmate.application.runtime.port.out;

import java.util.List;

/** Durable publication boundary for Java-to-Runtime messages. */
public interface OutboxRepository {
    /** Reads messages whose retry time and lease state allow publication. */
    List<OutboxSnapshot> findPending(int limit);

    /** Claims a message for one relay worker. */
    int lease(long outboxId, String owner);

    /** Records broker acknowledgement and the returned message identifier. */
    void markPublished(long outboxId, String topic, String messageId);

    /** Records delivery completion when the downstream acknowledgement is durable. */
    void markDelivered(long outboxId);

    /** Records a bounded, safe retry error summary. */
    void markFailed(long outboxId, String error);

    /** Immutable publication fact read by the relay. */
    record OutboxSnapshot(long id, String payload) {}
}

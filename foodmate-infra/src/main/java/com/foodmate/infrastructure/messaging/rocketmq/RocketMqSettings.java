package com.foodmate.infrastructure.messaging.rocketmq;

import java.util.Objects;

/** Validated RocketMQ transport configuration. */
public record RocketMqSettings(
        String nameServer,
        String commandTopic,
        String eventTopic,
        String proposalTopic,
        String resultTopic,
        String javaEventConsumerGroup,
        String javaProposalConsumerGroup,
        String producerGroup,
        int sendTimeoutMs,
        int producerMaxRetries,
        int consumerMaxRetries) {
    private static final String TOPIC_PATTERN = "[a-zA-Z0-9_-]+";

    public RocketMqSettings {
        requireText(nameServer, "nameServer");
        commandTopic = requireTopic(commandTopic, "commandTopic");
        eventTopic = requireTopic(eventTopic, "eventTopic");
        proposalTopic = requireTopic(proposalTopic, "proposalTopic");
        resultTopic = requireTopic(resultTopic, "resultTopic");
        javaEventConsumerGroup = requireTopic(javaEventConsumerGroup, "javaEventConsumerGroup");
        javaProposalConsumerGroup =
                requireTopic(javaProposalConsumerGroup, "javaProposalConsumerGroup");
        producerGroup = requireTopic(producerGroup, "producerGroup");
        if (sendTimeoutMs <= 0)
            throw new IllegalArgumentException("sendTimeoutMs must be positive");
        if (producerMaxRetries < 0)
            throw new IllegalArgumentException("producerMaxRetries must not be negative");
        if (consumerMaxRetries < 0)
            throw new IllegalArgumentException("consumerMaxRetries must not be negative");
    }

    public String deadLetterTopic(String consumerGroup) {
        return "%DLQ%" + requireTopic(consumerGroup, "consumerGroup");
    }

    private static String requireTopic(String value, String name) {
        requireText(value, name);
        String trimmed = value.trim();
        if (!trimmed.matches(TOPIC_PATTERN))
            throw new IllegalArgumentException(
                    name + " must match " + TOPIC_PATTERN + ": " + trimmed);
        return trimmed;
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}

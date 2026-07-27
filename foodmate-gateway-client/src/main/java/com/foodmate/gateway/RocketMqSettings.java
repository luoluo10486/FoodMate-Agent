package com.foodmate.gateway;

import java.util.Objects;

/**
 * RocketMQ 传输配置（ADR-0005）。
 *
 * <p>Topic 与 consumer group 属于契约化配置，构造时即校验：命名非法或为空时直接拒绝启动，
 * 而不是等到第一条消息发送失败。Topic 名只允许 {@code ^[%|a-zA-Z0-9_-]+$}，点号会被 Broker 拒绝。
 */
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

    /** Broker 侧的 Topic 名校验规则；`%` 是 %RETRY%/%DLQ% 保留前缀，业务 Topic 不得使用。 */
    private static final String TOPIC_PATTERN = "[a-zA-Z0-9_-]+";

    public RocketMqSettings {
        requireText(nameServer, "nameServer");
        commandTopic = requireTopic(commandTopic, "commandTopic");
        eventTopic = requireTopic(eventTopic, "eventTopic");
        proposalTopic = requireTopic(proposalTopic, "proposalTopic");
        resultTopic = requireTopic(resultTopic, "resultTopic");
        javaEventConsumerGroup = requireTopic(javaEventConsumerGroup, "javaEventConsumerGroup");
        javaProposalConsumerGroup = requireTopic(javaProposalConsumerGroup, "javaProposalConsumerGroup");
        producerGroup = requireTopic(producerGroup, "producerGroup");
        if (sendTimeoutMs <= 0) throw new IllegalArgumentException("sendTimeoutMs must be positive");
        if (producerMaxRetries < 0) throw new IllegalArgumentException("producerMaxRetries must not be negative");
        if (consumerMaxRetries < 0) throw new IllegalArgumentException("consumerMaxRetries must not be negative");
    }

    /** 消费失败耗尽重试后，Broker 把消息投递到该消费组的 DLQ Topic。 */
    public String deadLetterTopic(String consumerGroup) {
        return "%DLQ%" + requireTopic(consumerGroup, "consumerGroup");
    }

    private static String requireTopic(String value, String name) {
        requireText(value, name);
        String trimmed = value.trim();
        if (!trimmed.matches(TOPIC_PATTERN)) {
            throw new IllegalArgumentException(name + " must match " + TOPIC_PATTERN + " (RocketMQ 拒绝点号): " + trimmed);
        }
        return trimmed;
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}

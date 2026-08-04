package com.foodmate.infrastructure.messaging.rocketmq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RocketMqSettingsTest {
    private RocketMqSettings settings(String commandTopic) {
        return new RocketMqSettings(
                "localhost:9876",
                commandTopic,
                "foodmate-agent-event-v1",
                "foodmate-agent-proposal-v1",
                "foodmate-agent-result-v1",
                "foodmate-java-agent-event-v1",
                "foodmate-java-agent-proposal-v1",
                "foodmate-java-command-producer-v1",
                3000,
                3,
                8);
    }

    @Test
    void acceptsHyphenatedContractTopics() {
        RocketMqSettings settings = settings("foodmate-agent-command-v1");
        assertEquals("foodmate-agent-command-v1", settings.commandTopic());
        assertEquals(
                "%DLQ%foodmate-java-agent-event-v1",
                settings.deadLetterTopic("foodmate-java-agent-event-v1"));
    }

    @Test
    void rejectsDottedTopicBecauseBrokerRejectsIt() {
        assertThrows(IllegalArgumentException.class, () -> settings("foodmate.agent.command.v1"));
    }

    @Test
    void rejectsReservedPrefixAndBlankValues() {
        assertThrows(
                IllegalArgumentException.class, () -> settings("%DLQ%foodmate-agent-command-v1"));
        assertThrows(IllegalArgumentException.class, () -> settings("  "));
        assertThrows(NullPointerException.class, () -> settings(null));
    }

    @Test
    void rejectsNonPositiveTimeoutAndNegativeRetries() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RocketMqSettings(
                                "localhost:9876",
                                "foodmate-agent-command-v1",
                                "foodmate-agent-event-v1",
                                "foodmate-agent-proposal-v1",
                                "foodmate-agent-result-v1",
                                "foodmate-java-agent-event-v1",
                                "foodmate-java-agent-proposal-v1",
                                "foodmate-java-command-producer-v1",
                                0,
                                3,
                                8));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RocketMqSettings(
                                "localhost:9876",
                                "foodmate-agent-command-v1",
                                "foodmate-agent-event-v1",
                                "foodmate-agent-proposal-v1",
                                "foodmate-agent-result-v1",
                                "foodmate-java-agent-event-v1",
                                "foodmate-java-agent-proposal-v1",
                                "foodmate-java-command-producer-v1",
                                3000,
                                -1,
                                8));
    }
}

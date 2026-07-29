package com.foodmate.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** RocketMQ 配置校验。Topic 命名是契约的一部分（ADR-0005），非法命名必须让装配失败， 而不是等到第一条消息被 Broker 拒绝。 */
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
        // Broker 强制 ^[%|a-zA-Z0-9_-]+$，点号命名会返回 CODE: 1 illegal characters。
        assertThrows(IllegalArgumentException.class, () -> settings("foodmate.agent.command.v1"));
    }

    @Test
    void rejectsReservedPrefixAndBlankValues() {
        // %RETRY%/%DLQ% 是 RocketMQ 保留前缀，业务 Topic 不得占用。
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

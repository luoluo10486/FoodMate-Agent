package com.foodmate.application.runtime.messaging;

import java.util.Map;

/** application 层的传输无关消息处理契约。 */
@FunctionalInterface
public interface MqMessageHandler {
    MqConsumeDecision handle(String body, MqMessageContext context);

    record MqMessageContext(
            String topic,
            String messageId,
            String messageKey,
            int reconsumeTimes,
            Map<String, String> properties) {
        public MqMessageContext {
            properties = properties == null ? Map.of() : Map.copyOf(properties);
        }
    }
}

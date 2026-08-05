package com.foodmate.application.runtime.messaging;

/** application 层的传输无关消息处理契约。 */
@FunctionalInterface
public interface MqMessageHandler {
    MqConsumeDecision handle(String body, MqMessageContext context);

    record MqMessageContext(
            String topic,
            String messageId,
            String messageKey,
            int reconsumeTimes,
            MessageProperties properties) {
        public MqMessageContext {
            properties = properties == null ? MessageProperties.empty() : properties;
        }
    }
}

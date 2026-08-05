package com.foodmate.application.runtime.port.out;

import com.foodmate.application.runtime.messaging.MessageProperties;

/** 发布传输无关消息的端口；消息确认和 SDK 细节由基础设施实现。 */
public interface MessagePublisherPort {
    PublishResult publish(PublishRequest request);

    record PublishRequest(String topic, String key, String body, MessageProperties properties) {
        public PublishRequest {
            properties = properties == null ? MessageProperties.empty() : properties;
        }
    }

    record PublishResult(String messageId) {}
}

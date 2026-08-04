package com.foodmate.application.runtime.port.out;

import java.util.Map;

/** 发布传输无关消息的端口；消息确认和 SDK 细节由基础设施实现。 */
public interface MessagePublisherPort {
    PublishResult publish(PublishRequest request);

    record PublishRequest(String topic, String key, String body, Map<String, String> properties) {
        public PublishRequest {
            properties = properties == null ? Map.of() : Map.copyOf(properties);
        }
    }

    record PublishResult(String messageId) {}
}

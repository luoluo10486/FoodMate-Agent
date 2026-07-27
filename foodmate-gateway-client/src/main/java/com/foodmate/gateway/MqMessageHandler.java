package com.foodmate.gateway;

import java.util.Map;

/**
 * MQ 消息处理回调。
 *
 * <p>传输层只负责收发与 ACK，业务处理（Inbox、状态机、审计、SSE Outbox）由实现方在
 * 自己的事务中完成，因此本接口不暴露任何 RocketMQ 类型，application 层无需依赖 MQ 客户端。
 */
@FunctionalInterface
public interface MqMessageHandler {

    /**
     * @param body            原始消息体，保持 Outbox 中持久化的字节，不重新拼装
     * @param context         消息元数据（消息 ID、key、重投次数、来源 Topic）
     * @return 消费结论；返回前必须已完成业务事务提交
     */
    MqConsumeDecision handle(String body, MqMessageContext context);

    /** 传输层元数据。业务判定只能依赖消息体，这些字段用于幂等登记、DLQ 与排障。 */
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

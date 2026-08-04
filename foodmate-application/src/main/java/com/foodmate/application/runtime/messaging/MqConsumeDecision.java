package com.foodmate.application.runtime.messaging;

/** MQ 消息消费结果；传输层据此转换为 ACK、RETRY 或 REJECT。 */
public enum MqConsumeDecision {
    ACK,
    RETRY,
    REJECT
}

package com.foodmate.infrastructure.messaging.rocketmq;

import com.foodmate.application.runtime.messaging.MessageProperties;
import com.foodmate.application.runtime.messaging.MqConsumeDecision;
import com.foodmate.application.runtime.messaging.MqMessageHandler;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;

/** Translates RocketMQ callbacks into the application message handler contract. */
public final class RocketMqConsumerContainer implements AutoCloseable {
    private final DefaultMQPushConsumer consumer;
    private final String topic;
    private volatile boolean started;

    private RocketMqConsumerContainer(DefaultMQPushConsumer consumer, String topic) {
        this.consumer = consumer;
        this.topic = topic;
    }

    public static RocketMqConsumerContainer orderly(
            String nameServer,
            String consumerGroup,
            String topic,
            int maxReconsumeTimes,
            MqMessageHandler handler) {
        DefaultMQPushConsumer consumer =
                baseConsumer(nameServer, consumerGroup, topic, maxReconsumeTimes);
        consumer.registerMessageListener(
                (MessageListenerOrderly)
                        (messages, context) -> {
                            for (MessageExt message : messages) {
                                MqConsumeDecision decision = dispatch(handler, message);
                                if (decision == MqConsumeDecision.RETRY)
                                    return ConsumeOrderlyStatus.SUSPEND_CURRENT_QUEUE_A_MOMENT;
                            }
                            return ConsumeOrderlyStatus.SUCCESS;
                        });
        return new RocketMqConsumerContainer(consumer, topic);
    }

    public static RocketMqConsumerContainer concurrent(
            String nameServer,
            String consumerGroup,
            String topic,
            int maxReconsumeTimes,
            MqMessageHandler handler) {
        DefaultMQPushConsumer consumer =
                baseConsumer(nameServer, consumerGroup, topic, maxReconsumeTimes);
        consumer.registerMessageListener(
                (MessageListenerConcurrently)
                        (messages, context) -> {
                            for (MessageExt message : messages) {
                                if (dispatch(handler, message) == MqConsumeDecision.RETRY)
                                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                            }
                            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                        });
        return new RocketMqConsumerContainer(consumer, topic);
    }

    private static DefaultMQPushConsumer baseConsumer(
            String nameServer, String consumerGroup, String topic, int maxReconsumeTimes) {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(consumerGroup);
        consumer.setNamesrvAddr(nameServer);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.setMaxReconsumeTimes(maxReconsumeTimes);
        consumer.setConsumeMessageBatchMaxSize(1);
        try {
            consumer.subscribe(topic, "*");
        } catch (MQClientException exception) {
            throw new IllegalStateException("failed to subscribe topic " + topic, exception);
        }
        return consumer;
    }

    private static MqConsumeDecision dispatch(MqMessageHandler handler, MessageExt message) {
        try {
            return handler.handle(
                    new String(message.getBody(), StandardCharsets.UTF_8), context(message));
        } catch (java.lang.RuntimeException exception) {
            return MqConsumeDecision.RETRY;
        }
    }

    private static MqMessageHandler.MqMessageContext context(MessageExt message) {
        MessageProperties properties =
                MessageProperties.copyOf(
                        message.getProperties() == null
                                ? List.of()
                                : message.getProperties().entrySet().stream()
                                        .map(
                                                entry ->
                                                        new MessageProperties.Property(
                                                                entry.getKey(), entry.getValue()))
                                        .toList());
        return new MqMessageHandler.MqMessageContext(
                message.getTopic(),
                message.getMsgId(),
                message.getKeys(),
                message.getReconsumeTimes(),
                properties);
    }

    public synchronized void start() {
        if (started) return;
        try {
            consumer.start();
            started = true;
        } catch (MQClientException exception) {
            throw new IllegalStateException(
                    "failed to start consumer for topic " + topic, exception);
        }
    }

    @Override
    public synchronized void close() {
        if (!started) return;
        consumer.shutdown();
        started = false;
    }
}

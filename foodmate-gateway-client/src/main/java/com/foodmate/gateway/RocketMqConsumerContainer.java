package com.foodmate.gateway;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;

/**
 * RocketMQ 消费容器（ADR-0005）。
 *
 * <p>把 RocketMQ 的消费回调翻译成与传输无关的 {@link MqMessageHandler}，
 * 让 application 层能在自己的事务里处理消息而不依赖 MQ 客户端类型。
 *
 * <p>顺序消费用于 Agent 事件：Python 按 {@code run_id} 选择队列，同一 Run 的事件落在同一队列，
 * 顺序消费保证 {@code event_seq} 按序进入 Inbox，把「等待前序事件」的重试降到最少。
 * {@link MqConsumeDecision#RETRY} 会挂起当前队列，因此该队列上后续 Run 的事件也会等待；
 * 这是保序的代价，通过 {@code maxReconsumeTimes} 触发 DLQ 兜底避免永久阻塞。
 */
public final class RocketMqConsumerContainer implements AutoCloseable {
    private final DefaultMQPushConsumer consumer;
    private final String topic;
    private volatile boolean started;

    private RocketMqConsumerContainer(DefaultMQPushConsumer consumer, String topic) {
        this.consumer = consumer;
        this.topic = topic;
    }

    /** 顺序消费容器，用于必须保持 {@code event_seq} 顺序的 Agent 事件流。 */
    public static RocketMqConsumerContainer orderly(String nameServer, String consumerGroup, String topic,
                                                    int maxReconsumeTimes, MqMessageHandler handler) {
        DefaultMQPushConsumer consumer = baseConsumer(nameServer, consumerGroup, topic, maxReconsumeTimes);
        consumer.registerMessageListener((MessageListenerOrderly) (messages, context) -> {
            for (MessageExt message : messages) {
                MqConsumeDecision decision = dispatch(handler, message);
                if (decision == MqConsumeDecision.RETRY) {
                    // 挂起队列而不是单条重投，避免后续 event_seq 抢在缺失事件之前落库。
                    return ConsumeOrderlyStatus.SUSPEND_CURRENT_QUEUE_A_MOMENT;
                }
            }
            return ConsumeOrderlyStatus.SUCCESS;
        });
        return new RocketMqConsumerContainer(consumer, topic);
    }

    /** 并发消费容器，用于没有顺序要求的旁路流（DLQ 归档等）。 */
    public static RocketMqConsumerContainer concurrent(String nameServer, String consumerGroup, String topic,
                                                       int maxReconsumeTimes, MqMessageHandler handler) {
        DefaultMQPushConsumer consumer = baseConsumer(nameServer, consumerGroup, topic, maxReconsumeTimes);
        consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
            for (MessageExt message : messages) {
                if (dispatch(handler, message) == MqConsumeDecision.RETRY) {
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        return new RocketMqConsumerContainer(consumer, topic);
    }

    private static DefaultMQPushConsumer baseConsumer(String nameServer, String consumerGroup, String topic,
                                                      int maxReconsumeTimes) {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(consumerGroup);
        consumer.setNamesrvAddr(nameServer);
        // 新消费组从最新位点开始：历史消息由数据库对账处理，不在启动时重放业务事件。
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.setMaxReconsumeTimes(maxReconsumeTimes);
        // 每次只取一条：业务处理带数据库事务，批量会拉长事务并放大重试范围。
        consumer.setConsumeMessageBatchMaxSize(1);
        try {
            consumer.subscribe(topic, "*");
        } catch (MQClientException exception) {
            throw new IllegalStateException("failed to subscribe topic " + topic, exception);
        }
        return consumer;
    }

    /**
     * handler 抛出未受检异常时按可重试处理：这类异常通常来自数据库或网络，
     * 确定性错误应由 handler 自己返回 {@link MqConsumeDecision#REJECT}。
     */
    private static MqConsumeDecision dispatch(MqMessageHandler handler, MessageExt message) {
        try {
            return handler.handle(new String(message.getBody(), StandardCharsets.UTF_8), context(message));
        } catch (java.lang.RuntimeException exception) {
            return MqConsumeDecision.RETRY;
        }
    }

    private static MqMessageHandler.MqMessageContext context(MessageExt message) {
        Map<String, String> properties = new HashMap<>();
        if (message.getProperties() != null) properties.putAll(message.getProperties());
        return new MqMessageHandler.MqMessageContext(
                message.getTopic(), message.getMsgId(), message.getKeys(),
                message.getReconsumeTimes(), properties);
    }

    public synchronized void start() {
        if (started) return;
        try {
            consumer.start();
            started = true;
        } catch (MQClientException exception) {
            throw new IllegalStateException("failed to start consumer for topic " + topic, exception);
        }
    }

    @Override
    public synchronized void close() {
        if (!started) return;
        consumer.shutdown();
        started = false;
    }
}

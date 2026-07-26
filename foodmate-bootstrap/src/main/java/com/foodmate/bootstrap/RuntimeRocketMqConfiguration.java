package com.foodmate.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.runtime.RuntimeDlqService;
import com.foodmate.application.runtime.RuntimeEventMessageProcessor;
import com.foodmate.gateway.RocketMqConsumerContainer;
import com.foodmate.gateway.RocketMqSettings;
import com.foodmate.gateway.V1RocketMqRuntimeClient;
import com.foodmate.gateway.V1RuntimeClient;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ 异步主通道装配（ADR-0005）。
 *
 * <p>只有 {@code foodmate.runtime.transport=rocketmq} 时才装配，此时本类提供的
 * {@link V1RuntimeClient} 会覆盖 {@link RuntimeClientConfiguration} 的 HTTP 实现——
 * 配置指南 §5.9 规则 10 要求「同一进程不能同时启用 HTTP 与 MQ 业务派发」。
 *
 * <p>Topic 与 consumer group 的合法性在 {@link RocketMqSettings} 构造时校验，
 * 非法命名会让 Spring 装配直接失败，符合「非法配置使 readiness 失败」的架构规则。
 */
@Configuration
@ConditionalOnProperty(name = "foodmate.runtime.transport", havingValue = "rocketmq")
public class RuntimeRocketMqConfiguration {

    @Bean
    RocketMqSettings rocketMqSettings(
            @Value("${foodmate.runtime.rocketmq.name-server}") String nameServer,
            @Value("${foodmate.runtime.rocketmq.command-topic:foodmate-agent-command-v1}") String commandTopic,
            @Value("${foodmate.runtime.rocketmq.event-topic:foodmate-agent-event-v1}") String eventTopic,
            @Value("${foodmate.runtime.rocketmq.proposal-topic:foodmate-agent-proposal-v1}") String proposalTopic,
            @Value("${foodmate.runtime.rocketmq.result-topic:foodmate-agent-result-v1}") String resultTopic,
            @Value("${foodmate.runtime.rocketmq.java-event-consumer-group:foodmate-java-agent-event-v1}") String eventGroup,
            @Value("${foodmate.runtime.rocketmq.java-proposal-consumer-group:foodmate-java-agent-proposal-v1}") String proposalGroup,
            @Value("${foodmate.runtime.rocketmq.producer-group:foodmate-java-agent-command-producer-v1}") String producerGroup,
            @Value("${foodmate.runtime.rocketmq.send-timeout-ms:3000}") int sendTimeoutMs,
            @Value("${foodmate.runtime.rocketmq.producer-max-retries:3}") int producerMaxRetries,
            @Value("${foodmate.runtime.rocketmq.consumer-max-retries:8}") int consumerMaxRetries) {
        return new RocketMqSettings(nameServer, commandTopic, eventTopic, proposalTopic, resultTopic,
                eventGroup, proposalGroup, producerGroup, sendTimeoutMs, producerMaxRetries, consumerMaxRetries);
    }

    @Bean(destroyMethod = "close")
    V1RuntimeClient v1RocketMqRuntimeClient(RocketMqSettings settings, ObjectMapper objectMapper,
                                            @Value("${foodmate.runtime.contract-version:v1}") String contractVersion) throws Exception {
        DefaultMQProducer producer = new DefaultMQProducer(settings.producerGroup());
        producer.setNamesrvAddr(settings.nameServer());
        producer.setSendMsgTimeout(settings.sendTimeoutMs());
        producer.setRetryTimesWhenSendFailed(settings.producerMaxRetries());
        // Broker 已确认但客户端未收到响应时不静默换队列重发：Outbox Relay 负责重试，
        // 由 dispatch_id 幂等吸收，避免同一命令产生两条语义不同的消息。
        producer.setRetryAnotherBrokerWhenNotStoreOK(false);
        producer.start();
        return new V1RocketMqRuntimeClient(producer, settings, objectMapper, contractVersion);
    }

    /** 消费 Python 回传的 RunEvent；顺序消费保证同一 Run 的 event_seq 按序进入 Inbox。 */
    @Bean(initMethod = "start", destroyMethod = "close")
    RocketMqConsumerContainer runtimeEventConsumer(RocketMqSettings settings, RuntimeEventMessageProcessor processor) {
        return RocketMqConsumerContainer.orderly(settings.nameServer(), settings.javaEventConsumerGroup(),
                settings.eventTopic(), settings.consumerMaxRetries(), processor);
    }

    /** 归档耗尽重试的事件消息。DLQ 归档本身没有顺序要求，用并发消费即可。 */
    @Bean(initMethod = "start", destroyMethod = "close")
    RocketMqConsumerContainer runtimeEventDlqConsumer(RocketMqSettings settings, RuntimeDlqService dlqService) {
        String group = settings.javaEventConsumerGroup();
        return RocketMqConsumerContainer.concurrent(settings.nameServer(), group + "-dlq",
                settings.deadLetterTopic(group), 1, dlqService);
    }
}

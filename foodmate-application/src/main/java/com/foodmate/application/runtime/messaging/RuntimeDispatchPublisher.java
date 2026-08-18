package com.foodmate.application.runtime.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.common.service.AgentOperationMetrics;
import com.foodmate.application.runtime.port.out.OutboxRepository;
import com.foodmate.application.runtime.port.out.RuntimeClientPort;
import com.foodmate.shared.runtime.V1RunCommand;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 在事务提交后领取并发送不可变的 V1 dispatch outbox 记录。
 *
 * <p>ADR-0005 的两条语义：
 *
 * <ul>
 *   <li>重试保持原 {@code message_id/dispatch_id/attempt/request_hash/payload}，不重新组装消息； 因此这里只反序列化已持久化的
 *       payload，绝不重建 envelope。
 *   <li>RocketMQ 通道收到 Broker 持久化确认后标记 {@code published}（并记录 Broker 消息 ID）， HTTP 兼容通道仍标记 {@code
 *       delivered}，两种语义不混用。
 * </ul>
 */
@Component
public class RuntimeDispatchPublisher {
    private final OutboxRepository store;
    private final RuntimeClientPort client;
    private final String transport;
    private final String commandTopic;
    private final AgentOperationMetrics metrics;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public RuntimeDispatchPublisher(
            OutboxRepository store,
            ObjectProvider<RuntimeClientPort> clientProvider,
            @Value("${foodmate.runtime.transport:http}") String transport,
            @Value("${foodmate.runtime.rocketmq.command-topic:foodmate-agent-command-v1}")
                    String commandTopic) {
        this(store, clientProvider, transport, commandTopic, null);
    }

    @Autowired
    public RuntimeDispatchPublisher(
            OutboxRepository store,
            ObjectProvider<RuntimeClientPort> clientProvider,
            @Value("${foodmate.runtime.transport:http}") String transport,
            @Value("${foodmate.runtime.rocketmq.command-topic:foodmate-agent-command-v1}")
                    String commandTopic,
            ObjectProvider<AgentOperationMetrics> metricsProvider) {
        this.store = store;
        this.client = clientProvider.getIfAvailable();
        this.transport = transport;
        this.commandTopic = commandTopic;
        this.metrics = metricsProvider == null ? null : metricsProvider.getIfAvailable();
    }

    @Scheduled(fixedDelayString = "${foodmate.runtime.dispatch-poll-ms:500}")
    public void publishPending() {
        if (client == null) return;
        List<OutboxRepository.OutboxSnapshot> rows = store.findPending(10);
        if (metrics != null) metrics.queueDepth(transport, "dispatch", rows.size());
        boolean mq = "rocketmq".equals(transport);
        for (OutboxRepository.OutboxSnapshot row : rows) {
            var sample = metrics == null ? null : metrics.start();
            try {
                V1RunCommand command = mapper.readValue(row.payload(), V1RunCommand.class);
                if (store.lease(row.id(), owner()) == 1) {
                    RuntimeClientPort.Response response = client.dispatch(command);
                    if (mq) {
                        store.markPublished(row.id(), commandTopic, response.messageId());
                        if (metrics != null)
                            metrics.count(transport, "dispatch", "success", "published");
                    } else {
                        store.markDelivered(row.id());
                        if (metrics != null)
                            metrics.count(transport, "dispatch", "success", "delivered");
                    }
                }
            } catch (Exception exception) {
                store.markFailed(row.id(), safeMessage(exception));
                if (metrics != null) metrics.count(transport, "dispatch", "failed", "relay_error");
            } finally {
                if (metrics != null)
                    metrics.stop(sample, transport, "dispatch", "terminal", "relay");
            }
        }
    }

    private String owner() {
        return "publisher_" + Thread.currentThread().getId() + "_" + Instant.now().toEpochMilli();
    }

    private String safeMessage(Exception exception) {
        String value = exception.getMessage();
        return value == null
                ? exception.getClass().getSimpleName()
                : value.substring(0, Math.min(500, value.length()));
    }
}

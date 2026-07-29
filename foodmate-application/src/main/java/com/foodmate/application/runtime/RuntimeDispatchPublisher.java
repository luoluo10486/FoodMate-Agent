package com.foodmate.application.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.runtime.persistence.DispatchOutboxStore;
import com.foodmate.gateway.V1RuntimeClient;
import com.foodmate.shared.runtime.V1RunCommand;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
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
    private final DispatchOutboxStore store;
    private final V1RuntimeClient client;
    private final String transport;
    private final String commandTopic;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public RuntimeDispatchPublisher(
            DispatchOutboxStore store,
            ObjectProvider<V1RuntimeClient> clientProvider,
            @Value("${foodmate.runtime.transport:http}") String transport,
            @Value("${foodmate.runtime.rocketmq.command-topic:foodmate-agent-command-v1}")
                    String commandTopic) {
        this.store = store;
        this.client = clientProvider.getIfAvailable();
        this.transport = transport;
        this.commandTopic = commandTopic;
    }

    @Scheduled(fixedDelayString = "${foodmate.runtime.dispatch-poll-ms:500}")
    public void publishPending() {
        if (client == null) return;
        List<DispatchOutboxStore.OutboxSnapshot> rows = store.findPending(10);
        boolean mq = "rocketmq".equals(transport);
        for (DispatchOutboxStore.OutboxSnapshot row : rows) {
            try {
                V1RunCommand command = mapper.readValue(row.payload(), V1RunCommand.class);
                if (store.lease(row.id(), owner()) == 1) {
                    V1RuntimeClient.Response response = client.dispatch(command);
                    if (mq) {
                        store.markPublished(row.id(), commandTopic, response.messageId());
                    } else {
                        store.markDelivered(row.id());
                    }
                }
            } catch (Exception exception) {
                store.markFailed(row.id(), safeMessage(exception));
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

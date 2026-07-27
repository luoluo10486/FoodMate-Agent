package com.foodmate.application.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.gateway.V1RuntimeClient;
import com.foodmate.shared.runtime.V1RunCommand;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 在事务提交后领取并发送不可变的 V1 dispatch outbox 记录。
 *
 * <p>ADR-0005 的两条语义：
 * <ul>
 *   <li>重试保持原 {@code message_id/dispatch_id/attempt/request_hash/payload}，不重新组装消息；
 *       因此这里只反序列化已持久化的 payload，绝不重建 envelope。</li>
 *   <li>RocketMQ 通道收到 Broker 持久化确认后标记 {@code published}（并记录 Broker 消息 ID），
 *       HTTP 兼容通道仍标记 {@code delivered}，两种语义不混用。</li>
 * </ul>
 */
@Component
public class RuntimeDispatchPublisher {
    private final JdbcTemplate jdbc;
    private final V1RuntimeClient client;
    private final String transport;
    private final String commandTopic;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public RuntimeDispatchPublisher(ObjectProvider<JdbcTemplate> jdbcProvider,
                                    ObjectProvider<V1RuntimeClient> clientProvider,
                                    @Value("${foodmate.runtime.transport:http}") String transport,
                                    @Value("${foodmate.runtime.rocketmq.command-topic:foodmate-agent-command-v1}") String commandTopic) {
        this.jdbc = jdbcProvider.getIfAvailable();
        this.client = clientProvider.getIfAvailable();
        this.transport = transport;
        this.commandTopic = commandTopic;
    }

    @Scheduled(fixedDelayString = "${foodmate.runtime.dispatch-poll-ms:500}")
    public void publishPending() {
        if (jdbc == null || client == null) return;
        List<OutboxRow> rows = jdbc.query("SELECT outbox_id,payload_json::text FROM runtime_dispatch_outbox WHERE status='pending' AND next_attempt_at<=CURRENT_TIMESTAMP ORDER BY created_at LIMIT 10",
                (rs, row) -> new OutboxRow(rs.getLong(1), rs.getString(2)));
        boolean mq = "rocketmq".equals(transport);
        for (OutboxRow row : rows) {
            try {
                V1RunCommand command = mapper.readValue(row.payload(), V1RunCommand.class);
                int changed = jdbc.update("UPDATE runtime_dispatch_outbox SET status='leased',owner_token=?,lease_until=CURRENT_TIMESTAMP+INTERVAL '30 seconds',send_attempts=send_attempts+1,updated_at=CURRENT_TIMESTAMP WHERE outbox_id=? AND status='pending'", owner(), row.id());
                if (changed == 1) {
                    V1RuntimeClient.Response response = client.dispatch(command);
                    if (mq) {
                        jdbc.update("UPDATE runtime_dispatch_outbox SET status='published',owner_token=NULL,lease_until=NULL,transport='rocketmq',mq_topic=?,mq_message_id=?,published_at=CURRENT_TIMESTAMP,delivered_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE outbox_id=? AND status='leased'",
                                commandTopic, response.messageId(), row.id());
                    } else {
                        jdbc.update("UPDATE runtime_dispatch_outbox SET status='delivered',owner_token=NULL,lease_until=NULL,transport='http',delivered_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE outbox_id=? AND status='leased'", row.id());
                    }
                }
            } catch (Exception exception) {
                jdbc.update("UPDATE runtime_dispatch_outbox SET status=CASE WHEN deadline_at<=CURRENT_TIMESTAMP THEN 'expired' ELSE 'pending' END,owner_token=NULL,lease_until=NULL,next_attempt_at=CURRENT_TIMESTAMP+INTERVAL '2 seconds',last_error=?,updated_at=CURRENT_TIMESTAMP WHERE outbox_id=?", safeMessage(exception), row.id());
            }
        }
    }

    private String owner() { return "publisher_" + Thread.currentThread().getId() + "_" + Instant.now().toEpochMilli(); }
    private String safeMessage(Exception exception) { String value = exception.getMessage(); return value == null ? exception.getClass().getSimpleName() : value.substring(0, Math.min(500, value.length())); }
    private record OutboxRow(long id, String payload) {}
}

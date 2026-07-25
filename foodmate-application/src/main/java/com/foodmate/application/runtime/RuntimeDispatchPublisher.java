package com.foodmate.application.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.gateway.V1RuntimeClient;
import com.foodmate.shared.runtime.V1RunCommand;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Claims and sends immutable V1 dispatch outbox rows after their transaction commits. */
@Component
public class RuntimeDispatchPublisher {
    private final JdbcTemplate jdbc;
    private final V1RuntimeClient client;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public RuntimeDispatchPublisher(ObjectProvider<JdbcTemplate> jdbcProvider, ObjectProvider<V1RuntimeClient> clientProvider) {
        this.jdbc = jdbcProvider.getIfAvailable();
        this.client = clientProvider.getIfAvailable();
    }

    @Scheduled(fixedDelayString = "${foodmate.runtime.dispatch-poll-ms:500}")
    public void publishPending() {
        if (jdbc == null || client == null) return;
        List<OutboxRow> rows = jdbc.query("SELECT outbox_id,payload_json::text FROM runtime_dispatch_outbox WHERE status='pending' AND next_attempt_at<=CURRENT_TIMESTAMP ORDER BY created_at LIMIT 10",
                (rs, row) -> new OutboxRow(rs.getLong(1), rs.getString(2)));
        for (OutboxRow row : rows) {
            try {
                V1RunCommand command = mapper.readValue(row.payload(), V1RunCommand.class);
                int changed = jdbc.update("UPDATE runtime_dispatch_outbox SET status='leased',owner_token=?,lease_until=CURRENT_TIMESTAMP+INTERVAL '30 seconds',send_attempts=send_attempts+1,updated_at=CURRENT_TIMESTAMP WHERE outbox_id=? AND status='pending'", owner(), row.id());
                if (changed == 1) {
                    client.dispatch(command);
                    jdbc.update("UPDATE runtime_dispatch_outbox SET status='delivered',owner_token=NULL,lease_until=NULL,delivered_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE outbox_id=? AND status='leased'", row.id());
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

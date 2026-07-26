package com.foodmate.application.runtime;

import com.foodmate.gateway.V1RuntimeClient;
import com.foodmate.shared.id.IdGenerator;
import com.foodmate.shared.runtime.V1CancelCommand;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RuntimeCancellationService {
    private final JdbcTemplate jdbc;
    private final IdGenerator ids;
    private final V1RuntimeClient client;
    private final V1RuntimeEventService events;

    public RuntimeCancellationService(ObjectProvider<JdbcTemplate> jdbcProvider, IdGenerator ids,
                                      ObjectProvider<V1RuntimeClient> clientProvider, V1RuntimeEventService events) {
        this.jdbc = jdbcProvider.getIfAvailable(); this.ids = ids; this.client = clientProvider.getIfAvailable(); this.events = events;
    }

    @Transactional
    public CancelResult request(long userId, String runId, String reason) {
        // 取消、状态检查和 cancellation_epoch 提升必须先完成用户归属校验。
        events.requireRunOwner(runId, userId);
        if (jdbc == null) return new CancelResult(runId, "requested", false);
        long numeric = parse(runId);
        var rows = jdbc.query("SELECT d.dispatch_id,d.attempt,r.status FROM agent_runs r JOIN agent_run_dispatches d ON d.agent_run_dispatch_id=r.active_dispatch_id WHERE r.agent_run_id=? AND r.is_deleted=FALSE", (rs, row) -> new Object[] {rs.getString(1), rs.getInt(2), rs.getString(3)}, numeric);
        if (rows.isEmpty()) throw new com.foodmate.shared.runtime.RuntimeException("RUNTIME_STATE_CONFLICT", "active dispatch not found");
        String status = (String) rows.getFirst()[2];
        if (status.equals("completed") || status.equals("failed") || status.equals("cancelled")) return new CancelResult(runId, status, true);
        String cancelId = "can_" + UUID.randomUUID().toString().replace("-", "");
        String dispatchId = (String) rows.getFirst()[0]; int attempt = (Integer) rows.getFirst()[1];
        Instant requestedAt = Instant.now(); Instant deadline = requestedAt.plusSeconds(30);
        String hash = digest(runId + "|" + dispatchId + "|" + attempt + "|" + cancelId + "|" + reason + "|" + deadline);
        jdbc.update("INSERT INTO agent_run_cancellations(cancellation_id,agent_run_id,cancel_id,dispatch_id,request_hash,reason,status,requested_at) VALUES (?,?,?,?,?,?, 'requested',?)",
                ids.nextId(), numeric, cancelId, dispatchId, hash, reason, java.sql.Timestamp.from(requestedAt));
        jdbc.update("UPDATE agent_runs SET cancellation_epoch=cancellation_epoch+1,updated_at=CURRENT_TIMESTAMP WHERE agent_run_id=?", numeric);
        return new CancelResult(runId, "requested", false);
    }

    @Scheduled(fixedDelayString = "${foodmate.runtime.cancel-poll-ms:500}")
    public void publishRequested() {
        if (jdbc == null || client == null) return;
        var rows = jdbc.query("SELECT c.cancellation_id,c.agent_run_id,c.cancel_id,c.dispatch_id,d.attempt,c.request_hash,c.reason,c.requested_at FROM agent_run_cancellations c JOIN agent_runs r ON r.agent_run_id=c.agent_run_id JOIN agent_run_dispatches d ON d.dispatch_id=c.dispatch_id WHERE c.status='requested' ORDER BY c.created_at LIMIT 10",
                (rs, row) -> new PendingCancel(rs.getLong(1), Long.toString(rs.getLong(2)), rs.getString(3), rs.getString(4), rs.getInt(5), rs.getString(6), rs.getString(7), rs.getTimestamp(8).toInstant()));
        for (PendingCancel pending : rows) {
            try {
                V1CancelCommand command = new V1CancelCommand("v1", pending.runId(), pending.dispatchId(), pending.attempt(), pending.cancelId(), "req_cancel_" + pending.cancelId(), "trace_cancel_" + pending.runId(), pending.requestHash(), pending.requestedAt().plusSeconds(30), pending.reason(), pending.requestedAt());
                // 浏览器取消先落库，再由这里通过 command Topic 可靠发布（ADR-0005 §控制命令）。
                V1RuntimeClient.Response response = client.cancel(command);
                jdbc.update("UPDATE agent_run_cancellations SET status='dispatched',transport=?,mq_message_id=?,published_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE cancellation_id=? AND status='requested'",
                        response.messageId() == null ? "http" : "rocketmq", response.messageId(), pending.id());
            } catch (Exception exception) {
                // Runtime 暂时不可用时保留 requested，下一轮定时任务使用同一取消记录重试。
            }
        }
    }

    private long parse(String value) { try { return Long.parseLong(value); } catch (NumberFormatException e) { throw new com.foodmate.shared.runtime.RuntimeException("RUNTIME_STATE_CONFLICT", "run id is invalid"); } }
    private String digest(String value) { try { return "sha256:" + hex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String hex(byte[] bytes) { StringBuilder result = new StringBuilder(); for (byte value : bytes) result.append(String.format("%02x", value)); return result.toString(); }
    public record CancelResult(String runId, String status, boolean terminal) {}
    private record PendingCancel(long id, String runId, String cancelId, String dispatchId, int attempt, String requestHash, String reason, Instant requestedAt) {}
}

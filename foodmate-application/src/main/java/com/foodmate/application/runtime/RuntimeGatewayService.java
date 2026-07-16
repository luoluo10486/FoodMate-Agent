package com.foodmate.application.runtime;

import com.foodmate.shared.runtime.CancelCommand;
import com.foodmate.shared.runtime.RunCommand;
import com.foodmate.shared.runtime.EventInbox;
import com.foodmate.shared.runtime.RunEvent;
import com.foodmate.shared.runtime.RuntimeException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RuntimeGatewayService {
    private final Map<String, String> dispatches = new HashMap<>();
    private final Map<String, String> cancels = new HashMap<>();
    private final Map<String, Status> statuses = new HashMap<>();
    private final EventInbox inbox = new EventInbox();
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RuntimeGatewayService() { this.jdbc = null; }

    @Autowired
    public RuntimeGatewayService(ObjectProvider<JdbcTemplate> jdbcProvider) {
        this.jdbc = jdbcProvider.getIfAvailable();
    }

    public synchronized CommandResult dispatch(RunCommand command) {
        if (command.deadlineAt().isBefore(Instant.now())) throw new RuntimeException("RUNTIME_DEADLINE_EXCEEDED", "dispatch deadline exceeded");
        String fingerprint = command.runId() + "|" + command.input() + "|" + command.deadlineAt() + "|" + command.attempt();
        if (jdbc != null) return dispatchJdbc(command, fingerprint);
        String previous = dispatches.putIfAbsent(command.dispatchId(), fingerprint);
        if (previous != null && !previous.equals(fingerprint)) throw new IdempotencyConflict("RUNTIME_DISPATCH_IDEMPOTENCY_CONFLICT");
        if (previous != null) return new CommandResult(command.dispatchId(), command.runId(), statuses.getOrDefault(command.runId(), Status.DISPATCHED), true);
        statuses.putIfAbsent(command.runId(), Status.DISPATCHED);
        return new CommandResult(command.dispatchId(), command.runId(), Status.DISPATCHED, false);
    }

    public synchronized CommandResult cancel(CancelCommand command) {
        if (command.deadlineAt().isBefore(Instant.now())) throw new RuntimeException("RUNTIME_DEADLINE_EXCEEDED", "cancel deadline exceeded");
        String fingerprint = command.runId() + "|" + command.reason() + "|" + command.deadlineAt();
        if (jdbc != null) return cancelJdbc(command, fingerprint);
        String previous = cancels.putIfAbsent(command.cancelId(), fingerprint);
        if (previous != null && !previous.equals(fingerprint)) throw new IdempotencyConflict("RUNTIME_CANCEL_IDEMPOTENCY_CONFLICT");
        Status current = statuses.get(command.runId());
        if (current == null) throw new IllegalArgumentException("runId does not exist");
        if (previous == null && current != Status.SUCCEEDED && current != Status.FAILED) statuses.put(command.runId(), Status.CANCELED);
        return new CommandResult(command.cancelId(), command.runId(), statuses.get(command.runId()), previous != null);
    }

    public synchronized EventResult event(RunEvent event) {
        if (jdbc != null) return eventJdbc(event);
        if (!statuses.containsKey(event.runId())) throw new IllegalArgumentException("runId does not exist");
        EventInbox.Result result = inbox.accept(event);
        if (result == EventInbox.Result.ACCEPTED) statuses.put(event.runId(), toStatus(event.state()));
        return new EventResult(event.eventId(), event.runId(), statuses.get(event.runId()), result == EventInbox.Result.DUPLICATE);
    }

    private CommandResult dispatchJdbc(RunCommand command, String fingerprint) {
        String old = queryFingerprint("runtime_dispatches", "dispatch_id", command.dispatchId());
        if (old != null) {
            if (!old.equals(fingerprint)) throw new IdempotencyConflict("RUNTIME_DISPATCH_IDEMPOTENCY_CONFLICT");
            return new CommandResult(command.dispatchId(), command.runId(), statusJdbc(command.runId()), true);
        }
        try {
            jdbc.update("INSERT INTO runtime_runs(run_id,status) VALUES (?,?) ON CONFLICT (run_id) DO NOTHING", command.runId(), Status.DISPATCHED.name());
            jdbc.update("INSERT INTO runtime_dispatches(dispatch_id,run_id,request_fingerprint) VALUES (?,?,?)", command.dispatchId(), command.runId(), fingerprint);
        } catch (DuplicateKeyException conflict) {
            String concurrent = queryFingerprint("runtime_dispatches", "dispatch_id", command.dispatchId());
            if (!fingerprint.equals(concurrent)) throw new IdempotencyConflict("RUNTIME_DISPATCH_IDEMPOTENCY_CONFLICT");
            return new CommandResult(command.dispatchId(), command.runId(), statusJdbc(command.runId()), true);
        }
        return new CommandResult(command.dispatchId(), command.runId(), Status.DISPATCHED, false);
    }

    private CommandResult cancelJdbc(CancelCommand command, String fingerprint) {
        String old = queryFingerprint("runtime_cancels", "cancel_id", command.cancelId());
        if (old != null) {
            if (!old.equals(fingerprint)) throw new IdempotencyConflict("RUNTIME_CANCEL_IDEMPOTENCY_CONFLICT");
            return new CommandResult(command.cancelId(), command.runId(), statusJdbc(command.runId()), true);
        }
        Status current = statusJdbc(command.runId());
        if (current == null) throw new IllegalArgumentException("runId does not exist");
        jdbc.update("INSERT INTO runtime_cancels(cancel_id,run_id,request_fingerprint) VALUES (?,?,?)", command.cancelId(), command.runId(), fingerprint);
        if (current != Status.SUCCEEDED && current != Status.FAILED) {
            jdbc.update("UPDATE runtime_runs SET status=?,updated_at=CURRENT_TIMESTAMP WHERE run_id=?", Status.CANCELED.name(), command.runId());
            current = Status.CANCELED;
        }
        return new CommandResult(command.cancelId(), command.runId(), current, false);
    }

    private EventResult eventJdbc(RunEvent event) {
        String fingerprint = event.eventSeq() + "|" + event.state() + "|" + String.valueOf(event.payload());
        Status current = statusJdbc(event.runId());
        if (current == null) throw new IllegalArgumentException("runId does not exist");
        var known = jdbc.query("SELECT event_fingerprint FROM runtime_event_inbox WHERE run_id=? AND event_id=?", (rs, row) -> rs.getString(1), event.runId(), event.eventId());
        if (!known.isEmpty()) {
            if (!fingerprint.equals(known.getFirst())) throw new RuntimeException("RUNTIME_EVENT_IDEMPOTENCY_CONFLICT", "event fingerprint conflict");
            return new EventResult(event.eventId(), event.runId(), current, true);
        }
        var latest = jdbc.query("SELECT event_seq,state FROM runtime_event_inbox WHERE run_id=? ORDER BY event_seq DESC LIMIT 1", (rs, row) -> new Object[] {rs.getLong(1), rs.getString(2)}, event.runId());
        if (!latest.isEmpty()) {
            long previousSeq = (long) latest.getFirst()[0];
            RunEvent.State previous = RunEvent.State.valueOf((String) latest.getFirst()[1]);
            if (event.eventSeq() <= previousSeq) throw new RuntimeException("RUNTIME_EVENT_OUT_OF_ORDER", "event sequence out of order");
            if (event.eventSeq() > previousSeq + 1) throw new RuntimeException("RUNTIME_EVENT_GAP", "event sequence gap");
            if ((previous == RunEvent.State.DISPATCHED && event.state() != RunEvent.State.RUNNING)
                    || (previous == RunEvent.State.RUNNING && !terminal(event.state()))) throw new RuntimeException("RUNTIME_STATE_CONFLICT", "invalid state transition");
        }
        jdbc.update("INSERT INTO runtime_event_inbox(run_id,event_id,event_seq,event_fingerprint,state,payload_json,occurred_at) VALUES (?,?,?,?,?,CAST(? AS jsonb),?)",
                event.runId(), event.eventId(), event.eventSeq(), fingerprint, event.state().name(), payloadJson(event.payload()), event.occurredAt());
        jdbc.update("UPDATE runtime_runs SET status=?,updated_at=CURRENT_TIMESTAMP WHERE run_id=?", event.state().name(), event.runId());
        return new EventResult(event.eventId(), event.runId(), toStatus(event.state()), false);
    }

    private String queryFingerprint(String table, String keyColumn, String key) {
        var values = jdbc.query("SELECT request_fingerprint FROM " + table + " WHERE " + keyColumn + "=?", (rs, row) -> rs.getString(1), key);
        return values.isEmpty() ? null : values.getFirst();
    }

    private Status statusJdbc(String runId) {
        var values = jdbc.query("SELECT status FROM runtime_runs WHERE run_id=?", (rs, row) -> Status.valueOf(rs.getString(1)), runId);
        return values.isEmpty() ? null : values.getFirst();
    }

    private String payloadJson(Object payload) {
        try { return objectMapper.writeValueAsString(payload); }
        catch (JsonProcessingException exception) { throw new RuntimeException("RUNTIME_CONTRACT_INVALID", "event payload is not valid JSON"); }
    }

    private static boolean terminal(RunEvent.State state) { return state == RunEvent.State.SUCCEEDED || state == RunEvent.State.FAILED || state == RunEvent.State.CANCELED; }

    private static Status toStatus(RunEvent.State state) {
        return switch (state) { case DISPATCHED -> Status.DISPATCHED; case RUNNING -> Status.RUNNING; case SUCCEEDED -> Status.SUCCEEDED; case FAILED -> Status.FAILED; case CANCELED -> Status.CANCELED; };
    }

    public enum Status { DISPATCHED, RUNNING, SUCCEEDED, FAILED, CANCELED }
    public record CommandResult(String commandId, String runId, Status status, boolean duplicate) {}
    public record EventResult(String eventId, String runId, Status status, boolean duplicate) {}
    public static final class IdempotencyConflict extends RuntimeException { public IdempotencyConflict(String message) { super(message, message); } }
}

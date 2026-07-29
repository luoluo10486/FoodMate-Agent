package com.foodmate.application.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodmate.application.runtime.persistence.RuntimeRecoveryStore;
import com.foodmate.application.runtime.persistence.RuntimeRecoveryStore.RecoveryRequest;
import com.foodmate.application.runtime.persistence.RuntimeRecoveryStore.RecoveryRun;
import com.foodmate.shared.id.IdGenerator;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates a new dispatch attempt only after Java has reconciled the persisted Run facts. */
@Service
public class RuntimeRecoveryService {
    private final RuntimeRecoveryStore store;
    private final IdGenerator ids;
    private final AgentAdmissionService admission;
    private final ObjectMapper mapper =
            new ObjectMapper()
                    .findAndRegisterModules()
                    .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                    .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    private final int queuePriority;

    public RuntimeRecoveryService(
            ObjectProvider<RuntimeRecoveryStore> store,
            IdGenerator ids,
            AgentAdmissionService admission,
            @Value("${FOODMATE_AGENT_RECOVERY_QUEUE_PRIORITY:10}") int queuePriority) {
        this.store = store.getIfAvailable();
        this.ids = ids;
        this.admission = admission;
        this.queuePriority = queuePriority;
    }

    @Transactional
    public RecoveryResult recover(RecoveryRequest request) {
        if (store == null) throw error("RUNTIME_UNAVAILABLE", "database is not configured");
        if (request.checkpointVersion() < 1
                || blank(request.checkpointDigest())
                || request.checkpointDigest().length() > 71)
            throw error("RECOVERY_CONTEXT_INVALID", "checkpoint metadata is required");

        RecoveryRun run = store.lockRun(request.runId(), request.userId());
        if (run == null) throw error("RUNTIME_NOT_FOUND", "run does not belong to user");
        if (List.of("completed", "failed", "cancelled", "superseded").contains(run.status()))
            throw error("RECOVERY_RUN_TERMINAL", "terminal Run cannot be resumed");
        if (run.deadline() == null || !run.deadline().isAfter(Instant.now()))
            throw error("RUNTIME_DEADLINE_EXCEEDED", "Run deadline has expired");
        if (run.payload() == null || run.payload().isBlank())
            throw error("RUNTIME_CONTRACT_INVALID", "previous dispatch payload is missing");
        List<String> persistedInvocations = store.completedInvocationIds(request.runId());
        if (persistedInvocations == null
                || !Set.copyOf(request.completedInvocationIds()).containsAll(persistedInvocations))
            throw error(
                    "RECOVERY_COMPLETED_INVOCATIONS_MISMATCH",
                    "recovery request does not include all completed invocations");

        String previousDispatchId = run.previousDispatchId();
        String dispatchId = "dsp_" + UUID.randomUUID().toString().replace("-", "");
        int attempt = run.previousAttempt() + 1;
        String payload =
                withRecoveryContext(
                        run.payload(),
                        request,
                        previousDispatchId,
                        dispatchId,
                        attempt,
                        run.deadline(),
                        run.budgetRevision());
        String requestHash = digestWithoutRequestHash(payload);
        payload = replaceRequestHash(payload, requestHash);
        long dispatchRowId = ids.nextId();
        long epoch = run.previousEpoch() + 1;

        store.expireDispatch(run.dispatchRowId());
        store.expireOutbox(run.dispatchRowId());
        store.insertDispatch(
                dispatchRowId,
                request.runId(),
                dispatchId,
                attempt,
                epoch,
                "fence_" + UUID.randomUUID().toString().replace("-", ""),
                run.deadline());
        store.insertOutbox(
                ids.nextId(),
                dispatchRowId,
                request.runId(),
                dispatchId,
                attempt,
                run.deadline(),
                epoch,
                payload,
                requestHash);
        AgentAdmissionService.Admission result =
                admission.admit(
                        Long.toString(request.runId()),
                        request.userId(),
                        run.sessionId(),
                        queuePriority);
        if (result.state() == AgentAdmissionService.State.QUEUED)
            store.markOutboxQueued(request.runId(), dispatchId, queuePriority);
        store.markRunQueued(request.runId(), dispatchRowId);
        return new RecoveryResult(Long.toString(request.runId()), dispatchId, attempt, "queued");
    }

    private String withRecoveryContext(
            String payload,
            RecoveryRequest request,
            String previousDispatchId,
            String dispatchId,
            int attempt,
            Instant deadline,
            int budgetRevision) {
        try {
            ObjectNode root = (ObjectNode) mapper.readTree(payload);
            root.put("dispatch_id", dispatchId);
            root.put("attempt", attempt);
            root.put("request_id", "req_" + UUID.randomUUID().toString().replace("-", ""));
            root.put("deadline_at", deadline.toString());
            ObjectNode recovery = root.putObject("recovery_context");
            recovery.put("previous_dispatch_id", previousDispatchId);
            recovery.put("previous_attempt", attempt - 1);
            recovery.put("checkpoint_version", request.checkpointVersion());
            recovery.put("checkpoint_digest", request.checkpointDigest());
            recovery.put("budget_revision", budgetRevision);
            ArrayNode completed = recovery.putArray("completed_invocation_ids");
            request.completedInvocationIds().forEach(completed::add);
            return mapper.writeValueAsString(root);
        } catch (JsonProcessingException | ClassCastException exception) {
            throw error("RUNTIME_CONTRACT_INVALID", "stored dispatch payload is invalid");
        }
    }

    private String replaceRequestHash(String payload, String hash) {
        try {
            ObjectNode root = (ObjectNode) mapper.readTree(payload);
            root.put("request_hash", hash);
            return mapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw error("RUNTIME_CONTRACT_INVALID", "recovery payload is invalid");
        }
    }

    private String digestWithoutRequestHash(String payload) {
        try {
            ObjectNode root = (ObjectNode) mapper.readTree(payload);
            root.remove("request_hash");
            return "sha256:"
                    + hex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(mapper.writeValueAsBytes(root)));
        } catch (Exception exception) {
            throw error("RUNTIME_CONTRACT_INVALID", "cannot hash recovery payload");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static com.foodmate.shared.runtime.RuntimeException error(String code, String message) {
        return new com.foodmate.shared.runtime.RuntimeException(code, message);
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format("%02x", value));
        return result.toString();
    }

    public record RecoveryResult(String runId, String dispatchId, int attempt, String status) {}
}

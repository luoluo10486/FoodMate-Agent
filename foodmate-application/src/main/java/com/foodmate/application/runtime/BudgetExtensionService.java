package com.foodmate.application.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodmate.application.runtime.persistence.BudgetExtensionStore;
import com.foodmate.application.runtime.persistence.BudgetExtensionStore.*;
import com.foodmate.shared.id.IdGenerator;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 处理用户确认后的预算追加，并在同一事务中生成新的 dispatch attempt。 */
@Service
public class BudgetExtensionService {
    private final BudgetExtensionStore store;
    private final IdGenerator ids;
    private final int maxTokens;
    private final BigDecimal maxCost;
    private final int ttlSeconds;
    private final AgentAdmissionService admission;
    private final ObjectMapper mapper =
            new ObjectMapper()
                    .findAndRegisterModules()
                    .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                    .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    public BudgetExtensionService(
            ObjectProvider<BudgetExtensionStore> store,
            IdGenerator ids,
            AgentAdmissionService admission,
            @Value("${FOODMATE_AGENT_MAX_TOKEN_EXTENSION_PER_CONFIRMATION:30000}") int maxTokens,
            @Value("${FOODMATE_AGENT_MAX_COST_EXTENSION_CNY_PER_CONFIRMATION:1.00}")
                    BigDecimal maxCost,
            @Value("${FOODMATE_AGENT_BUDGET_EXTENSION_TTL_SECONDS:900}") int ttlSeconds) {
        this.store = store.getIfAvailable();
        this.ids = ids;
        this.admission = admission;
        this.maxTokens = maxTokens;
        this.maxCost = maxCost;
        this.ttlSeconds = ttlSeconds;
    }

    @Transactional
    public ExtensionResult confirm(
            long userId,
            long runId,
            int additionalTokens,
            BigDecimal additionalCost,
            String confirmationDigest) {
        if (store == null)
            throw new com.foodmate.shared.runtime.RuntimeException(
                    "RUNTIME_UNAVAILABLE", "database is not configured");
        if (additionalTokens <= 0
                || additionalTokens > maxTokens
                || additionalCost == null
                || additionalCost.signum() <= 0
                || additionalCost.compareTo(maxCost) > 0) {
            throw new com.foodmate.shared.runtime.RuntimeException(
                    "BUDGET_EXTENSION_LIMIT_EXCEEDED", "budget extension exceeds configured limit");
        }
        if (confirmationDigest == null
                || confirmationDigest.isBlank()
                || confirmationDigest.length() > 71) {
            throw new com.foodmate.shared.runtime.RuntimeException(
                    "BUDGET_CONFIRMATION_INVALID", "confirmation digest is required");
        }
        RunRow run = store.lockRun(runId, userId);
        if (run == null
                || !("completed".equals(run.status())
                        && "safety_degraded".equals(run.resultType()))) {
            throw new com.foodmate.shared.runtime.RuntimeException(
                    "RUNTIME_STATE_CONFLICT", "run is not waiting for budget confirmation");
        }
        ExistingExtension existing = store.findConfirmed(runId, confirmationDigest);
        if (existing != null) {
            if (existing.tokens() != additionalTokens
                    || existing.cost().compareTo(additionalCost) != 0) {
                throw new com.foodmate.shared.runtime.RuntimeException(
                        "BUDGET_CONFIRMATION_INVALID",
                        "confirmation digest does not match the requested budget");
            }
            DispatchResult currentDispatch = store.latestDispatchResult(runId);
            if (currentDispatch != null)
                return new ExtensionResult(
                        Long.toString(runId),
                        currentDispatch.dispatchId(),
                        currentDispatch.attempt(),
                        currentDispatch.revision(),
                        "queued");
            throw new com.foodmate.shared.runtime.RuntimeException(
                    "RUNTIME_STATE_CONFLICT", "confirmed budget dispatch is missing");
        }
        Snapshot current = store.lockLatestSnapshot(runId);
        if (current == null)
            throw new com.foodmate.shared.runtime.RuntimeException(
                    "RUNTIME_STATE_CONFLICT", "budget snapshot is missing");
        int extensionNo = store.nextExtensionNo(runId);
        long extensionId = ids.nextId();
        Instant expiresAt = Instant.now().plusSeconds(ttlSeconds);
        store.insertExtension(
                extensionId,
                runId,
                extensionNo,
                additionalTokens,
                additionalCost,
                confirmationDigest,
                expiresAt);
        int revision = current.revision() + 1;
        store.insertSnapshot(
                ids.nextId(),
                runId,
                revision,
                current.tokens() + additionalTokens,
                current.cost().add(additionalCost),
                current,
                confirmationDigest);
        PreviousDispatch old = store.lockPreviousDispatch(runId);
        if (old == null)
            throw new com.foodmate.shared.runtime.RuntimeException(
                    "RUNTIME_STATE_CONFLICT", "active dispatch is missing");
        String dispatchId = "dsp_" + UUID.randomUUID().toString().replace("-", "");
        Instant deadline = Instant.now().plusSeconds(current.executionTimeout());
        String payload =
                nextPayload(
                        old.payload(),
                        dispatchId,
                        old.attempt() + 1,
                        deadline,
                        revision,
                        current,
                        additionalTokens,
                        additionalCost);
        String requestHash = digestWithoutRequestHash(payload);
        payload = replaceRequestHash(payload, requestHash);
        long dispatchRowId = ids.nextId();
        store.expireDispatch(old.dispatchRowId());
        store.expireOutbox(old.dispatchRowId());
        store.insertDispatch(
                dispatchRowId,
                runId,
                dispatchId,
                old.attempt() + 1,
                old.epoch() + 1,
                "fence_" + UUID.randomUUID().toString().replace("-", ""),
                deadline);
        store.insertOutbox(
                ids.nextId(),
                dispatchRowId,
                runId,
                dispatchId,
                old.attempt() + 1,
                deadline,
                old.epoch() + 1,
                payload,
                requestHash);
        AgentAdmissionService.Admission admissionResult =
                admission.admit(Long.toString(runId), userId, run.sessionId(), 20);
        if (admissionResult.state() == AgentAdmissionService.State.QUEUED) {
            store.markOutboxQueued(runId, dispatchId, 20);
        }
        store.markRunQueued(runId, dispatchRowId);
        return new ExtensionResult(
                Long.toString(runId), dispatchId, old.attempt() + 1, revision, "queued");
    }

    private String nextPayload(
            String payload,
            String dispatchId,
            int attempt,
            Instant deadline,
            int revision,
            Snapshot current,
            int tokens,
            BigDecimal cost) {
        try {
            ObjectNode root = (ObjectNode) mapper.readTree(payload);
            root.put("dispatch_id", dispatchId);
            root.put("attempt", attempt);
            root.put("request_id", "req_" + UUID.randomUUID().toString().replace("-", ""));
            root.put("deadline_at", deadline.toString());
            ObjectNode options = (ObjectNode) root.with("runtime_options");
            ObjectNode budget = (ObjectNode) options.with("budget_snapshot");
            budget.put("max_total_tokens", current.tokens() + tokens);
            budget.put("max_cost_cny", current.cost().add(cost));
            budget.put("revision", revision);
            return mapper.writeValueAsString(root);
        } catch (JsonProcessingException | ClassCastException exception) {
            throw new com.foodmate.shared.runtime.RuntimeException(
                    "RUNTIME_CONTRACT_INVALID", "stored dispatch payload is invalid");
        }
    }

    private String replaceRequestHash(String payload, String hash) {
        try {
            ObjectNode root = (ObjectNode) mapper.readTree(payload);
            root.put("request_hash", hash);
            return mapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new com.foodmate.shared.runtime.RuntimeException(
                    "RUNTIME_CONTRACT_INVALID", "dispatch payload is invalid");
        }
    }

    private String digestWithoutRequestHash(String payload) {
        try {
            ObjectNode root = (ObjectNode) mapper.readTree(payload);
            root.remove("request_hash");
            byte[] bytes = mapper.writeValueAsBytes(root);
            return "sha256:" + hex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new com.foodmate.shared.runtime.RuntimeException(
                    "RUNTIME_CONTRACT_INVALID", "cannot hash dispatch payload");
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder();
        for (byte item : bytes) value.append(String.format("%02x", item));
        return value.toString();
    }

    public record ExtensionResult(
            String runId, String dispatchId, int attempt, int budgetRevision, String status) {}
}

package com.foodmate.application.runtime;

import com.foodmate.application.runtime.persistence.AdmissionReconciliationStore;
import com.foodmate.shared.id.IdGenerator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 收敛排队和执行超时。
 *
 * <p>Redis lease 不是最终事实，因此超时扫描必须以 PostgreSQL 的 deadline 和 Outbox 状态为准；扫描完成后再释放 Redis lease，防止 Java
 * 进程重启后 Run 永久占位。
 */
@Component
public class RuntimeAdmissionReconciler {
    private final AdmissionReconciliationStore store;
    private final AgentAdmissionService admission;
    private final IdGenerator ids;
    private final int queueTimeoutSeconds;

    public RuntimeAdmissionReconciler(
            AdmissionReconciliationStore store,
            AgentAdmissionService admission,
            IdGenerator ids,
            @Value("${FOODMATE_AGENT_QUEUE_TIMEOUT_SECONDS:30}") int queueTimeoutSeconds) {
        this.store = store;
        this.admission = admission;
        this.ids = ids;
        this.queueTimeoutSeconds = Math.max(1, queueTimeoutSeconds);
    }

    @Scheduled(fixedDelayString = "${foodmate.runtime.admission.reconcile-ms:1000}")
    @Transactional
    public void reconcile() {
        try {
            admission.renewActiveLeases();
        } catch (com.foodmate.shared.runtime.RuntimeException failure) {
            if (!"RUNTIME_COORDINATION_UNAVAILABLE".equals(failure.code())) throw failure;
        }
        reconcilePromotedQueueRows();
        List<AdmissionReconciliationStore.RunRef> queued =
                store.findQueueExpired(queueTimeoutSeconds, 20);
        queued.forEach(run -> fail(run, "RUNTIME_QUEUE_TIMEOUT", "Agent 排队超时"));

        List<AdmissionReconciliationStore.RunRef> expired = store.findExecutionExpired(20);
        expired.forEach(run -> fail(run, "RUNTIME_DEADLINE_EXCEEDED", "Agent 执行超时"));
    }

    /**
     * Redis promotion and PostgreSQL outbox promotion are deliberately separate operations. If the
     * JVM stops between them, this pass repairs the database from the Redis permit fact.
     */
    private void reconcilePromotedQueueRows() {
        try {
            List<String> promoted =
                    store.findQueued(20).stream()
                            .filter(run -> admission.isActive(run.runId()))
                            .map(AdmissionReconciliationStore.RunRef::runId)
                            .toList();
            if (!promoted.isEmpty()) store.promoteOutboxes(promoted);
        } catch (com.foodmate.shared.runtime.RuntimeException failure) {
            if (!"RUNTIME_COORDINATION_UNAVAILABLE".equals(failure.code())) throw failure;
        }
    }

    protected void fail(AdmissionReconciliationStore.RunRef run, String code, String message) {
        String result = "{\"error_code\":\"" + code + "\",\"message\":\"" + message + "\"}";
        int updated = store.failRun(run.agentRunId(), code, result);
        if (updated == 0) {
            releaseBestEffort(run.runId());
            return;
        }
        store.expireDispatches(run.agentRunId());
        store.failOutboxes(run.agentRunId(), code);
        // 直接由 Java 超时裁决时也必须产生可恢复的 SSE 终态事件。
        Long next = store.nextSseSequence(run.agentRunId());
        store.insertFailedEvent(
                ids.nextId(),
                run.agentRunId(),
                "sse_" + ids.nextId(),
                next,
                run.agentRunId() + ":timeout:" + code,
                "{\"error_code\":\"" + code + "\",\"message\":\"" + message + "\"}");
        store.updateSseSequence(run.agentRunId(), next);
        for (String promotedRunId : releaseBestEffort(run.runId())) {
            store.promoteOutboxes(List.of(promotedRunId));
        }
    }

    private List<String> releaseBestEffort(String runId) {
        try {
            return admission.releaseAndPromote(runId);
        } catch (com.foodmate.shared.runtime.RuntimeException failure) {
            if ("RUNTIME_COORDINATION_UNAVAILABLE".equals(failure.code())) return List.of();
            throw failure;
        }
    }
}

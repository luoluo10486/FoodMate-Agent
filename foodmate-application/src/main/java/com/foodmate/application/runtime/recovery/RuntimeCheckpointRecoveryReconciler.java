package com.foodmate.application.runtime.recovery;

import com.foodmate.application.runtime.port.out.RuntimeRecoveryRepository;
import com.foodmate.application.runtime.port.out.RuntimeRecoveryRepository.RecoveryCandidate;
import com.foodmate.application.runtime.service.RuntimeRecoveryService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Starts recovery after a Runtime startup notification. PostgreSQL facts decide whether a Run is
 * stale; the notification itself never authorizes a blind replay.
 */
@Component
public class RuntimeCheckpointRecoveryReconciler {
    private static final Logger log =
            LoggerFactory.getLogger(RuntimeCheckpointRecoveryReconciler.class);

    private final RuntimeRecoveryRepository store;
    private final RuntimeRecoveryService recovery;
    private final boolean enabled;
    private final int staleSeconds;
    private final int limit;

    public RuntimeCheckpointRecoveryReconciler(
            ObjectProvider<RuntimeRecoveryRepository> storeProvider,
            RuntimeRecoveryService recovery,
            @Value("${FOODMATE_AGENT_AUTO_RECOVERY_ENABLED:false}") boolean enabled,
            @Value("${FOODMATE_AGENT_RECOVERY_STALE_SECONDS:30}") int staleSeconds,
            @Value("${FOODMATE_AGENT_RECOVERY_RECONCILE_LIMIT:20}") int limit) {
        this.store = storeProvider.getIfAvailable();
        this.recovery = recovery;
        this.enabled = enabled;
        this.staleSeconds = Math.max(1, staleSeconds);
        this.limit = Math.max(1, limit);
    }

    @Scheduled(fixedDelayString = "${FOODMATE_AGENT_RECOVERY_RECONCILE_MS:5000}")
    public void scheduledReconcile() {
        if (enabled) triggerStaleRecoveries();
    }

    public TriggerResult triggerStaleRecoveries() {
        if (!enabled || store == null) return new TriggerResult(0, List.of());
        List<RecoveryCandidate> candidates = store.findStaleToolWaitRuns(staleSeconds, limit);
        int recovered = 0;
        List<String> dispatches = new java.util.ArrayList<>();
        for (RecoveryCandidate candidate : candidates) {
            try {
                RuntimeRecoveryService.RecoveryResult result =
                        recovery.recoverFromPersistedCheckpoint(
                                candidate.userId(), candidate.runId());
                recovered++;
                dispatches.add(result.dispatchId());
            } catch (com.foodmate.shared.runtime.RuntimeException exception) {
                // One stale Run must not block recovery of the remaining candidates.
                log.warn(
                        "runtime checkpoint recovery skipped: run_id={}, code={}",
                        candidate.runId(),
                        exception.code());
            }
        }
        return new TriggerResult(recovered, List.copyOf(dispatches));
    }

    public record TriggerResult(int recovered, List<String> dispatchIds) {}
}

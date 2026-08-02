package com.foodmate.application.runtime.service;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.foodmate.application.runtime.port.out.RuntimeRecoveryRepository.RecoveryRequest;

/** Reconciles an acknowledged checkpoint and creates a new runtime attempt. */
public interface RuntimeRecoveryService {
    RecoveryResult recover(RecoveryRequest request);

    RecoveryResult recoverFromPersistedCheckpoint(long userId, long runId);

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record RecoveryResult(String runId, String dispatchId, int attempt, String status) {}
}

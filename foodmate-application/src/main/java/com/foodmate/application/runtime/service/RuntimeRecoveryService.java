package com.foodmate.application.runtime.service;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/** 对已确认的 checkpoint 进行对账并创建新的 Runtime 尝试。 */
public interface RuntimeRecoveryService {
    RecoveryResult recover(RecoveryCommand command);

    RecoveryResult recoverFromPersistedCheckpoint(long userId, long runId);

    record RecoveryCommand(
            long userId,
            long runId,
            int checkpointVersion,
            String checkpointDigest,
            List<String> completedInvocationIds) {
        public RecoveryCommand {
            completedInvocationIds =
                    completedInvocationIds == null
                            ? List.of()
                            : List.copyOf(completedInvocationIds);
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record RecoveryResult(String runId, String dispatchId, int attempt, String status) {}
}

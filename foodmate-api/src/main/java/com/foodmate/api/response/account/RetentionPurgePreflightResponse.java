package com.foodmate.api.response.account;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.foodmate.application.retention.service.DataRetentionService;
import java.util.List;

/** 数据清理前置检查响应。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RetentionPurgePreflightResponse(
        long requestId,
        String status,
        String resourceType,
        long resourceId,
        boolean policyFound,
        boolean hardDeleteEnabled,
        boolean resourceSoftDeleted,
        boolean retentionElapsed,
        boolean legalHoldClear,
        boolean taskContractValid,
        boolean readyToExecute,
        List<Task> tasks,
        List<String> blockers) {
    public static RetentionPurgePreflightResponse from(DataRetentionService.PurgePreflight result) {
        return new RetentionPurgePreflightResponse(
                result.requestId(),
                result.status(),
                result.resourceType(),
                result.resourceId(),
                result.policyFound(),
                result.hardDeleteEnabled(),
                result.resourceSoftDeleted(),
                result.retentionElapsed(),
                result.legalHoldClear(),
                result.taskContractValid(),
                result.readyToExecute(),
                result.tasks().stream()
                        .map(
                                task ->
                                        new Task(
                                                task.taskType(),
                                                task.status(),
                                                task.attemptCount(),
                                                task.lastErrorCode()))
                        .toList(),
                result.blockers());
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Task(String taskType, String status, int attemptCount, String lastErrorCode) {}
}

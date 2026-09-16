package com.foodmate.application.runtime.service;

/** 协调用户跳过单个工具步骤的持久化、审计和 Runtime 投递。 */
public interface ToolSkipService {
    SkipResult request(long userId, String runId, String proposalId, String reason);

    void publishRequested();

    record SkipResult(
            String runId,
            String proposalId,
            String skipId,
            String dispatchId,
            int attempt,
            String status) {}
}

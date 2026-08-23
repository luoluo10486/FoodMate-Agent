package com.foodmate.application.runtime.service;

import java.util.List;

/** 接收用户对已完成 Agent 回答的结构化反馈。 */
public interface AgentFeedbackService {
    FeedbackResult submit(long userId, long runId, long messageId, SubmitCommand command);

    record SubmitCommand(
            Boolean helpful, List<String> reasonCodes, String comment, String idempotencyKey) {}

    record FeedbackResult(
            long feedbackId,
            long userId,
            long runId,
            long messageId,
            boolean helpful,
            List<String> reasonCodes,
            boolean highRisk,
            String idempotencyKey,
            String parametersDigest) {}
}

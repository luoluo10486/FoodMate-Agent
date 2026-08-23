package com.foodmate.application.runtime.port.out;

import java.util.List;

/** 持久化用户对 Agent 回答的结构化反馈，并返回受控的运行版本快照。 */
public interface AgentFeedbackRepository {
    FeedbackTarget target(long userId, long runId, long messageId);

    FeedbackView findByIdempotency(long userId, String idempotencyKey);

    FeedbackView findByMessage(long userId, long runId, long messageId);

    int insert(FeedbackWrite write);

    record FeedbackTarget(
            long runId,
            long messageId,
            String traceId,
            String evalId,
            String modelRouteVersion,
            String promptVersion,
            String rubricVersion) {}

    record FeedbackWrite(
            long feedbackId,
            long userId,
            long runId,
            long messageId,
            boolean helpful,
            List<String> reasonCodes,
            String comment,
            String traceId,
            String evalId,
            String modelRouteVersion,
            String promptVersion,
            String rubricVersion,
            boolean highRisk,
            String idempotencyKey,
            String parametersDigest) {}

    record FeedbackView(
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

package com.foodmate.application.runtime.service;

import com.foodmate.application.runtime.port.out.AgentFeedbackRepository.FeedbackView;
import java.util.List;

/** 接收用户对已完成 Agent 回答的结构化反馈。 */
public interface AgentFeedbackService {
    FeedbackView submit(long userId, long runId, long messageId, SubmitCommand command);

    record SubmitCommand(
            Boolean helpful, List<String> reasonCodes, String comment, String idempotencyKey) {}
}

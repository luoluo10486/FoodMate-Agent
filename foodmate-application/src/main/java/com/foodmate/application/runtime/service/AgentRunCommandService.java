package com.foodmate.application.runtime.service;

import com.foodmate.application.account.service.UserAccountService;

/** Creates a user message and its durable runtime dispatch command. */
public interface AgentRunCommandService {
    UserAccountService.MessageRecord createUserMessageRun(
            long userId, long sessionId, String content, String traceId);

    RunCreation createUserMessageRunDetails(
            long userId, long sessionId, String content, String traceId);

    record RunCreation(
            UserAccountService.MessageRecord message,
            String runId,
            String dispatchId,
            String status) {}
}

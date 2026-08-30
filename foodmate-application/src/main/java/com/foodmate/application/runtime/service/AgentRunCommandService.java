package com.foodmate.application.runtime.service;

import com.foodmate.application.account.service.UserAccountService;

/** 创建用户消息及其持久化 Runtime 派发命令。 */
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

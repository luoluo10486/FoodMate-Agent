package com.foodmate.application.conversation.service;

/** 维护会话的版本化摘要投影。 */
public interface SessionSummaryService {
    void maybeRefresh(long userId, long sessionId);

    void invalidate(long userId, long sessionId);

    void invalidateForUser(long userId);
}

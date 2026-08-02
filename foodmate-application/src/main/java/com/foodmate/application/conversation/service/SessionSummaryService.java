package com.foodmate.application.conversation.service;

/** Maintains the versioned summary projection for a conversation. */
public interface SessionSummaryService {
    void maybeRefresh(long userId, long sessionId);

    void invalidate(long userId, long sessionId);

    void invalidateForUser(long userId);
}

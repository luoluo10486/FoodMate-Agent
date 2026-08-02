package com.foodmate.application.conversation.port.out;

import java.util.List;

/** 会话摘要持久化端口；数据库锁和 SQL 细节由基础设施层实现。 */
public interface ConversationSummaryRepository {
    boolean ownsSession(long userId, long sessionId);

    List<MessageSnapshot> findEffectiveMessages(long sessionId);

    SummarySnapshot lockSummary(long sessionId);

    void insertSummary(NewSummary summary);

    int updateSummary(UpdatedSummary summary);

    int invalidate(long userId, long sessionId);

    int invalidateForUser(long userId);

    record MessageSnapshot(long messageId, int sequence, String role, String content) {}

    record SummarySnapshot(long id, int version, int sourceCount) {}

    record NewSummary(
            long id,
            long sessionId,
            String text,
            String structuredJson,
            int coveredFrom,
            int coveredTo,
            int sourceCount,
            String promptVersion,
            String digest,
            long operatorId) {}

    record UpdatedSummary(
            long id,
            int expectedVersion,
            String text,
            String structuredJson,
            int coveredFrom,
            int coveredTo,
            int sourceCount,
            String promptVersion,
            String digest,
            long operatorId) {}
}

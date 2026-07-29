package com.foodmate.application.runtime;

import com.foodmate.application.runtime.persistence.SessionSummaryStore;
import com.foodmate.shared.id.IdGenerator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 超过最近 8 条原始消息后更新摘要。摘要更新使用版本条件，避免并发请求互相覆盖。 当前实现是确定性的短摘要，后续可替换摘要模型但必须保留同一 CAS 契约。 */
@Service
public class SessionSummaryService {
    private static final int RAW_MESSAGE_LIMIT = 8;
    private final SessionSummaryStore store;
    private final IdGenerator ids;

    public SessionSummaryService(SessionSummaryStore store, IdGenerator ids) {
        this.store = store;
        this.ids = ids;
    }

    @Transactional
    public void maybeRefresh(long userId, long sessionId) {
        if (!store.ownsSession(userId, sessionId)) return;
        List<SessionSummaryStore.MessageSnapshot> messages = store.findEffectiveMessages(sessionId);
        if (messages.size() <= RAW_MESSAGE_LIMIT) return;
        SessionSummaryStore.SummarySnapshot current = store.lockSummary(sessionId);
        if (current != null && messages.size() == current.sourceCount() + RAW_MESSAGE_LIMIT) return;
        List<SessionSummaryStore.MessageSnapshot> oldMessages =
                messages.subList(0, messages.size() - RAW_MESSAGE_LIMIT);
        int from = oldMessages.getFirst().sequence();
        int to = oldMessages.getLast().sequence();
        String summary =
                oldMessages.stream()
                        .map(item -> item.role() + ": " + item.content())
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse("");
        summary = summary.length() > 2000 ? summary.substring(summary.length() - 2000) : summary;
        String digest = "sha256:" + hex(sha256(summary));
        if (current == null) {
            store.insertSummary(
                    new SessionSummaryStore.NewSummary(
                            ids.nextId(),
                            sessionId,
                            summary,
                            from,
                            to,
                            oldMessages.size(),
                            "foodmate-summary-deterministic-v1",
                            digest,
                            userId));
        } else {
            int changed =
                    store.updateSummary(
                            new SessionSummaryStore.UpdatedSummary(
                                    current.id(),
                                    current.version(),
                                    summary,
                                    from,
                                    to,
                                    oldMessages.size(),
                                    "foodmate-summary-deterministic-v1",
                                    digest,
                                    userId));
            if (changed != 1)
                throw new IllegalStateException("session summary was concurrently modified");
        }
    }

    /** 消息被更正或删除后，先失效摘要，下一次读取时重新生成。 */
    @Transactional
    public void invalidate(long userId, long sessionId) {
        store.invalidate(userId, sessionId);
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        for (byte b : bytes) out.append(String.format("%02x", b));
        return out.toString();
    }
}

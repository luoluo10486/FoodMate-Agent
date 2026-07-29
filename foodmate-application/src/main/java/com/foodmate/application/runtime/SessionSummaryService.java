package com.foodmate.application.runtime;

import com.foodmate.application.runtime.persistence.SessionSummaryStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.shared.id.IdGenerator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 超过最近 8 条原始消息后更新摘要。摘要更新使用版本条件，避免并发请求互相覆盖。 当前实现是确定性的短摘要，后续可替换摘要模型但必须保留同一 CAS 契约。 */
@Service
public class SessionSummaryService {
    private static final int RAW_MESSAGE_LIMIT = 8;
    private final SessionSummaryStore store;
    private final IdGenerator ids;
    private final ObjectMapper mapper = new ObjectMapper();

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
        String structured = structuredSummary(oldMessages);
        if (current == null) {
            store.insertSummary(
                    new SessionSummaryStore.NewSummary(
                            ids.nextId(),
                            sessionId,
                            summary,
                            structured,
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
                                    structured,
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

    /** 保存可检索的摘要骨架；原始消息仍是唯一事实，摘要只是可失效的压缩视图。 */
    private String structuredSummary(List<SessionSummaryStore.MessageSnapshot> messages) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("goals", messages.stream().filter(this::isGoal).map(SessionSummaryStore.MessageSnapshot::content).toList());
        value.put("constraints", messages.stream().filter(this::isConstraint).map(SessionSummaryStore.MessageSnapshot::content).toList());
        value.put("decisions", messages.stream().filter(item -> "assistant".equals(item.role())).map(SessionSummaryStore.MessageSnapshot::content).toList());
        value.put("open_questions", List.of());
        value.put("source_message_ids", messages.stream().map(SessionSummaryStore.MessageSnapshot::messageId).toList());
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("summary structured payload failed", exception);
        }
    }

    private boolean isGoal(SessionSummaryStore.MessageSnapshot item) {
        return "user".equals(item.role()) && item.content().matches(".*(想|希望|计划|目标|安排|帮我).*" );
    }

    private boolean isConstraint(SessionSummaryStore.MessageSnapshot item) {
        return item.content().matches(".*(不吃|忌口|过敏|低盐|低糖|预算|不能).*" );
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

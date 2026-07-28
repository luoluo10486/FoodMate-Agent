package com.foodmate.application.runtime;

import com.foodmate.shared.id.IdGenerator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 超过最近 8 条原始消息后更新摘要。摘要更新使用版本条件，避免并发请求互相覆盖。
 * 当前实现是确定性的短摘要，后续可替换摘要模型但必须保留同一 CAS 契约。
 */
@Service
public class SessionSummaryService {
    private static final int RAW_MESSAGE_LIMIT = 8;
    private final JdbcTemplate jdbc;
    private final IdGenerator ids;

    public SessionSummaryService(ObjectProvider<JdbcTemplate> provider, IdGenerator ids) {
        this.jdbc = provider.getIfAvailable();
        this.ids = ids;
    }

    @Transactional
    public void maybeRefresh(long userId, long sessionId) {
        if (jdbc == null) return;
        if (!Boolean.TRUE.equals(jdbc.query("SELECT EXISTS(SELECT 1 FROM sessions WHERE session_id=? AND user_id=? AND is_deleted=FALSE)",
                rs -> rs.next() && rs.getBoolean(1), sessionId, userId))) return;
        List<MessageRow> messages = jdbc.query("SELECT sequence_no,role,content FROM messages WHERE session_id=? AND is_deleted=FALSE ORDER BY sequence_no",
                (rs, row) -> new MessageRow(rs.getInt(1), rs.getString(2), rs.getString(3)), sessionId);
        if (messages.size() <= RAW_MESSAGE_LIMIT) return;
        SummaryRow current = jdbc.query("SELECT summary_id,version,source_message_count FROM session_summaries WHERE session_id=? AND is_deleted=FALSE FOR UPDATE",
                (rs, row) -> new SummaryRow(rs.getLong(1), rs.getInt(2), rs.getInt(3)), sessionId).stream().findFirst().orElse(null);
        if (current != null && messages.size() == current.sourceCount() + RAW_MESSAGE_LIMIT) return;
        List<MessageRow> oldMessages = messages.subList(0, messages.size() - RAW_MESSAGE_LIMIT);
        int from = oldMessages.getFirst().sequence();
        int to = oldMessages.getLast().sequence();
        String summary = oldMessages.stream()
                .map(item -> item.role() + ": " + item.content()).reduce((a, b) -> a + "\n" + b).orElse("");
        summary = summary.length() > 2000 ? summary.substring(summary.length() - 2000) : summary;
        String digest = "sha256:" + hex(sha256(summary));
        if (current == null) {
            jdbc.update("INSERT INTO session_summaries(summary_id,session_id,summary_text,key_constraints,covered_from_sequence,covered_to_sequence,source_message_count,prompt_version,content_digest,version,created_by,updated_by) VALUES (?,?,?, '{}'::jsonb,?,?,?,?,?,1,?,?)",
                    ids.nextId(), sessionId, summary, from, to, oldMessages.size(), "foodmate-summary-deterministic-v1", digest, userId, userId);
        } else {
            jdbc.update("UPDATE session_summaries SET summary_text=?,covered_from_sequence=?,covered_to_sequence=?,source_message_count=?,prompt_version=?,content_digest=?,version=version+1,updated_at=CURRENT_TIMESTAMP,updated_by=?,invalidated_at=NULL WHERE summary_id=? AND version=? AND is_deleted=FALSE",
                    summary, from, to, oldMessages.size(), "foodmate-summary-deterministic-v1", digest, userId, current.id(), current.version());
        }
    }

    /** 消息被更正或删除后，先失效摘要，下一次读取时重新生成。 */
    @Transactional
    public void invalidate(long userId, long sessionId) {
        if (jdbc != null) jdbc.update("UPDATE session_summaries SET invalidated_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,updated_by=? WHERE session_id=? AND is_deleted=FALSE AND EXISTS (SELECT 1 FROM sessions WHERE session_id=? AND user_id=? AND is_deleted=FALSE)", userId, sessionId, sessionId, userId);
    }

    private static byte[] sha256(String text) { try { return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static String hex(byte[] bytes) { StringBuilder out = new StringBuilder(); for (byte b : bytes) out.append(String.format("%02x", b)); return out.toString(); }
    private record MessageRow(int sequence, String role, String content) {}
    private record SummaryRow(long id, int version, int sourceCount) {}
}

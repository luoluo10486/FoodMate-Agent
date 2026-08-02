package com.foodmate.application.account.port.out;

import com.foodmate.application.account.service.UserAccountService.*;
import java.time.Instant;
import java.util.List;

/** 账户、认证会话、会话与消息用例的生产持久化端口。 */
public interface UserAccountRepository {
    boolean userExists(String username, String email);

    void insertUser(
            long id, String no, String username, String email, String password, String nickname);

    void insertProfile(long id, long userId, String name);

    void markLogin(long userId);

    void changePassword(long userId, String hash);

    void revokeAll(long userId);

    List<AuthSessionView> authSessions(long userId);

    List<AdminUserView> adminUsers();

    void revoke(long userId, long sessionId);

    void expireResetTokens(long userId);

    void insertResetToken(long id, long userId, String hash, Instant expires);

    Long resetTokenUser(String hash);

    void consumeResetToken(String hash);

    void touchAuthSession(String hash);

    ProfileRecord profile(long userId);

    void ensureProfile(long id, long userId);

    void updateProfile(long userId, ProfileUpdate update);

    void insertSession(long id, long userId, String title, String mode);

    long countSessions(long userId, String query, String status);

    List<SessionRecord> sessions(long userId, String query, String status, int limit, int offset);

    long countDeletedSessions(long userId);

    List<SessionRecord> deletedSessions(long userId, int limit, int offset);

    boolean sessionExists(long userId, long sessionId);

    void renameSession(long userId, long sessionId, String title);

    void setSessionStatus(long userId, long sessionId, String status);

    void deleteSession(long userId, long sessionId);

    int restoreSession(long userId, long sessionId);

    long countMessages(long sessionId);

    List<MessageRecord> messages(long sessionId, int limit, int offset);

    int updateMessage(long userId, long sessionId, long messageId, String content);

    MessageRecord message(long messageId);

    int deleteMessage(long userId, long sessionId, long messageId);

    List<SearchResult> search(long userId, String query, int limit, int offset);

    int nextSequence(long sessionId);

    void insertMessage(
            long id,
            long sessionId,
            Long runId,
            String role,
            String content,
            String payload,
            int sequence,
            long userId);

    void touchSession(long sessionId);

    void insertAuthSession(
            long id,
            long userId,
            String sessionHash,
            String csrfHash,
            String userAgent,
            String ip,
            Instant expires);

    UserRecord findUser(String value);

    UserRecord getUser(long id);

    AuthSessionRow findAuthSession(String hash);

    void revokeByHash(String hash);

    record AuthSessionRow(
            long userId, String csrfTokenHash, Instant expiresAt, Instant revokedAt) {}
}

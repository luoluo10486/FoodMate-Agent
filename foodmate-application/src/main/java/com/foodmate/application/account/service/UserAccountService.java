package com.foodmate.application.account.service;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import java.util.List;

/** 账户、认证、会话和消息用例接口。 */
public interface UserAccountService {
    AuthResult register(String username, String email, String password, String nickname);

    AuthResult register(
            String username,
            String email,
            String password,
            String nickname,
            SessionMetadata metadata);

    AuthResult login(String usernameOrEmail, String password);

    AuthResult login(String usernameOrEmail, String password, SessionMetadata metadata);

    void logout(String sessionToken);

    void logout(String sessionToken, String refreshToken);

    AuthResult refresh(String refreshToken, SessionMetadata metadata);

    void changePassword(long userId, String currentPassword, String newPassword);

    void requireCurrentPassword(long userId, String currentPassword);

    List<AuthSessionView> listAuthSessions(long userId);

    List<AdminUserView> listUsersForAdmin();

    void revokeAuthSession(long userId, long authSessionId);

    void revokeAllAuthSessions(long userId);

    String createPasswordResetToken(String email);

    void resetPassword(String token, String newPassword);

    UserRecord requireSessionUser(String sessionToken);

    void requireCsrf(String sessionToken, String csrfToken);

    ProfileRecord profile(long userId);

    ProfileRecord updateProfile(long userId, ProfileUpdate update);

    SessionRecord createSession(long userId, String title, String mode);

    List<SessionRecord> listSessions(long userId);

    PageResult<SessionRecord> listSessions(
            long userId, int page, int size, String query, String status);

    PageResult<SessionRecord> listDeletedSessions(long userId, int page, int size);

    void renameSession(long userId, long sessionId, String title);

    void setSessionStatus(long userId, long sessionId, String status);

    void deleteSession(long userId, long sessionId);

    void restoreSession(long userId, long sessionId);

    List<MessageRecord> listMessages(long userId, long sessionId);

    PageResult<MessageRecord> listMessages(long userId, long sessionId, int page, int size);

    MessageRecord updateMessage(long userId, long sessionId, long messageId, String content);

    void deleteMessage(long userId, long sessionId, long messageId);

    List<SearchResult> searchSessions(long userId, String query, int page, int size);

    void archiveSession(long userId, long sessionId);

    MessageRecord addMessage(
            long userId, long sessionId, String role, String content, Object structuredPayload);

    MessageRecord addMessage(
            long userId,
            long sessionId,
            String role,
            String content,
            Object structuredPayload,
            Long agentRunId);

    record AuthResult(
            long userId,
            String username,
            String role,
            String sessionToken,
            String csrfToken,
            Instant expiresAt,
            String refreshToken,
            Instant refreshExpiresAt) {
        public AuthResult(
                long userId,
                String username,
                String role,
                String sessionToken,
                String csrfToken,
                Instant expiresAt) {
            this(userId, username, role, sessionToken, csrfToken, expiresAt, null, null);
        }
    }

    record SessionMetadata(String userAgent, String ipAddress) {
        public static final SessionMetadata EMPTY = new SessionMetadata(null, null);
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record AuthSessionView(
            long authSessionId,
            String deviceId,
            String userAgent,
            String ipAddress,
            Instant expiresAt,
            Instant lastSeenAt,
            Instant createdAt,
            Instant revokedAt) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record AdminUserView(
            long userId,
            String username,
            String email,
            String nickname,
            String role,
            String status,
            long revision) {}

    record UserRecord(
            long userId,
            String username,
            String email,
            String passwordHash,
            String nickname,
            String role,
            String status) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record ProfileRecord(
            long userId,
            String displayName,
            String gender,
            java.time.LocalDate birthday,
            java.math.BigDecimal heightCm,
            java.math.BigDecimal weightKg,
            String activityLevel,
            String dietGoal,
            Integer calorieTarget,
            Integer proteinTarget,
            String allergens,
            String dislikes,
            String preferredUnits) {
        public ProfileRecord with(ProfileUpdate update) {
            return new ProfileRecord(
                    userId,
                    update.displayName() == null ? displayName : update.displayName(),
                    update.gender() == null ? gender : update.gender(),
                    birthday,
                    update.heightCm() == null ? heightCm : update.heightCm(),
                    update.weightKg() == null ? weightKg : update.weightKg(),
                    update.activityLevel() == null ? activityLevel : update.activityLevel(),
                    update.dietGoal() == null ? dietGoal : update.dietGoal(),
                    update.calorieTarget() == null ? calorieTarget : update.calorieTarget(),
                    update.proteinTarget() == null ? proteinTarget : update.proteinTarget(),
                    allergens,
                    dislikes,
                    preferredUnits);
        }
    }

    record ProfileUpdate(
            String displayName,
            String gender,
            java.math.BigDecimal heightCm,
            java.math.BigDecimal weightKg,
            String activityLevel,
            String dietGoal,
            Integer calorieTarget,
            Integer proteinTarget) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record SessionRecord(
            long sessionId,
            long userId,
            String title,
            String mode,
            String status,
            Instant lastMessageAt) {
        public SessionRecord withStatus(String value) {
            return new SessionRecord(sessionId, userId, title, mode, value, lastMessageAt);
        }

        public SessionRecord withTitle(String value) {
            return new SessionRecord(sessionId, userId, value, mode, status, lastMessageAt);
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record MessageRecord(
            long messageId,
            long sessionId,
            Long agentRunId,
            String role,
            String content,
            String structuredPayload,
            int sequenceNo,
            Instant createdAt) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record PageResult<T>(List<T> items, long total, int page, int size) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record SearchResult(long sessionId, String title, String snippet) {}
}

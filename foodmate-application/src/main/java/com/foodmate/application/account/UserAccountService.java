package com.foodmate.application.account;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.foodmate.shared.id.IdGenerator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** P1-1 账户、会话和消息用例。生产持久化通过端口访问，local-stub 显式使用内存。 */
@Service
public class UserAccountService {
    private static final int PASSWORD_ITERATIONS = 120_000;
    private static final long AUTH_SESSION_SECONDS = 2_592_000;

    private final UserAccountStore store;
    private final IdGenerator ids;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();
    private final Map<Long, UserRecord> users = new HashMap<>();
    private final Map<String, AuthSessionRecord> authSessions = new HashMap<>();
    private final Map<Long, ProfileRecord> profiles = new HashMap<>();
    private final Map<Long, SessionRecord> sessions = new HashMap<>();
    private final Map<Long, List<MessageRecord>> messages = new HashMap<>();

    public UserAccountService(
            ObjectProvider<UserAccountStore> storeProvider,
            ObjectProvider<IdGenerator> idProvider) {
        this.store = storeProvider.getIfAvailable();
        this.ids = Objects.requireNonNull(idProvider.getIfAvailable(), "IdGenerator is required");
    }

    public synchronized AuthResult register(
            String username, String email, String password, String nickname) {
        return register(username, email, password, nickname, SessionMetadata.EMPTY);
    }

    public synchronized AuthResult register(
            String username,
            String email,
            String password,
            String nickname,
            SessionMetadata metadata) {
        requireText(username, "username");
        requireText(email, "email");
        validatePassword(password);
        if (store != null) {
            if (store.userExists(username, email)) {
                throw conflict("username or email already exists");
            }
            long userId = ids.nextId();
            store.insertUser(
                    userId, "U" + userId, username, email, hashPassword(password), nickname);
            store.insertProfile(ids.nextId(), userId, nickname);
            return issueSession(userId, username, "user", metadata);
        }
        if (users.values().stream()
                .anyMatch(
                        user ->
                                user.username().equalsIgnoreCase(username)
                                        || user.email().equalsIgnoreCase(email))) {
            throw conflict("username or email already exists");
        }
        long userId = ids.nextId();
        users.put(
                userId,
                new UserRecord(
                        userId,
                        username,
                        email,
                        hashPassword(password),
                        nickname,
                        "user",
                        "active"));
        profiles.put(
                userId,
                new ProfileRecord(
                        userId, nickname, null, null, null, null, null, null, null, null, "[]",
                        "[]", "{}"));
        return issueSession(userId, username, "user", metadata);
    }

    public synchronized AuthResult login(String usernameOrEmail, String password) {
        return login(usernameOrEmail, password, SessionMetadata.EMPTY);
    }

    public synchronized AuthResult login(
            String usernameOrEmail, String password, SessionMetadata metadata) {
        requireText(usernameOrEmail, "username");
        UserRecord user = findUser(usernameOrEmail).orElseThrow(() -> invalidCredentials());
        if ("disabled".equals(user.status()))
            throw new com.foodmate.shared.error.BusinessException(
                    com.foodmate.shared.error.ErrorCode.AUTH_ACCOUNT_DISABLED);
        if ("locked".equals(user.status()))
            throw new com.foodmate.shared.error.BusinessException(
                    com.foodmate.shared.error.ErrorCode.AUTH_ACCOUNT_LOCKED);
        if (!verifyPassword(password, user.passwordHash())) throw invalidCredentials();
        if (store != null) store.markLogin(user.userId());
        return issueSession(user.userId(), user.username(), user.role(), metadata);
    }

    public synchronized void logout(String sessionToken) {
        if (sessionToken != null && !sessionToken.isBlank()) revokeSession(sha256(sessionToken));
    }

    public synchronized void changePassword(
            long userId, String currentPassword, String newPassword) {
        validatePassword(newPassword);
        UserRecord user = getUser(userId).orElseThrow(UserAccountService::authRequired);
        if (!verifyPassword(currentPassword, user.passwordHash())) throw invalidCredentials();
        if (store != null) {
            store.changePassword(userId, hashPassword(newPassword));
            store.revokeAll(userId);
        } else {
            users.put(
                    userId,
                    new UserRecord(
                            user.userId(),
                            user.username(),
                            user.email(),
                            hashPassword(newPassword),
                            user.nickname(),
                            user.role(),
                            user.status()));
            authSessions.replaceAll(
                    (key, value) ->
                            value.userId() == userId
                                    ? new AuthSessionRecord(
                                            value.userId(),
                                            value.sessionTokenHash(),
                                            value.csrfTokenHash(),
                                            value.expiresAt(),
                                            Instant.now())
                                    : value);
        }
    }

    public synchronized void requireCurrentPassword(long userId, String currentPassword) {
        UserRecord user = getUser(userId).orElseThrow(UserAccountService::authRequired);
        if (!verifyPassword(currentPassword, user.passwordHash())) throw invalidCredentials();
    }

    public synchronized List<AuthSessionView> listAuthSessions(long userId) {
        if (store != null) return store.authSessions(userId);
        return authSessions.values().stream()
                .filter(s -> s.userId() == userId)
                .map(
                        s ->
                                new AuthSessionView(
                                        0,
                                        null,
                                        null,
                                        null,
                                        s.expiresAt(),
                                        null,
                                        null,
                                        s.revokedAt()))
                .toList();
    }

    public synchronized List<AdminUserView> listUsersForAdmin() {
        if (store == null)
            return users.values().stream()
                    .map(
                            u ->
                                    new AdminUserView(
                                            u.userId(),
                                            u.username(),
                                            u.email(),
                                            u.nickname(),
                                            u.role(),
                                            u.status()))
                    .toList();
        return store.adminUsers();
    }

    public synchronized void revokeAuthSession(long userId, long authSessionId) {
        if (store != null) store.revoke(userId, authSessionId);
    }

    public synchronized void revokeAllAuthSessions(long userId) {
        if (store != null) store.revokeAll(userId);
        else
            authSessions.replaceAll(
                    (key, value) ->
                            value.userId() == userId
                                    ? new AuthSessionRecord(
                                            value.userId(),
                                            value.sessionTokenHash(),
                                            value.csrfTokenHash(),
                                            value.expiresAt(),
                                            Instant.now())
                                    : value);
    }

    public synchronized String createPasswordResetToken(String email) {
        UserRecord user = findUser(email).orElse(null);
        String raw = randomToken();
        if (user != null && store != null) {
            store.expireResetTokens(user.userId());
            store.insertResetToken(
                    ids.nextId(), user.userId(), sha256(raw), Instant.now().plusSeconds(900));
        }
        return raw;
    }

    public synchronized void resetPassword(String token, String newPassword) {
        validatePassword(newPassword);
        if (store == null) throw notFound("password reset is unavailable");
        String hash = sha256(token);
        Long userId = store.resetTokenUser(hash);
        if (userId == null) throw notFound("invalid or expired reset token");
        store.changePassword(userId, hashPassword(newPassword));
        store.consumeResetToken(hash);
        revokeAllAuthSessions(userId);
    }

    public synchronized UserRecord requireSessionUser(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) throw authRequired();
        String hash = sha256(sessionToken);
        AuthSessionRecord session = store == null ? authSessions.get(hash) : findSession(hash);
        if (session == null
                || session.revokedAt() != null
                || session.expiresAt().isBefore(Instant.now())) throw authRequired();
        if (store != null) store.touchAuthSession(hash);
        UserRecord user = getUser(session.userId()).orElseThrow(UserAccountService::authRequired);
        if (!"active".equals(user.status()))
            throw new com.foodmate.shared.error.BusinessException(
                    "disabled".equals(user.status())
                            ? com.foodmate.shared.error.ErrorCode.AUTH_ACCOUNT_DISABLED
                            : com.foodmate.shared.error.ErrorCode.AUTH_ACCOUNT_LOCKED);
        return user;
    }

    public synchronized void requireCsrf(String sessionToken, String csrfToken) {
        if (csrfToken == null || csrfToken.isBlank())
            throw new com.foodmate.shared.error.BusinessException(
                    com.foodmate.shared.error.ErrorCode.FORBIDDEN, "CSRF token is required");
        String sessionHash = sha256(sessionToken);
        AuthSessionRecord session =
                store == null ? authSessions.get(sessionHash) : findSession(sessionHash);
        if (session == null
                || !MessageDigest.isEqual(
                        session.csrfTokenHash().getBytes(StandardCharsets.UTF_8),
                        sha256(csrfToken).getBytes(StandardCharsets.UTF_8)))
            throw new com.foodmate.shared.error.BusinessException(
                    com.foodmate.shared.error.ErrorCode.FORBIDDEN, "invalid CSRF token");
    }

    public synchronized ProfileRecord profile(long userId) {
        if (store == null)
            return profiles.getOrDefault(
                    userId,
                    new ProfileRecord(
                            userId, null, null, null, null, null, null, null, null, null, "[]",
                            "[]", "{}"));
        return store.profile(userId);
    }

    public synchronized ProfileRecord updateProfile(long userId, ProfileUpdate update) {
        if (store == null) {
            ProfileRecord current = profiles.getOrDefault(userId, profile(userId));
            ProfileRecord next = current.with(update);
            profiles.put(userId, next);
            return next;
        }
        store.ensureProfile(ids.nextId(), userId);
        store.updateProfile(userId, update);
        return profile(userId);
    }

    public synchronized SessionRecord createSession(long userId, String title, String mode) {
        String actualMode = mode == null || mode.isBlank() ? "agent" : mode;
        if (!List.of("agent", "chat").contains(actualMode))
            throw new IllegalArgumentException("mode must be agent or chat");
        String actualTitle = title == null || title.isBlank() ? "新会话" : title.trim();
        if (actualTitle.length() > 255)
            throw new IllegalArgumentException("title must be at most 255 characters");
        long id = ids.nextId();
        if (store != null) {
            // 当前 V1 为单租户运行模式；数据库仍要求显式写入 tenant_id，不能依赖不存在的列默认值。
            store.insertSession(id, userId, actualTitle, actualMode);
        }
        SessionRecord record =
                new SessionRecord(id, userId, actualTitle, actualMode, "active", null);
        if (store == null) sessions.put(id, record);
        return record;
    }

    public synchronized List<SessionRecord> listSessions(long userId) {
        return listSessions(userId, 1, 50, null, null).items();
    }

    public synchronized PageResult<SessionRecord> listSessions(
            long userId, int page, int size, String query, String status) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        String q = query == null ? "" : query.trim();
        String wantedStatus = status == null || status.isBlank() ? null : status.trim();
        if (store != null) {
            long total = store.countSessions(userId, q, wantedStatus);
            List<SessionRecord> items =
                    store.sessions(userId, q, wantedStatus, safeSize, (safePage - 1) * safeSize);
            return new PageResult<>(items, total, safePage, safeSize);
        }
        List<SessionRecord> all =
                sessions.values().stream()
                        .filter(
                                s ->
                                        s.userId() == userId
                                                && !"deleted".equals(s.status())
                                                && (wantedStatus == null
                                                        || wantedStatus.equals(s.status()))
                                                && (q.isBlank()
                                                        || s.title()
                                                                .toLowerCase()
                                                                .contains(q.toLowerCase())))
                        .sorted(
                                Comparator.comparing(
                                        SessionRecord::lastMessageAt,
                                        Comparator.nullsLast(Comparator.reverseOrder())))
                        .toList();
        int from = Math.min((safePage - 1) * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        return new PageResult<>(all.subList(from, to), all.size(), safePage, safeSize);
    }

    public synchronized PageResult<SessionRecord> listDeletedSessions(
            long userId, int page, int size) {
        int safePage = Math.max(1, page), safeSize = Math.min(100, Math.max(1, size));
        if (store != null) {
            long total = store.countDeletedSessions(userId);
            int offset = (safePage - 1) * safeSize;
            List<SessionRecord> items = store.deletedSessions(userId, safeSize, offset);
            return new PageResult<>(items, total, safePage, safeSize);
        }
        List<SessionRecord> all =
                sessions.values().stream()
                        .filter(s -> s.userId() == userId && "deleted".equals(s.status()))
                        .toList();
        return new PageResult<>(all, all.size(), safePage, safeSize);
    }

    public synchronized void renameSession(long userId, long sessionId, String title) {
        requireText(title, "title");
        String actual = title.trim();
        if (actual.length() > 255)
            throw new IllegalArgumentException("title must be at most 255 characters");
        requireSession(userId, sessionId);
        if (store != null) store.renameSession(userId, sessionId, actual);
        sessions.computeIfPresent(sessionId, (key, value) -> value.withTitle(actual));
    }

    public synchronized void setSessionStatus(long userId, long sessionId, String status) {
        if (!List.of("active", "archived").contains(status))
            throw new IllegalArgumentException("invalid session status");
        requireSession(userId, sessionId);
        if (store != null) store.setSessionStatus(userId, sessionId, status);
        sessions.computeIfPresent(sessionId, (key, value) -> value.withStatus(status));
    }

    public synchronized void deleteSession(long userId, long sessionId) {
        requireSession(userId, sessionId);
        if (store != null) store.deleteSession(userId, sessionId);
        sessions.computeIfPresent(sessionId, (key, value) -> value.withStatus("deleted"));
    }

    public synchronized void restoreSession(long userId, long sessionId) {
        if (store != null) {
            int changed = store.restoreSession(userId, sessionId);
            if (changed != 1) throw notFound("session not found or restore period expired");
        } else {
            SessionRecord session = sessions.get(sessionId);
            if (session == null
                    || session.userId() != userId
                    || !"deleted".equals(session.status()))
                throw notFound("session not found or restore period expired");
            sessions.put(sessionId, session.withStatus("active"));
        }
    }

    public synchronized List<MessageRecord> listMessages(long userId, long sessionId) {
        return listMessages(userId, sessionId, 1, 100).items();
    }

    public synchronized PageResult<MessageRecord> listMessages(
            long userId, long sessionId, int page, int size) {
        requireSession(userId, sessionId);
        int safePage = Math.max(1, page), safeSize = Math.min(100, Math.max(1, size));
        if (store != null) {
            long total = store.countMessages(sessionId);
            int offset = (safePage - 1) * safeSize;
            List<MessageRecord> items = store.messages(sessionId, safeSize, offset);
            return new PageResult<>(items, total, safePage, safeSize);
        }
        List<MessageRecord> all = messages.getOrDefault(sessionId, List.of());
        int from = Math.min((safePage - 1) * safeSize, all.size());
        return new PageResult<>(
                all.subList(from, Math.min(from + safeSize, all.size())),
                all.size(),
                safePage,
                safeSize);
    }

    /** 更正用户消息后由上层失效会话摘要，避免旧摘要继续代表已删除内容。 */
    public synchronized MessageRecord updateMessage(
            long userId, long sessionId, long messageId, String content) {
        requireSession(userId, sessionId);
        requireText(content, "content");
        if (content.length() > 10000)
            throw new IllegalArgumentException("content must be at most 10000 characters");
        if (store != null) {
            int changed = store.updateMessage(userId, sessionId, messageId, content);
            if (changed != 1) throw notFound("message not found");
            MessageRecord updated = store.message(messageId);
            if (updated == null) throw notFound("message not found");
            return updated;
        }
        List<MessageRecord> records = messages.getOrDefault(sessionId, List.of());
        for (int i = 0; i < records.size(); i++) {
            MessageRecord current = records.get(i);
            if (current.messageId() == messageId && current.role().equals("user")) {
                MessageRecord updated =
                        new MessageRecord(
                                current.messageId(),
                                current.sessionId(),
                                current.agentRunId(),
                                current.role(),
                                content,
                                current.structuredPayload(),
                                current.sequenceNo(),
                                current.createdAt());
                records.set(i, updated);
                return updated;
            }
        }
        throw notFound("message not found");
    }

    /** 逻辑删除用户消息；sequence_no 不复用，保证历史事件和摘要来源仍可审计。 */
    public synchronized void deleteMessage(long userId, long sessionId, long messageId) {
        requireSession(userId, sessionId);
        if (store != null) {
            int changed = store.deleteMessage(userId, sessionId, messageId);
            if (changed != 1) throw notFound("message not found");
            return;
        }
        List<MessageRecord> records = messages.getOrDefault(sessionId, List.of());
        boolean removed =
                records.removeIf(
                        item -> item.messageId() == messageId && item.role().equals("user"));
        if (!removed) throw notFound("message not found");
    }

    public synchronized List<SearchResult> searchSessions(
            long userId, String query, int page, int size) {
        String q = query == null ? "" : query.trim();
        if (q.isBlank()) return List.of();
        int safeSize = Math.min(100, Math.max(1, size)), offset = Math.max(0, page - 1) * safeSize;
        if (store != null) return store.search(userId, q, safeSize, offset);
        return sessions.values().stream()
                .filter(
                        s ->
                                s.userId() == userId
                                        && !"deleted".equals(s.status())
                                        && s.title().toLowerCase().contains(q.toLowerCase()))
                .map(s -> new SearchResult(s.sessionId(), s.title(), s.title()))
                .limit(safeSize)
                .toList();
    }

    public synchronized void archiveSession(long userId, long sessionId) {
        setSessionStatus(userId, sessionId, "archived");
    }

    public synchronized MessageRecord addMessage(
            long userId, long sessionId, String role, String content, Object structuredPayload) {
        return addMessage(userId, sessionId, role, content, structuredPayload, null);
    }

    public synchronized MessageRecord addMessage(
            long userId,
            long sessionId,
            String role,
            String content,
            Object structuredPayload,
            Long agentRunId) {
        requireSession(userId, sessionId);
        if (!"user".equals(role))
            throw new IllegalArgumentException("only user messages are accepted in M1-2");
        requireText(content, "content");
        if (content.length() > 10000)
            throw new IllegalArgumentException("content must be at most 10000 characters");
        int sequence = nextSequence(sessionId);
        long messageId = ids.nextId();
        String payload = json(structuredPayload == null ? Map.of() : structuredPayload);
        if (store != null) {
            store.insertMessage(
                    messageId, sessionId, agentRunId, role, content, payload, sequence, userId);
            store.touchSession(sessionId);
        }
        MessageRecord record =
                new MessageRecord(
                        messageId,
                        sessionId,
                        agentRunId,
                        role,
                        content,
                        payload,
                        sequence,
                        Instant.now());
        if (store == null)
            messages.computeIfAbsent(sessionId, ignored -> new ArrayList<>()).add(record);
        return record;
    }

    private int nextSequence(long sessionId) {
        if (store != null) return store.nextSequence(sessionId);
        return messages.getOrDefault(sessionId, List.of()).size() + 1;
    }

    private void requireSession(long userId, long sessionId) {
        if (store != null) {
            if (!store.sessionExists(userId, sessionId)) throw notFound("session not found");
        } else if (!sessions.containsKey(sessionId)
                || sessions.get(sessionId).userId() != userId
                || "deleted".equals(sessions.get(sessionId).status()))
            throw notFound("session not found");
    }

    private AuthResult issueSession(
            long userId, String username, String role, SessionMetadata metadata) {
        String sessionToken = randomToken();
        String csrfToken = randomToken();
        String sessionHash = sha256(sessionToken);
        Instant expiresAt = Instant.now().plusSeconds(AUTH_SESSION_SECONDS);
        AuthSessionRecord record =
                new AuthSessionRecord(userId, sessionHash, sha256(csrfToken), expiresAt, null);
        if (store != null)
            store.insertAuthSession(
                    ids.nextId(),
                    userId,
                    sessionHash,
                    record.csrfTokenHash(),
                    metadata.userAgent(),
                    metadata.ipAddress(),
                    expiresAt);
        if (store == null) authSessions.put(sessionHash, record);
        return new AuthResult(userId, username, role, sessionToken, csrfToken, expiresAt);
    }

    private Optional<UserRecord> findUser(String value) {
        if (store != null) return Optional.ofNullable(store.findUser(value));
        return users.values().stream()
                .filter(
                        user ->
                                user.username().equalsIgnoreCase(value)
                                        || user.email().equalsIgnoreCase(value))
                .findFirst();
    }

    private Optional<UserRecord> getUser(long id) {
        if (store != null) return Optional.ofNullable(store.getUser(id));
        return Optional.ofNullable(users.get(id));
    }

    private AuthSessionRecord findSession(String hash) {
        UserAccountStore.AuthSessionRow row = store.findAuthSession(hash);
        return row == null
                ? null
                : new AuthSessionRecord(
                        row.userId(), hash, row.csrfTokenHash(), row.expiresAt(), row.revokedAt());
    }

    private void revokeSession(String hash) {
        if (store != null) store.revokeByHash(hash);
        AuthSessionRecord current = authSessions.get(hash);
        if (current != null)
            authSessions.put(
                    hash,
                    new AuthSessionRecord(
                            current.userId(),
                            hash,
                            current.csrfTokenHash(),
                            current.expiresAt(),
                            Instant.now()));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("structured payload must be JSON");
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashPassword(String password) {
        try {
            byte[] salt = new byte[16];
            random.nextBytes(salt);
            byte[] hash = pbkdf2(password.toCharArray(), salt, PASSWORD_ITERATIONS);
            return "pbkdf2$"
                    + PASSWORD_ITERATIONS
                    + "$"
                    + Base64.getEncoder().encodeToString(salt)
                    + "$"
                    + Base64.getEncoder().encodeToString(hash);
        } catch (Exception exception) {
            throw new IllegalStateException("unable to hash password", exception);
        }
    }

    private boolean verifyPassword(String password, String encoded) {
        try {
            String[] parts = encoded.split("\\$");
            if (parts.length != 4) return false;
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            return MessageDigest.isEqual(
                    expected, pbkdf2(password.toCharArray(), salt, Integer.parseInt(parts[1])));
        } catch (Exception exception) {
            return false;
        }
    }

    private byte[] pbkdf2(char[] password, byte[] salt, int iterations) throws Exception {
        KeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .getEncoded();
    }

    private String sha256(String value) {
        try {
            return Base64.getEncoder()
                    .encodeToString(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(name + " must not be blank");
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < 8)
            throw new IllegalArgumentException("password must contain at least 8 characters");
    }

    private static com.foodmate.shared.error.BusinessException invalidCredentials() {
        return new com.foodmate.shared.error.BusinessException(
                com.foodmate.shared.error.ErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    private static com.foodmate.shared.error.BusinessException authRequired() {
        return new com.foodmate.shared.error.BusinessException(
                com.foodmate.shared.error.ErrorCode.AUTH_REQUIRED);
    }

    private static com.foodmate.shared.error.BusinessException conflict(String message) {
        return new com.foodmate.shared.error.BusinessException(
                com.foodmate.shared.error.ErrorCode.CONFLICT, message);
    }

    private static com.foodmate.shared.error.BusinessException notFound(String message) {
        return new com.foodmate.shared.error.BusinessException(
                com.foodmate.shared.error.ErrorCode.NOT_FOUND, message);
    }

    public record AuthResult(
            long userId,
            String username,
            String role,
            String sessionToken,
            String csrfToken,
            Instant expiresAt) {}

    public record SessionMetadata(String userAgent, String ipAddress) {
        public static final SessionMetadata EMPTY = new SessionMetadata(null, null);
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AuthSessionView(
            long authSessionId,
            String deviceId,
            String userAgent,
            String ipAddress,
            Instant expiresAt,
            Instant lastSeenAt,
            Instant createdAt,
            Instant revokedAt) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AdminUserView(
            long userId,
            String username,
            String email,
            String nickname,
            String role,
            String status) {}

    public record UserRecord(
            long userId,
            String username,
            String email,
            String passwordHash,
            String nickname,
            String role,
            String status) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ProfileRecord(
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
        ProfileRecord with(ProfileUpdate update) {
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

    public record ProfileUpdate(
            String displayName,
            String gender,
            java.math.BigDecimal heightCm,
            java.math.BigDecimal weightKg,
            String activityLevel,
            String dietGoal,
            Integer calorieTarget,
            Integer proteinTarget) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record SessionRecord(
            long sessionId,
            long userId,
            String title,
            String mode,
            String status,
            Instant lastMessageAt) {
        SessionRecord withStatus(String value) {
            return new SessionRecord(sessionId, userId, title, mode, value, lastMessageAt);
        }

        SessionRecord withTitle(String value) {
            return new SessionRecord(sessionId, userId, value, mode, status, lastMessageAt);
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record MessageRecord(
            long messageId,
            long sessionId,
            Long agentRunId,
            String role,
            String content,
            String structuredPayload,
            int sequenceNo,
            Instant createdAt) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record PageResult<T>(List<T> items, long total, int page, int size) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record SearchResult(long sessionId, String title, String snippet) {}

    private record AuthSessionRecord(
            long userId,
            String sessionTokenHash,
            String csrfTokenHash,
            Instant expiresAt,
            Instant revokedAt) {}
}

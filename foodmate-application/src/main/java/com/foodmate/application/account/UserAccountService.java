package com.foodmate.application.account;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** P1-1 account, session and message use cases. JDBC is used when a datasource exists; local-stub uses memory. */
@Service
public class UserAccountService {
    private static final int PASSWORD_ITERATIONS = 120_000;
    private static final long AUTH_SESSION_SECONDS = 2_592_000;

    private final JdbcTemplate jdbc;
    private final IdGenerator ids;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();
    private final Map<Long, UserRecord> users = new HashMap<>();
    private final Map<String, AuthSessionRecord> authSessions = new HashMap<>();
    private final Map<Long, ProfileRecord> profiles = new HashMap<>();
    private final Map<Long, SessionRecord> sessions = new HashMap<>();
    private final Map<Long, List<MessageRecord>> messages = new HashMap<>();

    public UserAccountService(
            ObjectProvider<JdbcTemplate> jdbcProvider,
            ObjectProvider<IdGenerator> idProvider) {
        this.jdbc = jdbcProvider.getIfAvailable();
        this.ids = Objects.requireNonNull(idProvider.getIfAvailable(), "IdGenerator is required");
    }

    public synchronized AuthResult register(String username, String email, String password, String nickname) {
        return register(username, email, password, nickname, SessionMetadata.EMPTY);
    }

    public synchronized AuthResult register(String username, String email, String password, String nickname, SessionMetadata metadata) {
        requireText(username, "username");
        requireText(email, "email");
        validatePassword(password);
        if (jdbc != null) {
            if (exists("SELECT 1 FROM users WHERE (username=? OR email=?) AND is_deleted=FALSE", username, email)) {
                throw conflict("username or email already exists");
            }
            long userId = ids.nextId();
            jdbc.update("INSERT INTO users(user_id,user_no,username,email,password_hash,nickname) VALUES (?,?,?,?,?,?)",
                    userId, "U" + userId, username, email, hashPassword(password), nickname);
            jdbc.update("INSERT INTO user_profiles(profile_id,user_id,display_name) VALUES (?,?,?)", ids.nextId(), userId, nickname);
            return issueSession(userId, username, "user", metadata);
        }
        if (users.values().stream().anyMatch(user -> user.username().equalsIgnoreCase(username) || user.email().equalsIgnoreCase(email))) {
            throw conflict("username or email already exists");
        }
        long userId = ids.nextId();
        users.put(userId, new UserRecord(userId, username, email, hashPassword(password), nickname, "user", "active"));
        profiles.put(userId, new ProfileRecord(userId, nickname, null, null, null, null, null, null, null, null, "[]", "[]", "{}"));
        return issueSession(userId, username, "user", metadata);
    }

    public synchronized AuthResult login(String usernameOrEmail, String password) {
        return login(usernameOrEmail, password, SessionMetadata.EMPTY);
    }

    public synchronized AuthResult login(String usernameOrEmail, String password, SessionMetadata metadata) {
        requireText(usernameOrEmail, "username");
        UserRecord user = findUser(usernameOrEmail).orElseThrow(() -> invalidCredentials());
        if ("disabled".equals(user.status())) throw new com.foodmate.shared.error.BusinessException(com.foodmate.shared.error.ErrorCode.AUTH_ACCOUNT_DISABLED);
        if ("locked".equals(user.status())) throw new com.foodmate.shared.error.BusinessException(com.foodmate.shared.error.ErrorCode.AUTH_ACCOUNT_LOCKED);
        if (!verifyPassword(password, user.passwordHash())) throw invalidCredentials();
        if (jdbc != null) jdbc.update("UPDATE users SET last_login_at=CURRENT_TIMESTAMP,login_failed_count=0 WHERE user_id=?", user.userId());
        return issueSession(user.userId(), user.username(), user.role(), metadata);
    }

    public synchronized void logout(String sessionToken) { if (sessionToken != null && !sessionToken.isBlank()) revokeSession(sha256(sessionToken)); }

    public synchronized void changePassword(long userId, String currentPassword, String newPassword) {
        validatePassword(newPassword);
        UserRecord user = getUser(userId).orElseThrow(UserAccountService::authRequired);
        if (!verifyPassword(currentPassword, user.passwordHash())) throw invalidCredentials();
        if (jdbc != null) {
            jdbc.update("UPDATE users SET password_hash=?,updated_at=CURRENT_TIMESTAMP WHERE user_id=?", hashPassword(newPassword), userId);
            jdbc.update("UPDATE user_auth_sessions SET revoked_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND revoked_at IS NULL", userId);
        } else {
            users.put(userId, new UserRecord(user.userId(), user.username(), user.email(), hashPassword(newPassword), user.nickname(), user.role(), user.status()));
            authSessions.replaceAll((key, value) -> value.userId() == userId ? new AuthSessionRecord(value.userId(), value.sessionTokenHash(), value.csrfTokenHash(), value.expiresAt(), Instant.now()) : value);
        }
    }

    public synchronized List<AuthSessionView> listAuthSessions(long userId) {
        if (jdbc != null) return jdbc.query("SELECT auth_session_id,device_id,user_agent,ip_address,expires_at,last_seen_at,created_at,revoked_at FROM user_auth_sessions WHERE user_id=? AND is_deleted=FALSE ORDER BY last_seen_at DESC", (rs, row) -> new AuthSessionView(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getTimestamp(5).toInstant(), rs.getTimestamp(6).toInstant(), rs.getTimestamp(7).toInstant(), rs.getTimestamp(8) == null ? null : rs.getTimestamp(8).toInstant()), userId);
        return authSessions.values().stream().filter(s -> s.userId() == userId).map(s -> new AuthSessionView(0, null, null, null, s.expiresAt(), null, null, s.revokedAt())).toList();
    }

    public synchronized void revokeAuthSession(long userId, long authSessionId) {
        if (jdbc != null) jdbc.update("UPDATE user_auth_sessions SET revoked_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE auth_session_id=? AND user_id=? AND revoked_at IS NULL", authSessionId, userId);
    }

    public synchronized void revokeAllAuthSessions(long userId) {
        if (jdbc != null) jdbc.update("UPDATE user_auth_sessions SET revoked_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND revoked_at IS NULL", userId);
        else authSessions.replaceAll((key, value) -> value.userId() == userId ? new AuthSessionRecord(value.userId(), value.sessionTokenHash(), value.csrfTokenHash(), value.expiresAt(), Instant.now()) : value);
    }

    public synchronized String createPasswordResetToken(String email) {
        UserRecord user = findUser(email).orElse(null);
        String raw = randomToken();
        if (user != null && jdbc != null) {
            jdbc.update("UPDATE password_reset_tokens SET used_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND used_at IS NULL", user.userId());
            jdbc.update("INSERT INTO password_reset_tokens(password_reset_token_id,user_id,token_hash,expires_at,created_by) VALUES (?,?,?,?,?)", ids.nextId(), user.userId(), sha256(raw), java.sql.Timestamp.from(Instant.now().plusSeconds(900)), user.userId());
        }
        return raw;
    }

    public synchronized void resetPassword(String token, String newPassword) {
        validatePassword(newPassword);
        if (jdbc == null) throw notFound("password reset is unavailable");
        String hash = sha256(token);
        Long userId = jdbc.query("SELECT user_id FROM password_reset_tokens WHERE token_hash=? AND used_at IS NULL AND is_deleted=FALSE AND expires_at>CURRENT_TIMESTAMP", rs -> rs.next() ? rs.getLong(1) : null, hash);
        if (userId == null) throw notFound("invalid or expired reset token");
        jdbc.update("UPDATE users SET password_hash=?,updated_at=CURRENT_TIMESTAMP WHERE user_id=?", hashPassword(newPassword), userId);
        jdbc.update("UPDATE password_reset_tokens SET used_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE token_hash=?", hash);
        revokeAllAuthSessions(userId);
    }

    public synchronized UserRecord requireSessionUser(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) throw authRequired();
        String hash = sha256(sessionToken);
        AuthSessionRecord session = jdbc == null ? authSessions.get(hash) : findSession(hash);
        if (session == null || session.revokedAt() != null || session.expiresAt().isBefore(Instant.now())) throw authRequired();
        if (jdbc != null) jdbc.update("UPDATE user_auth_sessions SET last_seen_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE session_token_hash=?", hash);
        UserRecord user = getUser(session.userId()).orElseThrow(UserAccountService::authRequired);
        if (!"active".equals(user.status())) throw new com.foodmate.shared.error.BusinessException("disabled".equals(user.status()) ? com.foodmate.shared.error.ErrorCode.AUTH_ACCOUNT_DISABLED : com.foodmate.shared.error.ErrorCode.AUTH_ACCOUNT_LOCKED);
        return user;
    }

    public synchronized void requireCsrf(String sessionToken, String csrfToken) {
        if (csrfToken == null || csrfToken.isBlank()) throw new com.foodmate.shared.error.BusinessException(com.foodmate.shared.error.ErrorCode.FORBIDDEN, "CSRF token is required");
        String sessionHash = sha256(sessionToken);
        AuthSessionRecord session = jdbc == null ? authSessions.get(sessionHash) : findSession(sessionHash);
        if (session == null || !MessageDigest.isEqual(session.csrfTokenHash().getBytes(StandardCharsets.UTF_8), sha256(csrfToken).getBytes(StandardCharsets.UTF_8))) throw new com.foodmate.shared.error.BusinessException(com.foodmate.shared.error.ErrorCode.FORBIDDEN, "invalid CSRF token");
    }

    public synchronized ProfileRecord profile(long userId) {
        if (jdbc == null) return profiles.getOrDefault(userId, new ProfileRecord(userId, null, null, null, null, null, null, null, null, null, "[]", "[]", "{}"));
        return jdbc.query("SELECT display_name,gender,birthday,height_cm,weight_kg,activity_level,diet_goal,calorie_target,protein_target,allergens::text,dislikes::text,preferred_units::text FROM user_profiles WHERE user_id=? AND is_deleted=FALSE",
                rs -> rs.next() ? new ProfileRecord(userId, rs.getString(1), rs.getString(2), rs.getObject(3, java.time.LocalDate.class), rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getString(6), rs.getString(7), rs.getObject(8, Integer.class), rs.getObject(9, Integer.class), rs.getString(10), rs.getString(11), rs.getString(12)) : null, userId);
    }

    public synchronized ProfileRecord updateProfile(long userId, ProfileUpdate update) {
        if (jdbc == null) {
            ProfileRecord current = profiles.getOrDefault(userId, profile(userId));
            ProfileRecord next = current.with(update);
            profiles.put(userId, next);
            return next;
        }
        jdbc.update("INSERT INTO user_profiles(profile_id,user_id) VALUES (?,?) ON CONFLICT (user_id) WHERE is_deleted=FALSE DO NOTHING", ids.nextId(), userId);
        jdbc.update("UPDATE user_profiles SET display_name=COALESCE(?,display_name),gender=COALESCE(?,gender),height_cm=COALESCE(?,height_cm),weight_kg=COALESCE(?,weight_kg),activity_level=COALESCE(?,activity_level),diet_goal=COALESCE(?,diet_goal),calorie_target=COALESCE(?,calorie_target),protein_target=COALESCE(?,protein_target),updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND is_deleted=FALSE",
                update.displayName(), update.gender(), update.heightCm(), update.weightKg(), update.activityLevel(), update.dietGoal(), update.calorieTarget(), update.proteinTarget(), userId);
        return profile(userId);
    }

    public synchronized SessionRecord createSession(long userId, String title, String mode) {
        String actualMode = mode == null || mode.isBlank() ? "agent" : mode;
        if (!List.of("agent", "chat").contains(actualMode)) throw new IllegalArgumentException("mode must be agent or chat");
        long id = ids.nextId();
        if (jdbc != null) jdbc.update("INSERT INTO sessions(session_id,user_id,title,mode) VALUES (?,?,?,?)", id, userId, title, actualMode);
        SessionRecord record = new SessionRecord(id, userId, title, actualMode, "active", null);
        if (jdbc == null) sessions.put(id, record);
        return record;
    }

    public synchronized List<SessionRecord> listSessions(long userId) {
        if (jdbc != null) return jdbc.query("SELECT session_id,title,mode,status,last_message_at FROM sessions WHERE user_id=? AND is_deleted=FALSE ORDER BY COALESCE(last_message_at,created_at) DESC", (rs, row) -> new SessionRecord(rs.getLong(1), userId, rs.getString(2), rs.getString(3), rs.getString(4), rs.getTimestamp(5) == null ? null : rs.getTimestamp(5).toInstant()), userId);
        return sessions.values().stream().filter(session -> session.userId() == userId && "active".equals(session.status())).sorted(Comparator.comparing(SessionRecord::lastMessageAt, Comparator.nullsLast(Comparator.reverseOrder()))).toList();
    }

    public synchronized void archiveSession(long userId, long sessionId) {
        requireSession(userId, sessionId);
        if (jdbc != null) jdbc.update("UPDATE sessions SET status='archived',updated_at=CURRENT_TIMESTAMP WHERE session_id=? AND user_id=?", sessionId, userId);
        sessions.computeIfPresent(sessionId, (key, value) -> value.withStatus("archived"));
    }

    public synchronized MessageRecord addMessage(long userId, long sessionId, String role, String content, Object structuredPayload) {
        return addMessage(userId, sessionId, role, content, structuredPayload, null);
    }

    public synchronized MessageRecord addMessage(long userId, long sessionId, String role, String content, Object structuredPayload, Long agentRunId) {
        requireSession(userId, sessionId);
        if (!List.of("user", "assistant", "system", "tool").contains(role)) throw new IllegalArgumentException("invalid message role");
        int sequence = nextSequence(sessionId);
        long messageId = ids.nextId();
        String payload = json(structuredPayload == null ? Map.of() : structuredPayload);
        if (jdbc != null) {
            jdbc.update("INSERT INTO messages(message_id,session_id,agent_run_id,role,content,structured_payload,sequence_no,created_by) VALUES (?,?, ?,?, ?,CAST(? AS jsonb),?,?)", messageId, sessionId, agentRunId, role, content, payload, sequence, userId);
            jdbc.update("UPDATE sessions SET last_message_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE session_id=?", sessionId);
        }
        MessageRecord record = new MessageRecord(messageId, sessionId, agentRunId, role, content, payload, sequence, Instant.now());
        if (jdbc == null) messages.computeIfAbsent(sessionId, ignored -> new ArrayList<>()).add(record);
        return record;
    }

    public synchronized List<MessageRecord> listMessages(long userId, long sessionId) {
        requireSession(userId, sessionId);
        if (jdbc != null) return jdbc.query("SELECT message_id,agent_run_id,role,content,structured_payload::text,sequence_no,created_at FROM messages WHERE session_id=? AND is_deleted=FALSE ORDER BY sequence_no", (rs, row) -> new MessageRecord(rs.getLong(1), sessionId, rs.getObject(2, Long.class), rs.getString(3), rs.getString(4), rs.getString(5), rs.getInt(6), rs.getTimestamp(7).toInstant()), sessionId);
        return List.copyOf(messages.getOrDefault(sessionId, List.of()));
    }

    private int nextSequence(long sessionId) {
        if (jdbc != null) return jdbc.queryForObject("SELECT COALESCE(MAX(sequence_no),0)+1 FROM messages WHERE session_id=? AND is_deleted=FALSE", Integer.class, sessionId);
        return messages.getOrDefault(sessionId, List.of()).size() + 1;
    }

    private void requireSession(long userId, long sessionId) {
        if (jdbc != null) {
            if (!exists("SELECT 1 FROM sessions WHERE session_id=? AND user_id=? AND is_deleted=FALSE", sessionId, userId)) throw notFound("session not found");
        } else if (!sessions.containsKey(sessionId) || sessions.get(sessionId).userId() != userId || !"active".equals(sessions.get(sessionId).status())) throw notFound("session not found");
    }

    private AuthResult issueSession(long userId, String username, String role, SessionMetadata metadata) {
        String sessionToken = randomToken();
        String csrfToken = randomToken();
        String sessionHash = sha256(sessionToken);
        Instant expiresAt = Instant.now().plusSeconds(AUTH_SESSION_SECONDS);
        AuthSessionRecord record = new AuthSessionRecord(userId, sessionHash, sha256(csrfToken), expiresAt, null);
        if (jdbc != null) jdbc.update("INSERT INTO user_auth_sessions(auth_session_id,user_id,session_token_hash,csrf_token_hash,user_agent,ip_address,expires_at,created_by) VALUES (?,?,?,?,?,?,?,?)", ids.nextId(), userId, sessionHash, record.csrfTokenHash(), metadata.userAgent(), metadata.ipAddress(), java.sql.Timestamp.from(expiresAt), userId);
        if (jdbc == null) authSessions.put(sessionHash, record);
        return new AuthResult(userId, username, role, sessionToken, csrfToken, expiresAt);
    }

    private Optional<UserRecord> findUser(String value) {
        if (jdbc != null) return jdbc.query("SELECT user_id,username,email,password_hash,nickname,role,status FROM users WHERE (username=? OR email=?) AND is_deleted=FALSE", rs -> rs.next() ? Optional.of(new UserRecord(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7))) : Optional.empty(), value, value);
        return users.values().stream().filter(user -> user.username().equalsIgnoreCase(value) || user.email().equalsIgnoreCase(value)).findFirst();
    }

    private Optional<UserRecord> getUser(long id) {
        if (jdbc != null) return jdbc.query("SELECT user_id,username,email,password_hash,nickname,role,status FROM users WHERE user_id=? AND is_deleted=FALSE", rs -> rs.next() ? Optional.of(new UserRecord(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7))) : Optional.empty(), id);
        return Optional.ofNullable(users.get(id));
    }

    private AuthSessionRecord findSession(String hash) { return jdbc.query("SELECT user_id,csrf_token_hash,expires_at,revoked_at FROM user_auth_sessions WHERE session_token_hash=? AND is_deleted=FALSE", rs -> rs.next() ? new AuthSessionRecord(rs.getLong(1), hash, rs.getString(2), rs.getTimestamp(3).toInstant(), rs.getTimestamp(4) == null ? null : rs.getTimestamp(4).toInstant()) : null, hash); }
    private void revokeSession(String hash) { if (jdbc != null) jdbc.update("UPDATE user_auth_sessions SET revoked_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE session_token_hash=? AND revoked_at IS NULL", hash); AuthSessionRecord current = authSessions.get(hash); if (current != null) authSessions.put(hash, new AuthSessionRecord(current.userId(), hash, current.csrfTokenHash(), current.expiresAt(), Instant.now())); }
    private boolean exists(String sql, Object... args) { return Boolean.TRUE.equals(jdbc.query(sql, (org.springframework.jdbc.core.ResultSetExtractor<Boolean>) rs -> rs.next(), args)); }
    private String json(Object value) { try { return objectMapper.writeValueAsString(value); } catch (JsonProcessingException exception) { throw new IllegalArgumentException("structured payload must be JSON"); } }
    private String randomToken() { byte[] bytes = new byte[32]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private String hashPassword(String password) { try { byte[] salt = new byte[16]; random.nextBytes(salt); byte[] hash = pbkdf2(password.toCharArray(), salt, PASSWORD_ITERATIONS); return "pbkdf2$" + PASSWORD_ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(hash); } catch (Exception exception) { throw new IllegalStateException("unable to hash password", exception); } }
    private boolean verifyPassword(String password, String encoded) { try { String[] parts = encoded.split("\\$"); if (parts.length != 4) return false; byte[] salt = Base64.getDecoder().decode(parts[2]); byte[] expected = Base64.getDecoder().decode(parts[3]); return MessageDigest.isEqual(expected, pbkdf2(password.toCharArray(), salt, Integer.parseInt(parts[1]))); } catch (Exception exception) { return false; } }
    private byte[] pbkdf2(char[] password, byte[] salt, int iterations) throws Exception { KeySpec spec = new PBEKeySpec(password, salt, iterations, 256); return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
    private String sha256(String value) { try { return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); } }
    private static void requireText(String value, String name) { if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank"); }
    private static void validatePassword(String password) { if (password == null || password.length() < 8) throw new IllegalArgumentException("password must contain at least 8 characters"); }
    private static com.foodmate.shared.error.BusinessException invalidCredentials() { return new com.foodmate.shared.error.BusinessException(com.foodmate.shared.error.ErrorCode.AUTH_INVALID_CREDENTIALS); }
    private static com.foodmate.shared.error.BusinessException authRequired() { return new com.foodmate.shared.error.BusinessException(com.foodmate.shared.error.ErrorCode.AUTH_REQUIRED); }
    private static com.foodmate.shared.error.BusinessException conflict(String message) { return new com.foodmate.shared.error.BusinessException(com.foodmate.shared.error.ErrorCode.CONFLICT, message); }
    private static com.foodmate.shared.error.BusinessException notFound(String message) { return new com.foodmate.shared.error.BusinessException(com.foodmate.shared.error.ErrorCode.NOT_FOUND, message); }

    public record AuthResult(long userId, String username, String role, String sessionToken, String csrfToken, Instant expiresAt) {}
    public record SessionMetadata(String userAgent, String ipAddress) { public static final SessionMetadata EMPTY = new SessionMetadata(null, null); }
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AuthSessionView(long authSessionId, String deviceId, String userAgent, String ipAddress, Instant expiresAt, Instant lastSeenAt, Instant createdAt, Instant revokedAt) {}
    public record UserRecord(long userId, String username, String email, String passwordHash, String nickname, String role, String status) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ProfileRecord(long userId, String displayName, String gender, java.time.LocalDate birthday, java.math.BigDecimal heightCm, java.math.BigDecimal weightKg, String activityLevel, String dietGoal, Integer calorieTarget, Integer proteinTarget, String allergens, String dislikes, String preferredUnits) { ProfileRecord with(ProfileUpdate update) { return new ProfileRecord(userId, update.displayName() == null ? displayName : update.displayName(), update.gender() == null ? gender : update.gender(), birthday, update.heightCm() == null ? heightCm : update.heightCm(), update.weightKg() == null ? weightKg : update.weightKg(), update.activityLevel() == null ? activityLevel : update.activityLevel(), update.dietGoal() == null ? dietGoal : update.dietGoal(), update.calorieTarget() == null ? calorieTarget : update.calorieTarget(), update.proteinTarget() == null ? proteinTarget : update.proteinTarget(), allergens, dislikes, preferredUnits); } }
    public record ProfileUpdate(String displayName, String gender, java.math.BigDecimal heightCm, java.math.BigDecimal weightKg, String activityLevel, String dietGoal, Integer calorieTarget, Integer proteinTarget) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record SessionRecord(long sessionId, long userId, String title, String mode, String status, Instant lastMessageAt) { SessionRecord withStatus(String value) { return new SessionRecord(sessionId, userId, title, mode, value, lastMessageAt); } }
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record MessageRecord(long messageId, long sessionId, Long agentRunId, String role, String content, String structuredPayload, int sequenceNo, Instant createdAt) {}
    private record AuthSessionRecord(long userId, String sessionTokenHash, String csrfTokenHash, Instant expiresAt, Instant revokedAt) {}
}

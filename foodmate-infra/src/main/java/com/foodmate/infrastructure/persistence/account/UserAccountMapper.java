package com.foodmate.infrastructure.persistence.account;

import com.foodmate.application.account.port.out.UserAccountRepository.*;
import com.foodmate.application.account.service.UserAccountService.*;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserAccountMapper {
    @Select(
            "SELECT EXISTS(SELECT 1 FROM users WHERE (username=#{username} OR email=#{email}) AND is_deleted=FALSE)")
    boolean userExists(String username, String email);

    @Insert(
            "INSERT INTO users(user_id,user_no,username,email,password_hash,nickname) VALUES (#{id},#{no},#{username},#{email},#{password},#{nickname})")
    void insertUser(
            long id, String no, String username, String email, String password, String nickname);

    @Insert(
            "INSERT INTO user_profiles(profile_id,user_id,display_name) VALUES (#{id},#{userId},#{name})")
    void insertProfile(long id, long userId, String name);

    @Update(
            "UPDATE users SET last_login_at=CURRENT_TIMESTAMP,login_failed_count=0 WHERE user_id=#{userId}")
    void markLogin(long userId);

    @Update(
            "UPDATE users SET password_hash=#{hash},updated_at=CURRENT_TIMESTAMP WHERE user_id=#{userId}")
    void changePassword(long userId, String hash);

    @Update(
            "UPDATE user_auth_sessions SET revoked_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE user_id=#{userId} AND revoked_at IS NULL")
    void revokeAll(long userId);

    @Select(
            "SELECT auth_session_id AS authSessionId,device_id AS deviceId,user_agent AS userAgent,ip_address AS ipAddress,expires_at AS expiresAt,last_seen_at AS lastSeenAt,created_at AS createdAt,revoked_at AS revokedAt FROM user_auth_sessions WHERE user_id=#{userId} AND is_deleted=FALSE ORDER BY last_seen_at DESC")
    List<AuthSessionView> authSessions(long userId);

    @Select(
            "SELECT user_id AS userId,username,CASE WHEN email IS NULL THEN NULL ELSE CONCAT('email-',MD5(email)) END AS email,nickname,role,status FROM users WHERE is_deleted=FALSE ORDER BY created_at DESC")
    List<AdminUserView> adminUsers();

    @Update(
            "UPDATE user_auth_sessions SET revoked_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE auth_session_id=#{sessionId} AND user_id=#{userId} AND revoked_at IS NULL")
    void revoke(long userId, long sessionId);

    @Update(
            "UPDATE password_reset_tokens SET used_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE user_id=#{userId} AND used_at IS NULL")
    void expireResetTokens(long userId);

    @Insert(
            "INSERT INTO password_reset_tokens(password_reset_token_id,user_id,token_hash,expires_at,created_by) VALUES (#{id},#{userId},#{hash},#{expires},#{userId})")
    void insertResetToken(long id, long userId, String hash, Instant expires);

    @Select(
            "SELECT user_id FROM password_reset_tokens WHERE token_hash=#{hash} AND used_at IS NULL AND is_deleted=FALSE AND expires_at>CURRENT_TIMESTAMP")
    Long resetTokenUser(String hash);

    @Update(
            "UPDATE password_reset_tokens SET used_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE token_hash=#{hash}")
    void consumeResetToken(String hash);

    @Update(
            "UPDATE user_auth_sessions SET last_seen_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE session_token_hash=#{hash}")
    void touchAuthSession(String hash);

    @Select(
            "SELECT user_id AS userId,display_name AS displayName,gender,birthday,height_cm AS heightCm,weight_kg AS weightKg,activity_level AS activityLevel,diet_goal AS dietGoal,calorie_target AS calorieTarget,protein_target AS proteinTarget,allergens::text AS allergens,dislikes::text AS dislikes,preferred_units::text AS preferredUnits FROM user_profiles WHERE user_id=#{userId} AND is_deleted=FALSE")
    ProfileRecord profile(long userId);

    @Insert(
            "INSERT INTO user_profiles(profile_id,user_id) VALUES (#{id},#{userId}) ON CONFLICT (user_id) WHERE is_deleted=FALSE DO NOTHING")
    void ensureProfile(long id, long userId);

    @Update(
            "UPDATE user_profiles SET display_name=COALESCE(#{update.displayName},display_name),gender=COALESCE(#{update.gender},gender),height_cm=COALESCE(#{update.heightCm},height_cm),weight_kg=COALESCE(#{update.weightKg},weight_kg),activity_level=COALESCE(#{update.activityLevel},activity_level),diet_goal=COALESCE(#{update.dietGoal},diet_goal),calorie_target=COALESCE(#{update.calorieTarget},calorie_target),protein_target=COALESCE(#{update.proteinTarget},protein_target),updated_at=CURRENT_TIMESTAMP WHERE user_id=#{userId} AND is_deleted=FALSE")
    void updateProfile(long userId, ProfileUpdate update);

    @Insert(
            "INSERT INTO sessions(session_id,tenant_id,user_id,title,mode) VALUES (#{id},0,#{userId},#{title},#{mode})")
    void insertSession(long id, long userId, String title, String mode);

    @Select(
            "<script>SELECT COUNT(*) FROM sessions s WHERE s.user_id=#{userId} AND s.is_deleted=FALSE<if test='status != null'> AND s.status=#{status}</if><if test='query != null and query != &quot;&quot;'> AND (s.title ILIKE CONCAT('%',#{query},'%') OR EXISTS(SELECT 1 FROM messages m WHERE m.session_id=s.session_id AND m.is_deleted=FALSE AND m.content ILIKE CONCAT('%',#{query},'%')))</if></script>")
    long countSessions(long userId, String query, String status);

    @Select(
            "<script>SELECT s.session_id AS sessionId,s.user_id AS userId,s.title,s.mode,s.status,s.last_message_at AS lastMessageAt FROM sessions s WHERE s.user_id=#{userId} AND s.is_deleted=FALSE<if test='status != null'> AND s.status=#{status}</if><if test='query != null and query != &quot;&quot;'> AND (s.title ILIKE CONCAT('%',#{query},'%') OR EXISTS(SELECT 1 FROM messages m WHERE m.session_id=s.session_id AND m.is_deleted=FALSE AND m.content ILIKE CONCAT('%',#{query},'%')))</if> ORDER BY COALESCE(s.last_message_at,s.created_at) DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<SessionRecord> sessions(long userId, String query, String status, int limit, int offset);

    @Select(
            "SELECT COUNT(*) FROM sessions WHERE user_id=#{userId} AND is_deleted=TRUE AND deleted_at>CURRENT_TIMESTAMP-INTERVAL '30 days'")
    long countDeletedSessions(long userId);

    @Select(
            "SELECT session_id AS sessionId,user_id AS userId,title,mode,'deleted' AS status,last_message_at AS lastMessageAt FROM sessions WHERE user_id=#{userId} AND is_deleted=TRUE AND deleted_at>CURRENT_TIMESTAMP-INTERVAL '30 days' ORDER BY deleted_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<SessionRecord> deletedSessions(long userId, int limit, int offset);

    @Select(
            "SELECT EXISTS(SELECT 1 FROM sessions WHERE session_id=#{sessionId} AND user_id=#{userId} AND is_deleted=FALSE)")
    boolean sessionExists(long userId, long sessionId);

    @Update(
            "UPDATE sessions SET title=#{title},updated_at=CURRENT_TIMESTAMP,updated_by=#{userId} WHERE session_id=#{sessionId} AND user_id=#{userId} AND is_deleted=FALSE")
    void renameSession(long userId, long sessionId, String title);

    @Update(
            "UPDATE sessions SET status=#{status},updated_at=CURRENT_TIMESTAMP,updated_by=#{userId} WHERE session_id=#{sessionId} AND user_id=#{userId} AND is_deleted=FALSE")
    void setSessionStatus(long userId, long sessionId, String status);

    @Update(
            "UPDATE sessions SET is_deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,deleted_by=#{userId},updated_at=CURRENT_TIMESTAMP WHERE session_id=#{sessionId} AND user_id=#{userId} AND is_deleted=FALSE")
    void deleteSession(long userId, long sessionId);

    @Update(
            "UPDATE sessions SET is_deleted=FALSE,deleted_at=NULL,deleted_by=NULL,status='active',updated_at=CURRENT_TIMESTAMP,updated_by=#{userId} WHERE session_id=#{sessionId} AND user_id=#{userId} AND is_deleted=TRUE AND deleted_at>CURRENT_TIMESTAMP-INTERVAL '30 days'")
    int restoreSession(long userId, long sessionId);

    @Select("SELECT COUNT(*) FROM messages WHERE session_id=#{sessionId} AND is_deleted=FALSE")
    long countMessages(long sessionId);

    @Select(
            "SELECT message_id AS messageId,session_id AS sessionId,agent_run_id AS agentRunId,role,content,structured_payload::text AS structuredPayload,sequence_no AS sequenceNo,created_at AS createdAt FROM messages WHERE session_id=#{sessionId} AND is_deleted=FALSE ORDER BY sequence_no LIMIT #{limit} OFFSET #{offset}")
    List<MessageRecord> messages(long sessionId, int limit, int offset);

    @Update(
            "UPDATE messages SET content=#{content},updated_at=CURRENT_TIMESTAMP,updated_by=#{userId} WHERE message_id=#{messageId} AND session_id=#{sessionId} AND created_by=#{userId} AND role='user' AND is_deleted=FALSE")
    int updateMessage(long userId, long sessionId, long messageId, String content);

    @Select(
            "SELECT message_id AS messageId,session_id AS sessionId,agent_run_id AS agentRunId,role,content,structured_payload::text AS structuredPayload,sequence_no AS sequenceNo,created_at AS createdAt FROM messages WHERE message_id=#{messageId}")
    MessageRecord message(long messageId);

    @Update(
            "UPDATE messages SET is_deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,deleted_by=#{userId},updated_at=CURRENT_TIMESTAMP,updated_by=#{userId} WHERE message_id=#{messageId} AND session_id=#{sessionId} AND created_by=#{userId} AND role='user' AND is_deleted=FALSE")
    int deleteMessage(long userId, long sessionId, long messageId);

    @Select(
            "SELECT s.session_id AS sessionId,s.title,LEFT(COALESCE(m.content,''),160) AS snippet FROM sessions s LEFT JOIN LATERAL (SELECT content FROM messages WHERE session_id=s.session_id AND is_deleted=FALSE AND content ILIKE CONCAT('%',#{query},'%') ORDER BY sequence_no LIMIT 1) m ON TRUE WHERE s.user_id=#{userId} AND s.is_deleted=FALSE AND (s.title ILIKE CONCAT('%',#{query},'%') OR m.content IS NOT NULL) ORDER BY COALESCE(s.last_message_at,s.created_at) DESC LIMIT #{limit} OFFSET #{offset}")
    List<SearchResult> search(long userId, String query, int limit, int offset);

    @Select(
            "SELECT COALESCE(MAX(sequence_no),0)+1 FROM messages WHERE session_id=#{sessionId} AND is_deleted=FALSE")
    int nextSequence(long sessionId);

    @Insert(
            "INSERT INTO messages(message_id,session_id,agent_run_id,role,content,structured_payload,sequence_no,created_by) VALUES (#{id},#{sessionId},#{runId},#{role},#{content},CAST(#{payload} AS jsonb),#{sequence},#{userId})")
    void insertMessage(
            long id,
            long sessionId,
            Long runId,
            String role,
            String content,
            String payload,
            int sequence,
            long userId);

    @Update(
            "UPDATE sessions SET last_message_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE session_id=#{sessionId}")
    void touchSession(long sessionId);

    @Insert(
            "INSERT INTO user_auth_sessions(auth_session_id,user_id,session_token_hash,csrf_token_hash,user_agent,ip_address,expires_at,created_by) VALUES (#{id},#{userId},#{sessionHash},#{csrfHash},#{userAgent},#{ip},#{expires},#{userId})")
    void insertAuthSession(
            long id,
            long userId,
            String sessionHash,
            String csrfHash,
            String userAgent,
            String ip,
            Instant expires);

    @Select(
            "SELECT user_id AS userId,username,email,password_hash AS passwordHash,nickname,role,status FROM users WHERE (username=#{value} OR email=#{value}) AND is_deleted=FALSE")
    UserRecord findUser(String value);

    @Select(
            "SELECT user_id AS userId,username,email,password_hash AS passwordHash,nickname,role,status FROM users WHERE user_id=#{id} AND is_deleted=FALSE")
    UserRecord getUser(long id);

    @Select(
            "SELECT user_id AS userId,csrf_token_hash AS csrfTokenHash,expires_at AS expiresAt,revoked_at AS revokedAt FROM user_auth_sessions WHERE session_token_hash=#{hash} AND is_deleted=FALSE")
    AuthSessionRow findAuthSession(String hash);

    @Update(
            "UPDATE user_auth_sessions SET revoked_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE session_token_hash=#{hash} AND revoked_at IS NULL")
    void revokeByHash(String hash);
}

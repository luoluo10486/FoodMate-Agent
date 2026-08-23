package com.foodmate.infrastructure.persistence.account;

import com.foodmate.application.account.port.out.PersonalDataRepository.AvatarRow;
import com.foodmate.application.account.port.out.PersonalDataRepository.ExportProfileRow;
import com.foodmate.application.account.port.out.PersonalDataRepository.ExportRow;
import com.foodmate.application.account.port.out.PersonalDataRepository.ExportSessionRow;
import com.foodmate.application.account.port.out.PersonalDataRepository.ExportUserData;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PersonalDataMapper {
    @Update(
            "UPDATE user_avatar_assets SET status='replaced',updated_at=CURRENT_TIMESTAMP WHERE user_id=#{userId} AND status='active'")
    void replaceAvatars(long userId);

    @Insert(
            "INSERT INTO user_avatar_assets(avatar_asset_id,user_id,storage_key,url,mime_type,size_bytes,width,height,original_filename,content_sha256,status,created_by) VALUES (#{id},#{userId},#{key},#{url},#{mime},#{size},#{width},#{height},#{originalFilename},#{contentSha256},'active',#{userId})")
    void insertAvatar(
            long id,
            long userId,
            String key,
            String url,
            String mime,
            long size,
            int width,
            int height,
            String originalFilename,
            String contentSha256);

    @Update("UPDATE users SET avatar_url=NULL,updated_at=CURRENT_TIMESTAMP WHERE user_id=#{userId}")
    void clearAvatar(long userId);

    @Update(
            "UPDATE users SET avatar_url=#{url},updated_at=CURRENT_TIMESTAMP,updated_by=#{userId} WHERE user_id=#{userId} AND is_deleted=FALSE")
    void setAvatarUrl(long userId, String url);

    @Select(
            "SELECT storage_key FROM user_avatar_assets WHERE user_id=#{userId} AND status='active' AND is_deleted=FALSE")
    List<String> activeAvatarKeys(long userId);

    @Select(
            "SELECT avatar_asset_id AS avatarAssetId,storage_key AS storageKey,mime_type AS mimeType FROM user_avatar_assets WHERE user_id=#{userId} AND status='active' AND is_deleted=FALSE ORDER BY created_at DESC LIMIT 1")
    AvatarRow activeAvatar(long userId);

    @Update(
            "UPDATE user_avatar_assets SET status='deleted',is_deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,deleted_by=#{userId},updated_at=CURRENT_TIMESTAMP WHERE user_id=#{userId} AND status='active'")
    void deleteAvatars(long userId);

    @Insert(
            "INSERT INTO data_export_jobs(export_job_id,user_id,status,created_by) VALUES (#{id},#{userId},'queued',#{userId})")
    void insertExportJob(long id, long userId);

    @Select(
            "SELECT COUNT(*) FROM account_deletion_jobs WHERE user_id=#{userId} AND is_deleted=FALSE AND status IN ('queued','running')")
    int activeDeletionJobs(long userId);

    @Insert(
            "INSERT INTO account_deletion_jobs(deletion_job_id,user_id,status,created_by) VALUES (#{id},#{userId},'queued',#{userId})")
    void insertDeletionJob(long id, long userId);

    @Update(
            "UPDATE users SET status='disabled',updated_at=CURRENT_TIMESTAMP WHERE user_id=#{userId}")
    void disableUser(long userId);

    @Update(
            "UPDATE user_auth_sessions SET revoked_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE user_id=#{userId} AND revoked_at IS NULL")
    void revokeSessions(long userId);

    @Update(
            "UPDATE auth_refresh_tokens SET revoked_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE user_id=#{userId} AND revoked_at IS NULL AND is_deleted=FALSE")
    void revokeRefreshTokens(long userId);

    @Select(
            "SELECT export_job_id AS id,status,expires_at AS expiresAt,completed_at AS completedAt,download_consumed_at AS consumedAt,failure_code AS failureCode FROM data_export_jobs WHERE export_job_id=#{jobId} AND user_id=#{userId} AND is_deleted=FALSE")
    ExportRow findExport(long userId, long jobId);

    @Update(
            "UPDATE data_export_jobs SET download_consumed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE export_job_id=#{jobId} AND user_id=#{userId} AND status='completed' AND download_consumed_at IS NULL AND expires_at>CURRENT_TIMESTAMP")
    int consumeExport(long userId, long jobId);

    @Select("SELECT object_key FROM data_export_jobs WHERE export_job_id=#{jobId}")
    String exportObjectKey(long jobId);

    @Select(
            "SELECT export_job_id FROM data_export_jobs WHERE status='queued' ORDER BY created_at LIMIT #{limit}")
    List<Long> queuedExports(int limit);

    @Select(
            "SELECT deletion_job_id FROM account_deletion_jobs WHERE status='queued' ORDER BY created_at LIMIT #{limit}")
    List<Long> queuedDeletions(int limit);

    @Select("SELECT user_id FROM data_export_jobs WHERE export_job_id=#{jobId}")
    Long exportUser(long jobId);

    @Update(
            "UPDATE data_export_jobs SET status='running',updated_at=CURRENT_TIMESTAMP WHERE export_job_id=#{jobId}")
    void startExport(long jobId);

    @Select(
            "SELECT user_id AS userId,user_no AS userNo,username,email,nickname,role,status,created_at AS createdAt FROM users WHERE user_id=#{userId}")
    ExportUserData exportUserData(long userId);

    @Select(
            "SELECT profile_id AS profileId,user_id AS userId,display_name AS displayName,gender,birthday::text AS birthday,height_cm::text AS heightCm,weight_kg::text AS weightKg,activity_level AS activityLevel,diet_goal AS dietGoal,calorie_target AS calorieTarget,protein_target AS proteinTarget,allergens::text AS allergens,dislikes::text AS dislikes,preferred_units::text AS preferredUnits,profile_json::text AS profileJson,created_at AS createdAt,updated_at AS updatedAt,created_by AS createdBy,updated_by AS updatedBy,is_deleted AS isDeleted,deleted_at AS deletedAt,deleted_by AS deletedBy FROM user_profiles WHERE user_id=#{userId} AND is_deleted=FALSE")
    List<ExportProfileRow> exportProfile(long userId);

    @Select(
            "SELECT session_id AS sessionId,title,mode,status,created_at AS createdAt FROM sessions WHERE user_id=#{userId} AND is_deleted=FALSE")
    List<ExportSessionRow> exportSessions(long userId);

    @Update(
            "UPDATE data_export_jobs SET status='completed',object_key=#{key},completed_at=CURRENT_TIMESTAMP,expires_at=CURRENT_TIMESTAMP+INTERVAL '24 hours',updated_at=CURRENT_TIMESTAMP WHERE export_job_id=#{jobId}")
    void completeExport(long jobId, String key);

    @Update(
            "UPDATE data_export_jobs SET status='failed',failure_code='EXPORT_FAILED',updated_at=CURRENT_TIMESTAMP WHERE export_job_id=#{jobId}")
    void failExport(long jobId);

    @Select("SELECT user_id FROM account_deletion_jobs WHERE deletion_job_id=#{jobId}")
    Long deletionUser(long jobId);

    @Update(
            "UPDATE account_deletion_jobs SET status='running',started_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE deletion_job_id=#{jobId}")
    void startDeletion(long jobId);

    @Select(
            "SELECT storage_key FROM user_avatar_assets WHERE user_id=#{userId} AND storage_key IS NOT NULL AND is_deleted=FALSE UNION ALL SELECT object_key FROM data_export_jobs WHERE user_id=#{userId} AND object_key IS NOT NULL AND is_deleted=FALSE")
    List<String> deletionObjectKeys(long userId);

    @Update(
            "UPDATE users SET status='disabled',is_deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE user_id=#{userId}")
    void softDeleteUser(long userId);

    @Update(
            "UPDATE user_profiles SET is_deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE user_id=#{userId}")
    void softDeleteProfile(long userId);

    @Update(
            "UPDATE sessions SET is_deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE user_id=#{userId}")
    void softDeleteSessions(long userId);

    @Update(
            "UPDATE messages SET is_deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE created_by=#{userId}")
    void softDeleteMessages(long userId);

    @Update(
            "UPDATE user_avatar_assets SET is_deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE user_id=#{userId}")
    void softDeleteAvatars(long userId);

    @Update(
            "UPDATE data_export_jobs SET is_deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE user_id=#{userId}")
    void softDeleteExports(long userId);

    @Update(
            "UPDATE account_deletion_jobs SET status='completed',deleted_object_count=#{count},completed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE deletion_job_id=#{jobId}")
    void completeDeletion(long jobId, long count);

    @Update(
            "UPDATE account_deletion_jobs SET status='failed',failure_code=#{code},retry_count=retry_count+1,updated_at=CURRENT_TIMESTAMP WHERE deletion_job_id=#{jobId}")
    void failDeletion(long jobId, String code);
}

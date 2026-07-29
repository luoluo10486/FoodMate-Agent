package com.foodmate.infrastructure.persistence.account;

import com.foodmate.application.account.PersonalDataStore;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PersonalDataMapper extends PersonalDataStore {
 @Update("UPDATE user_avatar_assets SET status='replaced',updated_at=CURRENT_TIMESTAMP WHERE user_id=#{userId} AND status='active'") void replaceAvatars(long userId);
 @Insert("INSERT INTO user_avatar_assets(avatar_asset_id,user_id,storage_key,url,mime_type,size_bytes,status,created_by) VALUES (#{id},#{userId},#{key},NULL,#{mime},#{size},'active',#{userId})") void insertAvatar(long id,long userId,String key,String mime,long size);
 @Update("UPDATE users SET avatar_url=NULL,updated_at=CURRENT_TIMESTAMP WHERE user_id=#{userId}") void clearAvatar(long userId);
 @Insert("INSERT INTO knowledge_documents(document_id,title,source_type,status,version,storage_key,created_by,updated_by) VALUES (#{id},#{title},'admin_upload','uploaded','1',#{key},#{userId},#{userId})") void insertKnowledge(long id,String title,String key,long userId);
 @Select("SELECT storage_key FROM user_avatar_assets WHERE user_id=#{userId} AND status='active' AND is_deleted=FALSE") List<String> activeAvatarKeys(long userId);
 @Update("UPDATE user_avatar_assets SET status='deleted',is_deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,deleted_by=#{userId},updated_at=CURRENT_TIMESTAMP WHERE user_id=#{userId} AND status='active'") void deleteAvatars(long userId);
 @Insert("INSERT INTO data_export_jobs(export_job_id,user_id,status,created_by) VALUES (#{id},#{userId},'queued',#{userId})") void insertExportJob(long id,long userId);
 @Select("SELECT COUNT(*) FROM account_deletion_jobs WHERE user_id=#{userId} AND is_deleted=FALSE AND status IN ('queued','running')") int activeDeletionJobs(long userId);
 @Insert("INSERT INTO account_deletion_jobs(deletion_job_id,user_id,status,created_by) VALUES (#{id},#{userId},'queued',#{userId})") void insertDeletionJob(long id,long userId);
 @Update("UPDATE users SET status='disabled',updated_at=CURRENT_TIMESTAMP WHERE user_id=#{userId}") void disableUser(long userId);
 @Update("UPDATE user_auth_sessions SET revoked_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE user_id=#{userId} AND revoked_at IS NULL") void revokeSessions(long userId);
 @Select("SELECT export_job_id AS id,status,expires_at AS expiresAt,completed_at AS completedAt,download_consumed_at AS consumedAt,failure_code AS failureCode FROM data_export_jobs WHERE export_job_id=#{jobId} AND user_id=#{userId} AND is_deleted=FALSE") ExportRow findExport(long userId,long jobId);
 @Update("UPDATE data_export_jobs SET download_consumed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE export_job_id=#{jobId} AND user_id=#{userId} AND status='completed' AND download_consumed_at IS NULL AND expires_at>CURRENT_TIMESTAMP") int consumeExport(long userId,long jobId);
 @Select("SELECT object_key FROM data_export_jobs WHERE export_job_id=#{jobId}") String exportObjectKey(long jobId);
 @Select("SELECT export_job_id FROM data_export_jobs WHERE status='queued' ORDER BY created_at LIMIT #{limit}") List<Long> queuedExports(int limit);
 @Select("SELECT deletion_job_id FROM account_deletion_jobs WHERE status='queued' ORDER BY created_at LIMIT #{limit}") List<Long> queuedDeletions(int limit);
 @Select("SELECT user_id FROM data_export_jobs WHERE export_job_id=#{jobId}") Long exportUser(long jobId);
 @Update("UPDATE data_export_jobs SET status='running',updated_at=CURRENT_TIMESTAMP WHERE export_job_id=#{jobId}") void startExport(long jobId);
 @Select("SELECT user_id,user_no,username,email,nickname,role,status,created_at FROM users WHERE user_id=#{userId}") Map<String,Object> exportUserData(long userId);
 @Select("SELECT * FROM user_profiles WHERE user_id=#{userId} AND is_deleted=FALSE") List<Map<String,Object>> exportProfile(long userId);
 @Select("SELECT session_id,title,mode,status,created_at FROM sessions WHERE user_id=#{userId} AND is_deleted=FALSE") List<Map<String,Object>> exportSessions(long userId);
 @Update("UPDATE data_export_jobs SET status='completed',object_key=#{key},completed_at=CURRENT_TIMESTAMP,expires_at=CURRENT_TIMESTAMP+INTERVAL '24 hours',updated_at=CURRENT_TIMESTAMP WHERE export_job_id=#{jobId}") void completeExport(long jobId,String key);
 @Update("UPDATE data_export_jobs SET status='failed',failure_code='EXPORT_FAILED',updated_at=CURRENT_TIMESTAMP WHERE export_job_id=#{jobId}") void failExport(long jobId);
 @Select("SELECT user_id FROM account_deletion_jobs WHERE deletion_job_id=#{jobId}") Long deletionUser(long jobId);
 @Update("UPDATE account_deletion_jobs SET status='running',started_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE deletion_job_id=#{jobId}") void startDeletion(long jobId);
 @Select("SELECT storage_key FROM user_avatar_assets WHERE user_id=#{userId} AND storage_key IS NOT NULL AND is_deleted=FALSE UNION ALL SELECT object_key FROM data_export_jobs WHERE user_id=#{userId} AND object_key IS NOT NULL AND is_deleted=FALSE") List<String> deletionObjectKeys(long userId);
 @Update("UPDATE users SET status='disabled',is_deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE user_id=#{userId}") void softDeleteUser(long userId);
 @Update("UPDATE user_profiles SET is_deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE user_id=#{userId}") void softDeleteProfile(long userId);
 @Update("UPDATE sessions SET is_deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE user_id=#{userId}") void softDeleteSessions(long userId);
 @Update("UPDATE messages SET is_deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE created_by=#{userId}") void softDeleteMessages(long userId);
 @Update("UPDATE user_avatar_assets SET is_deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE user_id=#{userId}") void softDeleteAvatars(long userId);
 @Update("UPDATE data_export_jobs SET is_deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE user_id=#{userId}") void softDeleteExports(long userId);
 @Update("UPDATE account_deletion_jobs SET status='completed',deleted_object_count=#{count},completed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE deletion_job_id=#{jobId}") void completeDeletion(long jobId,long count);
 @Update("UPDATE account_deletion_jobs SET status='failed',failure_code=#{code},retry_count=retry_count+1,updated_at=CURRENT_TIMESTAMP WHERE deletion_job_id=#{jobId}") void failDeletion(long jobId,String code);
}

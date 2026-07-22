package com.foodmate.application.account;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.http.Method;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.foodmate.shared.error.BusinessException;
import com.foodmate.shared.error.ErrorCode;

@Service
public class PersonalDataService {
    private final JdbcTemplate jdbc;
    private final MinioClient minio;
    private final String bucket;
    private final com.foodmate.shared.id.IdGenerator ids;
    private final ObjectMapper mapper = new ObjectMapper();

    public PersonalDataService(ObjectProvider<JdbcTemplate> jdbc, ObjectProvider<MinioClient> minio,
                               ObjectProvider<com.foodmate.shared.id.IdGenerator> ids,
                               @org.springframework.beans.factory.annotation.Value("${foodmate.storage.bucket:foodmate-private}") String bucket) {
        this.jdbc = jdbc.getIfAvailable(); this.minio = minio.getIfAvailable(); this.ids = ids.getIfAvailable(); this.bucket = bucket;
    }

    public Avatar uploadAvatar(long userId, String filename, String contentType, long size, InputStream input) {
        if (jdbc == null || minio == null) throw new IllegalStateException("avatar storage unavailable");
        String key = "avatars/" + userId + "/" + ids.nextId() + "-" + filename.replaceAll("[^A-Za-z0-9._-]", "_");
        try {
            minio.putObject(PutObjectArgs.builder().bucket(bucket).object(key).stream(input, size, -1).contentType(contentType).build());
            jdbc.update("UPDATE user_avatar_assets SET status='replaced',updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND status='active'", userId);
            long id = ids.nextId();
            jdbc.update("INSERT INTO user_avatar_assets(avatar_asset_id,user_id,storage_key,url,mime_type,size_bytes,status,created_by) VALUES (?,?,?,NULL,?,?, 'active',?)", id, userId, key, contentType, size, userId);
            jdbc.update("UPDATE users SET avatar_url=NULL,updated_at=CURRENT_TIMESTAMP WHERE user_id=?", userId);
            return new Avatar(id, key, contentType, size);
        } catch (Exception e) { throw new IllegalStateException("avatar upload failed", e); }
    }

    public void deleteAvatar(long userId) {
        if (jdbc == null) return;
        List<String> keys = jdbc.query("SELECT storage_key FROM user_avatar_assets WHERE user_id=? AND status='active' AND is_deleted=FALSE", (rs, row) -> rs.getString(1), userId);
        if (minio != null) for (String key : keys) try { minio.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build()); } catch (Exception ignored) { }
        jdbc.update("UPDATE user_avatar_assets SET status='deleted',is_deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,deleted_by=?,updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND status='active'", userId, userId);
        jdbc.update("UPDATE users SET avatar_url=NULL,updated_at=CURRENT_TIMESTAMP WHERE user_id=?", userId);
    }

    public long requestExport(long userId) {
        long id = ids.nextId();
        jdbc.update("INSERT INTO data_export_jobs(export_job_id,user_id,status,created_by) VALUES (?,?, 'queued', ?)", id, userId, userId);
        return id;
    }

    public long requestDeletion(long userId) {
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM account_deletion_jobs WHERE user_id=? AND is_deleted=FALSE AND status IN ('queued','running')", Integer.class, userId);
        if (existing != null && existing > 0) throw new BusinessException(ErrorCode.CONFLICT, "account deletion already requested");
        long id = ids.nextId();
        jdbc.update("INSERT INTO account_deletion_jobs(deletion_job_id,user_id,status,created_by) VALUES (?,?, 'queued', ?)", id, userId, userId);
        jdbc.update("UPDATE users SET status='disabled',updated_at=CURRENT_TIMESTAMP WHERE user_id=?", userId);
        jdbc.update("UPDATE user_auth_sessions SET revoked_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND revoked_at IS NULL", userId);
        return id;
    }

    public ExportJob exportJob(long userId, long jobId) {
        if (jdbc == null) throw new IllegalStateException("export unavailable");
        ExportJob job = jdbc.query("SELECT export_job_id,status,expires_at,completed_at,download_consumed_at,failure_code FROM data_export_jobs WHERE export_job_id=? AND user_id=? AND is_deleted=FALSE", rs -> rs.next() ? new ExportJob(rs.getLong(1), rs.getString(2), rs.getTimestamp(3) == null ? null : rs.getTimestamp(3).toInstant(), rs.getTimestamp(4) == null ? null : rs.getTimestamp(4).toInstant(), rs.getTimestamp(5) == null ? null : rs.getTimestamp(5).toInstant(), rs.getString(6)) : null, jobId, userId);
        if (job == null) throw new BusinessException(ErrorCode.NOT_FOUND, "export job not found");
        return job;
    }

    public String consumeExport(long userId, long jobId) {
        if (jdbc == null || minio == null) throw new IllegalStateException("export unavailable");
        int updated = jdbc.update("UPDATE data_export_jobs SET download_consumed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE export_job_id=? AND user_id=? AND status='completed' AND download_consumed_at IS NULL AND expires_at>CURRENT_TIMESTAMP", jobId, userId);
        if (updated != 1) throw new BusinessException(ErrorCode.CONFLICT, "export is unavailable, expired, or already consumed");
        String key = jdbc.queryForObject("SELECT object_key FROM data_export_jobs WHERE export_job_id=?", String.class, jobId);
        try { return minio.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.GET).bucket(bucket).object(key).expiry(600).build()); }
        catch (Exception e) { throw new IllegalStateException("download link unavailable", e); }
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "${foodmate.account.jobs-delay-ms:30000}")
    public synchronized void processJobs() {
        if (jdbc == null || minio == null) return;
        List<Long> exports = jdbc.query("SELECT export_job_id FROM data_export_jobs WHERE status='queued' ORDER BY created_at LIMIT 2", (rs, row) -> rs.getLong(1));
        for (Long id : exports) processExport(id);
        List<Long> deletions = jdbc.query("SELECT deletion_job_id FROM account_deletion_jobs WHERE status='queued' ORDER BY created_at LIMIT 2", (rs, row) -> rs.getLong(1));
        for (Long id : deletions) processDeletion(id);
    }

    private void processExport(long jobId) {
        Long userId = jdbc.queryForObject("SELECT user_id FROM data_export_jobs WHERE export_job_id=?", Long.class, jobId);
        jdbc.update("UPDATE data_export_jobs SET status='running',updated_at=CURRENT_TIMESTAMP WHERE export_job_id=?", jobId);
        try {
            String json = mapper.writeValueAsString(Map.of("user", jdbc.queryForMap("SELECT user_id,user_no,username,email,nickname,role,status,created_at FROM users WHERE user_id=?", userId), "profile", jdbc.queryForList("SELECT * FROM user_profiles WHERE user_id=? AND is_deleted=FALSE", userId), "sessions", jdbc.queryForList("SELECT session_id,title,mode,status,created_at FROM sessions WHERE user_id=? AND is_deleted=FALSE", userId)));
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) { zip.putNextEntry(new ZipEntry("account.json")); zip.write(json.getBytes(StandardCharsets.UTF_8)); zip.closeEntry(); }
            String key = "exports/" + userId + "/" + jobId + ".zip";
            minio.putObject(PutObjectArgs.builder().bucket(bucket).object(key).stream(new ByteArrayInputStream(bytes.toByteArray()), bytes.size(), -1).contentType("application/zip").build());
            jdbc.update("UPDATE data_export_jobs SET status='completed',object_key=?,completed_at=CURRENT_TIMESTAMP,expires_at=CURRENT_TIMESTAMP + INTERVAL '24 hours',updated_at=CURRENT_TIMESTAMP WHERE export_job_id=?", key, jobId);
        } catch (Exception e) { jdbc.update("UPDATE data_export_jobs SET status='failed',failure_code='EXPORT_FAILED',updated_at=CURRENT_TIMESTAMP WHERE export_job_id=?", jobId); }
    }

    private void processDeletion(long jobId) {
        Long userId = jdbc.queryForObject("SELECT user_id FROM account_deletion_jobs WHERE deletion_job_id=?", Long.class, jobId);
        jdbc.update("UPDATE account_deletion_jobs SET status='running',started_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE deletion_job_id=?", jobId);
        try {
            List<String> objectKeys = jdbc.query("SELECT storage_key FROM user_avatar_assets WHERE user_id=? AND storage_key IS NOT NULL AND is_deleted=FALSE", (rs, row) -> rs.getString(1), userId);
            objectKeys.addAll(jdbc.query("SELECT object_key FROM data_export_jobs WHERE user_id=? AND object_key IS NOT NULL AND is_deleted=FALSE", (rs, row) -> rs.getString(1), userId));
            long deletedObjects = 0;
            for (String key : objectKeys) {
                try { minio.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build()); deletedObjects++; }
                catch (Exception ignored) { }
            }
            jdbc.update("UPDATE users SET status='disabled',is_deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE user_id=?", userId);
            jdbc.update("UPDATE user_profiles SET is_deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE user_id=?", userId);
            jdbc.update("UPDATE sessions SET is_deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE user_id=?", userId);
            jdbc.update("UPDATE messages SET is_deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE created_by=?", userId);
            jdbc.update("UPDATE user_avatar_assets SET is_deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE user_id=?", userId);
            jdbc.update("UPDATE data_export_jobs SET is_deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE user_id=?", userId);
            jdbc.update("UPDATE account_deletion_jobs SET status='completed',deleted_object_count=?,completed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE deletion_job_id=?", deletedObjects, jobId);
        } catch (Exception e) { jdbc.update("UPDATE account_deletion_jobs SET status='failed',failure_code='DELETION_FAILED',retry_count=retry_count+1,updated_at=CURRENT_TIMESTAMP WHERE deletion_job_id=?", jobId); }
    }

    public record Avatar(long avatarAssetId, String storageKey, String mimeType, long sizeBytes) {}
    public record ExportJob(long exportJobId, String status, Instant expiresAt, Instant completedAt, Instant downloadConsumedAt, String failureCode) {}
}

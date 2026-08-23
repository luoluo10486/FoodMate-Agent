package com.foodmate.application.account.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.account.port.out.PersonalDataRepository;
import com.foodmate.application.account.service.PersonalDataService;
import com.foodmate.application.common.port.out.ObjectStoragePort;
import com.foodmate.application.common.service.OperationAuditService;
import com.foodmate.shared.error.BusinessException;
import com.foodmate.shared.error.ErrorCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonalDataServiceImpl implements PersonalDataService {
    private static final Logger log = LoggerFactory.getLogger(PersonalDataServiceImpl.class);

    private final PersonalDataRepository store;
    private final ObjectStoragePort storage;
    private final String bucket;
    private final com.foodmate.shared.id.IdGenerator ids;
    private final ObjectMapper mapper;
    private final OperationAuditService audit;

    public PersonalDataServiceImpl(
            ObjectProvider<PersonalDataRepository> store,
            ObjectProvider<ObjectStoragePort> storage,
            ObjectProvider<com.foodmate.shared.id.IdGenerator> ids,
            ObjectProvider<OperationAuditService> audit,
            ObjectMapper mapper,
            @org.springframework.beans.factory.annotation.Value(
                            "${foodmate.storage.bucket:foodmate-private}")
                    String bucket) {
        this.store = store.getIfAvailable();
        this.storage = storage.getIfAvailable();
        this.ids = ids.getIfAvailable();
        this.audit = audit.getIfAvailable();
        this.mapper = mapper;
        this.bucket = bucket;
    }

    @Transactional
    public Avatar uploadAvatar(
            long userId, String filename, String contentType, long size, InputStream input) {
        if (store == null || storage == null)
            throw new IllegalStateException("avatar storage unavailable");
        String key =
                "avatars/"
                        + userId
                        + "/"
                        + ids.nextId()
                        + "-"
                        + filename.replaceAll("[^A-Za-z0-9._-]", "_");
        try {
            storage.put(bucket, key, input, size, contentType);
            store.replaceAvatars(userId);
            long id = ids.nextId();
            store.insertAvatar(id, userId, key, contentType, size);
            store.clearAvatar(userId);
            record(
                    userId,
                    "avatar",
                    Long.toString(id),
                    "account.avatar.upload",
                    "success",
                    null,
                    Map.of(
                            "size_bytes",
                            size,
                            "mime_type",
                            contentType == null ? "" : contentType));
            return new Avatar(id, key, contentType, size);
        } catch (RuntimeException e) {
            failure(userId, "avatar", null, "account.avatar.upload", e);
            throw new IllegalStateException("avatar upload failed", e);
        }
    }

    @Transactional
    public void deleteAvatar(long userId) {
        if (store == null) throw new IllegalStateException("avatar storage unavailable");
        List<String> keys = store.activeAvatarKeys(userId);
        if (storage != null)
            for (String key : keys)
                try {
                    storage.delete(bucket, key);
                } catch (RuntimeException ignored) {
                }
        store.deleteAvatars(userId);
        store.clearAvatar(userId);
        record(
                userId,
                "avatar",
                Long.toString(userId),
                "account.avatar.delete",
                "success",
                null,
                Map.of());
    }

    @Transactional
    public long requestExport(long userId) {
        if (store == null) throw new IllegalStateException("export unavailable");
        long id = ids.nextId();
        store.insertExportJob(id, userId);
        record(
                userId,
                "export_job",
                Long.toString(id),
                "account.data_export.request",
                "success",
                null,
                Map.of());
        return id;
    }

    @Transactional
    public long requestDeletion(long userId) {
        if (store == null) throw new IllegalStateException("account deletion unavailable");
        if (store.activeDeletionJobs(userId) > 0)
            throw new BusinessException(ErrorCode.CONFLICT, "account deletion already requested");
        long id = ids.nextId();
        store.insertDeletionJob(id, userId);
        store.disableUser(userId);
        store.revokeSessions(userId);
        record(
                userId,
                "user",
                Long.toString(userId),
                "account.deletion.request",
                "success",
                null,
                Map.of());
        return id;
    }

    public ExportJob exportJob(long userId, long jobId) {
        if (store == null) throw new IllegalStateException("export unavailable");
        PersonalDataRepository.ExportRow row = store.findExport(userId, jobId);
        ExportJob job =
                row == null
                        ? null
                        : new ExportJob(
                                row.id(),
                                row.status(),
                                row.expiresAt(),
                                row.completedAt(),
                                row.consumedAt(),
                                row.failureCode());
        if (job == null) throw new BusinessException(ErrorCode.NOT_FOUND, "export job not found");
        return job;
    }

    @Transactional
    public String consumeExport(long userId, long jobId) {
        if (store == null || storage == null) throw new IllegalStateException("export unavailable");
        int updated = store.consumeExport(userId, jobId);
        if (updated != 1)
            throw new BusinessException(
                    ErrorCode.CONFLICT, "export is unavailable, expired, or already consumed");
        String key = store.exportObjectKey(jobId);
        try {
            String url = storage.presignedGet(bucket, key, Duration.ofMinutes(10));
            record(
                    userId,
                    "export_job",
                    Long.toString(jobId),
                    "account.data_export.consume",
                    "success",
                    null,
                    Map.of());
            return url;
        } catch (RuntimeException e) {
            failure(userId, "export_job", Long.toString(jobId), "account.data_export.consume", e);
            throw new IllegalStateException("download link unavailable", e);
        }
    }

    private void record(
            long userId,
            String targetType,
            String targetId,
            String action,
            String result,
            String errorCode,
            Map<String, ?> metadata) {
        if (audit != null)
            audit.record(
                    userId, targetType, targetId, action, result, errorCode, null, null, metadata);
    }

    private void failure(
            long userId, String targetType, String targetId, String action, Exception exception) {
        if (audit != null)
            audit.recordFailure(
                    userId,
                    targetType,
                    targetId,
                    action,
                    "failed",
                    "PERSONAL_DATA_OPERATION_FAILED",
                    null,
                    null,
                    Map.of("exception_type", exception.getClass().getSimpleName()));
    }

    @org.springframework.scheduling.annotation.Scheduled(
            fixedDelayString = "${foodmate.account.jobs-delay-ms:30000}")
    public synchronized void processJobs() {
        if (store == null || storage == null) return;
        List<Long> exports = store.queuedExports(2);
        for (Long id : exports) processExport(id);
        List<Long> deletions = store.queuedDeletions(2);
        for (Long id : deletions) processDeletion(id);
    }

    private void processExport(long jobId) {
        Long userId = store.exportUser(jobId);
        store.startExport(jobId);
        try {
            String json =
                    mapper.writeValueAsString(
                            new PersonalDataRepository.ExportDocument(
                                    store.exportUserData(userId),
                                    store.exportProfile(userId),
                                    store.exportSessions(userId)));
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
                zip.putNextEntry(new ZipEntry("account.json"));
                zip.write(json.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            String key = "exports/" + userId + "/" + jobId + ".zip";
            storage.put(
                    bucket,
                    key,
                    new ByteArrayInputStream(bytes.toByteArray()),
                    bytes.size(),
                    "application/zip");
            store.completeExport(jobId, key);
        } catch (IOException | RuntimeException e) {
            log.error("account export failed: jobId={}, userId={}", jobId, userId, e);
            store.failExport(jobId);
        }
    }

    @Transactional
    protected void processDeletion(long jobId) {
        Long userId = store.deletionUser(jobId);
        store.startDeletion(jobId);
        try {
            List<String> objectKeys = store.deletionObjectKeys(userId);
            long deletedObjects = 0;
            String objectDeleteFailure = null;
            for (String key : objectKeys) {
                try {
                    storage.delete(bucket, key);
                    deletedObjects++;
                } catch (RuntimeException exception) {
                    objectDeleteFailure = exception.getMessage();
                }
            }
            if (objectDeleteFailure != null)
                throw new IllegalStateException("object cleanup failed: " + objectDeleteFailure);
            store.softDeleteUser(userId);
            store.softDeleteProfile(userId);
            store.softDeleteSessions(userId);
            store.softDeleteMessages(userId);
            store.softDeleteAvatars(userId);
            store.softDeleteExports(userId);
            store.completeDeletion(jobId, deletedObjects);
        } catch (RuntimeException e) {
            String detail = e.getMessage() == null ? "unknown" : e.getMessage();
            String code =
                    ("DELETION_FAILED:" + detail)
                            .substring(0, Math.min(64, ("DELETION_FAILED:" + detail).length()));
            store.failDeletion(jobId, code);
        }
    }
}

package com.foodmate.application.account.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.account.port.out.PersonalDataRepository;
import com.foodmate.application.account.service.PersonalDataService;
import com.foodmate.application.common.port.out.ObjectStoragePort;
import com.foodmate.application.common.service.OperationAuditService;
import com.foodmate.shared.error.BusinessException;
import com.foodmate.shared.error.ErrorCode;
import com.foodmate.shared.id.IdGenerator;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonalDataServiceImpl implements PersonalDataService {
    private static final Logger log = LoggerFactory.getLogger(PersonalDataServiceImpl.class);
    private static final String AVATAR_RESOURCE_PATH = "/api/users/me/avatar";
    private static final int MAX_AVATAR_BYTES = 2 * 1024 * 1024;
    private static final long MAX_AVATAR_PIXELS = 25_000_000L;

    private final PersonalDataRepository store;
    private final ObjectStoragePort storage;
    private final String bucket;
    private final IdGenerator ids;
    private final ObjectMapper mapper;
    private final OperationAuditService audit;

    public PersonalDataServiceImpl(
            ObjectProvider<PersonalDataRepository> store,
            ObjectProvider<ObjectStoragePort> storage,
            ObjectProvider<IdGenerator> ids,
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
        String key = null;
        boolean uploaded = false;
        try {
            if (store == null || storage == null || ids == null)
                throw new IllegalStateException("avatar storage unavailable");
            AvatarInput avatar = readAndValidateAvatar(filename, contentType, size, input);
            long id = ids.nextId();
            key = "avatars/" + userId + "/" + id + avatar.extension();
            uploaded = true;
            storage.put(
                    bucket,
                    key,
                    new ByteArrayInputStream(avatar.bytes()),
                    avatar.bytes().length,
                    avatar.contentType());
            store.replaceAvatars(userId);
            store.insertAvatar(
                    id,
                    userId,
                    key,
                    AVATAR_RESOURCE_PATH,
                    avatar.contentType(),
                    avatar.bytes().length,
                    avatar.dimensions().width(),
                    avatar.dimensions().height(),
                    avatar.originalFilename(),
                    avatar.sha256());
            store.setAvatarUrl(userId, AVATAR_RESOURCE_PATH);
            record(
                    userId,
                    "avatar",
                    Long.toString(id),
                    "account.avatar.upload",
                    "success",
                    null,
                    Map.of(
                            "size_bytes",
                            avatar.bytes().length,
                            "mime_type",
                            avatar.contentType(),
                            "content_sha256",
                            avatar.sha256()));
            return new Avatar(
                    id, AVATAR_RESOURCE_PATH, avatar.contentType(), avatar.bytes().length);
        } catch (BusinessException e) {
            failure(userId, "avatar", key, "account.avatar.upload", e.errorCode().code(), e);
            throw e;
        } catch (RuntimeException e) {
            if (uploaded && key != null) compensateObject(key, e);
            failure(
                    userId,
                    "avatar",
                    null,
                    "account.avatar.upload",
                    ErrorCode.USER_AVATAR_UPLOAD_FAILED.code(),
                    e);
            BusinessException failure =
                    new BusinessException(ErrorCode.USER_AVATAR_UPLOAD_FAILED, "头像上传失败");
            failure.initCause(e);
            throw failure;
        }
    }

    @Transactional
    public void deleteAvatar(long userId) {
        try {
            if (store == null || storage == null)
                throw new IllegalStateException("avatar storage unavailable");
            List<String> keys = store.activeAvatarKeys(userId);
            for (String key : keys) storage.delete(bucket, key);
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
        } catch (RuntimeException e) {
            failure(
                    userId,
                    "avatar",
                    Long.toString(userId),
                    "account.avatar.delete",
                    ErrorCode.USER_AVATAR_DELETE_FAILED.code(),
                    e);
            BusinessException failure =
                    new BusinessException(ErrorCode.USER_AVATAR_DELETE_FAILED, "头像删除失败");
            failure.initCause(e);
            throw failure;
        }
    }

    @Override
    public String avatarResourceUrl(long userId) {
        if (store == null) return null;
        return store.activeAvatar(userId) == null ? null : AVATAR_RESOURCE_PATH;
    }

    @Override
    public String avatarDownloadUrl(long userId) {
        if (store == null || storage == null)
            throw new IllegalStateException("avatar storage unavailable");
        PersonalDataRepository.AvatarRow avatar = store.activeAvatar(userId);
        if (avatar == null) throw new BusinessException(ErrorCode.USER_AVATAR_NOT_FOUND);
        try {
            String url = storage.presignedGet(bucket, avatar.storageKey(), Duration.ofMinutes(10));
            record(
                    userId,
                    "avatar",
                    Long.toString(avatar.avatarAssetId()),
                    "account.avatar.download",
                    "success",
                    null,
                    Map.of());
            return url;
        } catch (RuntimeException e) {
            failure(
                    userId,
                    "avatar",
                    Long.toString(avatar.avatarAssetId()),
                    "account.avatar.download",
                    ErrorCode.USER_AVATAR_DOWNLOAD_FAILED.code(),
                    e);
            throw new BusinessException(ErrorCode.USER_AVATAR_DOWNLOAD_FAILED, "头像访问地址不可用");
        }
    }

    private AvatarInput readAndValidateAvatar(
            String filename, String contentType, long declaredSize, InputStream input) {
        String normalizedType =
                contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
        if (filename == null
                || filename.isBlank()
                || filename.indexOf('\0') >= 0
                || filename.contains("/")
                || filename.contains("\\")
                || filename.equals(".")
                || filename.equals("..")
                || !List.of("image/jpeg", "image/png", "image/webp").contains(normalizedType))
            throw new BusinessException(ErrorCode.USER_AVATAR_INVALID, "头像文件类型不合法");
        if (declaredSize <= 0 || declaredSize > MAX_AVATAR_BYTES || input == null)
            throw new BusinessException(ErrorCode.USER_AVATAR_INVALID, "头像文件大小不合法");
        byte[] bytes;
        try {
            bytes = input.readNBytes(MAX_AVATAR_BYTES + 1);
        } catch (IOException e) {
            BusinessException failure =
                    new BusinessException(ErrorCode.USER_AVATAR_INVALID, "头像文件读取失败");
            failure.initCause(e);
            throw failure;
        }
        if (bytes.length == 0
                || bytes.length > MAX_AVATAR_BYTES
                || bytes.length != declaredSize
                || !matchesSignature(normalizedType, bytes))
            throw new BusinessException(ErrorCode.USER_AVATAR_INVALID, "头像文件内容不合法");
        ImageDimensions dimensions = imageDimensions(bytes);
        if (dimensions.width() <= 0
                || dimensions.height() <= 0
                || ((long) dimensions.width() * dimensions.height()) > MAX_AVATAR_PIXELS)
            throw new BusinessException(ErrorCode.USER_AVATAR_INVALID, "头像图片尺寸不合法");
        return new AvatarInput(
                bytes,
                normalizedType,
                safeFilename(filename),
                sha256(bytes),
                extension(normalizedType),
                dimensions);
    }

    private ImageDimensions imageDimensions(byte[] bytes) {
        try (ImageInputStream imageInput =
                ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (imageInput == null) throw invalidAvatar("无法解析头像图片");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) throw invalidAvatar("无法解析头像图片");
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || ((long) width * height) > MAX_AVATAR_PIXELS)
                    throw invalidAvatar("头像图片尺寸不合法");
                BufferedImage decoded = reader.read(0);
                if (decoded == null) throw invalidAvatar("无法解码头像图片");
                return new ImageDimensions(decoded.getWidth(), decoded.getHeight());
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof BusinessException business) throw business;
            throw invalidAvatar("无法解析头像图片");
        }
    }

    private boolean matchesSignature(String contentType, byte[] bytes) {
        return switch (contentType) {
            case "image/jpeg" ->
                    bytes.length >= 3
                            && (bytes[0] & 0xff) == 0xff
                            && (bytes[1] & 0xff) == 0xd8
                            && (bytes[2] & 0xff) == 0xff;
            case "image/png" ->
                    bytes.length >= 8
                            && bytes[0] == (byte) 0x89
                            && bytes[1] == 0x50
                            && bytes[2] == 0x4e
                            && bytes[3] == 0x47
                            && bytes[4] == 0x0d
                            && bytes[5] == 0x0a
                            && bytes[6] == 0x1a
                            && bytes[7] == 0x0a;
            case "image/webp" ->
                    bytes.length >= 12
                            && "RIFF".equals(new String(bytes, 0, 4, StandardCharsets.US_ASCII))
                            && "WEBP".equals(new String(bytes, 8, 4, StandardCharsets.US_ASCII));
            default -> false;
        };
    }

    private void compensateObject(String key, RuntimeException original) {
        try {
            storage.delete(bucket, key);
        } catch (RuntimeException cleanupFailure) {
            original.addSuppressed(cleanupFailure);
            log.error("avatar object compensation failed", cleanupFailure);
        }
    }

    private static String safeFilename(String filename) {
        String normalized = filename.replace('\\', '/');
        String base = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        return base.length() > 255 ? base.substring(0, 255) : base;
    }

    private static String extension(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw invalidAvatar("头像文件类型不合法");
        };
    }

    private static BusinessException invalidAvatar(String message) {
        return new BusinessException(ErrorCode.USER_AVATAR_INVALID, message);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private record AvatarInput(
            byte[] bytes,
            String contentType,
            String originalFilename,
            String sha256,
            String extension,
            ImageDimensions dimensions) {}

    private record ImageDimensions(int width, int height) {}

    @Transactional
    public long requestExport(long userId) {
        Long id = null;
        try {
            if (store == null) throw new IllegalStateException("export unavailable");
            id = ids.nextId();
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
        } catch (RuntimeException exception) {
            failure(
                    userId,
                    "export_job",
                    id == null ? null : Long.toString(id),
                    "account.data_export.request",
                    errorCode(exception),
                    exception);
            throw exception;
        }
    }

    @Transactional
    public long requestDeletion(long userId) {
        Long id = null;
        try {
            if (store == null) throw new IllegalStateException("account deletion unavailable");
            if (store.activeDeletionJobs(userId) > 0)
                throw new BusinessException(
                        ErrorCode.CONFLICT, "account deletion already requested");
            id = ids.nextId();
            store.insertDeletionJob(id, userId);
            store.disableUser(userId);
            store.revokeSessions(userId);
            store.revokeRefreshTokens(userId);
            record(
                    userId,
                    "user",
                    Long.toString(userId),
                    "account.deletion.request",
                    "success",
                    null,
                    Map.of());
            return id;
        } catch (RuntimeException exception) {
            failure(
                    userId,
                    "user",
                    Long.toString(userId),
                    "account.deletion.request",
                    errorCode(exception),
                    exception);
            throw exception;
        }
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
        try {
            if (store == null || storage == null)
                throw new IllegalStateException("export unavailable");
            int updated = store.consumeExport(userId, jobId);
            if (updated != 1)
                throw new BusinessException(
                        ErrorCode.CONFLICT, "export is unavailable, expired, or already consumed");
            String key = store.exportObjectKey(jobId);
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
            failure(
                    userId,
                    "export_job",
                    Long.toString(jobId),
                    "account.data_export.consume",
                    errorCode(e),
                    e);
            if (e instanceof BusinessException) throw e;
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
            long userId,
            String targetType,
            String targetId,
            String action,
            String errorCode,
            Exception exception) {
        if (audit != null)
            audit.recordFailure(
                    userId,
                    targetType,
                    targetId,
                    action,
                    "failed",
                    errorCode,
                    null,
                    null,
                    Map.of("exception_type", exception.getClass().getSimpleName()));
    }

    private static String errorCode(RuntimeException exception) {
        return exception instanceof BusinessException businessException
                ? businessException.errorCode().code()
                : ErrorCode.INTERNAL_ERROR.code();
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

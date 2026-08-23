package com.foodmate.application.account.port.out;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import java.util.List;

public interface PersonalDataRepository {
    void replaceAvatars(long userId);

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

    void clearAvatar(long userId);

    void setAvatarUrl(long userId, String url);

    List<String> activeAvatarKeys(long userId);

    AvatarRow activeAvatar(long userId);

    void deleteAvatars(long userId);

    void insertExportJob(long id, long userId);

    int activeDeletionJobs(long userId);

    void insertDeletionJob(long id, long userId);

    void disableUser(long userId);

    void revokeSessions(long userId);

    void revokeRefreshTokens(long userId);

    ExportRow findExport(long userId, long jobId);

    int consumeExport(long userId, long jobId);

    String exportObjectKey(long jobId);

    List<Long> queuedExports(int limit);

    List<Long> queuedDeletions(int limit);

    Long exportUser(long jobId);

    void startExport(long jobId);

    ExportUserData exportUserData(long userId);

    List<ExportProfileRow> exportProfile(long userId);

    List<ExportSessionRow> exportSessions(long userId);

    void completeExport(long jobId, String key);

    void failExport(long jobId);

    Long deletionUser(long jobId);

    void startDeletion(long jobId);

    List<String> deletionObjectKeys(long userId);

    void softDeleteUser(long userId);

    void softDeleteProfile(long userId);

    void softDeleteSessions(long userId);

    void softDeleteMessages(long userId);

    void softDeleteAvatars(long userId);

    void softDeleteExports(long userId);

    void completeDeletion(long jobId, long count);

    void failDeletion(long jobId, String code);

    record AvatarRow(long avatarAssetId, String storageKey, String mimeType) {}

    record ExportRow(
            long id,
            String status,
            Instant expiresAt,
            Instant completedAt,
            Instant consumedAt,
            String failureCode) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record ExportUserData(
            Long userId,
            String userNo,
            String username,
            String email,
            String nickname,
            String role,
            String status,
            Instant createdAt) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record ExportProfileRow(
            Long profileId,
            Long userId,
            String displayName,
            String gender,
            String birthday,
            String heightCm,
            String weightKg,
            String activityLevel,
            String dietGoal,
            Integer calorieTarget,
            Integer proteinTarget,
            String allergens,
            String dislikes,
            String preferredUnits,
            String profileJson,
            Instant createdAt,
            Instant updatedAt,
            Long createdBy,
            Long updatedBy,
            Boolean isDeleted,
            Instant deletedAt,
            Long deletedBy) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record ExportSessionRow(
            Long sessionId, String title, String mode, String status, Instant createdAt) {}

    record ExportDocument(
            ExportUserData user, List<ExportProfileRow> profile, List<ExportSessionRow> sessions) {}
}

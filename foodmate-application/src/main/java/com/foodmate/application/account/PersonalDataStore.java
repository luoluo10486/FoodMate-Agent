package com.foodmate.application.account;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface PersonalDataStore {
    void replaceAvatars(long userId);

    void insertAvatar(long id, long userId, String key, String mime, long size);

    void clearAvatar(long userId);

    void insertKnowledge(long id, String title, String key, long userId);

    List<String> activeAvatarKeys(long userId);

    void deleteAvatars(long userId);

    void insertExportJob(long id, long userId);

    int activeDeletionJobs(long userId);

    void insertDeletionJob(long id, long userId);

    void disableUser(long userId);

    void revokeSessions(long userId);

    ExportRow findExport(long userId, long jobId);

    int consumeExport(long userId, long jobId);

    String exportObjectKey(long jobId);

    List<Long> queuedExports(int limit);

    List<Long> queuedDeletions(int limit);

    Long exportUser(long jobId);

    void startExport(long jobId);

    Map<String, Object> exportUserData(long userId);

    List<Map<String, Object>> exportProfile(long userId);

    List<Map<String, Object>> exportSessions(long userId);

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

    record ExportRow(
            long id,
            String status,
            Instant expiresAt,
            Instant completedAt,
            Instant consumedAt,
            String failureCode) {}
}

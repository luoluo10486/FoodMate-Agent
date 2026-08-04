package com.foodmate.application.account.service;

import java.io.InputStream;
import java.time.Instant;

/** 个人数据、头像和账户数据任务用例接口。 */
public interface PersonalDataService {
    Avatar uploadAvatar(
            long userId, String filename, String contentType, long size, InputStream input);

    void deleteAvatar(long userId);

    long requestExport(long userId);

    long requestDeletion(long userId);

    ExportJob exportJob(long userId, long jobId);

    String consumeExport(long userId, long jobId);

    record Avatar(long avatarAssetId, String storageKey, String mimeType, long sizeBytes) {}

    record ExportJob(
            long exportJobId,
            String status,
            Instant expiresAt,
            Instant completedAt,
            Instant downloadConsumedAt,
            String failureCode) {}
}

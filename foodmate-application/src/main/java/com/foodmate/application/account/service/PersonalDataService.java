package com.foodmate.application.account.service;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.io.InputStream;
import java.time.Instant;

/** 个人数据、头像和账户数据任务用例接口。 */
public interface PersonalDataService {
    Avatar uploadAvatar(
            long userId, String filename, String contentType, long size, InputStream input);

    void deleteAvatar(long userId);

    String avatarResourceUrl(long userId);

    String avatarDownloadUrl(long userId);

    long requestExport(long userId);

    long requestDeletion(long userId);

    ExportJob exportJob(long userId, long jobId);

    String consumeExport(long userId, long jobId);

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Avatar(long avatarAssetId, String avatarUrl, String mimeType, long sizeBytes) {}

    record ExportJob(
            long exportJobId,
            String status,
            Instant expiresAt,
            Instant completedAt,
            Instant downloadConsumedAt,
            String failureCode) {}
}

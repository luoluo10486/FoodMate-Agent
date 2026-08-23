package com.foodmate.application.account.port.out;

import java.time.Instant;
import java.util.List;

/** Persistence contract for bounded, redacted administrator exports. */
public interface AdminExportRepository {
    int insertJob(
            long jobId, long operatorId, String resource, String filtersJson, String fieldsJson);

    JobRow find(long jobId);

    List<Long> queuedJobs(int limit);

    int startJob(long jobId);

    int completeJob(long jobId, String objectKey);

    int failJob(long jobId, String failureCode);

    int consumeJob(long operatorId, long jobId);

    String objectKey(long jobId);

    record JobRow(
            long jobId,
            long operatorId,
            String resource,
            String filtersJson,
            String fieldsJson,
            String status,
            String objectKey,
            Instant expiresAt,
            Instant completedAt,
            Instant consumedAt,
            String failureCode) {}
}

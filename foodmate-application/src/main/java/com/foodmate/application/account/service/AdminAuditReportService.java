package com.foodmate.application.account.service;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import java.util.List;

/** 生成不包含业务载荷的定期运营审计只读报告。 */
public interface AdminAuditReportService {
    Report current();

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Report(
            Instant generatedAt, int staleThresholdMinutes, String status, List<Check> checks) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Check(
            String code,
            String status,
            long pendingCount,
            long failedCount,
            Instant oldestAt,
            List<String> reasonCodes) {}
}

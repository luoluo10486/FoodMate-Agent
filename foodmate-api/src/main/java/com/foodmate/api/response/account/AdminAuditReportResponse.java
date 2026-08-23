package com.foodmate.api.response.account;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.foodmate.application.account.service.AdminAuditReportService;
import java.time.Instant;
import java.util.List;

/** 管理员运营审计报告响应；仅包含聚合状态和稳定原因码。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AdminAuditReportResponse(
        Instant generatedAt, int staleThresholdMinutes, String status, List<Check> checks) {
    public static AdminAuditReportResponse from(AdminAuditReportService.Report report) {
        return new AdminAuditReportResponse(
                report.generatedAt(),
                report.staleThresholdMinutes(),
                report.status(),
                report.checks().stream()
                        .map(
                                check ->
                                        new Check(
                                                check.code(),
                                                check.status(),
                                                check.pendingCount(),
                                                check.failedCount(),
                                                check.oldestAt(),
                                                check.reasonCodes()))
                        .toList());
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Check(
            String code,
            String status,
            long pendingCount,
            long failedCount,
            Instant oldestAt,
            List<String> reasonCodes) {}
}

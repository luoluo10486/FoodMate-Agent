package com.foodmate.api.response.account;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.foodmate.application.account.service.AdminOperationalQueryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Redacted trace detail response; payloads and prompts are intentionally absent. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AdminTraceDetailResponse(
        AdminOperationalQueryService.Trace summary, List<Span> spans) {
    public static AdminTraceDetailResponse from(AdminOperationalQueryService.TraceDetail detail) {
        return new AdminTraceDetailResponse(
                detail.summary(), detail.spans().stream().map(Span::from).toList());
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Span(
            String spanId,
            String spanType,
            String name,
            String service,
            String status,
            Instant startedAt,
            Instant finishedAt,
            BigDecimal durationMs,
            String errorCode,
            Long sequenceNo) {
        private static Span from(AdminOperationalQueryService.TraceSpan span) {
            return new Span(
                    span.spanId(),
                    span.spanType(),
                    span.name(),
                    span.service(),
                    span.status(),
                    span.startedAt(),
                    span.finishedAt(),
                    span.durationMs(),
                    span.errorCode(),
                    span.sequenceNo());
        }
    }
}

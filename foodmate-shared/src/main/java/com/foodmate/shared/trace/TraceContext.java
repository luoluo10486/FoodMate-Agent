package com.foodmate.shared.trace;

import java.util.UUID;

public record TraceContext(
        String requestId,
        String traceId,
        String sessionId,
        String agentRunId
) {
    public static TraceContext of(String requestId, String traceId) {
        return new TraceContext(requestId, traceId, null, null);
    }

    public static TraceContext newContext() {
        String requestId = "req_" + UUID.randomUUID().toString().replace("-", "");
        String traceId = "trace_" + UUID.randomUUID().toString().replace("-", "");
        return of(requestId, traceId);
    }
}


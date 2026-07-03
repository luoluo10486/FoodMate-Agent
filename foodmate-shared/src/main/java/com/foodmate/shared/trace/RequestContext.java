package com.foodmate.shared.trace;

public record RequestContext(
        Long userId,
        String role,
        String status,
        TraceContext traceContext
) {
}


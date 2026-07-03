package com.foodmate.shared.trace;

/**
 * 请求上下文，承载当前用户与链路信息。
 */
public record RequestContext(
        Long userId,
        String role,
        String status,
        TraceContext traceContext
) {
}

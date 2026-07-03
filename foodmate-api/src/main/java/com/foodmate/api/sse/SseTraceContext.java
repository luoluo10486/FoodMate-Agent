package com.foodmate.api.sse;

import com.foodmate.shared.trace.TraceContext;
import com.foodmate.shared.trace.TraceContextHolder;

public final class SseTraceContext {
    private SseTraceContext() {
    }

    public static Runnable capture(Runnable runnable) {
        return with(TraceContextHolder.currentOrNew(), runnable);
    }

    public static Runnable with(TraceContext traceContext, Runnable runnable) {
        return () -> TraceContextHolder.runWith(traceContext, runnable);
    }
}

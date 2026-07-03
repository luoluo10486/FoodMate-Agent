package com.foodmate.shared.trace;

public final class TraceContextHolder {
    private static final ThreadLocal<TraceContext> CURRENT = new ThreadLocal<>();

    private TraceContextHolder() {
    }

    public static void set(TraceContext traceContext) {
        CURRENT.set(traceContext);
    }

    public static TraceContext currentOrNew() {
        TraceContext traceContext = CURRENT.get();
        if (traceContext == null) {
            traceContext = TraceContext.newContext();
            CURRENT.set(traceContext);
        }
        return traceContext;
    }

    public static void clear() {
        CURRENT.remove();
    }
}

